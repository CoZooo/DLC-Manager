package com.cozooo.dlc_manager.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class DlcFxServerFileSearchModel {
    private DlcFxServerFileSearchModel() {
    }

    public static <T> List<T> filter(List<T> items, String query, Function<T, String> nameResolver) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return List.copyOf(items);
        }
        Function<T, String> resolver = nameResolver == null ? item -> String.valueOf(item) : nameResolver;
        List<T> out = new ArrayList<>();
        for (T item : items) {
            if (item == null) {
                continue;
            }
            String name = resolver.apply(item);
            if (name != null && name.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                out.add(item);
            }
        }
        return out;
    }
}
