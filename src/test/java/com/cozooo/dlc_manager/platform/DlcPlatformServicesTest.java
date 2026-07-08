package com.cozooo.dlc_manager.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DlcPlatformServicesTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetProvider() {
        DlcPlatformServices.setProvider(null);
    }

    @Test
    void readsLanguageFromOptionsFile() throws Exception {
        Files.writeString(tempDir.resolve("options.txt"), "sound:1.0\nlang:zh_cn\n");

        assertEquals("zh_cn", DlcPlatformServices.readLanguageFromOptions(tempDir));
    }

    @Test
    void ignoresMissingOrBlankLanguageInOptionsFile() throws Exception {
        assertNull(DlcPlatformServices.readLanguageFromOptions(tempDir));

        Files.writeString(tempDir.resolve("options.txt"), "lang:\n");
        assertNull(DlcPlatformServices.readLanguageFromOptions(tempDir));
    }

    @Test
    void forwardsStaticAccessToConfiguredProvider() {
        Path gameDir = tempDir.resolve("game");
        DlcPlatformServices.setProvider(new TestProvider(gameDir));

        assertEquals(gameDir, DlcPlatformServices.gameDir());
        assertEquals(gameDir.resolve("config"), DlcPlatformServices.configDir());
        assertEquals("TestLoader", DlcPlatformServices.loaderName());
        assertEquals("1.2.3", DlcPlatformServices.mcVersion());
    }

    @Test
    void legacyPlatformServicesWrapperUsesSameProvider() {
        Path gameDir = tempDir.resolve("legacy");
        PlatformServices.setProvider(new TestProvider(gameDir));

        assertEquals(gameDir, DlcPlatformServices.gameDir());
        assertEquals(DlcPlatformServices.get(), PlatformServices.get());
    }

    private record TestProvider(Path gameDir) implements DlcPlatformServices.Provider {
        @Override
        public Path configDir() {
            return gameDir.resolve("config");
        }

        @Override
        public boolean isModLoaded(String modId) {
            return "example".equals(modId);
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
            return "TestLoader";
        }

        @Override
        public String mcVersion() {
            return "1.2.3";
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
            return "";
        }

        @Override
        public String playerUuid() {
            return "";
        }
    }
}
