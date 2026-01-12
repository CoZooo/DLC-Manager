package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import net.minecraft.client.Minecraft;

import java.util.Map;

final class DlcDisplayNameUtil {
    private DlcDisplayNameUtil() {
    }

    static String resolveDisplayName(DlcItem item) {
        if (item == null) {
            return "";
        }
        return resolveDisplayName(item, item.getName());
    }

    static String resolveDisplayName(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
        if (item == null) {
            return identifier;
        }
        return resolveDisplayName(item, identifier);
    }

    private static String resolveDisplayName(DlcItem item, String fallback) {
        Map<String, String> names = item.getDisplayNames();
        if (names == null || names.isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        String lang = Minecraft.getInstance().options.languageCode;
        if (names.containsKey(lang)) {
            return names.get(lang);
        }
        if (names.containsKey("en_us")) {
            return names.get("en_us");
        }
        return names.values().stream().findFirst().orElse(fallback == null ? "" : fallback);
    }
}
