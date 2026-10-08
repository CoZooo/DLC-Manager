package com.cozooo.dlc_bootstrap.launch;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DlcDistributionAssembler {
    private DlcDistributionAssembler() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 3) throw new IllegalArgumentException("Expected service JAR, mod JAR and distribution JAR");
        pack(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }

    public static void pack(Path service, Path mod, Path output) throws IOException {
        Path destination = output.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent());
        Path temp = Files.createTempFile(destination.getParent(), "distribution-", ".tmp");
        try (JarFile serviceJar = new JarFile(service.toFile()); JarFile modJar = new JarFile(mod.toFile())) {
            if (serviceJar.getEntry("META-INF/services/cpw.mods.modlauncher.api.ITransformationService") == null)
                throw new IOException("Early loading service is missing");
            if (modJar.getEntry("META-INF/mods.toml") == null && modJar.getEntry("META-INF/neoforge.mods.toml") == null)
                throw new IOException("DLC Manager mod metadata is missing");
            Set<String> names = new HashSet<>();
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                var entries = serviceJar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.isDirectory()) copy(serviceJar, entry, zip, names);
                }
                for (String name : new String[]{"META-INF/mods.toml", "META-INF/neoforge.mods.toml", "assets/dlc_manager/textures/gui/icon.png"}) {
                    var entry = modJar.getJarEntry(name);
                    if (entry != null) copy(modJar, entry, zip, names);
                }
                add(zip, names, "META-INF/dlc-manager/mod.jar");
                Files.copy(mod, zip);
                zip.closeEntry();
            }
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void copy(JarFile source, ZipEntry entry, ZipOutputStream target, Set<String> names) throws IOException {
        add(target, names, entry.getName());
        try (var input = source.getInputStream(entry)) { input.transferTo(target); }
        target.closeEntry();
    }

    private static void add(ZipOutputStream target, Set<String> names, String name) throws IOException {
        if (!names.add(name)) throw new IOException("Duplicate distribution entry: " + name);
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        target.putNextEntry(entry);
    }
}
