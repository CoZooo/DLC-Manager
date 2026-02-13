package com.cozooo.dlc_manager.client.preset;

import com.cozooo.dlc_manager.core.JsonCaseUtil;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class DlcPresetEntry {
    private final String id;
    private final String fileName;
    private final Path sourcePath;
    private final Map<String, String> displayName;
    private final Map<String, String> tip;
    private final List<String> enable;
    private final List<String> conflicts;
    private final boolean alarm;
    private DlcPresetStatus status;

    DlcPresetEntry(String id,
                   String fileName,
                   Path sourcePath,
                   Map<String, String> displayName,
                   Map<String, String> tip,
                   List<String> enable,
                   List<String> conflicts,
                   boolean alarm,
                   DlcPresetStatus status) {
        this.id = id;
        this.fileName = fileName;
        this.sourcePath = sourcePath;
        this.displayName = displayName == null ? Collections.emptyMap() : displayName;
        this.tip = tip == null ? Collections.emptyMap() : tip;
        this.enable = enable == null ? List.of() : List.copyOf(enable);
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        this.alarm = alarm;
        this.status = status == null ? DlcPresetStatus.OFF : status;
    }

    public String getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public List<String> getEnable() {
        return enable;
    }

    public List<String> getConflicts() {
        return conflicts;
    }

    public DlcPresetStatus getStatus() {
        return status;
    }

    public boolean isAlarm() {
        return alarm;
    }

    void setStatus(DlcPresetStatus status) {
        this.status = status == null ? DlcPresetStatus.OFF : status;
    }

    public String resolveDisplayName(String lang) {
        String key = JsonCaseUtil.normalizeLangKey(lang);
        if (key != null && displayName.containsKey(key)) {
            String value = displayName.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        if (displayName.containsKey("en_us")) {
            String value = displayName.get("en_us");
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fileName;
    }

    public String resolveTip(String lang) {
        String key = JsonCaseUtil.normalizeLangKey(lang);
        if (key != null && tip.containsKey(key)) {
            String value = tip.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        if (tip.containsKey("en_us")) {
            String value = tip.get("en_us");
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        for (String value : tip.values()) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
