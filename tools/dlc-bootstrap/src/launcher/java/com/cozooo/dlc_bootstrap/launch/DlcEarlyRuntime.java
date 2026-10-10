package com.cozooo.dlc_bootstrap.launch;

import cpw.mods.modlauncher.api.IEnvironment;
//? if >=1.21.1 {
/*import net.neoforged.neoforgespi.ILaunchContext;
*///?}
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class DlcEarlyRuntime {
    private static URLClassLoader runtime;
    private static DlcBootstrapEntrypoint bootstrap;

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

    //? if >=1.21.1 <26.1 {
    /*public static synchronized void initialize(ILaunchContext context) {
        initialize(context.environment());
    }
    *///?}

    //? if >=26.1 {
    /*public static synchronized void initialize(ILaunchContext context) {
        System.clearProperty("dlc_manager.early.ready");
        try {
            Properties profile = loadProfile();
            Path game = context.gameDirectory();
            String target = String.valueOf(context.getRequiredDistribution()).toLowerCase(java.util.Locale.ROOT).contains("server")
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
    *///?}

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
            DlcBootstrapEntrypoint installer = bootstrap();
            installer.configure(progress::accept, cancelled);
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
            DlcBootstrapEntrypoint installer = bootstrap();
            installer.configure(progress::accept, cancelled);
            installer.initialize(game, profile.getProperty("loader"), profile.getProperty("minecraft"), server, development);
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
        bootstrap = ServiceLoader.load(DlcBootstrapEntrypoint.class, runtime).findFirst()
            .orElseThrow(() -> new IOException("DLC early runtime entrypoint is missing"));
    }

    private static boolean developmentLaunch() {
        //? if >=26.1 {
        /*return !net.neoforged.fml.loading.FMLLoader.getCurrent().isProduction();
        *///?}
        //? if >=1.21.1 <26.1 {
        /*return !net.neoforged.fml.loading.FMLLoader.isProduction();
        *///?}
        //? if <1.21.1 {
        return !net.minecraftforge.fml.loading.FMLLoader.isProduction();
        //?}
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Required resource installation interrupted");
        }
    }

    public static Path modFile() { return bootstrap().modFile(); }
    public static boolean development() { return bootstrap().development(); }
    public static boolean ready() { return runtime != null && System.getProperty("dlc_manager.early.ready") != null; }

    private static DlcBootstrapEntrypoint bootstrap() {
        DlcBootstrapEntrypoint value = bootstrap;
        if (runtime == null || value == null) throw new IllegalStateException("DLC early runtime has not been initialized");
        return value;
    }

    static Path distributionFile(java.net.URI location) {
        Path path = Path.of(location);
        while ("union".equals(path.getFileSystem().provider().getScheme())) {
            path = ((cpw.mods.niofs.union.UnionFileSystem) path.getFileSystem()).getPrimaryPath();
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
