package com.cozooo.dlc_manager.platform;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DlcFxPlatformProvider implements PlatformServices.Provider {
    private static final String PREF_LANGUAGE = "language";
    private static final Preferences PREFS = Preferences.userNodeForPackage(DlcFxPlatformProvider.class);

    private volatile Path gameDir;
    private volatile Path configDir;
    private volatile String languageOverride = readStoredLanguage();

    public DlcFxPlatformProvider() {
        this.gameDir = Path.of("").toAbsolutePath().normalize();
        this.configDir = gameDir.resolve("config");
    }

    public DlcFxPlatformProvider(Path gameDir) {
        setGameDir(gameDir);
    }

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
        if (modId == null || modId.isBlank()) {
            return false;
        }
        if ("tacz".equalsIgnoreCase(modId)) {
            return hasTaczSupportJar();
        }
        String needle = modId.toLowerCase(java.util.Locale.ROOT);
        for (Path jar : modFiles()) {
            String fileName = jar.getFileName() == null ? "" : jar.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (fileName.contains(needle)) {
                return true;
            }
            if (jarContainsIgnoreCase(jar, "modid=\"" + needle + "\"")
                || jarContainsIgnoreCase(jar, "modid='" + needle + "'")
                || jarContainsIgnoreCase(jar, "modId = \"" + needle + "\"")
                || jarContainsIgnoreCase(jar, "\"id\":\"" + needle + "\"")
                || jarContainsIgnoreCase(jar, "\"id\": \"" + needle + "\"")) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTaczSupportJar() {
        for (Path jar : modFiles()) {
            String fileName = jar.getFileName() == null ? "" : jar.getFileName().toString();
            String lower = fileName.toLowerCase(Locale.ROOT);
            if ((fileName.startsWith("tacz-") || fileName.startsWith("TACZ-"))
                    && lower.endsWith(".jar")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isDedicatedServer() {
        return false;
    }

    @Override
    public String languageCode() {
        if (languageOverride != null && !languageOverride.isBlank()) {
            return languageOverride;
        }
        return systemLanguageCode();
    }

    public void setLanguageOverride(String languageOverride) {
        String normalized = normalizeLanguage(languageOverride);
        this.languageOverride = normalized;
        if (normalized == null) {
            PREFS.remove(PREF_LANGUAGE);
        } else {
            PREFS.put(PREF_LANGUAGE, normalized);
        }
        try {
            PREFS.flush();
        } catch (Throwable ignored) {
        }
    }

    public String languageOverride() {
        return languageOverride;
    }

    private static String systemLanguageCode() {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        if ("zh".equalsIgnoreCase(language)) {
            return "zh_cn";
        }
        return "en_us";
    }

    private static String readStoredLanguage() {
        try {
            return normalizeLanguage(PREFS.get(PREF_LANGUAGE, null));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "en_us", "zh_cn" -> normalized;
            default -> null;
        };
    }

    @Override
    public String loaderName() {
        for (Path jar : modFiles()) {
            if (jarHasEntry(jar, "fabric.mod.json")) {
                return "Fabric";
            }
            if (jarHasEntry(jar, "META-INF/neoforge.mods.toml")) {
                return "NeoForge";
            }
            if (jarHasEntry(jar, "META-INF/mods.toml")) {
                return "Forge";
            }
        }
        if (java.nio.file.Files.exists(configDir.resolve("fabric_loader_dependencies.json"))) {
            return "Fabric";
        }
        return "";
    }

    @Override
    public String mcVersion() {
        String fromVersionJson = readLatestVersionJson();
        if (fromVersionJson != null) {
            return fromVersionJson;
        }
        for (Path jar : modFiles()) {
            String fromJar = readMinecraftVersionFromJar(jar);
            if (fromJar != null && !fromJar.isBlank()) {
                return fromJar;
            }
        }
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

    private List<Path> modFiles() {
        Path mods = gameDir.resolve("mods");
        if (!java.nio.file.Files.isDirectory(mods)) {
            return List.of();
        }
        List<Path> jars = new ArrayList<>();
        try (var stream = java.nio.file.Files.list(mods)) {
            stream.filter(path -> {
                String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                return java.nio.file.Files.isRegularFile(path) && name.endsWith(".jar");
            }).forEach(jars::add);
        } catch (Exception ignored) {
        }
        return jars;
    }

    private static boolean jarHasEntry(Path jar, String entryName) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            return jf.getEntry(entryName) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean jarContainsIgnoreCase(Path jar, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        String lowerNeedle = needle.toLowerCase(java.util.Locale.ROOT);
        for (String entry : List.of("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")) {
            String content = readJarText(jar, entry);
            if (content != null && content.toLowerCase(java.util.Locale.ROOT).contains(lowerNeedle)) {
                return true;
            }
        }
        return false;
    }

    private String readLatestVersionJson() {
        Path versions = gameDir.resolve("versions");
        if (!java.nio.file.Files.isDirectory(versions)) {
            return null;
        }
        try (var stream = java.nio.file.Files.list(versions)) {
            return stream
                .filter(java.nio.file.Files::isDirectory)
                .map(path -> path.getFileName() == null ? "" : path.getFileName().toString())
                .map(DlcFxPlatformProvider::extractMcVersion)
                .filter(value -> value != null && !value.isBlank())
                .sorted(DlcFxPlatformProvider::compareVersionsDescending)
                .findFirst()
                .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readMinecraftVersionFromJar(Path jar) {
        String fabric = readJarText(jar, "fabric.mod.json");
        String version = extractJsonString(fabric, "minecraft");
        if (version != null) {
            return extractMcVersion(version);
        }
        for (String entry : List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
            String toml = readJarText(jar, entry);
            version = extractMcVersion(toml);
            if (version != null) {
                return version;
            }
        }
        return null;
    }

    private static String readJarText(Path jar, String entryName) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            var entry = jf.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream in = jf.getInputStream(entry)) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractJsonString(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractMcVersion(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(1\\.\\d+(?:\\.\\d+)?)").matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int compareVersionsDescending(String left, String right) {
        return -compareVersions(left, right);
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int ai = i < a.length ? parseInt(a[i]) : 0;
            int bi = i < b.length ? parseInt(b[i]) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return left.compareTo(right);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
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
