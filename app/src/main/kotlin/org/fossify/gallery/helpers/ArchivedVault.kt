package org.fossify.gallery.helpers

/** Metadata for one archived encrypted vault. Stored in app-private preferences. */
data class ArchivedVault(
    val id: String,
    val path: String,
    val archivedAt: Long,
    val originalPath: String = "",
)
