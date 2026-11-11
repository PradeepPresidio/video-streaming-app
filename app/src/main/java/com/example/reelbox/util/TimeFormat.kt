package com.example.reelbox.util

/** Formats a millisecond position as m:ss or h:mm:ss. Negative input is treated as 0. */
fun formatTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L)) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Human-friendly duration such as "1m 45s" or "45s". */
fun formatDurationShort(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) + 500) / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return when {
        m == 0L -> "${s}s"
        s == 0L -> "${m}m"
        else -> "${m}m ${s}s"
    }
}
