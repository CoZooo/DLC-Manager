package com.cozooo.dlc_manager.core

import java.nio.file.Path

object DlcTmpPaths {
    const val TMP_DIR: String = "tmp"
    const val LEGACY_TMP_DIR: String = "update_tmp"

    @JvmStatic
    fun root(base: Path?): Path? {
        if (base == null) {
            return null
        }
        return base.toAbsolutePath().normalize().resolve(TMP_DIR)
    }

    @JvmStatic
    fun legacyRoot(base: Path?): Path? {
        if (base == null) {
            return null
        }
        return base.toAbsolutePath().normalize().resolve(LEGACY_TMP_DIR)
    }

    @JvmStatic
    fun resolve(base: Path?, first: String?, vararg more: String?): Path? {
        var path = root(base) ?: return null
        if (!first.isNullOrBlank()) {
            path = path.resolve(first)
        }
        for (segment in more) {
            if (!segment.isNullOrBlank()) {
                path = path.resolve(segment)
            }
        }
        return path
    }
}
