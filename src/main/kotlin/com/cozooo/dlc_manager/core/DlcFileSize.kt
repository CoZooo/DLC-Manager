package com.cozooo.dlc_manager.core

import java.util.Locale

object DlcFileSize {
    const val KIB = 1024L
    const val MIB = KIB * 1024L
    const val GIB = MIB * 1024L
    private val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")

    @JvmStatic fun ceilMiB(bytes: Long): Long {
        if (bytes <= 0) return 0
        return bytes / MIB + if (bytes % MIB == 0L) 0 else 1
    }

    @JvmStatic fun formatProgress(done: Long, total: Long): String = format(done) + if (total < 0) "" else "/" + format(total)

    @JvmStatic fun format(bytes: Long): String {
        if (bytes < 0) return "--"
        if (bytes < KIB) return "$bytes B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= KIB && unit < units.size - 1) { value /= KIB; unit++ }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }
}
