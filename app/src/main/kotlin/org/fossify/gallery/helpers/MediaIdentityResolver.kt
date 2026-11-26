package org.fossify.gallery.helpers

import android.content.Context
import android.database.Cursor
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.ArrayDeque

/**
 * Finds a file again after it was renamed or moved.
 *
 * The resolution goes through layers from the cheapest and most certain to the broadest:
 *
 *  0. Batch folder-relocation cache (when another file from the same moved folder was already found).
 *  1. The cached path itself (if it exists on disk).
 *  2. The MediaStore `_ID`, which is preserved by renames and moves on the same volume.
 *  3. A targeted MediaStore `SIZE` query (or batch `SIZE IN (...)` in a session) confirmed by the
 *     content fingerprint.
 *  4. A bounded, size-prefiltered filesystem fallback for hidden (`.nomedia`), vault (`.fenc`), or
 *     not-yet-indexed folders — scanning sibling/ancestor directories and standard media roots
 *     with a strict `stat()` cap so performance stays bounded.
 */
object MediaIdentityResolver {
    /** How deep we search below fallback roots when scanning for candidates. */
    internal const val MAX_SCAN_DEPTH = 4

    /** Maximum number of files inspected (`stat()` only) during a single fallback scan. */
    internal const val MAX_FALLBACK_FILES_VISITED = 15000

    /** Maximum number of size-matched candidates retained per size bucket. */
    internal const val MAX_CANDIDATES_PER_SIZE = 40

    /** Chunk size for SQLite `IN (...)` queries against MediaStore. */
    internal const val MEDIA_STORE_BATCH_SIZE = 200

    internal val SKIPPED_DIR_NAMES = hashSetOf(
        ".thumbnails",
        "recycle_bin"
    )

    /** Result of a successful resolution. */
    data class Resolution(
        val path: String,
        val identity: MediaIdentity,
        /** True when the match was confirmed by the content fingerprint. */
        val confirmed: Boolean,
        val mediaStoreId: Long = 0L
    )

    data class MediaStoreCandidate(
        val mediaStoreId: Long,
        val path: String,
        val displayName: String,
        val dateAddedSec: Long = 0L
    )

