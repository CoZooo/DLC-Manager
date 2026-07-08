package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object DlcPackDcFiles {
    @JvmStatic
    fun writeAtomic(path: Path?, obj: JsonObject?): Boolean {
        if (path == null || obj == null) {
            return false
        }
        val target = DlcDcConfigIO.toDcPath(path) ?: return false
        val parent = target.parent
        var temp: Path? = null
        return try {
            if (parent != null) {
                Files.createDirectories(parent)
                temp = Files.createTempFile(parent, target.fileName.toString() + ".", ".dc")
            } else {
                temp = Files.createTempFile(target.fileName.toString() + ".", ".dc")
            }
            if (!DlcDcConfigIO.write(temp, obj, DlcDcConfigIO.Layout.GENERIC)) {
                return false
            }
            moveReplacing(temp, target)
            temp = null
            if (target != path) {
                Files.deleteIfExists(path)
            }
            true
        } catch (_: IOException) {
            false
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp)
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
