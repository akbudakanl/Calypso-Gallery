package org.fossify.gallery.extensions

import org.fossify.commons.extensions.getFormattedDuration

fun Long.getFormattedDuration(): String {
    if (this <= 0L) {
        return 0.getFormattedDuration()
    }
    val seconds = (this / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return seconds.getFormattedDuration()
}