    /**
     * Per-repair-pass cache that turns O(N) folder moves into O(1) lookups and batches
     * MediaStore queries and fallback scans across all missing favorites and virtual album items.
     */
    class ResolutionSession(
        val allowFilesystemFallback: Boolean = true
    ) {
        private val relocatedFolders = HashMap<String, String>()
        private val mediaStoreIdMap = HashMap<Long, String>()
        private val queriedMediaStoreIds = HashSet<Long>()
        private val mediaStoreSizeMap = HashMap<Long, MutableList<MediaStoreCandidate>>()
        private val queriedSizes = HashSet<Long>()
        private var fallbackSizeIndex: HashMap<Long, MutableList<File>>? = null
        private val targetSizes = HashSet<Long>()

        fun registerMissingSize(size: Long) {
            if (size > 0L) {
                targetSizes.add(size)
            }
        }

        fun recordRelocation(hintPaths: List<String>, resolvedPath: String) {
            val resolvedFile = File(resolvedPath)
            val newParent = resolvedFile.parent ?: return
            for (hint in hintPaths) {
                if (hint.isEmpty()) continue
                val oldParent = File(hint).parent ?: continue
                if (oldParent.isEmpty() || oldParent.equals(newParent, ignoreCase = true)) continue
                if (!File(oldParent).isDirectory) {
                    relocatedFolders[oldParent.lowercase()] = newParent

                    // If an ancestor folder with the same name moved (e.g. /Pictures/Trip/Day1 ->
                    // /DCIM/Trip/Day1), map that ancestor too so sibling subfolders resolve in O(1).
                    var oldAnc = File(oldParent).parentFile
                    var newAnc = File(newParent).parentFile
                    var levels = 0
                    while (levels < 2 && oldAnc != null && newAnc != null) {
                        if (oldAnc.name.isEmpty() || !oldAnc.name.equals(newAnc.name, ignoreCase = true)) {
                            break
                        }
                        if (!oldAnc.isDirectory && newAnc.isDirectory) {
                            relocatedFolders.putIfAbsent(oldAnc.absolutePath.lowercase(), newAnc.absolutePath)
                        }
                        oldAnc = oldAnc.parentFile
                        newAnc = newAnc.parentFile
                        levels++
                    }
                }
            }
        }

        fun resolveFromRelocatedFolder(hintPaths: List<String>, identity: MediaIdentity): Resolution? {
            if (relocatedFolders.isEmpty()) return null
            for (hint in hintPaths) {
                if (hint.isEmpty()) continue
                val hintFile = File(hint)
                val oldParent = hintFile.parent ?: continue
                val fileName = hintFile.name
                if (fileName.isEmpty()) continue

                val directTarget = relocatedFolders[oldParent.lowercase()]
                if (directTarget != null) {
                    val candidate = File(directTarget, fileName)
                    if (MediaIdentityScoring.matchesFile(candidate, identity)) {
                        return Resolution(candidate.absolutePath, identity, confirmed = identity.hasFingerprint)
                    }
                }

                // Check mapped ancestors for nested subfolders (e.g. /OldRoot/Sub/img.jpg -> /NewRoot/Sub/img.jpg)
                val oldParentLower = oldParent.lowercase()
                for ((oldRootLower, newRoot) in relocatedFolders) {
                    val prefix = "$oldRootLower${File.separator}"
                    if (oldParentLower.startsWith(prefix)) {
                        val relSub = oldParent.substring(oldRootLower.length)
                        val candidate = File("$newRoot$relSub", fileName)
                        if (MediaIdentityScoring.matchesFile(candidate, identity)) {
                            return Resolution(candidate.absolutePath, identity, confirmed = identity.hasFingerprint)
                        }
                    }
                }
            }
            return null
        }

        /**
         * Preloads MediaStore `_ID` and `SIZE` matches for all missing [identities] using batched
         * `IN (...)` queries instead of issuing one query per file.
         */
        fun preloadMediaStore(context: Context, identities: List<MediaIdentity>) {
            if (identities.isEmpty()) return

            val missingIds = ArrayList<Long>()
            val missingSizes = ArrayList<Long>()
            for (id in identities) {
                if (id.mediaStoreId > 0L && queriedMediaStoreIds.add(id.mediaStoreId)) {
                    missingIds.add(id.mediaStoreId)
                }
                if (id.size > 0L) {
                    targetSizes.add(id.size)
                    if (queriedSizes.add(id.size)) {
                        missingSizes.add(id.size)
                    }
                }
            }

            if (missingIds.isNotEmpty()) {
                MediaIdentityIndex.queryMediaStoreByIds(context, missingIds, mediaStoreIdMap)
            }
            if (missingSizes.isNotEmpty()) {
                MediaIdentityIndex.queryMediaStoreBySizes(context, missingSizes, mediaStoreSizeMap)
            }
        }

        fun getCachedMediaStorePath(mediaStoreId: Long): String? {
            return if (queriedMediaStoreIds.contains(mediaStoreId)) mediaStoreIdMap[mediaStoreId] else null
        }

        fun isMediaStoreIdQueried(mediaStoreId: Long): Boolean = queriedMediaStoreIds.contains(mediaStoreId)

        fun getCachedSizeCandidates(size: Long): List<MediaStoreCandidate>? {
            return if (queriedSizes.contains(size)) mediaStoreSizeMap[size].orEmpty() else null
        }

        fun getFallbackCandidates(
            identity: MediaIdentity,
            roots: List<File>
        ): List<File> {
            if (identity.size > 0L) {
                targetSizes.add(identity.size)
            }
            if (fallbackSizeIndex == null) {
                fallbackSizeIndex = MediaIdentityIndex.buildSizeIndex(roots, targetSizes)
            }
            val index = fallbackSizeIndex ?: return emptyList()
            return if (identity.size > 0L) {
                index[identity.size].orEmpty()
            } else {
                index.values.flatten().take(MAX_CANDIDATES_PER_SIZE)
            }
        }
    }


