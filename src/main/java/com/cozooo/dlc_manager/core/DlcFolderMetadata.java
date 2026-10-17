package com.cozooo.dlc_manager.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class DlcFolderMetadata {
    public static final String FILE_NAME = DlcDcFormatUtil.FOLDER_METADATA_FILE_NAME;
    private static final Entry DEFAULT = new Entry(0, Map.of());
    private final List<Path> roots;
    private final Path gameDir;
    private final Map<Path, Map<String, Entry>> cache = new HashMap<>();

    public record Entry(int priority, Map<String, String> displayNames) {
        public Entry { displayNames = Map.copyOf(displayNames); }
        public String displayName(String folder, String language) {
            String value = displayNames.get(language == null ? "" : language.toLowerCase(Locale.ROOT));
            if (value == null || value.isBlank()) value = displayNames.get("en_us");
            return value == null || value.isBlank() ? folder : value;
        }
    }
    private record Segment(String name, Entry metadata) { }
    private record Key(String path, List<Segment> segments) { }
    public record Group(String key, String path, String label, List<DlcItem> items) {
        public boolean isRoot() { return path.isEmpty(); }
    }

    public DlcFolderMetadata(List<Path> roots, Path gameDir) {
        this.roots = roots.stream().map(path -> path.toAbsolutePath().normalize())
            .sorted(Comparator.comparingInt(Path::getNameCount).reversed()).toList();
        this.gameDir = gameDir.toAbsolutePath().normalize();
    }

    public List<Group> group(List<DlcItem> items, String language) {
        Map<Key, List<DlcItem>> grouped = new LinkedHashMap<>();
        for (DlcItem item : items) grouped.computeIfAbsent(key(item), ignored -> new ArrayList<>()).add(item);
        List<Key> keys = new ArrayList<>(grouped.keySet());
        keys.sort(DlcFolderMetadata::compare);
        List<Group> result = new ArrayList<>();
        for (Key key : keys) {
            String label = String.join("/", key.segments().stream()
                .map(segment -> segment.metadata().displayName(segment.name(), language)).toList());
            result.add(new Group(identity(key), key.path(), label, List.copyOf(grouped.get(key))));
        }
        return List.copyOf(result);
    }

    public Map<String, Entry> load(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        return cache.computeIfAbsent(normalized, path -> {
            JsonObject object = DlcDcConfigIO.read(path.resolve(FILE_NAME), DlcDcConfigIO.Layout.GENERIC);
            if (object == null) return Map.of();
            Map<String, Entry> entries = new LinkedHashMap<>();
            for (var folder : object.entrySet()) {
                if (!folder.getValue().isJsonObject()) continue;
                JsonObject values = folder.getValue().getAsJsonObject();
                int priority = 0;
                var raw = DlcDcConfigIO.getIgnoreCase(values, "priority");
                if (raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
                    try { priority = raw.getAsBigDecimal().intValueExact(); }
                    catch (ArithmeticException | NumberFormatException ignored) { }
                }
                Map<String, String> names = new TreeMap<>();
                for (var name : values.entrySet()) {
                    if (name.getKey().regionMatches(true, 0, "DisplayName.", 0, 12)
                        && name.getValue().isJsonPrimitive() && name.getValue().getAsJsonPrimitive().isString())
                        names.put(name.getKey().substring(12).toLowerCase(Locale.ROOT), name.getValue().getAsString());
                }
                entries.put(folder.getKey(), new Entry(priority, names));
            }
            return Map.copyOf(entries);
        });
    }

    private Key key(DlcItem item) {
        Path source = item.getSourcePath();
        if (source == null) return new Key("", List.of());
        Path normalized = source.toAbsolutePath().normalize();
        Path root = roots.stream().filter(normalized::startsWith).findFirst().orElse(null);
        Path directory = item.getGroupDirectory() == null ? normalized.getParent() : item.getGroupDirectory().toAbsolutePath().normalize();
        if (directory == null) return new Key("", List.of());
        if (root == null) {
            try {
                String relative = gameDir.relativize(directory).toString().replace('\\', '/');
                return new Key(relative, directorySegments(relative));
            } catch (IllegalArgumentException ignored) {
                return new Key("", List.of());
            }
        }
        boolean remote = directory.startsWith(root.resolve("remote")) || directory.startsWith(root.resolve("downloaded/DLCs"));
        Path origin = directory.startsWith(root.resolve("remote")) ? root.resolve("remote")
            : directory.startsWith(root.resolve("downloaded/DLCs")) ? root.resolve("downloaded/DLCs") : root;
        if (!directory.startsWith(origin)) return new Key("", List.of());
        Path relative = origin.relativize(directory);
        List<Segment> segments = new ArrayList<>();
        Path parent = Path.of("");
        if (!relative.toString().isEmpty()) for (Path part : relative) {
            String name = part.toString();
            Entry local = load(root.resolve(parent)).get(name);
            Entry downloaded = load(root.resolve("remote").resolve(parent)).get(name);
            Entry selected = local != null && downloaded != null && !local.equals(downloaded)
                ? (remote ? downloaded : local) : local != null ? local : downloaded;
            segments.add(new Segment(name, selected == null ? DEFAULT : selected));
            parent = parent.resolve(part);
        }
        return new Key(relative.toString().replace('\\', '/'), List.copyOf(segments));
    }

    private static List<Segment> directorySegments(String path) {
        if (path.isEmpty()) return List.of();
        return java.util.Arrays.stream(path.split("/")).map(name -> new Segment(name, DEFAULT)).toList();
    }

    private static int compare(Key left, Key right) {
        for (int index = 0; index < Math.min(left.segments().size(), right.segments().size()); index++) {
            Segment a = left.segments().get(index), b = right.segments().get(index);
            int priority = Integer.compare(a.metadata().priority(), b.metadata().priority());
            if (priority != 0) return priority;
            int name = a.name().compareTo(b.name());
            if (name != 0) return name;
        }
        int depth = Integer.compare(left.segments().size(), right.segments().size());
        return depth != 0 ? depth : identity(left).compareTo(identity(right));
    }

    private static String identity(Key key) {
        JsonArray values = new JsonArray();
        values.add(key.path());
        for (Segment segment : key.segments()) {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("priority", segment.metadata().priority());
            JsonObject names = new JsonObject();
            new TreeMap<>(segment.metadata().displayNames()).forEach(names::addProperty);
            metadata.add("DisplayName", names);
            values.add(metadata);
        }
        return values.toString();
    }
}
