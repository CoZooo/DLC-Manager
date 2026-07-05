package com.cozooo.dlc_manager.ui.remote;

import com.cozooo.dlc_manager.core.DlcType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DlcFxRemoteDownloadListModel {
    public static final String ROOT_GROUP = "__root__";

    private DlcFxRemoteDownloadListModel() {
    }

    public enum RowType {
        GROUP,
        ITEM
    }

    public static final class Row<T> {
        public final RowType type;
        public final String groupKey;
        public final String groupLabel;
        public final String folderRaw;
        public final T item;
        public boolean collapsed;
        public boolean expanding;
        public boolean collapsing;

        private Row(RowType type, String groupKey, String groupLabel, String folderRaw, T item, boolean collapsed) {
            this.type = type;
            this.groupKey = groupKey;
            this.groupLabel = groupLabel;
            this.folderRaw = folderRaw;
            this.item = item;
            this.collapsed = collapsed;
        }

        public static <T> Row<T> group(String groupKey, String label, String folderRaw, boolean collapsed) {
            return new Row<>(RowType.GROUP, groupKey, label, folderRaw, null, collapsed);
        }

        public static <T> Row<T> item(T item, String groupKey) {
            return new Row<>(RowType.ITEM, groupKey, null, null, item, false);
        }
    }

    public static <T> List<Row<T>> rows(List<T> items,
                                        DlcType typeFilter,
                                        Function<T, String> folderResolver,
                                        Function<T, DlcType> typeResolver,
                                        Predicate<T> hiddenPredicate,
                                        Predicate<String> collapsedPredicate,
                                        Function<String, String> labelResolver) {
        List<T> filtered = items == null ? List.of() : items.stream()
                .filter(item -> item != null && isVisible(item, hiddenPredicate))
                .filter(item -> typeFilter == null || typeFilter == resolveType(item, typeResolver))
                .toList();
        Map<String, List<T>> byFolder = groupByFolder(filtered, folderResolver);
        boolean hasSubDir = byFolder.keySet().stream().anyMatch(key -> !ROOT_GROUP.equals(key));
        List<Row<T>> rows = new ArrayList<>();
        for (Map.Entry<String, List<T>> entry : byFolder.entrySet()) {
            String rawFolder = normalizeFolder(entry.getKey());
            String groupKey = rawFolder;
            if (ROOT_GROUP.equals(rawFolder) && !hasSubDir) {
                for (T item : entry.getValue()) {
                    rows.add(Row.item(item, groupKey));
                }
                continue;
            }
            boolean collapsed = collapsedPredicate != null && collapsedPredicate.test(groupKey);
            rows.add(Row.group(groupKey, label(rawFolder, labelResolver), rawFolder, collapsed));
            if (collapsed) {
                continue;
            }
            for (T item : entry.getValue()) {
                rows.add(Row.item(item, groupKey));
            }
        }
        return rows;
    }

    public static String normalizeFolder(String raw) {
        return raw == null || raw.isBlank() ? ROOT_GROUP : raw;
    }

    private static <T> boolean isVisible(T item, Predicate<T> hiddenPredicate) {
        return hiddenPredicate == null || !hiddenPredicate.test(item);
    }

    private static <T> DlcType resolveType(T item, Function<T, DlcType> typeResolver) {
        return typeResolver == null ? null : typeResolver.apply(item);
    }

    private static <T> Map<String, List<T>> groupByFolder(List<T> items, Function<T, String> folderResolver) {
        Map<String, List<T>> grouped = new TreeMap<>();
        for (T item : items) {
            String folder = folderResolver == null ? ROOT_GROUP : folderResolver.apply(item);
            grouped.computeIfAbsent(normalizeFolder(folder), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private static String label(String rawFolder, Function<String, String> labelResolver) {
        if (labelResolver == null) {
            return rawFolder;
        }
        String label = labelResolver.apply(rawFolder);
        return label == null || label.isBlank() ? rawFolder : label;
    }
}
