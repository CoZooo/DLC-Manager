package com.cozooo.dlc_manager.client.preset;

import java.util.Locale;

public enum DlcPresetStatus {
    ON,
    PART,
    OFF;

    public boolean isEnabled() {
        return this == ON || this == PART;
    }

    public String toConfigValue() {
        return switch (this) {
            case ON -> "On";
            case PART -> "Part";
            case OFF -> "Off";
        };
    }

    public static DlcPresetStatus fromString(String raw) {
        if (raw == null) {
            return OFF;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "on" -> ON;
            case "part" -> PART;
            case "off" -> OFF;
            default -> OFF;
        };
    }
}
