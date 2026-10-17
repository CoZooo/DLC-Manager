package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcScannerTest {
    @TempDir
    Path tempDir;

    private Path gameDir;
    private Path dlcRoot;

    @BeforeEach
    void setUp() {
        gameDir = tempDir.resolve("game");
        dlcRoot = gameDir.resolve("DLC");
        PlatformServices.setProvider(new TestPlatformProvider(gameDir, tempDir.resolve("config")));
        DlcConfig.setDlcRoot("DLC");
        DlcConfig.setResourcepackRoot("resourcepacks");
        DlcConfig.setDatapackRoot("datapacks");
        DlcConfig.setTaczRoot("tacz");
        DlcStateConfig.load();
    }

    @AfterEach
    void tearDown() {
        PlatformServices.setProvider(null);
        DlcRuntimeState.updateItemIndex(null);
    }

    @Test
    void scanClassifiesArchivesDirectoriesAndSkipsMetadataRoots() throws Exception {
        Files.createDirectories(dlcRoot.resolve("info"));
        Files.writeString(dlcRoot.resolve("mod-a.jar"), "jar");
        Files.writeString(dlcRoot.resolve("info").resolve("ignored.jar"), "jar");
        Path resourceDir = dlcRoot.resolve("resource-dir");
        Files.createDirectories(resourceDir.resolve("assets").resolve("minecraft"));
        Files.writeString(resourceDir.resolve("pack.mcmeta"), "{}");
        Path datapackZip = dlcRoot.resolve("data-pack.zip");
        writeZip(datapackZip, "pack.mcmeta", "{}", "data/example/functions/test.mcfunction", "say test");

        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan();

        assertEquals(List.of("mod-a.jar"), names(grouped.get(DlcType.MOD)));
        assertEquals(List.of("resource-dir"), names(grouped.get(DlcType.RESOURCE_PACK)));
        assertEquals(List.of("data-pack.zip"), names(grouped.get(DlcType.DATA_PACK)));
        assertTrue(grouped.values().stream().flatMap(List::stream).noneMatch(item -> "ignored.jar".equals(item.getName())));
    }

    @Test
    void scanUsesRemoteMetadataForUnknownFilesWithAppliedTargets() throws Exception {
        Files.createDirectories(dlcRoot.resolve("remote"));
        Files.writeString(dlcRoot.resolve("remote").resolve("config.dc"), """
            identifier = "config-entry"
            file_name = "config.toml"
            type = "others"
            appliedTarget = "config/config.toml"

            [DisplayName]
            en_us = "Config Entry"
            """);
        Files.writeString(dlcRoot.resolve("config.toml"), "value = true");

        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan();

        DlcItem item = grouped.get(DlcType.UNKNOWN).get(0);
        assertEquals("config-entry", item.getIdentifier());
        assertEquals("config.toml", item.getName());
        assertEquals("Config Entry", item.getDisplayNames().get("en_us"));
        assertEquals("config/config.toml", item.getAppliedTargetRaw());
    }

    @Test
    void folderMetadataIsNotAResourceAndRemoteItemsKeepTheirLogicalOrigin() throws Exception {
        Files.createDirectories(dlcRoot.resolve("remote/A"));
        Files.createDirectories(dlcRoot.resolve("A"));
        Files.createDirectories(dlcRoot.resolve("downloaded/DLCs/A"));
        Files.writeString(dlcRoot.resolve("_metadata.dc"), "[A]\nDisplayName.en_US='Local'\n");
        Files.writeString(dlcRoot.resolve("remote/_metadata.dc"), "[A]\nDisplayName.en_US='Remote'\n");
        Files.writeString(dlcRoot.resolve("remote/A/entry.dc"), "[basic]\nidentifier='remote-entry'\nfile_name='remote.jar'\ntype='mod'\n");
        Files.writeString(dlcRoot.resolve("A/local.jar"), "jar");
        Files.writeString(dlcRoot.resolve("downloaded/DLCs/A/remote.jar"), "jar");
        var items = new DlcScanner().scan().get(DlcType.MOD);
        assertEquals(2, items.size());
        var remote = items.stream().filter(item -> item.getName().equals("remote.jar")).findFirst().orElseThrow();
        assertEquals(dlcRoot.resolve("remote/A"), remote.getGroupDirectory());
        var groups = new DlcFolderMetadata(List.of(dlcRoot), gameDir).group(items, "en_us");
        assertEquals(List.of("Local", "Remote"), groups.stream().map(DlcFolderMetadata.Group::label).toList());
    }

    private static List<String> names(List<DlcItem> items) {
        return items.stream().map(DlcItem::getName).toList();
    }

    private static void writeZip(Path path, String... entries) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (int i = 0; i + 1 < entries.length; i += 2) {
                out.putNextEntry(new ZipEntry(entries[i]));
                out.write(entries[i + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }
}
