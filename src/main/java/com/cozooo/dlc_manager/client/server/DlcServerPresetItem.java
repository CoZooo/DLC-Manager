package com.cozooo.dlc_manager.client.server;

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DlcServerPresetItem {
    public String id;
    public String fileName;
    public Map<String, String> displayNames = Collections.emptyMap();
    public Map<String, String> tips = Collections.emptyMap();
    public List<String> enable = List.of();
    public List<String> conflicts = List.of();
    public boolean alarm;
    public DlcPresetStatus status = DlcPresetStatus.OFF;

    public static DlcServerPresetItem fromPreset(DlcPresetEntry entry) {
        DlcServerPresetItem item = new DlcServerPresetItem();
        if (entry == null) {
            return item;
        }
        item.id = entry.getId();
        item.fileName = entry.getFileName();
        item.displayNames = entry.getDisplayNames();
        item.tips = entry.getTips();
        item.enable = entry.getEnable();
        item.conflicts = entry.getConflicts();
        item.alarm = entry.isAlarm();
        item.status = entry.getStatus();
        return item;
    }

    public DlcPresetEntry toPresetEntry() {
        return new DlcPresetEntry(id, fileName, null, displayNames, tips, enable, conflicts, alarm, status);
    }
}
