package com.cozooo.dlc_manager.client.download;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class DlcLightHash {
    public static final int SEGMENT = 64 * 1024;
    private static final String PREFIX = "lh1-";

    private DlcLightHash() {
    }

    public static String compute(Path file) throws IOException {
        long size = Files.size(file);
        long modified = Files.getLastModifiedTime(file).toMillis();
        MessageDigest digest = sha1();
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(size).array());
        if (size <= SEGMENT * 2L) {
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        } else {
            try (SeekableByteChannel channel = Files.newByteChannel(file)) {
                digest.update(readAt(channel, 0L));
                digest.update(readAt(channel, size - SEGMENT));
            }
        }
        StringBuilder result = new StringBuilder(PREFIX);
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value));
        }
        if (Files.size(file) != size || Files.getLastModifiedTime(file).toMillis() != modified) {
            throw new IOException("file changed while hashing: " + file.getFileName());
        }
        return result.toString();
    }

    private static byte[] readAt(SeekableByteChannel channel, long position) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(SEGMENT);
        channel.position(position);
        int read = 0;
        while (buffer.hasRemaining() && read >= 0) {
            read = channel.read(buffer);
        }
        byte[] bytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(bytes);
        return bytes;
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
