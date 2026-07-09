package com.cozooo.dlc_manager.ui.common;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;

import java.util.Map;

public final class DlcFxTooltipText {
    private DlcFxTooltipText() {
    }

    public static String resolveLocalized(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        String lang = DlcDcConfigIO.normalizeLangKey(DlcPlatformServices.languageCode());
        String value = lang == null ? null : values.get(lang);
        if (value == null || value.isBlank()) {
            value = values.get("en_us");
        }
        if (value == null || value.isBlank()) {
            value = values.values().stream().findFirst().orElse("");
        }
        return value == null ? "" : value;
    }

    public static void appendBlock(StringBuilder builder, String text) {
        if (builder == null || text == null || text.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(text);
    }

    public static void appendLine(StringBuilder builder, String text) {
        if (builder == null || text == null || text.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(text);
    }
}
