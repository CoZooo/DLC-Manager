package com.cozooo.dlc_bootstrap.launch;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class DlcEarlyRuntime {
    private static URLClassLoader runtime;

    private DlcEarlyRuntime() {}

    public static synchronized void initialize(IEnvironment environment) {
        System.clearProperty("dlc_manager.early.ready");
        try {
            Properties profile = loadProfile();
            String target = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get())
                .orElseThrow(() -> new IOException("Launch target is unavailable before mod discovery"));
            DlcEarlyProgress.run(target, profile.getProperty("loader"), DlcEarlyRuntime.class.getClassLoader(),
                (progress, cancelled) -> install(environment, profile, progress, cancelled));
        } catch (InterruptedException ex) {
            System.clearProperty("dlc_manager.early.ready");
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Required resource installation failed: installation interrupted; startup stopped", ex);
        } catch (Exception ex) {
            System.clearProperty("dlc_manager.early.ready");
            throw new IllegalStateException("Required resource installation failed: startup stopped", ex);
        } catch (LinkageError ex) {
            System.clearProperty("dlc_manager.early.ready");
            throw new IllegalStateException("Required resource installation failed: startup stopped", ex);
        }
    }

    public static synchronized void initialize(Object context) {
        System.clearProperty("dlc_manager.early.ready");
        try {
            Object environment = invokeOptional(context, "environment");
            if (environment instanceof IEnvironment oldEnvironment) {
                initialize(oldEnvironment);
                return;
            }
            Properties profile = loadProfile();
            Path game = (Path) invoke(context, "gameDirectory");
            Object distribution = invoke(context, "getRequiredDistribution");
            String target = String.valueOf(distribution).toLowerCase(java.util.Locale.ROOT).contains("server")
                ? "neoforgeserver" : "neoforgeclient";
            boolean server = target.contains("server");
            boolean development = developmentLaunch();
            DlcEarlyProgress.run(target, profile.getProperty("loader"), DlcEarlyRuntime.class.getClassLoader(),
                (progress, cancelled) -> install(game, profile, server, development, progress, cancelled));
        } catch (InterruptedException ex) {
            System.clearProperty("dlc_manager.early.ready");
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Required resource installation failed: installation interrupted; startup stopped", ex);
        } catch (Exception ex) {
            System.clearProperty("dlc_manager.early.ready");
            throw new IllegalStateException("Required resource installation failed: startup stopped", ex);
        } catch (LinkageError ex) {
            System.clearProperty("dlc_manager.early.ready");
            throw new IllegalStateException("Required resource installation failed: startup stopped", ex);
        }
    }

    private static Properties loadProfile() throws IOException {
        Properties profile = new Properties();
        try (var input = DlcEarlyRuntime.class.getResourceAsStream("/dlc-bootstrap.properties")) {
            if (input == null) throw new IOException("DLC early runtime profile is missing");
            profile.load(input);
        }
        if (profile.getProperty("loader") == null || profile.getProperty("minecraft") == null) {
            throw new IOException("Invalid DLC early runtime profile");
        }
        return profile;
    }

    private static void install(IEnvironment environment, Properties profile, Consumer<Map<String, Object>> progress,
                                BooleanSupplier cancelled) throws Exception {
        checkCancelled(cancelled);
        Path game = environment.getProperty(IEnvironment.Keys.GAMEDIR.get())
            .orElseThrow(() -> new IOException("Game directory is unavailable before mod discovery"));
        ensureRuntime(game, profile, cancelled);
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(runtime);
            Class<?> implementation = runtime.loadClass("com.cozooo.dlc_bootstrap.internal.bootstrap.DlcBootstrapService");
            ITransformationService installer = (ITransformationService) implementation
                .getConstructor(Consumer.class, BooleanSupplier.class).newInstance(progress, cancelled);
            installer.initialize(environment);
            checkCancelled(cancelled);
        } finally {
            thread.setContextClassLoader(previous);
            if (cancelled.getAsBoolean() || thread.isInterrupted()) System.clearProperty("dlc_manager.early.ready");
        }
    }

    private static void install(Path game, Properties profile, boolean server, boolean development, Consumer<Map<String, Object>> progress,
                                BooleanSupplier cancelled) throws Exception {
        checkCancelled(cancelled);
        ensureRuntime(game, profile, cancelled);
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(runtime);
            Class<?> implementation = runtime.loadClass("com.cozooo.dlc_bootstrap.internal.bootstrap.DlcBootstrapService");
            Object installer = implementation.getConstructor(Consumer.class, BooleanSupplier.class)
                .newInstance(progress, cancelled);
            installer.getClass().getMethod("initialize", Path.class, String.class, String.class, boolean.class, boolean.class)
                .invoke(installer, game, profile.getProperty("loader"), profile.getProperty("minecraft"), server, development);
            checkCancelled(cancelled);
        } finally {
            thread.setContextClassLoader(previous);
            if (cancelled.getAsBoolean() || thread.isInterrupted()) System.clearProperty("dlc_manager.early.ready");
        }
    }

    private static synchronized void ensureRuntime(Path game, Properties profile, BooleanSupplier cancelled) throws Exception {
        if (runtime != null) return;
        ArrayList<URL> jars = new ArrayList<>();
        Path cache = game.resolve(".dlc-manager/runtime/early");
        jars.add(extract(cache, "META-INF/dlc-manager/early.jar").toUri().toURL());
        for (String resource : profile.getProperty("runtimeLibraries", "").split(",")) {
            checkCancelled(cancelled);
            if (!resource.isBlank()) jars.add(extract(cache, resource).toUri().toURL());
        }
        checkCancelled(cancelled);
        Path distribution = distributionFile(DlcEarlyRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        System.setProperty("dlc_manager.distribution.path", distribution.toAbsolutePath().normalize().toString());
        runtime = new RuntimeClassLoader(jars.toArray(URL[]::new), DlcEarlyRuntime.class.getClassLoader());
    }

    private static boolean developmentLaunch() {
        try {
            Class<?> loader = Class.forName("net.neoforged.fml.loading.FMLLoader", false, DlcEarlyRuntime.class.getClassLoader());
            Object current = loader.getMethod("getCurrentOrNull").invoke(null);
            return current != null && !Boolean.TRUE.equals(current.getClass().getMethod("isProduction").invoke(current));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static Object invokeOptional(Object target, String name) throws ReflectiveOperationException {
        try {
            return findContextMethod(target, name).invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        return findContextMethod(target, name).invoke(target);
    }

    private static Method findContextMethod(Object target, String name) throws NoSuchMethodException {
        for (Class<?> type : target.getClass().getInterfaces()) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return target.getClass().getMethod(name);
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Required resource installation interrupted");
        }
    }

    public static Path modFile() { return (Path) payloadValue("getModFile"); }
    public static boolean development() { return (boolean) payloadValue("isDevelopment"); }
    public static boolean ready() { return runtime != null && System.getProperty("dlc_manager.early.ready") != null; }

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
