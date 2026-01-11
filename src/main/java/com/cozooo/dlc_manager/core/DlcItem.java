package com.cozooo.dlc_manager.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DlcItem {
    private final String id;
    private final String name;
    private final DlcType type;
    private final Path sourcePath;
    private boolean enabled;
    private final List<Path> appliedTargets = new ArrayList<>();
    private final java.util.Map<String, String> tooltips;
    private final java.util.List<String> dependencies;
    private final java.util.List<String> conflicts;
    private final boolean allowEnableByAll;
    private boolean dirty;

    public DlcItem(String id, String name, DlcType type, Path sourcePath, boolean enabled, java.util.Map<String, String> tooltips,
        java.util.List<String> dependencies, java.util.List<String> conflicts, boolean allowEnableByAll) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.sourcePath = sourcePath;
        this.enabled = enabled;
        this.tooltips = tooltips == null ? java.util.Map.of() : tooltips;
        this.dependencies = dependencies == null ? java.util.List.of() : dependencies;
        this.conflicts = conflicts == null ? java.util.List.of() : conflicts;
        this.allowEnableByAll = allowEnableByAll;
    }

    public String getId() {
        return id;
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

    public java.util.List<String> getDependencies() {
        return dependencies;
    }

    public java.util.List<String> getConflicts() {
        return conflicts;
    }

    public boolean AllowEnableByAll() {
        return allowEnableByAll;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
