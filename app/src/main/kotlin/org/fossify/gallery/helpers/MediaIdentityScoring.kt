package org.fossify.gallery.helpers

import android.content.Context
import android.database.Cursor
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.ArrayDeque


/**
 * Candidate ranking, folder continuity scoring, and signature matching extracted from [MediaIdentityResolver].
 */
internal object MediaIdentityScoring {

    internal data class ResolvedCandidate(
        val path: String,
        val mediaStoreId: Long,
        val lastModified: Long,
        val name: String,
        val dateAddedSec: Long = 0L
    )

    internal data class CandidateSelection(
        val candidate: ResolvedCandidate,
        val highConfidenceRelocation: Boolean
    )

    internal data class CandidateScore(
        val exactMediaStoreId: Int,
        val inodeMatches: Int,
        val matchedAnchorCount: Int,
        val trailingFolderMatches: Int,
        val lastModifiedMatch: Int,
        val folderNameSimilarity: Int,
        val commonPrefixSegments: Int,
        val exactFileNameMatch: Int,
        val pathLength: Int,
        val normalizedPath: String,
        val hasPositiveContinuity: Boolean
    ) : Comparable<CandidateScore> {
        override fun compareTo(other: CandidateScore): Int {
            if (exactMediaStoreId != other.exactMediaStoreId) return other.exactMediaStoreId.compareTo(exactMediaStoreId)
            if (inodeMatches != other.inodeMatches) return other.inodeMatches.compareTo(inodeMatches)
            if (matchedAnchorCount != other.matchedAnchorCount) return other.matchedAnchorCount.compareTo(matchedAnchorCount)
            if (trailingFolderMatches != other.trailingFolderMatches) return other.trailingFolderMatches.compareTo(trailingFolderMatches)
            if (lastModifiedMatch != other.lastModifiedMatch) return other.lastModifiedMatch.compareTo(lastModifiedMatch)
            if (folderNameSimilarity != other.folderNameSimilarity) return other.folderNameSimilarity.compareTo(folderNameSimilarity)
            if (commonPrefixSegments != other.commonPrefixSegments) return other.commonPrefixSegments.compareTo(commonPrefixSegments)
            if (exactFileNameMatch != other.exactFileNameMatch) return other.exactFileNameMatch.compareTo(exactFileNameMatch)
            if (pathLength != other.pathLength) return pathLength.compareTo(other.pathLength)
            return normalizedPath.compareTo(other.normalizedPath)
        }
    }

    internal fun selectBestCandidate(
        candidates: List<ResolvedCandidate>,
        identity: MediaIdentity,
        hintPaths: List<String>,
        allowAmbiguous: Boolean
    ): CandidateSelection? {
        if (candidates.isEmpty()) return null

        val folderMatchCache = HashMap<String, FolderIdentity.MatchResult>(4)
        val scored = candidates.map { it to scoreCandidate(it, identity, hintPaths, folderMatchCache) }
        val eligible = scored.filter { it.second.hasPositiveContinuity }
        if (eligible.isEmpty()) {
            return null
        }

        if (eligible.size == 1) {
            val (winner, score) = eligible[0]
            val highConfidence = score.exactMediaStoreId > 0 ||
                score.inodeMatches > 0 ||
                score.matchedAnchorCount > 0 ||
                score.trailingFolderMatches > 0 ||
                score.folderNameSimilarity >= 50
            return CandidateSelection(winner, highConfidenceRelocation = highConfidence)
        }

        if (identity.mediaStoreId > 0L) {
            val idMatches = eligible.filter { it.first.mediaStoreId == identity.mediaStoreId }
            if (idMatches.size == 1) {
                return CandidateSelection(idMatches[0].first, highConfidenceRelocation = true)
            }
        }

        if (!allowAmbiguous) {
            return null
        }

        var bestPair = eligible[0]
        for (i in 1 until eligible.size) {
            val pair = eligible[i]
            if (pair.second < bestPair.second) {
                bestPair = pair
            }
        }

        val (bestCandidate, bestScore) = bestPair
        val highConfidenceRelocation = bestScore.exactMediaStoreId > 0 ||
            bestScore.inodeMatches > 0 ||
            bestScore.matchedAnchorCount > 0 ||
            bestScore.trailingFolderMatches > 0 ||
            (bestScore.folderNameSimilarity > 0 && bestScore.commonPrefixSegments > 0)
        return CandidateSelection(bestCandidate, highConfidenceRelocation = highConfidenceRelocation)
    }