    /**
     * Resolves the file behind [identity].
     *
     * @param hintPaths paths we already know about, tried first without any scanning.
     * @param searchRoots folders that are scanned for candidates when MediaStore does not resolve.
     * @param session optional shared session that caches relocated folders and MediaStore batches.
     * @param excludedPaths lowercased/raw paths already claimed in the same target container so they
     *   are not overwritten or mistakenly bound to another item's moved reference.
     * @param allowAmbiguous when false, defers items that have multiple matching candidates on disk so
     *   unambiguous siblings in a batch pass can reveal the relocated parent folder first. When true,
     *   ranks verified duplicates by folder/metadata affinity so a valid file is still loaded.
     */
    fun resolve(
        context: Context,
        identity: MediaIdentity,
        hintPaths: List<String> = emptyList(),
        searchRoots: List<String> = emptyList(),
        session: ResolutionSession? = null,
        excludedPaths: Set<String> = emptySet(),
        allowAmbiguous: Boolean = true
    ): Resolution? {
        if (!identity.isUsable) {
            return null
        }

        val excludedLower = if (excludedPaths.isEmpty()) emptySet() else excludedPaths.mapTo(HashSet()) { it.lowercase() }

        // Layer 0a: the cached path still holds.
        for (path in hintPaths) {
            if (path.isNotEmpty() && path.lowercase() !in excludedLower) {
                val file = File(path)
                if (file.isFile) {
                    return Resolution(path, identity, confirmed = false, mediaStoreId = identity.mediaStoreId)
                }
            }
        }

        // Layer 0b: another item from the same moved physical folder already revealed its new home.
        session?.resolveFromRelocatedFolder(hintPaths, identity)?.let { res ->
            if (res.path.lowercase() !in excludedLower) {
                return res
            }
        }

        // Layer 1: the MediaStore id is authoritative (verified against size/fingerprint).
        if (identity.mediaStoreId > 0L) {
            val msPath = if (session != null && session.isMediaStoreIdQueried(identity.mediaStoreId)) {
                session.getCachedMediaStorePath(identity.mediaStoreId)
            } else {
                MediaIdentityIndex.findByMediaStoreId(context, identity.mediaStoreId)
            }
            if (msPath != null && msPath.lowercase() !in excludedLower) {
                val file = File(msPath)
                if (file.isFile && (identity.size <= 0L || file.length() == identity.size)) {
                    val fpMatches = !identity.hasFingerprint || MediaIdentity.computeFingerprint(file) == identity.fingerprint
                    if (fpMatches) {
                        session?.recordRelocation(hintPaths, msPath)
                        return Resolution(
                            path = msPath,
                            identity = identity,
                            confirmed = identity.hasFingerprint,
                            mediaStoreId = identity.mediaStoreId
                        )
                    }
                }
            }
        }

        // If every original parent folder still sits on disk (e.g. files inside it were deleted or
        // emptied out rather than the folder moving), never wander into foreign folders via SIZE
        // search. Only allow finding in-folder file renames inside those existing parent folders.
        val existingParentDirs = collectExistingParentDirs(hintPaths)
        val allHintParentsExist = hintPaths.any { it.isNotEmpty() } &&
            hintPaths.filter { it.isNotEmpty() }.all { File(it).parentFile?.isDirectory == true }

        // Layer 1.5: query MediaStore by exact byte size, prioritizing matching filename, then
        // confirm with the content fingerprint and folder continuity.
        if (identity.size > 0L && !allHintParentsExist) {
            val sizeCandidates = if (session != null) {
                session.getCachedSizeCandidates(identity.size) ?: MediaIdentityIndex.queryMediaStoreBySingleSize(context, identity.size)
            } else {
                MediaIdentityIndex.queryMediaStoreBySingleSize(context, identity.size)
            }
            resolveFromMediaStoreCandidates(
                candidates = sizeCandidates,
                identity = identity,
                hintPaths = hintPaths,
                excludedLower = excludedLower,
                allowAmbiguous = allowAmbiguous
            )?.let { (res, highConfidenceRelocation) ->
                if (highConfidenceRelocation) {
                    session?.recordRelocation(hintPaths, res.path)
                }
                return res
            }
        }

        // Layer 2: bounded filesystem fallback (handles hidden/.nomedia, vault .fenc, or unindexed files).
        if (session != null && !session.allowFilesystemFallback && !allHintParentsExist) {
            return null
        }

        val extraRoots = if (allHintParentsExist) emptyList() else MediaIdentityIndex.buildAndroidFallbackRoots(context, hintPaths)
        val effectiveSearchRoots = if (allHintParentsExist) existingParentDirs else searchRoots
        return resolveInFilesystem(
            identity = identity,
            hintPaths = hintPaths,
            searchRoots = effectiveSearchRoots,
            session = if (allHintParentsExist) null else session,
            extraRoots = extraRoots,
            excludedPaths = excludedPaths,
            allowAmbiguous = allowAmbiguous
        )
    }

