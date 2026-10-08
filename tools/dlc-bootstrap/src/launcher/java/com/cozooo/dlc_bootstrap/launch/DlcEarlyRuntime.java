package com.cozooo.dlc_bootstrap.launch;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Properties;

public final class DlcEarlyRuntime {
    private static URLClassLoader runtime;

    private DlcEarlyRuntime() {}

    public static synchronized void initialize(IEnvironment environment) {
        System.clearProperty("dlc_manager.early.ready");
        try {
            if (runtime == null) {
                Path game = environment.getProperty(IEnvironment.Keys.GAMEDIR.get())
                    .orElseThrow(() -> new IOException("Game directory is unavailable before mod discovery"));
                Properties profile = new Properties();
                try (var input = DlcEarlyRuntime.class.getResourceAsStream("/dlc-bootstrap.properties")) {
                    if (input == null) throw new IOException("DLC early runtime profile is missing");
                    profile.load(input);
                }
                ArrayList<URL> jars = new ArrayList<>();
                Path cache = game.resolve(".dlc-manager/runtime/early");
                jars.add(extract(cache, "META-INF/dlc-manager/early.jar").toUri().toURL());
                for (String resource : profile.getProperty("runtimeLibraries", "").split(",")) {
                    if (!resource.isBlank()) jars.add(extract(cache, resource).toUri().toURL());
                }
                Path distribution = distributionFile(DlcEarlyRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                System.setProperty("dlc_manager.distribution.path", distribution.toAbsolutePath().normalize().toString());
                runtime = new RuntimeClassLoader(jars.toArray(URL[]::new), DlcEarlyRuntime.class.getClassLoader());
            }
            Class<?> implementation = runtime.loadClass("com.cozooo.dlc_bootstrap.internal.bootstrap.DlcBootstrapService");
            ITransformationService installer = (ITransformationService) implementation.getConstructor().newInstance();
            installer.initialize(environment);
        } catch (ReflectiveOperationException | IOException | java.net.URISyntaxException ex) {
            throw new IllegalStateException("Required resource installation failed: startup stopped", ex);
        } catch (RuntimeException | LinkageError ex) {
            System.clearProperty("dlc_manager.early.ready");
            throw new IllegalStateException("Required resource installation failed: startup stopped", ex);
        }
    }

    public static Path modFile() { return (Path) payloadValue("getModFile"); }
    public static boolean development() { return (boolean) payloadValue("isDevelopment"); }

    private static Object payloadValue(String method) {
        if (runtime == null) throw new IllegalStateException("DLC early runtime has not been initialized");
        try {
            return runtime.loadClass("com.cozooo.dlc_bootstrap.internal.bootstrap.DlcModPayload").getMethod(method).invoke(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot access prepared DLC Manager mod", ex);
        }
    }

    static Path distributionFile(java.net.URI location) throws ReflectiveOperationException {
        Path path = Path.of(location);
        while ("union".equals(path.getFileSystem().provider().getScheme())) {
            path = (Path) path.getFileSystem().getClass().getMethod("getPrimaryPath").invoke(path.getFileSystem());
        }
        return path.toAbsolutePath().normalize();
    }

    private static Path extract(Path cache, String resource) throws IOException {
        byte[] bytes;
        try (var input = DlcEarlyRuntime.class.getResourceAsStream("/" + resource)) {
            if (input == null) throw new IOException("DLC early runtime dependency is missing: " + resource);
            bytes = input.readAllBytes();
        }
        byte[] digest = sha256(bytes);
        Path directory = cache.resolve(HexFormat.of().formatHex(digest));
        Files.createDirectories(directory);
        Path target = directory.resolve(Path.of(resource).getFileName());
        if (!Files.isRegularFile(target) || !MessageDigest.isEqual(digest, sha256(Files.readAllBytes(target)))) {
            Path temp = Files.createTempFile(directory, "runtime-", ".tmp");
            try {
                Files.write(temp, bytes);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        return target;
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static final class RuntimeClassLoader extends URLClassLoader {
        RuntimeClassLoader(URL[] jars, ClassLoader parent) { super(jars, parent); }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (!name.startsWith("kotlin.") && !name.startsWith("com.cozooo.dlc_bootstrap.internal."))
                    return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
}
