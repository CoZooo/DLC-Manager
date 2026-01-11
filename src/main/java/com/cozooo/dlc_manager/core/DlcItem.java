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
    private boolean dirty;

    public DlcItem(String id, String name, DlcType type, Path sourcePath, boolean enabled, java.util.Map<String, String> tooltips) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.sourcePath = sourcePath;
        this.enabled = enabled;
        this.tooltips = tooltips == null ? java.util.Map.of() : tooltips;
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

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
