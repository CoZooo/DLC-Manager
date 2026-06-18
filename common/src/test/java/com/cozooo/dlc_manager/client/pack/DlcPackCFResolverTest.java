package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.download.DlcCurseForgeClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DlcPackCFResolverTest {
    @Test
    void resolvesResourcePackTargetFromFileClassSlug() {
        DlcCurseForgeClient.CurseForgeFile file = new DlcCurseForgeClient.CurseForgeFile();
        file.classSlug = "resource-packs";

        assertEquals("resourcepacks", DlcPackCFResolver.resolveTargetDirectoryName(file, null, "example.zip"));
    }

    @Test
    void resolvesDataPackTargetFromModCategorySlug() {
        DlcCurseForgeClient.CurseForgeMod mod = new DlcCurseForgeClient.CurseForgeMod();
        mod.categorySlugs.add("data-packs");

        assertEquals("datapacks", DlcPackCFResolver.resolveTargetDirectoryName(null, mod, "example.zip"));
    }

    @Test
    void resolvesJarFilesToMods() {
        DlcCurseForgeClient.CurseForgeFile file = new DlcCurseForgeClient.CurseForgeFile();
        file.classSlug = "data-packs";

        assertEquals("mods", DlcPackCFResolver.resolveTargetDirectoryName(file, null, "example.jar"));
    }

    @Test
    void doesNotResolveNonJarFilesToMods() {
        DlcCurseForgeClient.CurseForgeFile file = new DlcCurseForgeClient.CurseForgeFile();
        file.classSlug = "mc-mods";

        assertEquals("resourcepacks", DlcPackCFResolver.resolveTargetDirectoryName(file, null, "example.zip"));
    }

    @Test
    void doesNotMisclassifyWorldRelatedSlugsAsDatapacks() {
        DlcCurseForgeClient.CurseForgeFile file = new DlcCurseForgeClient.CurseForgeFile();
        file.classSlug = "world-generation";

        assertEquals("resourcepacks", DlcPackCFResolver.resolveTargetDirectoryName(file, null, "example.zip"));
    }

    @Test
    void ignoresDlcManagerModpackJarNames() {
        assertEquals("mods", DlcPackCFResolver.resolveTargetDirectoryName(null, null, "example.jar"));
        assertEquals("resourcepacks", DlcPackCFResolver.resolveTargetDirectoryName(null, null, "example.zip"));
        assertEquals(true, DlcPackInstallPlan.isIgnoredModpackJarFileName("DLC-Manager-Fabric-1.20.1-1.0.0.jar"));
        assertEquals(false, DlcPackInstallPlan.isIgnoredModpackJarFileName("example.jar"));
    }
}
