package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcPackModrinthTest {
    @TempDir Path temp;

    @BeforeEach
    void isolatePlatformFiles() {
        Path instance = temp.resolve("instance");
        PlatformServices.setProvider(new TestPlatformProvider(instance, instance.resolve("config")));
    }

    @AfterEach void reset() { PlatformServices.setProvider(null); }

    @Test void detectsMrpackAndPreviewsOnlyMetadataWithoutExtractingTheIndex() throws Exception {
        Path root = pack("preview");
        Files.writeString(root.resolve("modrinth.index.json"), "not parsed during preview");
        Path dlc = temp.resolve("DLC");
        Path archive = dlc.resolve("pack/example.MRPACK");
        Files.createDirectories(archive.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive)); var files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry(root.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
        assertTrue(DlcPackArchive.findArchives(dlc).contains(archive.toAbsolutePath().normalize()));
        var preview = DlcPackArchive.extractPreviewToStaging(archive, dlc);
        assertFalse(Files.exists(preview.stagingDir.resolve("modrinth.index.json")));
        var result = DlcPackPackage.inspectPreview(preview);
        assertTrue(result.valid, result.error);
        assertEquals(DlcPackPackage.Format.MODRINTH, result.pack.format);
        assertEquals("MR", result.pack.metadata.identifier);
        assertTrue(result.pack.modrinthFiles.isEmpty());
        assertFalse(DlcPackPackage.inspect(root).valid, "A malformed index must fail a full inspection");
    }

    @Test void requiresPackMetadataAndRejectsUnsupportedOrUnsafeManifests() throws Exception {
        Path plain = temp.resolve("plain"); Files.createDirectories(plain);
        Files.writeString(plain.resolve("modrinth.index.json"), index(List.of()).toString());
        assertFalse(DlcPackPackage.inspect(plain).valid);
        for (String path : List.of("../escape.jar", "/absolute.jar", "C:\\escape.jar", "mods/../escape.jar", "mods//a.jar", "mods/./a.jar")) {
            JsonObject entry = entry(path, "data", "required", "required");
            Files.writeString(plain.resolve("modrinth.index.json"), index(List.of(entry)).toString());
            assertThrows(java.io.IOException.class, () -> DlcPackModrinthManifest.read(plain.resolve("modrinth.index.json")), path);
        }
        JsonObject bad = index(List.of()); bad.addProperty("formatVersion", 2);
        Files.writeString(plain.resolve("modrinth.index.json"), bad.toString());
        assertThrows(java.io.IOException.class, () -> DlcPackModrinthManifest.read(plain.resolve("modrinth.index.json")));
        JsonObject duplicated = entry("mods/a.jar", "data", "required", "required");
        Files.writeString(plain.resolve("modrinth.index.json"), index(List.of(duplicated, duplicated)).toString());
        assertThrows(java.io.IOException.class, () -> DlcPackModrinthManifest.read(plain.resolve("modrinth.index.json")));
    }

    @Test void selectsEnvironmentFilesAndAppliesSideOverridesAfterCommonOverrides() throws Exception {
        Path root = pack("sides");
        Files.writeString(root.resolve("modrinth.index.json"), index(List.of(
            entry("mods/client.jar", "client", "required", "unsupported"),
            entry("mods/server.jar", "server", "unsupported", "required"),
            entry("mods/optional.jar", "optional", "optional", "optional"))).toString());
        write(root.resolve("overrides/config/shared.toml"), "common");
        write(root.resolve("client-overrides/config/shared.toml"), "client");
        write(root.resolve("server-overrides/config/shared.toml"), "server");
        for (boolean server : List.of(false, true)) {
            provider(server);
            var result = DlcPackPackage.inspect(root);
            assertTrue(result.valid, result.error);
            List<String> files = DlcPackModrinthManifest.filesForEnvironment(result.pack).stream().map(file -> file.path).toList();
            assertEquals(List.of(server ? "mods/server.jar" : "mods/client.jar", "mods/optional.jar"), files);
            var plan = DlcPackInstallPlan.build(result.pack);
            assertEquals(1, plan.files.size());
            assertEquals(server ? "server" : "client", Files.readString(plan.files.get(0).source));
        }
    }

    @Test void installsVerifiedManifestResourcesAndRestoresOverwrittenFilesOnUninstall() throws Exception {
        provider(false);
        Path root = pack("install"), instance = temp.resolve("instance"), dlc = instance.resolve("DLC");
        Files.writeString(root.resolve("modrinth.index.json"), index(List.of(entry("mods/a.jar", "downloaded", "required", "required"))).toString());
        write(root.resolve("overrides/config/a.toml"), "common");
        write(root.resolve("client-overrides/config/a.toml"), "client override");
        write(instance.resolve("config/a.toml"), "original config");
        write(instance.resolve("mods/a.jar"), "original mod");
        Path packDir = dlc.resolve("tmp/pack/MR");
        write(packDir.resolve("mr_cache/mods/a.jar"), "downloaded");
        var result = DlcPackPackage.inspect(root);
        assertTrue(result.valid, result.error);
        var installer = new DlcPackInstaller(instance, dlc);
        installer.install(result.pack);
        assertEquals("downloaded", Files.readString(instance.resolve("mods/a.jar")));
        assertEquals("client override", Files.readString(instance.resolve("config/a.toml")));
        assertFalse(Files.exists(instance.resolve("modrinth.index.json")));
        assertFalse(Files.exists(instance.resolve("metadata")));
        installer.uninstall("MR");
        assertEquals("original mod", Files.readString(instance.resolve("mods/a.jar")));
        assertEquals("original config", Files.readString(instance.resolve("config/a.toml")));
    }

    @Test void validatesSizeAndBothHashesAndHonorsCancellationBeforeDownloading() throws Exception {
        Path root = pack("integrity");
        Files.writeString(root.resolve("modrinth.index.json"), index(List.of(entry("mods/a.jar", "valid", "required", "required"))).toString());
        var pack = DlcPackPackage.inspect(root).pack;
        var file = pack.modrinthFiles.get(0);
        Path cached = temp.resolve("file.jar");
        write(cached, "valid"); assertTrue(DlcPackModrinthManifest.verifyFile(cached, file));
        write(cached, "wrong"); assertFalse(DlcPackModrinthManifest.verifyFile(cached, file));
        write(cached, "longer"); assertFalse(DlcPackModrinthManifest.verifyFile(cached, file));
        assertThrows(DlcDownloadService.DownloadCanceledException.class, () -> DlcPackManifestResolver.downloadManifestFiles(pack, temp.resolve("stage"), temp.resolve("cache"), () -> true, null));
    }

    @Test void manifestCannotBypassProtectedDlcPaths() throws Exception {
        Path root = pack("forbidden"), instance = temp.resolve("instance"), dlc = instance.resolve("DLC");
        Files.writeString(root.resolve("modrinth.index.json"), index(List.of(entry("DLC/tmp/secret", "bad", "required", "required"))).toString());
        var pack = DlcPackPackage.inspect(root).pack;
        assertTrue(DlcPackInstallPlan.build(pack, instance, dlc).forbiddenPaths.contains("DLC/tmp/secret"));
        assertThrows(java.io.IOException.class, () -> new DlcPackInstaller(instance, dlc).install(pack));
        assertFalse(Files.exists(dlc.resolve("tmp/secret")));
    }

    @Test void retriesTheNextUrlAfterAHashFailureAndOnlyCachesVerifiedFiles() throws Exception {
        Path root = pack("fallback");
        JsonObject file = entry("mods/a.jar", "valid", "required", "required");
        file.getAsJsonArray("downloads").add("https://cdn.modrinth.com/test/fallback.jar");
        Files.writeString(root.resolve("modrinth.index.json"), index(List.of(file)).toString());
        var pack = DlcPackPackage.inspect(root).pack;
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        DlcPackModrinthManifest.Downloader download = (url, directory, name, size, progress, cancel) -> {
            Files.createDirectories(directory);
            Path result = directory.resolve(name);
            Files.writeString(result, attempts.incrementAndGet() == 1 ? "wrong" : "valid");
            return result;
        };
        Path cache = temp.resolve("download-cache");
        var files = DlcPackModrinthManifest.downloadManifestFiles(pack, temp.resolve("stage"), cache, () -> false, null, download);
        assertEquals(2, attempts.get());
        assertEquals(1, files.size());
        assertEquals("valid", Files.readString(files.get(0).source));
        DlcPackModrinthManifest.downloadManifestFiles(pack, temp.resolve("stage"), cache, () -> false, null, download);
        assertEquals(2, attempts.get(), "A verified cache entry must not be downloaded again");
        Files.writeString(files.get(0).source, "wrong");
        assertThrows(java.io.IOException.class, () -> DlcPackModrinthManifest.downloadManifestFiles(pack, temp.resolve("failed-stage"), cache, () -> false, null,
            (url, directory, name, size, progress, cancel) -> { throw new java.io.IOException("Network unavailable"); }));
    }

    private Path pack(String folder) throws Exception {
        Path root = temp.resolve(folder);
        write(root.resolve("overrides/metadata/pack.dc"), "[basic]\nidentifier = \"MR\"\nversion = \"1.0.0\"\n");
        Files.writeString(root.resolve("modrinth.index.json"), index(List.of()).toString());
        return root;
    }
    private void write(Path path, String value) throws Exception { Files.createDirectories(path.getParent()); Files.writeString(path, value); }
    private JsonObject index(List<JsonObject> files) {
        JsonObject index = new JsonObject(); index.addProperty("formatVersion", 1); index.addProperty("game", "minecraft");
        index.addProperty("name", "Example"); index.addProperty("versionId", "1.0.0");
        JsonArray entries = new JsonArray(); files.forEach(entries::add); index.add("files", entries); return index;
    }
    private JsonObject entry(String path, String data, String client, String server) throws Exception {
        byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        JsonObject file = new JsonObject(); file.addProperty("path", path); file.addProperty("fileSize", bytes.length);
        JsonObject hashes = new JsonObject();
        hashes.addProperty("sha1", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)));
        hashes.addProperty("sha512", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(bytes)));
        file.add("hashes", hashes);
        JsonArray urls = new JsonArray(); urls.add("https://cdn.modrinth.com/test/a.jar"); file.add("downloads", urls);
        JsonObject env = new JsonObject(); env.addProperty("client", client); env.addProperty("server", server); file.add("env", env); return file;
    }
    private void provider(boolean server) {
        PlatformServices.setProvider(new DlcPlatformServices.Provider() {
            public Path gameDir() { return temp.resolve("instance"); }
            public Path configDir() { return gameDir().resolve("config"); }
            public boolean isModLoaded(String id) { return false; }
            public boolean isDedicatedServer() { return server; }
            public String languageCode() { return "en_us"; }
            public String loaderName() { return "fabric"; }
            public String mcVersion() { return "1.20.1"; }
            public String modVersion(String id) { return ""; }
            public Path modFilePath(String id) { return null; }
            public String playerName() { return "Player"; }
            public String playerUuid() { return ""; }
        });
    }
}
