package com.cozooo.dlc_manager.client.required;

import java.nio.file.Path;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.PlatformServices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DlcRequiredEntry {
    private final Path configPath;
    private final Path requiredDir;
    private final String baseName;
    private String identifier;
    private final Map<String, String> displayNames = new HashMap<>();
    private final Map<String, String> tips = new HashMap<>();
    private String requiredFileName;
    private String directUrl;
    private List<String> directUrls = new ArrayList<>();
    private String downloadPriority = "curseforge";
    private String modrinthProjectId;
    private String modrinthVersionId;
    private String curseforgeProjectId;
    private String curseforgeFileId;
    private boolean inList = true;
    private boolean hidden;
    private boolean userRemoved;
    private String appliedTargetRaw;
    private String resolvedFileName;
    private final List<DlcRequiredDependency> dependencies = new ArrayList<>();

    public DlcRequiredEntry(Path configPath, Path requiredDir) {
        this.configPath = configPath;
        this.requiredDir = requiredDir;
        String name = configPath == null ? "" : configPath.getFileName().toString();
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }
        this.baseName = name;
        this.identifier = name;
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

    public Map<String, String> getDisplayNames() {
        return Collections.unmodifiableMap(displayNames);
    }

    public void putDisplayName(String lang, String value) {
        if (lang == null || lang.isBlank() || value == null) {
            return;
        }
        displayNames.put(lang, value);
    }

    public Map<String, String> getTips() {
        return Collections.unmodifiableMap(tips);
    }

    public void putTip(String lang, String value) {
        if (lang == null || lang.isBlank() || value == null) {
            return;
        }
        tips.put(lang, value);
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
        if (downloadPriority == null || downloadPriority.isBlank()) {
            return;
        }
        this.downloadPriority = downloadPriority.trim();
    }

    public boolean isDirectUrlPriority() {
        return "directurl".equalsIgnoreCase(downloadPriority);
    }

    public boolean isCurseForgePriority() {
        return "curseforge".equalsIgnoreCase(downloadPriority);
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

    public boolean isUserRemoved() {
        return userRemoved;
    }

    public void setUserRemoved(boolean userRemoved) {
        this.userRemoved = userRemoved;
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
        return requiredDir.resolve(fileName);
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
            Path path = Path.of(value);
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
        if (displayNames.isEmpty()) {
            return fallback;
        }
        String lang = PlatformServices.get().languageCode();
        if (displayNames.containsKey(lang)) {
            return displayNames.get(lang);
        }
        if (displayNames.containsKey("en_us")) {
            return displayNames.get("en_us");
        }
        return displayNames.values().stream().findFirst().orElse(fallback);
    }
}
