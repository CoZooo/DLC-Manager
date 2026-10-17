package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcFolderMetadataTest {
    @TempDir Path directory;

    private Path root() { return directory.resolve("DLC"); }
    private DlcFolderMetadata folders() { return new DlcFolderMetadata(List.of(root()), directory); }
    private void write(String parent, String text) throws Exception {
        Path path = root().resolve(parent).resolve(DlcFolderMetadata.FILE_NAME);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }
    private DlcItem item(String path) {
        return new DlcItem(path, path, path, DlcType.MOD, root().resolve(path), null, false,
            Map.of(), Map.of(), List.of(), List.of(), true, false);
    }

    @Test void missingMetadataUsesNamesAndDoesNotCreateFiles() {
        var groups = folders().group(List.of(item("Z/z.jar"), item("A/a.jar")), "zh_cn");
        assertEquals(List.of("A", "Z"), groups.stream().map(DlcFolderMetadata.Group::label).toList());
        assertFalse(Files.exists(root()));
    }

    @Test void signedPrioritiesAndLocalizedNestedLabelsApplyToSiblings() throws Exception {
        write("", "[A]\npriority=5\nDisplayName.en_US='Alpha'\nDisplayName.zh_CN='甲'\n[Z]\npriority=-2\n");
        write("A", "[B]\npriority=-3\nDisplayName.zh_CN='乙'\n[C]\npriority=2\n");
        var items = List.of(item("A/C/c.jar"), item("A/B/b.jar"), item("Z/z.jar"));
        assertEquals(List.of("Z", "甲/乙", "甲/C"), folders().group(items, "zh_CN").stream().map(DlcFolderMetadata.Group::label).toList());
        assertEquals("Alpha/B", folders().group(List.of(item("A/B/b.jar")), "en_us").get(0).label());
    }

    @Test void equalLocalAndRemoteDefinitionsMergeRegardlessOfFieldOrderAndLocaleCase() throws Exception {
        write("", "[A]\npriority=-1\nDisplayName.en_US='Folder'\nDisplayName.zh_CN='文件夹'\n");
        write("remote", "# same definition\n[A]\nDisplayName.zh_cn='文件夹'\nDisplayName.en_us='Folder'\npriority=-1\n");
        var groups = folders().group(List.of(item("A/local.jar"), item("downloaded/DLCs/A/remote.jar")), "zh_cn");
        assertEquals(1, groups.size());
        assertEquals("文件夹", groups.get(0).label());
        assertEquals(2, groups.get(0).items().size());
    }

    @Test void oneSidedDefinitionIsSharedAndDefaultZeroMatchesExplicitZero() throws Exception {
        write("remote", "[A]\nDisplayName.en_US='Remote folder'\n[B]\npriority=0\n");
        write("", "[B]\n");
        var groups = folders().group(List.of(item("A/local.jar"), item("downloaded/DLCs/A/remote.jar"), item("B/local.jar"), item("downloaded/DLCs/B/remote.jar")), "en_us");
        assertEquals(2, groups.size());
        assertEquals(List.of("Remote folder", "B"), groups.stream().map(DlcFolderMetadata.Group::label).toList());
        assertTrue(groups.stream().allMatch(group -> group.items().size() == 2));
    }

    @Test void conflictingMetadataSplitsGroupsEvenIfCurrentNamesMatch() throws Exception {
        write("", "[A]\nDisplayName.en_US='Same'\nDisplayName.zh_CN='本地'\n");
        write("remote", "[A]\nDisplayName.en_US='Same'\nDisplayName.zh_CN='远程'\n");
        var groups = folders().group(List.of(item("A/a.jar"), item("downloaded/DLCs/A/b.jar")), "en_us");
        assertEquals(2, groups.size());
        assertEquals(groups.get(0).label(), groups.get(1).label());
        assertNotEquals(groups.get(0).key(), groups.get(1).key());
        assertTrue(groups.stream().allMatch(group -> group.items().size() == 1));
    }

    @Test void conflictingParentDefinitionsKeepDescendantsSeparate() throws Exception {
        write("", "[A]\npriority=0\n");
        write("remote", "[A]\npriority=-1\n");
        assertEquals(2, folders().group(List.of(item("A/B/a.jar"), item("downloaded/DLCs/A/B/b.jar")), "en_us").size());
    }

    @Test void nestedMetadataOnlyDescribesImmediateChildrenAndRootResourcesMerge() throws Exception {
        write("", "[A]\nDisplayName.en_US='Top'\n");
        write("A", "[B]\nDisplayName.en_US='Nested'\n");
        var groups = folders().group(List.of(item("a.jar"), item("downloaded/DLCs/b.jar"), item("A/B/c.jar"), item("B/d.jar")), "en_us");
        assertEquals(3, groups.size());
        assertTrue(groups.get(0).isRoot());
        assertEquals(2, groups.get(0).items().size());
        assertEquals(List.of("", "Top/Nested", "B"), groups.stream().map(DlcFolderMetadata.Group::label).toList());
    }

    @Test void legacyRemoteResourcesUseTheConfigurationDirectoryWithoutMovingFiles() throws Exception {
        write("", "[A]\nDisplayName.en_US='Local'\n");
        write("remote", "[A]\nDisplayName.en_US='Remote'\n");
        DlcItem remote = item("A/legacy.jar");
        remote.setGroupDirectory(root().resolve("remote/A"));
        var groups = folders().group(List.of(item("A/local.jar"), remote), "en_us");
        assertEquals(2, groups.size());
        assertTrue(groups.stream().anyMatch(group -> group.label().equals("Remote") && group.items().contains(remote)));
        assertEquals(root().resolve("A/legacy.jar"), remote.getSourcePath());
    }

    @Test void metadataIsNeverLoadedOrRewrittenAsARemoteResource() throws Exception {
        write("remote", "[A]\npriority=-1\nDisplayName.en_US='Folder'\n");
        write("remote/A", "[B]\npriority=2\n");
        String original = Files.readString(root().resolve("remote/_metadata.dc"));
        assertTrue(DlcRemoteLoader.load(root()).isEmpty());
        assertNull(DlcRemoteLoader.loadSingle(root().resolve("remote/_metadata.dc"), root()));
        assertEquals(original, Files.readString(root().resolve("remote/_metadata.dc")));
    }

    @Test void quotedFolderNamesRemainLiteralAndMalformedMetadataFallsBack() throws Exception {
        write("", "[\"A.B\"]\nDisplayName.en_US='Dotted'\n[Folder Name]\nDisplayName.zh_CN='名称'\n");
        assertEquals("Dotted", folders().group(List.of(item("A.B/a.jar")), "en_us").get(0).label());
        assertEquals("名称", folders().group(List.of(item("Folder Name/a.jar")), "zh_cn").get(0).label());
        write("", "[broken\n");
        assertEquals("A", folders().group(List.of(item("A/a.jar")), "en_us").get(0).label());
    }
}
