package com.cozooo.dlc_manager.javafx;

import com.cozooo.dlc_manager.platform.PlatformServices;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Standalone {@link PlatformServices.Provider} for the JavaFX DLC Manager.
 * <p>
 * Unlike the in-game provider which reads the running Minecraft instance, this
 * implementation returns the game directory chosen by the user in the UI.  By
 * installing it via {@code PlatformServices.setProvider(...)} at startup, every
 * piece of logic ported from {@code common} (DlcScanner, DlcApplier, DlcConfig,
 * DlcStateConfig, …) operates against the selected game directory without any
 * modification.
 * <p>
 * The game directory and config directory are mutable so the UI can update them
 * when the user picks a different game folder.
 */
public final class StandalonePlatformProvider implements PlatformServices.Provider {

    private volatile Path gameDir;
    private volatile Path configDir;

    public StandalonePlatformProvider() {
        // Default to the current working directory until the user picks one.
        this.gameDir = Path.of("").toAbsolutePath().normalize();
        this.configDir = gameDir.resolve("config");
    }

    public StandalonePlatformProvider(Path gameDir) {
        setGameDir(gameDir);
    }

    /**
     * Update the target game directory.  {@code configDir} is derived as
     * {@code gameDir/config}.
     */
    public void setGameDir(Path gameDir) {
        if (gameDir == null) {
            return;
        }
        this.gameDir = gameDir.toAbsolutePath().normalize();
        this.configDir = this.gameDir.resolve("config");
    }

    @Override
    public Path gameDir() {
        return gameDir;
    }

    @Override
    public Path configDir() {
        return configDir;
    }

    @Override
    public boolean isModLoaded(String modId) {
        // In standalone mode we cannot query the mod loader.
        // Detection is done via file existence in DlcScanner instead.
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
        // Unknown until we inspect the game dir; left blank.
        return "";
    }

    @Override
    public String mcVersion() {
        return "";
    }

    @Override
    public String modVersion(String modId) {
        return "";
    }

    @Override
    public Path modFilePath(String modId) {
        return null;
    }

    public boolean isResourcePackSelected(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        Set<String> selected = selectedResourcePackIds();
        if (selected != null) {
            String stripped = stripArchiveExtension(fileName);
            return selected.contains("file/" + fileName)
                || selected.contains("file/" + stripped)
                || selected.contains("vanilla/" + fileName)
                || selected.contains("vanilla/" + stripped);
        }
        for (Path dir : resourcepackRoots()) {
            if (java.nio.file.Files.exists(dir.resolve(fileName))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String playerName() {
        return "DLC-Manager-JavaFX";
    }

    @Override
    public String playerUuid() {
        return "javafx-manager-0000-0000-0000-000000000000";
    }

    private Set<String> selectedResourcePackIds() {
        Path options = gameDir.resolve("options.txt");
        if (!java.nio.file.Files.exists(options)) {
            return null;
        }
        try {
            for (String line : java.nio.file.Files.readAllLines(options)) {
                if (line == null || !line.startsWith("resourcePacks:")) {
                    continue;
                }
                String value = line.substring("resourcePacks:".length()).trim();
                if (value.isEmpty() || value.equals("[]")) {
                    return Set.of();
                }
                com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(value);
                if (!parsed.isJsonArray()) {
                    return null;
                }
                Set<String> ids = new HashSet<>();
                for (com.google.gson.JsonElement e : parsed.getAsJsonArray()) {
                    if (e != null && e.isJsonPrimitive()) {
                        ids.add(e.getAsString());
                    }
                }
                return ids;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private java.util.List<Path> resourcepackRoots() {
        return java.util.List.of(gameDir.resolve("resourcepacks"));
    }

    private static String stripArchiveExtension(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".zip") || lower.endsWith(".jar")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
}
