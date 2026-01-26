package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.cozooo.dlc_manager.platform.PlatformServices;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DlcRemoteEntry {
    private Path configPath;
    private final Path dlcRoot;
    private final Path baseDir;
    private String fileNameHint;
    private boolean fileNameExplicit;
    private String dlcIdentifier;
    private String directUrl;
    private List<String> directUrls = List.of();
    private String downloadPriority = "curseforge";
    private String modrinthProjectId;
    private String modrinthVersionId;
    private String curseforgeProjectId;
    private String curseforgeFileId;
    private DlcType type;
    private String appliedTargetRaw;
    private boolean inList;
    private boolean hidden;
    private boolean allowEnableByAll;
    private List<String> dependencyNames = List.of();
    private List<String> conflictNames = List.of();
    private Map<String, String> displayNames = Map.of();
    private Map<String, String> tips = Map.of();
    private List<DlcRemoteEntry> remoteDependencies = List.of();

    public DlcRemoteEntry(Path configPath, Path dlcRoot, Path baseDir, String fileNameHint) {
        this.configPath = configPath;
        this.dlcRoot = dlcRoot;
        this.baseDir = baseDir;
        this.fileNameHint = fileNameHint;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public Path getDlcRoot() {
        return dlcRoot;
    }

    public void setConfigPath(Path configPath) {
        this.configPath = configPath;
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public String getFileNameHint() {
        return fileNameHint;
    }

    public void setFileNameHint(String fileNameHint) {
        this.fileNameHint = fileNameHint;
    }

    public boolean isFileNameExplicit() {
        return fileNameExplicit;
    }

    public void setFileNameExplicit(boolean fileNameExplicit) {
        this.fileNameExplicit = fileNameExplicit;
    }

    public String getDlcIdentifier() {
        return dlcIdentifier;
    }

    public void setDlcIdentifier(String dlcIdentifier) {
        this.dlcIdentifier = dlcIdentifier;
    }

    public String getDirectUrl() {
        if (directUrls != null && !directUrls.isEmpty()) {
            return directUrls.get(0);
        }
        return directUrl;
    }

    public void setDirectUrl(String directUrl) {
        this.directUrl = directUrl;
        if (directUrl != null && !directUrl.isBlank()) {
            this.directUrls = List.of(directUrl);
        }
    }

    public List<String> getDirectUrls() {
        if (directUrls != null && !directUrls.isEmpty()) {
            return directUrls;
        }
        if (directUrl != null && !directUrl.isBlank()) {
            return List.of(directUrl);
        }
        return List.of();
    }

    public void setDirectUrls(List<String> directUrls) {
        if (directUrls == null) {
            this.directUrls = List.of();
            return;
        }
        List<String> cleaned = new ArrayList<>();
        for (String url : directUrls) {
            if (url != null && !url.isBlank()) {
                cleaned.add(url.trim());
            }
        }
        this.directUrls = cleaned;
        if (!cleaned.isEmpty()) {
            this.directUrl = cleaned.get(0);
        }
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

    public DlcType getType() {
        return type;
    }

    public void setType(DlcType type) {
        this.type = type;
    }

    public String getAppliedTargetRaw() {
        return appliedTargetRaw;
    }

    public void setAppliedTargetRaw(String appliedTargetRaw) {
        this.appliedTargetRaw = appliedTargetRaw;
    }

    public boolean isInList() {
        return inList;
    }

    public void setInList(boolean inList) {
        this.inList = inList;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isAllowEnableByAll() {
        return allowEnableByAll;
    }

    public void setAllowEnableByAll(boolean allowEnableByAll) {
        this.allowEnableByAll = allowEnableByAll;
    }

    public List<String> getDependencyNames() {
        return dependencyNames;
    }

    public void setDependencyNames(List<String> dependencyNames) {
        this.dependencyNames = dependencyNames == null ? List.of() : dependencyNames;
    }

    public List<String> getConflictNames() {
        return conflictNames;
    }

    public void setConflictNames(List<String> conflictNames) {
        this.conflictNames = conflictNames == null ? List.of() : conflictNames;
    }

    public Map<String, String> getDisplayNames() {
        return displayNames;
    }

    public void setDisplayNames(Map<String, String> displayNames) {
        this.displayNames = displayNames == null ? Map.of() : displayNames;
    }

    public Map<String, String> getTips() {
        return tips;
    }

    public void setTips(Map<String, String> tips) {
        this.tips = tips == null ? Map.of() : tips;
    }

    public List<DlcRemoteEntry> getRemoteDependencies() {
        return remoteDependencies;
    }

    public void setRemoteDependencies(List<DlcRemoteEntry> remoteDependencies) {
        this.remoteDependencies = remoteDependencies == null ? List.of() : remoteDependencies;
    }

    public String resolveDisplayName() {
        if (displayNames == null || displayNames.isEmpty()) {
            return fileNameHint != null ? fileNameHint : "";
        }
        String lang = JsonCaseUtil.normalizeLangKey(PlatformServices.get().languageCode());
        if (displayNames.containsKey(lang)) {
            return displayNames.get(lang);
        }
        if (displayNames.containsKey("en_us")) {
            return displayNames.get("en_us");
        }
        return displayNames.values().stream().findFirst().orElse(fileNameHint != null ? fileNameHint : "");
    }

    public Path getTargetPath() {
        if (fileNameHint == null || fileNameHint.isBlank() || baseDir == null) {
            return null;
        }
        String safeName = sanitizeFileName(fileNameHint);
        if (safeName == null || safeName.isBlank()) {
            return null;
        }
        return baseDir.resolve(safeName).normalize();
    }

    public Path resolveTargetPath(String fileName) {
        if (baseDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        String safeName = sanitizeFileName(fileName);
        if (safeName == null || safeName.isBlank()) {
            return null;
        }
        return baseDir.resolve(safeName).normalize();
    }

    public String getUniqueKey() {
        if (modrinthVersionId != null && !modrinthVersionId.isBlank()) {
            return "modrinth:" + modrinthVersionId;
        }
        if (curseforgeFileId != null && !curseforgeFileId.isBlank()) {
            String prefix = (curseforgeProjectId == null || curseforgeProjectId.isBlank())
                ? "curseforge"
                : "curseforge:" + curseforgeProjectId;
            return prefix + ":" + curseforgeFileId;
        }
        if (directUrl != null && !directUrl.isBlank()) {
            return "url:" + directUrl;
        }
        if (fileNameHint != null && !fileNameHint.isBlank()) {
            return "file:" + fileNameHint;
        }
        return "path:" + (configPath == null ? "unknown" : configPath.toString());
    }

    public List<String> getResolvedDependencyNames() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        if (dependencyNames != null) {
            for (String name : dependencyNames) {
                if (name != null && !name.isBlank()) {
                    names.add(name.trim());
                }
            }
        }
        if (remoteDependencies != null) {
            for (DlcRemoteEntry dep : remoteDependencies) {
                if (dep == null) {
                    continue;
                }
                String identifier = dep.getDlcIdentifier();
                if (identifier != null && !identifier.isBlank()) {
                    names.add(identifier.trim());
                }
            }
        }
        return new ArrayList<>(names);
    }

    private static String sanitizeFileName(String name) {
        String cleaned = DlcDownloadUtil.stripQuery(name);
        if (cleaned == null) {
            return null;
        }
        return cleaned.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
