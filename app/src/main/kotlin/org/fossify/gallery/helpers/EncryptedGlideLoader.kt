@file:Suppress("MatchingDeclarationName")

package org.fossify.gallery.helpers

import com.bumptech.glide.load.Option

object EncryptedGlideOptions {
    val PREFER_EMBEDDED_THUMBNAIL: Option<Boolean> =
        Option.memory("org.fossify.gallery.prefer_embedded_thumbnail", false)
    val PREFER_FULL_DECRYPTED_CONTENT: Option<Boolean> =
        Option.memory("org.fossify.gallery.prefer_full_decrypted_content", false)
}

/** ADR 0002 §4: never retain decrypted vault bitmaps in Glide's disk or memory caches. */
fun <T : com.bumptech.glide.request.BaseRequestOptions<T>> T.applyEncryptedCachePolicy(
    isEncrypted: Boolean
): T = apply {
    if (isEncrypted) {
        diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
        skipMemoryCache(true)
    }
}
