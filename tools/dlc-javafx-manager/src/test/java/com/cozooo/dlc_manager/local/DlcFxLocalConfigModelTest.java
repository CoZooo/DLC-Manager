package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.ui.config.DlcFxConfigModel;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        DlcFxConfigModel model = new DlcFxConfigModel();

        assertEquals(4, model.options(DlcFxConfigModel.Tab.PATHS).size());
        assertEquals(4, model.options(DlcFxConfigModel.Tab.SERVER).size());
        assertEquals(26, model.options(DlcFxConfigModel.Tab.UI).size());
        assertEquals(5, model.options(DlcFxConfigModel.Tab.MODPACK).size());
        assertEquals(9, model.options(DlcFxConfigModel.Tab.DOWNLOADS).size());
    }

    @Test
    void downloadMirrorModeChoicesMatchMainClientValues() {
        DlcFxConfigModel model = new DlcFxConfigModel();
        DlcFxConfigModel.Option option = model.find("downloadMirrorMode");

        assertEquals(List.of(
                DlcConfig.DOWNLOAD_MIRROR_SMART_OFFICIAL,
                DlcConfig.DOWNLOAD_MIRROR_SMART_MIRROR,
                DlcConfig.DOWNLOAD_MIRROR_SMART_PRECHECK,
                DlcConfig.DOWNLOAD_MIRROR_OFFICIAL_ONLY,
                DlcConfig.DOWNLOAD_MIRROR_MIRROR_ONLY,
                DlcConfig.DOWNLOAD_MIRROR_OFFICIAL_FIRST,
                DlcConfig.DOWNLOAD_MIRROR_MIRROR_FIRST
        ), option.choiceValues());
        assertTrue(model.find("titleAnchor").choiceValues().contains(DlcConfig.TITLE_ANCHOR_HIDDEN));
    }

    @Test
    void applyingModelWritesBackThroughDlcConfig() {
        DlcFxConfigModel model = new DlcFxConfigModel();
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
        DlcFxConfigModel model = new DlcFxConfigModel();
        model.loadFromConfig();
        DlcFxConfigModel.Option option = model.find("apiPort");

        assertTrue(option.isDefaultValue());
        assertTrue(option.setValueFromString("10087"));

        org.junit.jupiter.api.Assertions.assertFalse(option.isDefaultValue());
    }

    @Test
    void serverSourceEditsServerConfigJsonWithoutUsingLocalDlcConfig() {
        JsonObject root = DlcConfig.buildServerConfigJson();
        JsonObject[] saved = new JsonObject[1];
        DlcFxConfigModel model = new DlcFxConfigModel(DlcFxConfigModel.serverSource(root, json -> saved[0] = json));
        model.loadFromConfig();

        assertEquals(5, model.options(DlcFxConfigModel.Tab.SERVER).size());
        assertTrue(model.find("serverPort").setValueFromString("28888"));
        assertTrue(model.find("serverPassword").setValueFromString("secret"));
        model.applyToConfig();

        JsonObject server = saved[0].getAsJsonObject("server");
        assertFalse(saved[0].getAsJsonObject("ui").has("serverSupport"));
        assertEquals(28888, server.get("serverPort").getAsInt());
        assertEquals("secret", server.get("serverPassword").getAsString());
        assertEquals(28888, model.snapshot().getAsJsonObject("server").get("serverPort").getAsInt());
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
