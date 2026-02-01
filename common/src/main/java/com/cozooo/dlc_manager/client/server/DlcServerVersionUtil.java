package com.cozooo.dlc_manager.client.server;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DlcServerVersionUtil {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+(?:[A-Za-z0-9._+-]*)?)\\+(\\d+\\.\\d+\\.\\d+(?:[A-Za-z0-9._+-]*)?)");

    private DlcServerVersionUtil() {
    }

    public static VersionPair parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String base = matcher.group(1);
        String dlc = matcher.group(2);
        if (base == null || base.isBlank() || dlc == null || dlc.isBlank()) {
            return null;
        }
        return new VersionPair(base, dlc);
    }

    public static final class VersionPair {
        public final String base;
        public final String dlc;

        public VersionPair(String base, String dlc) {
            this.base = base;
            this.dlc = dlc;
        }
    }
}
