package com.cozooo.dlc_manager.client.config;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import net.minecraft.network.chat.Component;

public abstract class DlcConfigValue {
    private final String name;
    private final String commentKey;
    private final String translationKey;

    protected DlcConfigValue(String name, String commentKey, String translationKey) {
        this.name = name;
        this.commentKey = commentKey;
        this.translationKey = translationKey;
    }

    public final String getName() { return name; }
    public final String getConfigGuiDisplayName() { return DlcTextCompat.translatable(translationKey).getString(); }
    public final String getComment() {
        return commentKey == null || commentKey.isBlank() ? "" : DlcTextCompat.translatable(commentKey).getString();
    }

    public abstract String getStringValue();
    public abstract String getDefaultStringValue();
    public abstract void setValueFromString(String value);

    public final void resetToDefault() { setValueFromString(getDefaultStringValue()); }

    public static final class StringValue extends DlcConfigValue {
        private final String defaultValue;
        private String value;

        public StringValue(String name, String defaultValue, String commentKey, String translationKey) {
            super(name, commentKey, translationKey);
            this.defaultValue = defaultValue == null ? "" : defaultValue;
            value = this.defaultValue;
        }

        @Override public String getStringValue() { return value; }
        @Override public String getDefaultStringValue() { return defaultValue; }
        public void setStringValue(String value) { this.value = value == null ? "" : value; }
        @Override public void setValueFromString(String value) { setStringValue(value); }
    }

    public static final class BooleanValue extends DlcConfigValue {
        private final boolean defaultValue;
        private boolean value;

        public BooleanValue(String name, boolean defaultValue, String commentKey, String translationKey) {
            super(name, commentKey, translationKey);
            this.defaultValue = defaultValue;
            value = defaultValue;
        }

        public boolean getBooleanValue() { return value; }
        public void setBooleanValue(boolean value) { this.value = value; }
        @Override public String getStringValue() { return Boolean.toString(value); }
        @Override public String getDefaultStringValue() { return Boolean.toString(defaultValue); }
        @Override public void setValueFromString(String value) {
            if (value == null) return;
            String normalized = value.trim();
            if (normalized.equalsIgnoreCase("true")) this.value = true;
            else if (normalized.equalsIgnoreCase("false")) this.value = false;
        }
    }

    public static final class IntegerValue extends DlcConfigValue {
        private final int defaultValue;
        private final int min;
        private final int max;
        private int value;

        public IntegerValue(String name, int defaultValue, int min, int max, String commentKey, String translationKey) {
            super(name, commentKey, translationKey);
            if (min > max) throw new IllegalArgumentException("Invalid integer range");
            this.min = min;
            this.max = max;
            this.defaultValue = Math.max(min, Math.min(max, defaultValue));
            value = this.defaultValue;
        }

        public int getIntegerValue() { return value; }
        public int getMinIntegerValue() { return min; }
        public int getMaxIntegerValue() { return max; }
        public void setIntegerValue(int value) { this.value = Math.max(min, Math.min(max, value)); }
        @Override public String getStringValue() { return Integer.toString(value); }
        @Override public String getDefaultStringValue() { return Integer.toString(defaultValue); }
        @Override public void setValueFromString(String value) {
            if (value == null) return;
            try { setIntegerValue(Integer.parseInt(value.trim())); }
            catch (NumberFormatException ignored) { }
        }
    }
}
