package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DlcFxServerPresetStore {

    private static final List<DlcServerEntry> ENTRIES = new ArrayList<>();
    private static String lastSelectedKey;

    private DlcFxServerPresetStore() {
    }

    static synchronized List<DlcServerEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES));
    }

    static synchronized void setEntries(List<DlcServerEntry> newEntries) {
        ENTRIES.clear();
        if (newEntries != null) {
            ENTRIES.addAll(newEntries);
        }
    }

    static synchronized void removeEntry(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        ENTRIES.removeIf(entry -> entry != null && key.equals(entry.getKey()));
    }

    static synchronized String getLastSelectedKey() {
        return lastSelectedKey;
    }

    static synchronized void setLastSelectedKey(String key) {
        lastSelectedKey = key;
    }
}
