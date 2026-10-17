package com.cozooo.dlc_manager.client.required;
import java.nio.file.Path;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.core.DlcPaths;
import java.util.ArrayList;
import java.util.List;

public class DlcRequiredEntry {
    private final Path configPath;
    private final Path requiredDir;
    private final String baseName;
    private String identifier;
    private String matchMode = "hash";
    private String requiredFileName;
    private String directUrl;
    private List<String> directUrls = new ArrayList<>();
    private String downloadPriority = "curseforge";
    private String modrinthProjectId;
    private String modrinthVersionId;
    private String curseforgeProjectId;
    private String curseforgeFileId;
    private String appliedTargetRaw;
    private boolean unzip;
    private String resolvedFileName;
    private final List<DlcRequiredDependency> dependencies = new ArrayList<>();

    public DlcRequiredEntry(Path configPath, Path requiredDir) {
        this.configPath = configPath;
        this.requiredDir = requiredDir;
        String name = configPath == null ? "" : configPath.getFileName().toString();
        name = DlcDcFormatUtil.stripConfigExtension(name);
        this.baseName = name;
        this.identifier = name;
    }

    public boolean isUnzip() { return unzip; }
    public void setUnzip(boolean value) { unzip = value; }
    public boolean shouldUnzip(String fileName) { return unzip && fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".zip"); }

    public List<Path> resolveInstallationTargets(String fileName) {
        List<Path> result = new ArrayList<>();
        if (fileName == null || fileName.isBlank() || appliedTargetRaw == null) return result;
        for (String part : appliedTargetRaw.split(",")) {
            String value = part.trim().replace('\\', '/');
            if (value.isEmpty()) continue;
            Path path = DlcPaths.parsePathLenient(value);
            if (path == null) continue;
            if (!path.isAbsolute()) path = DlcPaths.gameDir().resolve(path);
            path = path.normalize();
            String name = path.getFileName() == null ? "" : path.getFileName().toString();
            boolean directory = value.endsWith("/") || value.equals(".") || value.equals("..")
                || value.endsWith("/.") || value.endsWith("/..") || !java.nio.file.Files.isRegularFile(path) && !name.contains(".");
            result.add(shouldUnzip(fileName) || !directory ? path : path.resolve(fileName));
        }
        return result;
    }

    public String getMatchMode() { return matchMode; }
    public void setMatchMode(String value) { matchMode = com.cozooo.dlc_manager.core.DlcFileMatch.mode(value); }
    public Path getDlcRoot() { return requiredDir == null ? null : requiredDir.getParent(); }
    public Path getDownloadDir() { return getDlcRoot() == null ? null : getDlcRoot().resolve("downloaded/required"); }
    public String getHistoryKey() { return com.cozooo.dlc_manager.core.DlcResourceHistory.key(configPath == null ? "" : com.cozooo.dlc_manager.core.DlcResourceHistory.relativePath(configPath), identifier); }

    public Path resolveDownloadPath(String fileName) {
        if (getDownloadDir() == null || fileName == null || fileName.isBlank()) return null;
        Path path = getDownloadDir().resolve(fileName).normalize();
        return path.startsWith(getDownloadDir().normalize()) ? path : null;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public Path getRequiredDir() {
        return requiredDir;
    }

    public String getBaseName() {
        return baseName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        if (identifier != null && !identifier.isBlank()) {
            this.identifier = identifier;
        }
    }

    public String getDirectUrl() {
        if (directUrls != null && !directUrls.isEmpty()) {
            return directUrls.get(0);
        }
        return directUrl;
    }

    public String getRequiredFileName() {
        return requiredFileName;
    }

    public void setRequiredFileName(String requiredFileName) {
        if (requiredFileName != null && !requiredFileName.isBlank()) {
            this.requiredFileName = requiredFileName;
            if (this.resolvedFileName == null || this.resolvedFileName.isBlank()) {
                this.resolvedFileName = requiredFileName;
            }
        }
    }

    public void setDirectUrl(String directUrl) {
        this.directUrl = directUrl;
        if (directUrl != null && !directUrl.isBlank()) {
            this.directUrls = new ArrayList<>();
            this.directUrls.add(directUrl);
        }
    }

    public List<String> getDirectUrls() {
        if (directUrls != null && !directUrls.isEmpty()) {
            return new ArrayList<>(directUrls);
        }
        if (directUrl != null && !directUrl.isBlank()) {
            return List.of(directUrl);
        }
        return List.of();
    }

    public void setDirectUrls(List<String> directUrls) {
        this.directUrls = new ArrayList<>();
        if (directUrls == null) {
            return;
        }
        for (String url : directUrls) {
            if (url != null && !url.isBlank()) {
                this.directUrls.add(url.trim());
            }
        }
        if (!this.directUrls.isEmpty()) {
            this.directUrl = this.directUrls.get(0);
        }
    }

    public String getModrinthProjectId() {
        return modrinthProjectId;
    }

    public void setModrinthProjectId(String modrinthProjectId) {
        this.modrinthProjectId = modrinthProjectId;
    }

    public String getModrinthVersionId() {
        return modrinthVersionId;
    }

    public void setModrinthVersionId(String modrinthVersionId) {
        this.modrinthVersionId = modrinthVersionId;
    }

    public String getCurseforgeProjectId() {
        return curseforgeProjectId;
    }

    public void setCurseforgeProjectId(String curseforgeProjectId) {
        this.curseforgeProjectId = curseforgeProjectId;
    }

    public String getCurseforgeFileId() {
        return curseforgeFileId;
    }

    public void setCurseforgeFileId(String curseforgeFileId) {
        this.curseforgeFileId = curseforgeFileId;
    }

    public String getDownloadPriority() {
        return downloadPriority;
    }

    public void setDownloadPriority(String downloadPriority) {
        this.downloadPriority = normalizePriority(downloadPriority);
    }

    public boolean isDirectUrlPriority() {
        return getDownloadPriorityList().stream().findFirst().map("directurl"::equals).orElse(false);
    }

    public boolean isCurseForgePriority() {
        return getDownloadPriorityList().stream().findFirst().map("curseforge"::equals).orElse(false);
    }

    public List<String> getDownloadPriorityList() {
        return parsePriority(downloadPriority);
    }

    private static String normalizePriority(String raw) {
        List<String> order = parsePriority(raw);
        return String.join(",", order);
    }

    private static List<String> parsePriority(String raw) {
        List<String> order = new ArrayList<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String token = normalizePriorityToken(part);
                if (token == null || token.isBlank()) {
                    continue;
                }
                if (!order.contains(token)) {
                    order.add(token);
                }
            }
        }
        if (order.isEmpty()) {
            order.add("curseforge");
            order.add("modrinth");
            order.add("directurl");
            return order;
        }
        for (String fallback : List.of("curseforge", "modrinth", "directurl")) {
            if (!order.contains(fallback)) {
                order.add(fallback);
            }
        }
        return order;
    }

    private static String normalizePriorityToken(String raw) {
        if (raw == null) {
            return null;
        }
        String token = raw.trim().toLowerCase();
        if (token.isBlank()) {
            return null;
        }
        token = token.replace("_", "").replace("-", "");
        return switch (token) {
            case "curseforge" -> "curseforge";
            case "modrinth" -> "modrinth";
            case "directurl", "direct" -> "directurl";
            default -> null;
        };
    }

    public List<DlcRequiredDependency> getDependencies() {
        return dependencies;
    }

    public void addDependency(DlcRequiredDependency dependency) {
        if (dependency != null) {
            dependencies.add(dependency);
        }
    }

    public void setAppliedTargetRaw(String appliedTargetRaw) {
        this.appliedTargetRaw = appliedTargetRaw;
    }

    public String getAppliedTargetRaw() {
        return appliedTargetRaw;
    }

    public void setResolvedFileName(String resolvedFileName) {
        this.resolvedFileName = resolvedFileName;
    }

    public String getResolvedFileName() {
        return resolvedFileName;
    }

    public Path resolveRequiredPath(String fileName) {
        if (requiredDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        Path downloaded = resolveDownloadPath(fileName);
        if (downloaded != null && java.nio.file.Files.exists(downloaded)) return downloaded;
        Path local = requiredDir.resolve(fileName).normalize();
        return local.startsWith(requiredDir.normalize()) ? local : null;
    }

    public List<Path> resolveAppliedTargets() {
        List<Path> targets = new ArrayList<>();
        if (appliedTargetRaw == null || appliedTargetRaw.isBlank()) {
            return targets;
        }
        String[] parts = appliedTargetRaw.split(",");
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (value.isEmpty()) {
                continue;
            }
            Path path = DlcPaths.parsePathLenient(value);
            if (path == null) {
                continue;
            }
            if (!path.isAbsolute()) {
                path = DlcPaths.gameDir().resolve(path);
            }
            targets.add(path.normalize());
        }
        return targets;
    }

    public String resolveDisplayName() {
        String fallback = baseName;
        if (fallback == null || fallback.isBlank()) {
            fallback = identifier;
        }
        if (fallback == null || fallback.isBlank()) {
            fallback = "required";
        }
        return fallback;
    }
}
