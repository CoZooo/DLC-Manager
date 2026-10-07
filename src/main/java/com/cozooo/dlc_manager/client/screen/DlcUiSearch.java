package com.cozooo.dlc_manager.client.screen;

import java.util.Locale;

public final class DlcUiSearch {
    private DlcUiSearch() {}

    public static boolean matches(String query, String... fields) {
        if (query == null || query.isBlank()) return true;
        StringBuilder text = new StringBuilder();
        for (String field : fields) if (field != null) text.append(field).append(' ');
        String haystack = text.toString().toLowerCase(Locale.ROOT);
        for (String word : query.strip().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!haystack.contains(word)) return false;
        }
        return true;
    }
}