    internal fun scoreCandidate(
        candidate: ResolvedCandidate,
        identity: MediaIdentity,
        hintPaths: List<String>,
        folderMatchCache: MutableMap<String, FolderIdentity.MatchResult>
    ): CandidateScore {
        val exactMediaStoreId = if (identity.mediaStoreId > 0L && candidate.mediaStoreId == identity.mediaStoreId) 1 else 0
        val lastModifiedMatch = if (identity.lastModified > 0L &&
            CryptoManager.areTimestampsEquivalent(candidate.lastModified, identity.lastModified)
        ) 1 else 0

        val candFile = File(candidate.path)
        val candParent = candFile.parentFile
        val candParentKey = candParent?.absolutePath?.lowercase().orEmpty()
        val folderMatch = if (identity.hasFolderEvidence && candParent != null) {
            folderMatchCache.getOrPut(candParentKey) {
                FolderIdentity.matchesCandidateParent(
                    candidateParent = candParent,
                    excludeFile = candFile,
                    expectedInode = identity.folderInode,
                    serializedAnchors = identity.folderSignature
                )
            }
        } else {
            FolderIdentity.MatchResult(inodeMatches = false, matchedAnchorCount = 0)
        }

        val inodeMatches = if (folderMatch.inodeMatches) 1 else 0
        val matchedAnchorCount = folderMatch.matchedAnchorCount

        val candParentSegs = splitPathSegments(candFile.parent ?: "")
        val candParentName = candParentSegs.lastOrNull().orEmpty()

        var bestTrailing = 0
        var bestNameSim = 0
        var bestPrefix = 0
        var bestExactFileName = 0
        var hasSourceHint = false
        var inExistingHintParent = false

        for (hint in hintPaths) {
            if (hint.isEmpty()) continue
            hasSourceHint = true
            val hintFile = File(hint)
            val hintParent = hintFile.parentFile
            if (hintParent != null && hintParent.isDirectory && candParent != null &&
                candParent.absolutePath.equals(hintParent.absolutePath, ignoreCase = true)
            ) {
                inExistingHintParent = true
            }

            val hintParentSegs = splitPathSegments(hintFile.parent ?: "")
            val hintParentName = hintParentSegs.lastOrNull().orEmpty()

            val trailing = countTrailingMatchingSegments(hintParentSegs, candParentSegs)
            val nameSim = computeFolderNameSimilarity(hintParentName, candParentName)
            val prefix = countCommonPrefixSegments(hintParentSegs, candParentSegs)
            val exactName = if (hintFile.name.isNotEmpty() && candidate.name.equals(hintFile.name, ignoreCase = true)) 1 else 0

            if (trailing > bestTrailing ||
                (trailing == bestTrailing && nameSim > bestNameSim) ||
                (trailing == bestTrailing && nameSim == bestNameSim && prefix > bestPrefix) ||
                (trailing == bestTrailing && nameSim == bestNameSim && prefix == bestPrefix && exactName > bestExactFileName)
            ) {
                bestTrailing = trailing
                bestNameSim = nameSim
                bestPrefix = prefix
                bestExactFileName = exactName
            }
        }

        val isPreExistingForeignCopy = candidate.mediaStoreId > 0L &&
            identity.mediaStoreId > 0L &&
            candidate.mediaStoreId != identity.mediaStoreId &&
            candidate.dateAddedSec > 0L &&
            identity.capturedAtSec > 0L &&
            candidate.dateAddedSec < identity.capturedAtSec - 5L

        val hasPositiveContinuity = !hasSourceHint ||
            exactMediaStoreId > 0 ||
            inExistingHintParent ||
            inodeMatches > 0 ||
            matchedAnchorCount > 0 ||
            (!isPreExistingForeignCopy && (bestTrailing > 0 || bestNameSim >= 50))

        val normalized = candidate.path.replace('\\', '/').lowercase()
        return CandidateScore(
            exactMediaStoreId = exactMediaStoreId,
            inodeMatches = inodeMatches,
            matchedAnchorCount = matchedAnchorCount,
            trailingFolderMatches = bestTrailing,
            lastModifiedMatch = lastModifiedMatch,
            folderNameSimilarity = bestNameSim,
            commonPrefixSegments = bestPrefix,
            exactFileNameMatch = bestExactFileName,
            pathLength = normalized.length,
            normalizedPath = normalized,
            hasPositiveContinuity = hasPositiveContinuity
        )
    }

