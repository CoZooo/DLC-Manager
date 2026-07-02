package com.cozooo.dlc_manager.ui.preset;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DlcFxPresetFlow<T> {
    private final Function<T, String> idProvider;
    private final Predicate<T> enabledProvider;
    private final Map<String, Boolean> pendingEnabled = new LinkedHashMap<>();

    public DlcFxPresetFlow(Function<T, String> idProvider, Predicate<T> enabledProvider) {
        this.idProvider = idProvider;
        this.enabledProvider = enabledProvider;
    }

    public boolean hasPendingChanges() {
        return !pendingEnabled.isEmpty();
    }

    public Map<String, Boolean> pendingChanges() {
        return new LinkedHashMap<>(pendingEnabled);
    }

    public void clear() {
        pendingEnabled.clear();
    }

    public boolean effectiveEnabled(T entry) {
        String id = id(entry);
        Boolean pending = pendingEnabled.get(id);
        return pending != null ? pending : originalEnabled(entry);
    }

    public boolean isPending(T entry) {
        String id = id(entry);
        Boolean pending = pendingEnabled.get(id);
        return pending != null && pending != originalEnabled(entry);
    }

    public void setPending(T entry, boolean enabled) {
        String id = id(entry);
        if (id.isBlank()) {
            return;
        }
        if (enabled == originalEnabled(entry)) {
            pendingEnabled.remove(id);
        } else {
            pendingEnabled.put(id, enabled);
        }
    }

    public void setAll(List<T> entries, boolean enabled) {
        for (T entry : entries == null ? List.<T>of() : entries) {
            setPending(entry, enabled);
        }
    }

    private String id(T entry) {
        String id = entry == null ? null : idProvider.apply(entry);
        return id == null ? "" : id;
    }

    private boolean originalEnabled(T entry) {
        return entry != null && enabledProvider.test(entry);
    }
}
