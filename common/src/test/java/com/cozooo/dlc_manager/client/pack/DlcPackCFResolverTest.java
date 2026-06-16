package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.download.CurseForgeClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DlcPackCFResolverTest {
    @Test
    void resolvesResourcePackTargetFromFileClassSlug() {
        CurseForgeClient.CurseForgeFile file = new CurseForgeClient.CurseForgeFile();
        file.classSlug = "resource-packs";

        assertEquals("resourcepacks", DlcPackCFResolver.resolveTargetDirectoryName(file, null));
    }

    @Test
    void resolvesDataPackTargetFromModCategorySlug() {
        CurseForgeClient.CurseForgeMod mod = new CurseForgeClient.CurseForgeMod();
        mod.categorySlugs.add("data-packs");

        assertEquals("datapacks", DlcPackCFResolver.resolveTargetDirectoryName(null, mod));
    }

    @Test
    void defaultsUnknownCurseForgeFilesToMods() {
        CurseForgeClient.CurseForgeFile file = new CurseForgeClient.CurseForgeFile();
        file.classSlug = "mc-mods";

        assertEquals("mods", DlcPackCFResolver.resolveTargetDirectoryName(file, null));
    }
}
