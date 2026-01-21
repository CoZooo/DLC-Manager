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
    private final String appliedTargetRaw;
    private boolean enabled;
    private final List<Path> appliedTargets = new ArrayList<>();
    private final java.util.Map<String, String> tooltips;
    private final java.util.Map<String, String> displayNames;
    private final java.util.List<String> dependencies;
    private final java.util.List<String> conflicts;
    private final boolean allowEnableByAll;
    private final boolean hidden;
    private boolean dirty;

    public DlcItem(String id, String identifier, String name, DlcType type, Path sourcePath, String appliedTargetRaw, boolean enabled, java.util.Map<String, String> tooltips,
        java.util.Map<String, String> displayNames, java.util.List<String> dependencies, java.util.List<String> conflicts,
        boolean allowEnableByAll, boolean hidden) {
        this.id = id;
        this.identifier = identifier;
        this.name = name;
        this.type = type;
        this.sourcePath = sourcePath;
        this.appliedTargetRaw = appliedTargetRaw;
        this.enabled = enabled;
        this.tooltips = tooltips == null ? java.util.Map.of() : tooltips;
        this.displayNames = displayNames == null ? java.util.Map.of() : displayNames;
        this.dependencies = dependencies == null ? java.util.List.of() : dependencies;
        this.conflicts = conflicts == null ? java.util.List.of() : conflicts;
        this.allowEnableByAll = allowEnableByAll;
        this.hidden = hidden;
    }

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

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
