package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.PlatformServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxLocalConfigModelTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        PlatformServices.setProvider(new TestProvider(tempDir.resolve("game"), tempDir.resolve("game").resolve("config")));
        DlcConfig.loadClient();
    }

    @AfterEach
    void tearDown() {
        PlatformServices.setProvider(null);
    }

    @Test
    void exposesSameConfigGroupsAsMainClientConfigScreen() {
        DlcFxLocalConfigModel model = new DlcFxLocalConfigModel();

        assertEquals(4, model.options(DlcFxLocalConfigModel.Tab.PATHS).size());
        assertEquals(4, model.options(DlcFxLocalConfigModel.Tab.SERVER).size());
        assertEquals(25, model.options(DlcFxLocalConfigModel.Tab.UI).size());
        assertEquals(3, model.options(DlcFxLocalConfigModel.Tab.MODPACK).size());
        assertEquals(9, model.options(DlcFxLocalConfigModel.Tab.DOWNLOADS).size());
    }

    @Test
    void downloadMirrorModeChoicesMatchMainClientValues() {
        DlcFxLocalConfigModel model = new DlcFxLocalConfigModel();
        DlcFxLocalConfigModel.Option option = model.find("downloadMirrorMode");

        assertEquals(List.of(
                DlcConfig.DOWNLOAD_MIRROR_SMART_OFFICIAL,
                DlcConfig.DOWNLOAD_MIRROR_SMART_MIRROR,
                DlcConfig.DOWNLOAD_MIRROR_SMART_PRECHECK,
                DlcConfig.DOWNLOAD_MIRROR_OFFICIAL_ONLY,
                DlcConfig.DOWNLOAD_MIRROR_MIRROR_ONLY,
                DlcConfig.DOWNLOAD_MIRROR_OFFICIAL_FIRST,
                DlcConfig.DOWNLOAD_MIRROR_MIRROR_FIRST
        ), option.choiceValues());
    }

    @Test
    void applyingModelWritesBackThroughDlcConfig() {
        DlcFxLocalConfigModel model = new DlcFxLocalConfigModel();
        model.loadFromConfig();
        assertTrue(model.find("dlcRoot").setValueFromString("CustomDLC"));
        assertTrue(model.find("noticeDisplayMode").setValueFromString("main_menu_panel"));
        assertTrue(model.find("maxConcurrentDownloads").setValueFromString("7"));

        model.applyToConfig();

        assertEquals("CustomDLC", DlcConfig.getDlcRootRaw());
        assertTrue(DlcConfig.isNoticeDisplayModeMainMenuPanel());
        assertEquals(7, DlcConfig.getMaxConcurrentDownloads());
    }

    @Test
    void optionTracksWhetherValueEqualsDefault() {
        DlcFxLocalConfigModel model = new DlcFxLocalConfigModel();
        model.loadFromConfig();
        DlcFxLocalConfigModel.Option option = model.find("apiPort");

        assertTrue(option.isDefaultValue());
        assertTrue(option.setValueFromString("10087"));

        org.junit.jupiter.api.Assertions.assertFalse(option.isDefaultValue());
    }

    private record TestProvider(Path gameDir, Path configDir) implements PlatformServices.Provider {
        @Override
        public boolean isModLoaded(String modId) {
            return false;
        }

        @Override
        public boolean isDedicatedServer() {
            return false;
        }

        @Override
        public String languageCode() {
            return "en_us";
        }

        @Override
        public String loaderName() {
            return "fabric";
        }

        @Override
        public String mcVersion() {
            return "1.20.1";
        }

        @Override
        public String modVersion(String modId) {
            return "";
        }

        @Override
        public Path modFilePath(String modId) {
            return null;
        }

        @Override
        public String playerName() {
            return "Player";
        }

        @Override
        public String playerUuid() {
            return "00000000-0000-0000-0000-000000000000";
        }
    }
}
