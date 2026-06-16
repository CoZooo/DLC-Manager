package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.core.DcConfigIO
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class DlcUpdateVersion {
    private var modpack: String? = null
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
            obj.addProperty("modpack", modpack ?: "")
            obj.addProperty("base", if (base.isNullOrBlank()) DEFAULT_VERSION else base)
            obj.addProperty("dlc", if (dlc.isNullOrBlank()) DEFAULT_VERSION else dlc)
            if (!rollbackFrom.isNullOrBlank()) {
                obj.addProperty("rollback_from", rollbackFrom)
            }
            if (!changelogShown.isNullOrBlank()) {
                obj.addProperty("changelog_shown", changelogShown)
            }
            DcConfigIO.write(path, obj, DcConfigIO.Layout.GENERIC)
        } catch (_: IOException) {
        }
    }

    fun getBase(): String? = base

    fun getModpack(): String? = modpack

    fun setModpack(modpack: String?) {
        this.modpack = modpack
    }

    fun setBase(base: String?) {
        this.base = base
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
            version.modpack = ""
            version.base = DEFAULT_VERSION
            version.dlc = DEFAULT_VERSION
            if (dlcRoot == null) {
                return version
            }
            val path = resolveVersionPath(dlcRoot)
            if (!Files.exists(path)) {
                version.save(dlcRoot)
                return version
            }
            try {
                val obj = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC) ?: return version
                version.modpack = DcConfigIO.getStringIgnoreCase(obj, "modpack")
                version.base = DcConfigIO.getStringIgnoreCase(obj, "base")
                version.dlc = DcConfigIO.getStringIgnoreCase(obj, "dlc")
                var rollback = DcConfigIO.getStringIgnoreCase(obj, "rollback_from")
                if (rollback.isNullOrBlank()) {
                    rollback = DcConfigIO.getStringIgnoreCase(obj, "rollbackFrom")
                }
                version.rollbackFrom = rollback
                var shown = DcConfigIO.getStringIgnoreCase(obj, "changelog_shown")
                if (shown.isNullOrBlank()) {
                    shown = DcConfigIO.getStringIgnoreCase(obj, "changelogShown")
                }
                version.changelogShown = shown
            } catch (_: Exception) {
            }
            if (version.base.isNullOrBlank()) {
                version.base = DEFAULT_VERSION
            }
            if (version.dlc.isNullOrBlank()) {
                version.dlc = DEFAULT_VERSION
            }
            if (version.modpack == null) {
                version.modpack = ""
            }
            return version
        }

        private fun resolveVersionPath(dlcRoot: Path): Path {
            val infoDir = dlcRoot.resolve("info")
            return DcConfigIO.toDcPath(infoDir.resolve("version")) ?: infoDir.resolve("version.dc")
        }
    }
}
