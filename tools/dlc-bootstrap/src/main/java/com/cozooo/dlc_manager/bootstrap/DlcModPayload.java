package com.cozooo.dlc_manager.bootstrap;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarFile;

public final class DlcModPayload {
    public static final String RESOURCE = "META-INF/dlc-manager/mod.jar";
    private static Path modFile;
    private static boolean development;

    private DlcModPayload() {}

    public static void prepare(Path game) throws IOException { prepare(game, false); }

    public static synchronized void prepare(Path game, boolean developmentLaunch) throws IOException {
        Path cache = game.resolve(".dlc-manager/runtime");
        Files.createDirectories(cache);
        Path temp = Files.createTempFile(cache, "payload-", ".tmp");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = DlcModPayload.class.getResourceAsStream("/" + RESOURCE)) {
                if (input == null) throw new IOException("Embedded DLC Manager mod is missing");
                Files.copy(new DigestInputStream(input, digest), temp, StandardCopyOption.REPLACE_EXISTING);
            }
            byte[] hash = digest.digest();
            Path target = cache.resolve(HexFormat.of().formatHex(hash)).resolve("dlc-manager.jar");
            Files.createDirectories(target.getParent());
            if (!Files.isRegularFile(target) || !matches(target, hash)) {
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            try (JarFile jar = new JarFile(target.toFile())) {
                if (jar.getEntry("META-INF/mods.toml") == null && jar.getEntry("META-INF/neoforge.mods.toml") == null) {
                    throw new IOException("Embedded DLC Manager mod metadata is missing");
                }
            }
            String distributionProperty = System.getProperty(DlcDistributionPaths.DISTRIBUTION_PROPERTY);
            if (distributionProperty == null || distributionProperty.isBlank()) {
                throw new IOException("DLC Manager distribution path is unavailable before mod discovery");
            }
            Path distribution = Path.of(distributionProperty);
            modFile = target.toAbsolutePath().normalize();
            development = developmentLaunch;
            System.setProperty(DlcDistributionPaths.PAYLOAD_PROPERTY, modFile.toString());
            System.setProperty(DlcDistributionPaths.DISTRIBUTION_PROPERTY, distribution.toAbsolutePath().normalize().toString());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("Cannot prepare embedded DLC Manager mod", ex);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static boolean isDevelopment() { return development; }

    public static Path getModFile() {
        if (modFile == null) throw new IllegalStateException("DLC Manager mod was not prepared before discovery");
        return modFile;
    }

    private static boolean matches(Path path, byte[] expected) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return MessageDigest.isEqual(digest.digest(), expected);
    }
}
