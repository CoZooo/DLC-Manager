package com.cozooo.dlc_manager.bootstrap

import java.nio.file.Files
import java.nio.file.Path

object DlcDistributionPaths {
    const val DISTRIBUTION_PROPERTY = "dlc_manager.distribution.path"
    const val PAYLOAD_PROPERTY = "dlc_manager.distribution.payload"

    @JvmStatic
    fun originalModFile(modId: String?, loadedFile: Path?): Path? {
        if (modId != "dlc_manager" || loadedFile == null) return loadedFile
        try {
            val distribution = System.getProperty(DISTRIBUTION_PROPERTY)
            val payload = System.getProperty(PAYLOAD_PROPERTY)
            if (distribution != null && payload != null && loadedFile.toAbsolutePath().normalize() == Path.of(payload).toAbsolutePath().normalize()) {
                val original = Path.of(distribution).toAbsolutePath().normalize()
                if (Files.isRegularFile(original)) return original
            }
        } catch (_: RuntimeException) {
        }
        return loadedFile
    }
}
