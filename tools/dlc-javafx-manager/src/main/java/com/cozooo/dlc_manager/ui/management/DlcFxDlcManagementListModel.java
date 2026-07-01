package com.cozooo.dlc_manager.ui.management;

import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class DlcFxDlcManagementListModel {
    public static final String ROOT_GROUP = "__root__";
    public static final String REDIRECT_FOLDER_KEY = "redirect:__links__";

    private DlcFxDlcManagementListModel() {
    }

    public enum RowType {
        GROUP_HEADER,
        PATH_HEADER,
        ITEM,
        REDIRECT_HEADER,
        REDIRECT_ITEM
    }

    public static final class Row<T> {
        public final RowType type;
        public final String label;
        public final DlcType groupType;
        public final T item;
        public final DlcRedirectEntry redirectEntry;
        public final String itemFolderKey;
        public final String folderKey;
        public final DlcType folderType;
        public final String folderRaw;
        public boolean collapsed;
        public boolean expanding;
        public boolean collapsing;

        private Row(RowType type, String label, DlcType groupType, T item, DlcRedirectEntry redirectEntry,
                    String itemFolderKey, String folderKey, DlcType folderType, String folderRaw, boolean collapsed) {
            this.type = type;
            this.label = label;
            this.groupType = groupType;
            this.item = item;
            this.redirectEntry = redirectEntry;
            this.itemFolderKey = itemFolderKey;
            this.folderKey = folderKey;
            this.folderType = folderType;
            this.folderRaw = folderRaw;
            this.collapsed = collapsed;
        }

        public static <T> Row<T> groupHeader(DlcType type) {
            return new Row<>(RowType.GROUP_HEADER, null, type, null, null, null, null, null, null, false);
        }

        public static <T> Row<T> pathHeader(String label, String folderKey, DlcType folderType, String folderRaw, boolean collapsed) {
            return new Row<>(RowType.PATH_HEADER, label, null, null, null, null, folderKey, folderType, folderRaw, collapsed);
        }

        public static <T> Row<T> item(T item, String itemFolderKey) {
            return new Row<>(RowType.ITEM, null, null, item, null, itemFolderKey, null, null, null, false);
        }

        public static <T> Row<T> redirectHeader(String label, String folderKey, boolean collapsed) {
            return new Row<>(RowType.REDIRECT_HEADER, label, null, null, null, null, folderKey, null, null, collapsed);
        }

        public static <T> Row<T> redirectItem(DlcRedirectEntry entry, String itemFolderKey) {
            return new Row<>(RowType.REDIRECT_ITEM, null, null, null, entry, itemFolderKey, null, null, null, false);
        }
    }

    public static List<Row<DlcItem>> rows(List<DlcItem> items,
                                          List<DlcRedirectEntry> redirects,
                                          String searchText,
                                          DlcType typeFilter,
                                          Predicate<DlcType> visibleTypePredicate,
                                          Function<DlcItem, String> folderResolver,
                                          Function<DlcItem, String> displayNameResolver,
                                          Function<String, String> labelResolver,
                                          Predicate<String> collapsedPredicate,
                                          Predicate<DlcItem> dirtyPredicate,
                                          String redirectHeaderText) {
        List<Row<DlcItem>> rows = new ArrayList<>();
        appendRedirectRows(rows, redirects, typeFilter, collapsedPredicate, redirectHeaderText);
        rows.addAll(rows(
                items,
                searchText,
                typeFilter,
                visibleTypePredicate,
                folderResolver,
                displayNameResolver,
                DlcItem::getType,
                labelResolver,
                collapsedPredicate,
                dirtyPredicate,
                DlcItem::setDirty
        ));
        return rows;
    }

    public static <T> List<Row<T>> rows(List<T> items,
                                        String searchText,
                                        DlcType typeFilter,
                                        Predicate<DlcType> visibleTypePredicate,
                                        Function<T, String> folderResolver,
                                        Function<T, String> displayNameResolver,
                                        Function<T, DlcType> typeResolver,
                                        Function<String, String> labelResolver,
                                        Predicate<String> collapsedPredicate,
                                        Predicate<T> dirtyPredicate) {
        return rows(items, searchText, typeFilter, visibleTypePredicate, folderResolver, displayNameResolver, typeResolver,
                labelResolver, collapsedPredicate, dirtyPredicate, null);
    }

    public static <T> List<Row<T>> rows(List<T> items,
                                        String searchText,
                                        DlcType typeFilter,
                                        Predicate<DlcType> visibleTypePredicate,
                                        Function<T, String> folderResolver,
                                        Function<T, String> displayNameResolver,
                                        Function<T, DlcType> typeResolver,
                                        Function<String, String> labelResolver,
                                        Predicate<String> collapsedPredicate,
                                        Predicate<T> dirtyPredicate,
                                        BiConsumer<T, Boolean> dirtyWriter) {
        List<Row<T>> rows = new ArrayList<>();
        String normalizedSearch = searchText == null ? "" : searchText.toLowerCase();
        for (DlcType type : DlcType.values()) {
            if (!isVisibleType(type, visibleTypePredicate) || (typeFilter != null && typeFilter != type)) {
                continue;
            }
            List<T> typeItems = (items == null ? List.<T>of() : items).stream()
                    .filter(item -> item != null
                            && typeOf(item, typeResolver) == type
                            && matchesSearch(item, normalizedSearch, displayNameResolver))
                    .toList();
            if (typeItems.isEmpty()) {
                continue;
            }
            if (typeFilter == null) {
                rows.add(Row.groupHeader(type));
            }
            Map<String, List<T>> byPath = groupByRelativeFolder(typeItems, folderResolver);
            boolean hasSubDir = byPath.keySet().stream().anyMatch(key -> !ROOT_GROUP.equals(key));
            for (Map.Entry<String, List<T>> entry : byPath.entrySet()) {
                String folderKey = folderKey(type, entry.getKey());
                boolean collapsed = collapsedPredicate != null && collapsedPredicate.test(folderKey);
                if (ROOT_GROUP.equals(entry.getKey())) {
                    if (hasSubDir) {
                        rows.add(Row.pathHeader(label(entry.getKey(), labelResolver), folderKey, type, entry.getKey(), collapsed));
                    }
                } else {
                    rows.add(Row.pathHeader(label(entry.getKey(), labelResolver), folderKey, type, entry.getKey(), collapsed));
                }
                if (collapsed) {
                    continue;
                }
                for (T item : entry.getValue()) {
                    if (dirtyPredicate != null && dirtyWriter != null) {
                        dirtyWriter.accept(item, dirtyPredicate.test(item));
                    }
                    rows.add(Row.item(item, folderKey));
                }
            }
        }
        return rows;
    }

    public static String folderKey(DlcType type, String raw) {
        return (type == null ? "unknown" : type.name()) + ":" + normalizeFolder(raw);
    }

    public static String normalizeFolder(String raw) {
        return raw == null || raw.isBlank() ? ROOT_GROUP : raw;
    }

    private static void appendRedirectRows(List<Row<DlcItem>> rows,
                                           List<DlcRedirectEntry> redirects,
                                           DlcType typeFilter,
                                           Predicate<String> collapsedPredicate,
                                           String redirectHeaderText) {
        if (typeFilter != null || redirects == null || redirects.isEmpty()) {
            return;
        }
        boolean collapsed = collapsedPredicate != null && collapsedPredicate.test(REDIRECT_FOLDER_KEY);
        rows.add(Row.redirectHeader(redirectHeaderText, REDIRECT_FOLDER_KEY, collapsed));
        if (collapsed) {
            return;
        }
        for (DlcRedirectEntry entry : redirects) {
            rows.add(Row.redirectItem(entry, REDIRECT_FOLDER_KEY));
        }
    }

    private static boolean isVisibleType(DlcType type, Predicate<DlcType> visibleTypePredicate) {
        return visibleTypePredicate == null || visibleTypePredicate.test(type);
    }

    private static <T> DlcType typeOf(T item, Function<T, DlcType> typeResolver) {
        return typeResolver == null ? null : typeResolver.apply(item);
    }

    private static <T> boolean matchesSearch(T item, String search, Function<T, String> displayNameResolver) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String displayName = displayNameResolver == null ? null : displayNameResolver.apply(item);
        if (contains(displayName, search)) {
            return true;
        }
        if (item instanceof DlcItem dlcItem) {
            return contains(dlcItem.getName(), search)
                    || contains(dlcItem.getId(), search)
                    || contains(dlcItem.getIdentifier(), search);
        }
        return false;
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    private static <T> Map<String, List<T>> groupByRelativeFolder(List<T> items, Function<T, String> folderResolver) {
        Map<String, List<T>> grouped = new TreeMap<>();
        for (T item : items) {
            String folder = folderResolver == null ? ROOT_GROUP : folderResolver.apply(item);
            grouped.computeIfAbsent(normalizeFolder(folder), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private static String label(String raw, Function<String, String> labelResolver) {
        if (labelResolver == null) {
            return raw;
        }
        String label = labelResolver.apply(raw);
        return label == null || label.isBlank() ? raw : label;
    }
}
