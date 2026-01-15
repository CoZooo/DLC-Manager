package com.cozooo.dlc_manager.client.required;

import java.util.HashMap;
import java.util.Map;

public final class DlcRequiredRuntimeState {
    private static final Map<String, Boolean> BASELINE = new HashMap<>();
    private static final Map<String, Boolean> OVERRIDE = new HashMap<>();

    private DlcRequiredRuntimeState() {
    }

    public static boolean getBaseline(String identifier, boolean current) {
        if (identifier == null || identifier.isBlank()) {
            return current;
        }
        BASELINE.putIfAbsent(identifier, current);
        return BASELINE.getOrDefault(identifier, current);
    }

    public static Boolean getOverride(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        return OVERRIDE.get(identifier);
    }

    public static void setOverride(String identifier, Boolean value) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        if (value == null) {
            OVERRIDE.remove(identifier);
        } else {
            OVERRIDE.put(identifier, value);
        }
    }

    public static boolean isPending(String identifier, boolean baseline) {
        Boolean override = getOverride(identifier);
        return override != null && override != baseline;
    }

    public static boolean getDesired(String identifier, boolean current) {
        Boolean override = getOverride(identifier);
        return override != null ? override : current;
    }

    public static boolean hasPendingChanges() {
        for (var e : OVERRIDE.entrySet()) {
            String id = e.getKey();
            Boolean override = e.getValue();
            Boolean base = BASELINE.get(id);
            if (override != null && (base == null || !override.equals(base))) {
                return true;
            }
        }
        return false;
    }
}
