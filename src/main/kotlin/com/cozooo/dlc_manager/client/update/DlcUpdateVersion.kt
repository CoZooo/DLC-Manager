package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcPaths
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class DlcUpdateVersion {
    private var base: String? = null
    private var dlc: String? = null
    private var rollbackFrom: String? = null
    private var changelogShown: String? = null

    fun save(dlcRoot: Path?) {
        if (dlcRoot == null) {
            return
        }
        val path = resolveVersionPath(dlcRoot)
        try {
            Files.createDirectories(path.parent)
            val obj = JsonObject()
            obj.addProperty("version", if (base.isNullOrBlank()) DEFAULT_VERSION else base)
            if (!dlc.isNullOrBlank()) {
                obj.addProperty("dlc", dlc)
            }
            if (!rollbackFrom.isNullOrBlank()) {
                obj.addProperty("rollback_from", rollbackFrom)
            }
            if (!changelogShown.isNullOrBlank()) {
                obj.addProperty("changelog_shown", changelogShown)
            }
            DlcDcConfigIO.write(path, obj, DlcDcConfigIO.Layout.GENERIC)
        } catch (_: IOException) {
        }
    }

    fun getBase(): String? = base

    /** Canonical public name; getBase remains for source compatibility. */
    fun getVersion(): String? = base

    fun getModpack(): String? = DlcConfig.getModpack()

    fun setModpack(modpack: String?) {
        DlcConfig.setModpack(modpack)
    }

    fun setBase(base: String?) {
        this.base = base
    }

    /** Canonical public name; setBase remains for source compatibility. */
    fun setVersion(version: String?) {
        this.base = version
    }

    fun getDlc(): String? = dlc

    fun setDlc(dlc: String?) {
        this.dlc = dlc
    }

    fun getRollbackFrom(): String? = rollbackFrom

    fun setRollbackFrom(rollbackFrom: String?) {
        this.rollbackFrom = rollbackFrom
    }

    fun getChangelogShown(): String? = changelogShown

    fun setChangelogShown(changelogShown: String?) {
        this.changelogShown = changelogShown
    }

    companion object {
        private const val DEFAULT_VERSION = "1.0.0"

        @JvmStatic
        fun load(dlcRoot: Path?): DlcUpdateVersion {
            val version = DlcUpdateVersion()
            version.base = DEFAULT_VERSION
            version.dlc = null
            if (dlcRoot == null) {
                return version
            }
            val path = resolveVersionPath(dlcRoot)
            if (!Files.exists(path)) {
                version.save(dlcRoot)
                return version
            }
            try {
                val obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC) ?: return version
                val canonical = DlcDcConfigIO.getStringIgnoreCase(obj, "version")
                val legacy = DlcDcConfigIO.getStringIgnoreCase(obj, "base")
                val migrateLegacy = canonical.isNullOrBlank()
                version.base = if (migrateLegacy) legacy else canonical
                version.dlc = DlcDcConfigIO.getStringIgnoreCase(obj, "dlc")
                var rollback = DlcDcConfigIO.getStringIgnoreCase(obj, "rollback_from")
                if (rollback.isNullOrBlank()) {
                    rollback = DlcDcConfigIO.getStringIgnoreCase(obj, "rollbackFrom")
                }
                version.rollbackFrom = rollback
                var shown = DlcDcConfigIO.getStringIgnoreCase(obj, "changelog_shown")
                if (shown.isNullOrBlank()) {
                    shown = DlcDcConfigIO.getStringIgnoreCase(obj, "changelogShown")
                }
                version.changelogShown = shown
                if (migrateLegacy && !version.base.isNullOrBlank()) {
                    DlcDcConfigIO.removeIgnoreCase(obj, "base")
                    obj.addProperty("version", version.base)
                    DlcDcConfigIO.write(path, obj, DlcDcConfigIO.Layout.GENERIC)
                }
            } catch (_: Exception) {
            }
            if (version.base.isNullOrBlank()) {
                version.base = DEFAULT_VERSION
            }
            if (version.dlc.isNullOrBlank()) {
                version.dlc = null
            }
            return version
        }

        private fun resolveVersionPath(dlcRoot: Path): Path {
            return DlcPaths.resolveVersionConfigPath(dlcRoot)
        }
    }
}
