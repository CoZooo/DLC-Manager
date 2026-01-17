package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.DlcType;
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
    private String dlcIdentifier;
    private String directUrl;
    private String modrinthProjectId;
    private String modrinthVersionId;
    private DlcType type;
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

    public String getDlcIdentifier() {
        return dlcIdentifier;
    }

    public void setDlcIdentifier(String dlcIdentifier) {
        this.dlcIdentifier = dlcIdentifier;
    }

    public String getDirectUrl() {
        return directUrl;
    }

    public void setDirectUrl(String directUrl) {
        this.directUrl = directUrl;
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

    public DlcType getType() {
        return type;
    }

    public void setType(DlcType type) {
        this.type = type;
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
        String lang = PlatformServices.get().languageCode();
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
        return baseDir.resolve(fileNameHint).normalize();
    }

    public Path resolveTargetPath(String fileName) {
        if (baseDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        return baseDir.resolve(fileName).normalize();
    }

    public String getUniqueKey() {
        if (modrinthVersionId != null && !modrinthVersionId.isBlank()) {
            return "modrinth:" + modrinthVersionId;
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
}
