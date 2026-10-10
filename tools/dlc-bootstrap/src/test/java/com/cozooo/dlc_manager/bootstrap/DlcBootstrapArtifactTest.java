package com.cozooo.dlc_manager.bootstrap;

import com.cozooo.dlc_bootstrap.launch.DlcDistributionAssembler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcBootstrapArtifactTest {
    @TempDir Path game;

    @Test
    void neoForge26ProfileUsesTheNativeLocatorEntryPoint() throws Exception {
        Path serviceJar = Path.of(System.getProperty("neoforgeBootstrapJar26_1Artifact"));
        try (JarFile jar = new JarFile(serviceJar.toFile())) {
            Properties profile = new Properties();
            try (var input = jar.getInputStream(jar.getEntry("dlc-bootstrap.properties"))) {
                profile.load(input);
            }
            assertEquals("neoforge", profile.getProperty("loader"));
            assertEquals("26.1", profile.getProperty("minecraft"));
            assertEquals("com.cozooo.dlc_bootstrap.launch.DlcNeoForgePayloadBridge",
                new String(jar.getInputStream(jar.getEntry("META-INF/services/net.neoforged.neoforgespi.locating.IModFileCandidateLocator")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim());
            assertNotNull(jar.getEntry("com/cozooo/dlc_bootstrap/launch/DlcNeoForgePayloadBridge.class"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"forge", "neoforge"})
    void packagedServiceInstallsResourcesWithoutGameClassesOrExternalKotlin(String loaderName) throws Exception {
        Path serviceJar = Path.of(System.getProperty(loaderName + "BootstrapJar"));
        Path modJar = game.resolve("main-mod.jar");
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(modJar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry(loaderName.equals("forge") ? "META-INF/mods.toml" : "META-INF/neoforge.mods.toml"));
            zip.write("modLoader=\"javafml\"\nloaderVersion=\"[1,)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"dlc_manager\"\nversion=\"1\"\ndisplayName=\"DLC Manager\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/dlc_manager/textures/gui/icon.png"));
            zip.write(new byte[]{4, 5});
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("META-INF/jarjar/metadata.json"));
            zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("com/cozooo/dlc_manager/FakeGameMod.class"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }
        Path artifact = game.resolve("DLC-Manager.jar");
        DlcDistributionAssembler.pack(serviceJar, modJar, artifact);
        try (JarFile jar = new JarFile(artifact.toFile())) {
            assertEquals("LIBRARY", jar.getManifest().getMainAttributes().getValue("FMLModType"));
            assertNotNull(jar.getEntry("META-INF/services/cpw.mods.modlauncher.api.ITransformationService"));
            assertNull(jar.getEntry("com/google/gson/Gson.class"));
            assertNull(jar.getEntry("com/cozooo/dlc_manager/FakeGameMod.class"));
            assertNotNull(jar.getEntry("com/cozooo/dlc_bootstrap/launch/DlcEarlyProgress.class"));
            
            assertArrayEquals(new byte[]{4, 5}, jar.getInputStream(jar.getEntry("assets/dlc_manager/textures/gui/icon.png")).readAllBytes());
            assertArrayEquals(Files.readAllBytes(modJar), jar.getInputStream(jar.getEntry(DlcModPayload.RESOURCE)).readAllBytes());
            String nativeSpi = loaderName.equals("forge") ? "net.minecraftforge.forgespi.locating.IModLocator" : "net.neoforged.neoforgespi.locating.IModFileCandidateLocator";
            String nativeProvider = loaderName.equals("forge") ? "DlcForgePayloadBridge" : "DlcNeoForgePayloadBridge";
            assertEquals("com.cozooo.dlc_bootstrap.launch." + nativeProvider,
                new String(jar.getInputStream(jar.getEntry("META-INF/services/" + nativeSpi)).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim());
            assertNotNull(jar.getEntry("com/cozooo/dlc_bootstrap/launch/" + nativeProvider + ".class"));
            String notices = new String(jar.getInputStream(jar.getEntry("META-INF/NOTICE.txt")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(notices.contains("Gson 2.10.1"));
            String license = new String(jar.getInputStream(jar.getEntry("META-INF/dlc-manager/licenses/Apache-2.0.txt")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(license.contains("4. Redistribution."));
            assertNotNull(jar.getEntry("META-INF/dlc-manager/licenses/GSON-COPYRIGHT.txt"));
            assertNull(jar.getEntry("com/cozooo/dlc_bootstrap/internal/kotlin/jvm/internal/Intrinsics.class"));
            String kotlinPath = "META-INF/jarjar/kotlin-stdlib-2.2.20.jar";
            boolean kotlinBundled = jar.getEntry(kotlinPath) != null;
            if (kotlinBundled) {
                assertArrayEquals(Files.readAllBytes(Path.of(System.getProperty("kotlinStdlibJar"))), jar.getInputStream(jar.getEntry(kotlinPath)).readAllBytes());
                String metadata = new String(jar.getInputStream(jar.getEntry("META-INF/jarjar/metadata.json")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(metadata.contains(kotlinPath));
                assertTrue(metadata.contains("org.jetbrains.kotlin"));
            } else assertNull(jar.getEntry("META-INF/jarjar/metadata.json"));
            assertNotNull(jar.getEntry("META-INF/dlc-manager/early.jar"));
            try (var runtimeJar = new java.util.jar.JarInputStream(jar.getInputStream(jar.getEntry("META-INF/dlc-manager/early.jar")))) {
                java.util.jar.JarEntry entry;
                boolean installerFound = false;
                boolean dcLanguageFound = false;
                while ((entry = runtimeJar.getNextJarEntry()) != null) {
                    assertFalse(entry.getName().startsWith("kotlin/"));
                    assertFalse(entry.getName().startsWith("com/cozooo/dlc_bootstrap/internal/kotlin/"));
                    assertFalse(entry.getName().startsWith("com/cozooo/dclanguage/"));
                    assertFalse(entry.getName().startsWith("jetbrains/mps/"));
                    assertFalse(entry.getName().startsWith("org/jetbrains/mps/"));
                    assertFalse(entry.getName().startsWith("net/minecraftforge/"));
                    assertFalse(entry.getName().startsWith("net/neoforged/"));
                    assertFalse(entry.getName().startsWith("com/sighs/"));
                    installerFound |= entry.getName().equals("com/cozooo/dlc_bootstrap/internal/bootstrap/DlcBootstrapService.class");
                    dcLanguageFound |= entry.getName().equals("com/cozooo/dlc_bootstrap/internal/dclanguage/DcDocuments.class");
                }
                assertTrue(installerFound);
                assertTrue(dcLanguageFound);
            }
            assertEquals(kotlinBundled, notices.contains("Kotlin JVM Standard Library 2.2.20"));
            for (String name : new String[]{"COPYRIGHT.txt", "ATTRIBUTION.txt", "gwt_license.txt", "guava_license.txt", "threetenbp_license.txt", "boost_LICENSE.txt"}) {
                assertEquals(kotlinBundled, jar.getEntry("META-INF/dlc-manager/licenses/kotlin/" + name) != null);
            }
            assertNull(jar.getEntry("kotlin/jvm/internal/Intrinsics.class"));
            assertNull(jar.getEntry("org/jetbrains/annotations/NotNull.class"));
            var descriptor = java.lang.module.ModuleFinder.of(artifact).findAll().iterator().next().descriptor();
            assertTrue(descriptor.provides().stream().anyMatch(service -> service.service().equals("cpw.mods.modlauncher.api.ITransformationService")
                && service.providers().contains("com.cozooo.dlc_bootstrap.launch.DlcBootstrapBridge")));
            assertTrue(jar.stream().noneMatch(entry -> entry.getName().startsWith("net/minecraft/")
                || entry.getName().startsWith("dev/architectury/") || entry.getName().startsWith("com/sighs/")));
        }
        URL modLauncher = loaderName.equals("neoforge")
            ? Path.of(System.getProperty("neoForgeModLauncher")).toUri().toURL()
            : cpw.mods.modlauncher.api.ITransformationService.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader isolated = new URLClassLoader(new URL[]{artifact.toUri().toURL(), modLauncher}, ClassLoader.getPlatformClassLoader())) {
            Class<?> serviceType = isolated.loadClass("cpw.mods.modlauncher.api.ITransformationService");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object service = ServiceLoader.load((Class) serviceType, isolated).findFirst().orElseThrow();
            assertEquals("dlcbootstrap", serviceType.getMethod("name").invoke(service));
            assertTrue(((java.util.List<?>) serviceType.getMethod("transformers").invoke(service)).isEmpty());
            Class<?> environmentType = isolated.loadClass("cpw.mods.modlauncher.api.IEnvironment");
            isolated.loadClass("cpw.mods.modlauncher.api.TypesafeMap").getConstructor(Class.class).newInstance(environmentType);
            Class<?> keys = isolated.loadClass("cpw.mods.modlauncher.api.IEnvironment$Keys");
            Object gameKey = ((Supplier<?>) keys.getField("GAMEDIR").get(null)).get();
            Object targetKey = ((Supplier<?>) keys.getField("LAUNCHTARGET").get(null)).get();
            var launchTarget = new java.util.concurrent.atomic.AtomicReference<>("forgeserver");
            Object environment = Proxy.newProxyInstance(isolated, new Class<?>[]{environmentType}, (proxy, method, arguments) -> {
                if (method.getName().equals("getProperty")) {
                    if (arguments[0] == gameKey) return Optional.of(game);
                    if (arguments[0] == targetKey) return Optional.of(launchTarget.get());
                    return Optional.empty();
                }
                throw new UnsupportedOperationException(method.getName());
            });
            Path required = game.resolve("DLC/required");
            Files.createDirectories(required);
            Files.writeString(required.resolve("main.dc"), "identifier = \"main\"\nfile_name = \"main.jar\"\nappliedTarget = \"mods/main.jar\"\n");
            Files.writeString(required.resolve("main.jar"), "resource");
            serviceType.getMethod("initialize", environmentType).invoke(service, environment);
            assertEquals("resource", Files.readString(game.resolve("mods/main.jar")));
            assertEquals(game.toAbsolutePath().normalize().toString(), System.getProperty(DlcStartupRequirements.READY_PROPERTY));
            Class<?> runtimeBridge = isolated.loadClass("com.cozooo.dlc_bootstrap.launch.DlcEarlyRuntime");
            var runtimeField = runtimeBridge.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            ClassLoader privateRuntime = (ClassLoader) runtimeField.get(null);
            verifyProgressBridge(privateRuntime, serviceType, environmentType, environment);
            launchTarget.set("forgeclient");
            serviceType.getMethod("initialize", environmentType).invoke(service, environment);
            assertEquals(game.toAbsolutePath().normalize().toString(), System.getProperty(DlcStartupRequirements.READY_PROPERTY));
            launchTarget.set("forgeserver");
            Class<?> dcDocuments = privateRuntime.loadClass("com.cozooo.dlc_bootstrap.internal.dclanguage.DcDocuments");
            assertSame(privateRuntime, dcDocuments.getClassLoader());
            Object parsed = dcDocuments.getMethod("readText", String.class).invoke(null, "[basic]\nidentifier = \"isolated\"\n");
            assertEquals("isolated", parsed.getClass().getMethod("getString", String.class, String.class).invoke(parsed, "basic", "identifier"));
            assertSame(privateRuntime, parsed.getClass().getMethod("toJson").invoke(parsed).getClass().getClassLoader());
            Class<?> catalogType = privateRuntime.loadClass("com.cozooo.dlc_bootstrap.internal.dclanguage.DcSchemaCatalog");
            Object catalog = catalogType.getMethod("loadDefault").invoke(null);
            assertFalse(((java.util.List<?>) catalogType.getMethod("profiles").invoke(catalog)).isEmpty());
            Class<?> syntaxType = privateRuntime.loadClass("com.cozooo.dlc_bootstrap.internal.dclanguage.generated.DcGeneratedSyntax");
            assertEquals(syntaxType.getField("DEFINITION_SHA256").get(null), catalogType.getMethod("definitionHash").invoke(catalog));
            if (!System.getProperty("kotlinStdlibJar").isEmpty()) {
                Class<?> intrinsics = privateRuntime.loadClass("kotlin.jvm.internal.Intrinsics");
                assertSame(privateRuntime, intrinsics.getClassLoader());
                Path stdlib = Path.of(intrinsics.getProtectionDomain().getCodeSource().getLocation().toURI());
                assertTrue(stdlib.startsWith(game.resolve(".dlc-manager/runtime/early")));
                assertArrayEquals(Files.readAllBytes(Path.of(System.getProperty("kotlinStdlibJar"))), Files.readAllBytes(stdlib));
            } else assertThrows(ClassNotFoundException.class, () -> privateRuntime.loadClass("kotlin.jvm.internal.Intrinsics"));
            Path extracted = Path.of(System.getProperty(DlcDistributionPaths.PAYLOAD_PROPERTY));
            assertTrue(extracted.startsWith(game.resolve(".dlc-manager/runtime")));
            assertArrayEquals(Files.readAllBytes(modJar), Files.readAllBytes(extracted));
            assertEquals(artifact.toAbsolutePath(), Path.of(System.getProperty(DlcDistributionPaths.DISTRIBUTION_PROPERTY)));
            Files.writeString(extracted, "corrupted cache");
            serviceType.getMethod("initialize", environmentType).invoke(service, environment);
            assertArrayEquals(Files.readAllBytes(modJar), Files.readAllBytes(extracted));
            Files.delete(game.resolve("mods/main.jar"));
            Files.delete(game.resolve("mods"));
            Files.writeString(game.resolve("mods"), "not a directory");
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> serviceType.getMethod("initialize", environmentType).invoke(service, environment));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertTrue(failure.getCause().getMessage().contains("Required resource installation failed"));
            assertNull(System.getProperty(DlcStartupRequirements.READY_PROPERTY));
            Files.delete(game.resolve("mods"));
            verifyNativeDiscovery(artifact, loaderName);
        } finally {
            System.clearProperty(DlcStartupRequirements.READY_PROPERTY);
            System.clearProperty(DlcDistributionPaths.PAYLOAD_PROPERTY);
            System.clearProperty(DlcDistributionPaths.DISTRIBUTION_PROPERTY);
        }
    }

    private void verifyProgressBridge(ClassLoader runtime, Class<?> serviceType, Class<?> environmentType, Object environment) throws Exception {
        Class<?> implementation = runtime.loadClass("com.cozooo.dlc_bootstrap.internal.bootstrap.DlcBootstrapService");
        assertNotNull(implementation.getConstructor(), "The original no-argument service constructor must remain available");
        var constructor = implementation.getConstructor(java.util.function.Consumer.class, java.util.function.BooleanSupplier.class);
        var snapshots = new java.util.ArrayList<java.util.Map<String, Object>>();
        java.util.function.Consumer<java.util.Map<String, Object>> observe = snapshot -> {
            assertEquals(java.util.Set.of("stage", "completed", "total", "resource", "bytes", "totalBytes"), snapshot.keySet());
            assertNull(snapshot.getClass().getClassLoader(), "The callback map must be a JDK type");
            for (Object value : snapshot.values()) {
                assertTrue(value instanceof String || value instanceof Integer || value instanceof Long);
                assertNull(value.getClass().getClassLoader(), "Progress must not expose private-runtime classes");
            }
            assertThrows(UnsupportedOperationException.class, () -> snapshot.put("unexpected", true));
            snapshots.add(snapshot);
        };
        Object reporting = constructor.newInstance(observe, (java.util.function.BooleanSupplier) () -> false);
        serviceType.getMethod("initialize", environmentType).invoke(reporting, environment);
        var stages = snapshots.stream().map(snapshot -> snapshot.get("stage")).toList();
        assertTrue(stages.containsAll(java.util.List.of("SCANNING", "PLANNED", "VERIFYING", "COMPLETE")));

        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<java.util.Map<String, Object>> cancelOnCompletion = snapshot -> {
            if ("COMPLETE".equals(snapshot.get("stage"))) cancelled.set(true);
        };
        Object interrupted = constructor.newInstance(cancelOnCompletion, (java.util.function.BooleanSupplier) cancelled::get);
        try {
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> serviceType.getMethod("initialize", environmentType).invoke(interrupted, environment));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertTrue(cancelled.get());
            assertNull(System.getProperty(DlcStartupRequirements.READY_PROPERTY), "Cancellation must not publish early.ready");
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            System.clearProperty(DlcStartupRequirements.READY_PROPERTY);
        }
    }

    private void verifyNativeDiscovery(Path artifact, String loaderName) throws Exception {
        var urls = new java.util.ArrayList<URL>();
        urls.add(artifact.toUri().toURL());
        for (String file : System.getProperty(loaderName + "LocatorApi").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            urls.add(Path.of(file).toUri().toURL());
        }
        try (var isolated = new URLClassLoader(urls.toArray(URL[]::new), loaderName.equals("forge") ? getClass().getClassLoader() : ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("com.cozooo.dlc_bootstrap.launch.")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = findClass(name);
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        }) {
            Class<?> runtimeBridge = isolated.loadClass("com.cozooo.dlc_bootstrap.launch.DlcEarlyRuntime");
            Class<?> environmentType = isolated.loadClass("cpw.mods.modlauncher.api.IEnvironment");
            isolated.loadClass("cpw.mods.modlauncher.api.TypesafeMap").getConstructor(Class.class).newInstance(environmentType);
            Class<?> keys = isolated.loadClass("cpw.mods.modlauncher.api.IEnvironment$Keys");
            Object gameKey = ((Supplier<?>) keys.getField("GAMEDIR").get(null)).get();
            Object targetKey = ((Supplier<?>) keys.getField("LAUNCHTARGET").get(null)).get();
            Object environment = Proxy.newProxyInstance(isolated, new Class<?>[]{environmentType}, (proxy, method, args) -> {
                if (method.getName().equals("getProperty")) {
                    if (args[0] == gameKey) return Optional.of(game);
                    if (args[0] == targetKey) return Optional.of("forgeserver");
                    return Optional.empty();
                }
                throw new UnsupportedOperationException(method.getName());
            });
            runtimeBridge.getMethod("initialize", environmentType).invoke(null, environment);
            Path path = (Path) runtimeBridge.getMethod("modFile").invoke(null);
            var runtimeField = runtimeBridge.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            ClassLoader privateRuntime = (ClassLoader) runtimeField.get(null);
            Class<?> payload = privateRuntime.loadClass("com.cozooo.dlc_bootstrap.internal.bootstrap.DlcModPayload");
            String prefix = loaderName.equals("forge") ? "net.minecraftforge.forgespi" : "net.neoforged.neoforgespi";
            Class<?> spi = isolated.loadClass(prefix + (loaderName.equals("forge") ? ".locating.IModLocator" : ".locating.IModFileCandidateLocator"));
            @SuppressWarnings({"unchecked", "rawtypes"})
            ServiceLoader<?> services = ServiceLoader.load((Class) spi, isolated);
            Object locator = services.stream().filter(provider -> provider.type().getName().startsWith("com.cozooo.dlc_bootstrap.launch."))
                .findFirst().orElseThrow().get();
            if (loaderName.equals("forge")) {
                Class<?> fml = isolated.loadClass("net.minecraftforge.fml.loading.FMLLoader");
                Class<?> versionType = isolated.loadClass("net.minecraftforge.fml.loading.VersionInfo");
                var version = versionType.getConstructor(String.class, String.class, String.class, String.class)
                    .newInstance("47.2.0", "1.20.1", "20230612", "net.minecraftforge");
                var versionField = fml.getDeclaredField("versionInfo");
                versionField.setAccessible(true);
                versionField.set(null, version);
                var files = (java.util.List<?>) spi.getMethod("scanMods").invoke(locator);
                assertEquals(1, files.size());
                Object candidate = files.get(0);
                assertNull(candidate.getClass().getMethod("ex").invoke(candidate));
                Object mod = candidate.getClass().getMethod("file").invoke(candidate);
                assertEquals(path, mod.getClass().getMethod("getFilePath").invoke(mod));
                Object secureJar = mod.getClass().getMethod("getSecureJar").invoke(mod);
                Object provider = secureJar.getClass().getMethod("moduleDataProvider").invoke(secureJar);
                var uri = (java.net.URI) isolated.loadClass("cpw.mods.jarhandling.SecureJar$ModuleDataProvider").getMethod("uri").invoke(provider);
                var resolve = runtimeBridge.getDeclaredMethod("distributionFile", java.net.URI.class);
                resolve.setAccessible(true);
                assertEquals(path, resolve.invoke(null, uri));
                var infos = (java.util.List<?>) mod.getClass().getMethod("getModInfos").invoke(mod);
                assertEquals("dlc_manager", infos.get(0).getClass().getMethod("getModId").invoke(infos.get(0)));
                payload.getMethod("prepare", Path.class, boolean.class).invoke(null, game, true);
                assertTrue(((java.util.List<?>) spi.getMethod("scanMods").invoke(locator)).isEmpty());
            } else {
                Class<?> fileType = isolated.loadClass(prefix + ".locating.IModFile");
                Object file = Proxy.newProxyInstance(isolated, new Class<?>[]{fileType}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
                Class<?> pipelineType = isolated.loadClass(prefix + ".locating.IDiscoveryPipeline");
                var calls = new java.util.concurrent.atomic.AtomicInteger();
                Object pipeline = Proxy.newProxyInstance(isolated, new Class<?>[]{pipelineType}, (proxy, method, args) -> {
                    if (method.getName().equals("addPath")) {
                        assertEquals(path, args[0]);
                        assertEquals("ERROR", args[2].toString());
                        calls.incrementAndGet();
                        return Optional.of(file);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
                Class<?> contextType = isolated.loadClass(prefix + ".ILaunchContext");
                Object context = Proxy.newProxyInstance(isolated, new Class<?>[]{contextType}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
                var scan = spi.getMethod("findCandidates", contextType, pipelineType);
                scan.invoke(locator, context, pipeline);
                assertEquals(1, calls.get());
                payload.getMethod("prepare", Path.class, boolean.class).invoke(null, game, true);
                scan.invoke(locator, context, pipeline);
                assertEquals(1, calls.get());
            }
        }
    }

}
