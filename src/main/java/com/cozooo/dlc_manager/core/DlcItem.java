package com.cozooo.dlc_manager.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DlcItem {
    private final String id;
    private final String identifier;
    private final String name;
    private final DlcType type;
    private final Path sourcePath;
    private final List<Path> sourcePaths;
    private final String appliedTargetRaw;
    private boolean enabled;
    private final List<Path> appliedTargets = new ArrayList<>();
    private final java.util.Map<String, String> tooltips;
    private final java.util.Map<String, String> displayNames;
    private final java.util.List<String> dependencies;
    private final java.util.List<String> conflicts;
    private final boolean allowEnableByAll;
    private final boolean hidden;
    private final boolean configType;
    private final boolean alarm;
    private String redirectUrl;
    private String matchMode = "hash";
    private Path dlcRoot;
    private boolean dirty;

    public DlcItem(String id, String identifier, String name, DlcType type, Path sourcePath, String appliedTargetRaw, boolean enabled, java.util.Map<String, String> tooltips,
        java.util.Map<String, String> displayNames, java.util.List<String> dependencies, java.util.List<String> conflicts,
        boolean allowEnableByAll, boolean hidden) {
        this(id, identifier, name, type, sourcePath, appliedTargetRaw, enabled, tooltips, displayNames, dependencies, conflicts, allowEnableByAll, hidden, false, false);
    }

    public DlcItem(String id, String identifier, String name, DlcType type, Path sourcePath, String appliedTargetRaw, boolean enabled, java.util.Map<String, String> tooltips,
        java.util.Map<String, String> displayNames, java.util.List<String> dependencies, java.util.List<String> conflicts,
        boolean allowEnableByAll, boolean hidden, boolean configType) {
        this(id, identifier, name, type, sourcePath, appliedTargetRaw, enabled, tooltips, displayNames, dependencies, conflicts, allowEnableByAll, hidden, configType, false);
    }

    public DlcItem(String id, String identifier, String name, DlcType type, Path sourcePath, String appliedTargetRaw, boolean enabled, java.util.Map<String, String> tooltips,
        java.util.Map<String, String> displayNames, java.util.List<String> dependencies, java.util.List<String> conflicts,
        boolean allowEnableByAll, boolean hidden, boolean configType, boolean alarm) {
        this.id = id;
        this.identifier = identifier;
        this.name = name;
        this.type = type;
        this.sourcePath = sourcePath;
        this.sourcePaths = sourcePath == null ? List.of() : List.of(sourcePath);
        this.appliedTargetRaw = appliedTargetRaw;
        this.enabled = enabled;
        this.tooltips = tooltips == null ? java.util.Map.of() : tooltips;
        this.displayNames = displayNames == null ? java.util.Map.of() : displayNames;
        this.dependencies = dependencies == null ? java.util.List.of() : dependencies;
        this.conflicts = conflicts == null ? java.util.List.of() : conflicts;
        this.allowEnableByAll = allowEnableByAll;
        this.hidden = hidden;
        this.configType = configType;
        this.alarm = alarm;
    }

    public DlcItem(String id, String identifier, String name, DlcType type, Path sourcePath, List<Path> sourcePaths, String appliedTargetRaw,
        boolean enabled, java.util.Map<String, String> tooltips, java.util.Map<String, String> displayNames,
        java.util.List<String> dependencies, java.util.List<String> conflicts, boolean allowEnableByAll, boolean hidden) {
        this(id, identifier, name, type, sourcePath, sourcePaths, appliedTargetRaw, enabled, tooltips, displayNames, dependencies, conflicts, allowEnableByAll, hidden, false, false);
    }

    public DlcItem(String id, String identifier, String name, DlcType type, Path sourcePath, List<Path> sourcePaths, String appliedTargetRaw,
        boolean enabled, java.util.Map<String, String> tooltips, java.util.Map<String, String> displayNames,
        java.util.List<String> dependencies, java.util.List<String> conflicts, boolean allowEnableByAll, boolean hidden, boolean configType) {
        this(id, identifier, name, type, sourcePath, sourcePaths, appliedTargetRaw, enabled, tooltips, displayNames, dependencies, conflicts, allowEnableByAll, hidden, configType, false);
    }

    public DlcItem(String id, String identifier, String name, DlcType type, Path sourcePath, List<Path> sourcePaths, String appliedTargetRaw,
        boolean enabled, java.util.Map<String, String> tooltips, java.util.Map<String, String> displayNames,
        java.util.List<String> dependencies, java.util.List<String> conflicts, boolean allowEnableByAll, boolean hidden, boolean configType, boolean alarm) {
        this.id = id;
        this.identifier = identifier;
        this.name = name;
        this.type = type;
        this.sourcePath = sourcePath;
        if (sourcePaths == null || sourcePaths.isEmpty()) {
            this.sourcePaths = sourcePath == null ? List.of() : List.of(sourcePath);
        } else {
            this.sourcePaths = List.copyOf(sourcePaths);
        }
        this.appliedTargetRaw = appliedTargetRaw;
        this.enabled = enabled;
        this.tooltips = tooltips == null ? java.util.Map.of() : tooltips;
        this.displayNames = displayNames == null ? java.util.Map.of() : displayNames;
        this.dependencies = dependencies == null ? java.util.List.of() : dependencies;
        this.conflicts = conflicts == null ? java.util.List.of() : conflicts;
        this.allowEnableByAll = allowEnableByAll;
        this.hidden = hidden;
        this.configType = configType;
        this.alarm = alarm;
    }

    public String getMatchMode() { return matchMode; }
    public void setMatchMode(String value) { matchMode = DlcFileMatch.mode(value); }
    public Path getDlcRoot() { return dlcRoot == null ? DlcPaths.dlcRoot() : dlcRoot; }
    public void setDlcRoot(Path value) { dlcRoot = value; }

    public String getId() {
        return id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    public DlcType getType() {
        return type;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public List<Path> getSourcePaths() {
        return sourcePaths;
    }

    public String getAppliedTargetRaw() {
        return appliedTargetRaw;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Path> getAppliedTargets() {
        return appliedTargets;
    }

    public java.util.Map<String, String> getTooltips() {
        return tooltips;
    }

    public java.util.Map<String, String> getDisplayNames() {
        return displayNames;
    }

    public java.util.List<String> getDependencies() {
        return dependencies;
    }

    public java.util.List<String> getConflicts() {
        return conflicts;
    }

    public boolean AllowEnableByAll() {
        return allowEnableByAll;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isConfigType() {
        return configType;
    }

    public boolean isAlarm() {
        return alarm;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
