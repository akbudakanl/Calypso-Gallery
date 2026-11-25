package org.fossify.gallery.helpers

/** Metadata for one archived encrypted vault. Stored in app-private preferences. */
data class ArchivedVault(
    val id: String,
    val path: String,
    val archivedAt: Long,
    val originalPath: String = "",
    /** CLI bridge folder id, so unarchiving can restore that exact bridge even when
     *  another vault has since been created at the same original path. */
    val bridgeId: String = "",
)