    /**
     * Resolves [identity] on the local filesystem without querying Android MediaStore.
     * Expands missing [searchRoots] to existing ancestor/sibling directories and [extraRoots],
     * pre-filtering strictly by `file.length() == identity.size` before computing fingerprints.
     */
    fun resolveInFilesystem(
        identity: MediaIdentity,
        hintPaths: List<String> = emptyList(),
        searchRoots: List<String> = emptyList(),
        session: ResolutionSession? = null,
        extraRoots: List<String> = emptyList(),
        excludedPaths: Set<String> = emptySet(),
        allowAmbiguous: Boolean = true
    ): Resolution? {
        if (!identity.isUsable) {
            return null
        }

        val excludedLower = if (excludedPaths.isEmpty()) emptySet() else excludedPaths.mapTo(HashSet()) { it.lowercase() }

        for (path in hintPaths) {
            if (path.isNotEmpty() && path.lowercase() !in excludedLower && File(path).isFile) {
                return Resolution(path, identity, confirmed = false, mediaStoreId = identity.mediaStoreId)
            }
        }

        session?.resolveFromRelocatedFolder(hintPaths, identity)?.let { res ->
            if (res.path.lowercase() !in excludedLower) {
                return res
            }
        }

        val existingParentDirs = collectExistingParentDirs(hintPaths)
        val allHintParentsExist = hintPaths.any { it.isNotEmpty() } &&
            hintPaths.filter { it.isNotEmpty() }.all { File(it).parentFile?.isDirectory == true }

        val candidates: List<File> = if (allHintParentsExist) {
            // Existing source folder guard: only inspect files directly inside the existing parent(s)
            // for in-place file renames; never scan or pull identical copies from other folders.
            existingParentDirs.flatMap { dirPath ->
                File(dirPath).listFiles()?.filter { it.isFile } ?: emptyList()
            }
        } else {
            val roots = MediaIdentityIndex.expandSearchRoots(hintPaths, searchRoots, extraRoots)
            if (roots.isEmpty()) {
                return null
            }

            if (session != null) {
                session.getFallbackCandidates(identity, roots)
            } else {
                val targetSizes = if (identity.size > 0L) setOf(identity.size) else emptySet()
                val index = MediaIdentityIndex.buildSizeIndex(roots, targetSizes)
                if (identity.size > 0L) {
                    index[identity.size].orEmpty()
                } else {
                    index.values.flatten().take(MAX_CANDIDATES_PER_SIZE)
                }
            }
        }

        if (candidates.isEmpty()) {
            return null
        }

        val preferredName = hintPaths.firstOrNull { it.isNotEmpty() }?.let { File(it).name } ?: ""
        val ordered = if (preferredName.isNotEmpty()) {
            candidates.sortedByDescending { it.name.equals(preferredName, ignoreCase = true) }
        } else {
            candidates
        }

        val confirmedMatches = ArrayList<MediaIdentityScoring.ResolvedCandidate>(2)
        val unconfirmedMatches = ArrayList<MediaIdentityScoring.ResolvedCandidate>(2)

        for (file in ordered) {
            if (!file.isFile) continue
            if (identity.size > 0L && file.length() != identity.size) continue

            if (identity.hasFingerprint) {
                if (MediaIdentity.computeFingerprint(file) == identity.fingerprint) {
                    confirmedMatches.add(
                        MediaIdentityScoring.ResolvedCandidate(
                            path = file.absolutePath,
                            mediaStoreId = 0L,
                            lastModified = file.lastModified(),
                            name = file.name
                        )
                    )
                }
            } else if (MediaIdentityIndex.matchesCheapSignature(file, identity)) {
                unconfirmedMatches.add(
                    MediaIdentityScoring.ResolvedCandidate(
                        path = file.absolutePath,
                        mediaStoreId = 0L,
                        lastModified = file.lastModified(),
                        name = file.name
                    )
                )
            }
        }

        val resolved = resolveMatchLists(
            confirmedMatches = confirmedMatches,
            unconfirmedMatches = unconfirmedMatches,
            identity = identity,
            hintPaths = hintPaths,
            excludedLower = excludedLower,
            allowAmbiguous = allowAmbiguous
        ) ?: return null

        val (res, highConfidenceRelocation) = resolved
        if (highConfidenceRelocation && !allHintParentsExist) {
            session?.recordRelocation(hintPaths, res.path)
        }
        return res
    }

    private fun collectExistingParentDirs(hintPaths: List<String>): List<String> {
        val dirs = LinkedHashSet<String>()
        for (path in hintPaths) {
            if (path.isEmpty()) continue
            val parent = File(path).parentFile
            if (parent != null && parent.isDirectory) {
                dirs.add(parent.absolutePath)
            }
        }
        return dirs.toList()
    }