    internal fun splitPathSegments(path: String): List<String> {
        if (path.isEmpty()) return emptyList()
        return path.replace('\\', '/')
            .trimEnd('/')
            .split('/')
            .filter { it.isNotEmpty() }
    }

    internal fun countTrailingMatchingSegments(a: List<String>, b: List<String>): Int {
        var count = 0
        var ia = a.size - 1
        var ib = b.size - 1
        while (ia >= 0 && ib >= 0 && a[ia].equals(b[ib], ignoreCase = true)) {
            count++
            ia--
            ib--
        }
        return count
    }

    internal fun countCommonPrefixSegments(a: List<String>, b: List<String>): Int {
        val limit = minOf(a.size, b.size)
        var count = 0
        while (count < limit && a[count].equals(b[count], ignoreCase = true)) {
            count++
        }
        return count
    }

    internal fun computeFolderNameSimilarity(aRaw: String, bRaw: String): Int {
        if (aRaw.isEmpty() || bRaw.isEmpty()) return 0
        val a = aRaw.lowercase()
        val b = bRaw.lowercase()
        if (a == b) return 100

        val minLen = minOf(a.length, b.length)
        val maxLen = maxOf(a.length, b.length)
        val hasWordBoundaryPrefix = (a.length > b.length && a.startsWith(b) && !a[b.length].isLetterOrDigit()) ||
            (b.length > a.length && b.startsWith(a) && !b[a.length].isLetterOrDigit())
        if (hasWordBoundaryPrefix || (minLen >= 3 && (a.startsWith(b) || b.startsWith(a)))) {
            return 70 + (minLen * 20) / maxLen
        }
        if (minLen >= 3 && (a.contains(b) || b.contains(a))) {
            return 50 + (minLen * 20) / maxLen
        }

        var commonPrefix = 0
        while (commonPrefix < minLen && a[commonPrefix] == b[commonPrefix]) {
            commonPrefix++
        }
        if (commonPrefix >= 3) {
            return 20 + (commonPrefix * 30) / maxLen
        }

        val tokenRegex = Regex("[^a-z0-9]+")
        val tokensA = a.split(tokenRegex).filter { it.isNotEmpty() }.toHashSet()
        val tokensB = b.split(tokenRegex).filter { it.isNotEmpty() }.toHashSet()
        if (tokensA.isNotEmpty() && tokensB.isNotEmpty()) {
            val shared = tokensA.intersect(tokensB).size
            if (shared > 0) {
                return 15 + (shared * 30) / maxOf(tokensA.size, tokensB.size)
            }
        }

        return 0
    }

    internal fun matchesFile(file: File, identity: MediaIdentity): Boolean {
        if (!file.isFile) return false
        if (identity.size > 0L && file.length() != identity.size) return false
        return if (identity.hasFingerprint) {
            MediaIdentity.computeFingerprint(file) == identity.fingerprint
        } else {
            MediaIdentityIndex.matchesCheapSignature(file, identity)
        }
    }

}
