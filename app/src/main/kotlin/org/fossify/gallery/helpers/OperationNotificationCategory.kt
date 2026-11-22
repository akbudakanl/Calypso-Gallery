package org.fossify.gallery.helpers

/**
 * Groups foreground file operations into the user-facing notification categories exposed in
 * Settings → Notifications. The legacy CHANNEL_* values are kept because CryptoProgressDialog
 * still accepts a channel id from its call sites; they are category markers now, not real channels.
 */
object OperationNotificationCategory {
    const val COPY_MOVE = "copy_move"
    const val CRYPTO = "crypto"
    const val DELETE = "delete"
    const val VIRTUAL_ALBUM = "virtual_album"

    const val CHANNEL_COPY_MOVE = "Copy/Move"
    const val CHANNEL_CRYPTO = "Encrypt/Decrypt"
    const val CHANNEL_DELETE = "Delete"
    const val CHANNEL_VIRTUAL_ALBUM = "VirtualAlbum"

    fun fromChannel(channelId: String): String {
        return when (channelId) {
            CHANNEL_COPY_MOVE -> COPY_MOVE
            CHANNEL_DELETE -> DELETE
            CHANNEL_VIRTUAL_ALBUM -> VIRTUAL_ALBUM
            else -> CRYPTO
        }
    }

    fun fromOwner(owner: String): String {
        return when {
            owner.startsWith("virtual-album") -> VIRTUAL_ALBUM
            owner == "copy-move" || owner == "crypto-copy-move" || owner == "copy" || owner == "move" -> COPY_MOVE
            owner.contains("encrypt") || owner.contains("decrypt") ||
                owner == "vault-export" || owner == "vault-sync" || owner == "resume" -> CRYPTO
            else -> DELETE
        }
    }
}