    private fun resolveFromMediaStoreCandidates(
        candidates: List<MediaStoreCandidate>,
        identity: MediaIdentity,
        hintPaths: List<String>,
        excludedLower: Set<String>,
        allowAmbiguous: Boolean
    ): Pair<Resolution, Boolean>? {
        if (candidates.isEmpty()) return null

        val preferredName = hintPaths.firstOrNull { it.isNotEmpty() }?.let { File(it).name } ?: ""
        val ordered = if (preferredName.isNotEmpty()) {
            candidates.sortedByDescending { it.displayName.equals(preferredName, ignoreCase = true) }
        } else {
            candidates
        }

        val confirmedMatches = ArrayList<MediaIdentityScoring.ResolvedCandidate>(2)
        val unconfirmedMatches = ArrayList<MediaIdentityScoring.ResolvedCandidate>(2)

        for (candidate in ordered) {
            val file = File(candidate.path)
            if (!file.isFile || file.length() != identity.size) continue

            val entry = MediaIdentityScoring.ResolvedCandidate(
                path = candidate.path,
                mediaStoreId = candidate.mediaStoreId,
                lastModified = file.lastModified(),
                name = file.name,
                dateAddedSec = candidate.dateAddedSec
            )
            if (identity.hasFingerprint) {
                if (MediaIdentity.computeFingerprint(file) == identity.fingerprint) {
                    confirmedMatches.add(entry)
                }
            } else if (MediaIdentityIndex.matchesCheapSignature(file, identity)) {
                unconfirmedMatches.add(entry)
            }
        }

        return resolveMatchLists(
            confirmedMatches = confirmedMatches,
            unconfirmedMatches = unconfirmedMatches,
            identity = identity,
            hintPaths = hintPaths,
            excludedLower = excludedLower,
            allowAmbiguous = allowAmbiguous
        )
    }

    private fun resolveMatchLists(
        confirmedMatches: List<MediaIdentityScoring.ResolvedCandidate>,
        unconfirmedMatches: List<MediaIdentityScoring.ResolvedCandidate>,
        identity: MediaIdentity,
        hintPaths: List<String>,
        excludedLower: Set<String>,
        allowAmbiguous: Boolean
    ): Pair<Resolution, Boolean>? {
        val activeConfirmed = confirmedMatches.filter { it.path.lowercase() !in excludedLower }
        if (activeConfirmed.isNotEmpty()) {
            val selected = MediaIdentityScoring.selectBestCandidate(
                candidates = activeConfirmed,
                identity = identity,
                hintPaths = hintPaths,
                allowAmbiguous = allowAmbiguous
            ) ?: return null
            val match = selected.candidate
            return Resolution(
                path = match.path,
                identity = identity,
                confirmed = true,
                mediaStoreId = match.mediaStoreId
            ) to selected.highConfidenceRelocation
        }

        if (confirmedMatches.isEmpty()) {
            val activeUnconfirmed = unconfirmedMatches.filter { it.path.lowercase() !in excludedLower }
            if (activeUnconfirmed.isNotEmpty()) {
                val canDisambiguateUnconfirmed = activeUnconfirmed.size == 1 ||
                    (identity.size > 0L && identity.lastModified > 0L)
                if (!canDisambiguateUnconfirmed) {
                    return null
                }
                val selected = MediaIdentityScoring.selectBestCandidate(
                    candidates = activeUnconfirmed,
                    identity = identity,
                    hintPaths = hintPaths,
                    allowAmbiguous = allowAmbiguous
                ) ?: return null
                val match = selected.candidate
                return Resolution(
                    path = match.path,
                    identity = identity,
                    confirmed = false,
                    mediaStoreId = match.mediaStoreId
                ) to selected.highConfidenceRelocation
            }
        }

        return null
    }

    fun findInFolder(identity: MediaIdentity, folder: File): String? {
        if (!folder.isDirectory) {
            return null
        }

        val children = folder.listFiles() ?: return null
        for (child in children) {
            if (!child.isFile) {
                continue
            }

            if (identity.size > 0L && child.length() != identity.size) {
                continue
            }

            if (!identity.hasFingerprint) {
                if (MediaIdentityIndex.matchesCheapSignature(child, identity)) {
                    return child.absolutePath
                }
                continue
            }

            if (MediaIdentity.computeFingerprint(child) == identity.fingerprint) {
                return child.absolutePath
            }
        }

        return null
    }
}
