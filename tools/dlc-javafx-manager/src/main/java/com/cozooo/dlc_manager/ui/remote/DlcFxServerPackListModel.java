package com.cozooo.dlc_manager.ui.remote;

import com.cozooo.dlc_manager.client.pack.DlcPackMetadata;
import com.cozooo.dlc_manager.client.server.DlcServerPackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DlcFxServerPackListModel {
    private DlcFxServerPackListModel() {
    }

    public record Entry(DlcServerPackInfo info) {
        public String archivePath() {
            return safe(info == null ? null : info.archivePath);
        }

        public String orderKey() {
            String explicit = safe(info == null ? null : info.orderKey);
            if (!explicit.isBlank()) {
                return explicit;
            }
            return archivePath() + "#" + identifier() + "@" + version();
        }

        public String identifier() {
            return safe(info == null ? null : info.identifier);
        }

        public String version() {
            return safe(info == null ? null : info.version);
        }

        public boolean installed() {
            return info != null && info.installed;
        }

        public String installedVersion() {
            return safe(info == null ? null : info.installedVersion);
        }
    }

    public record Group(String identifier, List<Entry> versions, String orderKey, String selectedVersionKey) {
        public Entry displayEntry() {
            for (Entry entry : versions) {
                if (entry.orderKey().equals(selectedVersionKey)) {
                    return entry;
                }
            }
            return versions.isEmpty() ? null : versions.getFirst();
        }

        public Group withSelectedVersionKey(String key) {
            return new Group(identifier, versions, orderKey, key);
        }
    }

    public static List<Group> groups(List<DlcServerPackInfo> packs, List<String> order) {
        Map<String, List<Entry>> byIdentifier = new LinkedHashMap<>();
        for (DlcServerPackInfo info : packs == null ? List.<DlcServerPackInfo>of() : packs) {
            Entry entry = new Entry(info);
            if (entry.identifier().isBlank() || entry.version().isBlank() || entry.archivePath().isBlank()) {
                continue;
            }
            byIdentifier.computeIfAbsent(entry.identifier(), key -> new ArrayList<>()).add(entry);
        }
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> mapEntry : byIdentifier.entrySet()) {
            List<Entry> versions = new ArrayList<>(mapEntry.getValue());
            versions.sort((a, b) -> {
                int compare = DlcPackMetadata.compareVersionStrings(b.version(), a.version());
                return compare != 0 ? compare : a.archivePath().compareToIgnoreCase(b.archivePath());
            });
            Entry installed = versions.stream().filter(Entry::installed).findFirst().orElse(null);
            Entry orderEntry = installed == null ? versions.getFirst() : installed;
            groups.add(new Group(mapEntry.getKey(), List.copyOf(versions), orderEntry.orderKey(), orderEntry.orderKey()));
        }
        List<String> normalizedOrder = normalizedOrder(order, groups);
        groups.sort(Comparator.comparingInt(group -> {
            int index = normalizedOrder.indexOf(group.orderKey());
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        return groups;
    }

    public static List<String> normalizedOrder(List<String> savedOrder, List<Group> groups) {
        List<String> out = new ArrayList<>();
        if (savedOrder != null) {
            for (String key : savedOrder) {
                String identifier = identifierFromOrderKey(key);
                if (identifier == null) {
                    continue;
                }
                Group group = findGroup(groups, identifier);
                if (group != null && !out.contains(group.orderKey())) {
                    out.add(group.orderKey());
                }
            }
        }
        for (Group group : groups == null ? List.<Group>of() : groups) {
            if (group != null && !out.contains(group.orderKey())) {
                out.add(group.orderKey());
            }
        }
        return out;
    }

    public static String identifierFromOrderKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        int hash = key.lastIndexOf('#');
        int at = key.lastIndexOf('@');
        if (hash < 0 || at <= hash + 1) {
            return null;
        }
        String identifier = key.substring(hash + 1, at);
        return identifier.isBlank() ? null : identifier;
    }

    private static Group findGroup(List<Group> groups, String identifier) {
        for (Group group : groups == null ? List.<Group>of() : groups) {
            if (group != null && identifier.equals(group.identifier())) {
                return group;
            }
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
