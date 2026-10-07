package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.client.download.DlcLightHash
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DlcUpdateFileVerifier {
    data class Download(
        val config: Path?,
        val target: Path,
        val size: Long,
        val hash: String,
        val required: Boolean,
    )

    @JvmStatic
    @Throws(IOException::class)
    fun verify(dlcRoot: Path?, downloads: Collection<Download>) {
        for (download in downloads) {
            val resolved = if (download.required) {
                val entry = DlcRequiredLoader.loadSingle(download.config, dlcRoot)
                entry?.resolveRequiredPath(entry.getResolvedFileName())
            } else {
                DlcRemoteLoader.loadSingle(download.config, dlcRoot)?.getTargetPath()
            }
            if (resolved == null || resolved.toAbsolutePath().normalize() != download.target.toAbsolutePath().normalize()) {
                throw IOException("download does not match DLC config: ${download.config}")
            }
            val target = download.target
            if (!Files.isRegularFile(target) || Files.size(target) != download.size ||
                download.hash != DlcLightHash.compute(target)) {
                throw IOException("downloaded DLC file failed final verification: ${target.fileName}")
            }
        }
    }
}
