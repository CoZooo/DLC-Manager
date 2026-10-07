package com.cozooo.dlc_manager.client.download

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object DlcLightHash {
    const val SEGMENT = 64 * 1024
    private const val PREFIX = "lh1-"

    @JvmStatic
    @Throws(IOException::class)
    fun compute(file: Path): String {
        val size = Files.size(file)
        val modified = Files.getLastModifiedTime(file).toMillis()
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(ByteBuffer.allocate(java.lang.Long.BYTES).putLong(size).array())
        if (size <= SEGMENT * 2L) {
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read > 0) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
        } else {
            Files.newByteChannel(file).use { channel ->
                digest.update(readAt(channel, 0L))
                digest.update(readAt(channel, size - SEGMENT))
            }
        }
        val result = StringBuilder(PREFIX)
        for (value in digest.digest()) {
            result.append(String.format("%02x", value))
        }
        if (Files.size(file) != size || Files.getLastModifiedTime(file).toMillis() != modified) {
            throw IOException("file changed while hashing: ${file.fileName}")
        }
        return result.toString()
    }

    private fun readAt(channel: SeekableByteChannel, position: Long): ByteArray {
        val buffer = ByteBuffer.allocate(SEGMENT)
        channel.position(position)
        var read = 0
        while (buffer.hasRemaining() && read >= 0) {
            read = channel.read(buffer)
        }
        val bytes = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(bytes)
        return bytes
    }
}
