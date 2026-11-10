package com.cozooo.dlc_manager.sync;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;
import com.google.gson.JsonObject;

public final class DlcSyncModel {
    private DlcSyncModel() {}
    public enum Mode { CONFIG, FILES }
    public enum Direction { PUSH, PULL }
    public enum Category { ALL, DLC, REQUIRED, UPDATE, MANAGER_CONFIG, PRESET }
    public record Update(String baseOld, String dlcOld, String baseNew, String dlcNew, String platform) {}
    public record File(String id, long size, String hash) {}
    public record Item(String key, String identifier, File config, List<File> resources, Boolean enabled, Map<String, String> displayNames, Map<String, String> tooltips, Update update, JsonObject settings) {
        public Item(String key, String identifier, File config, List<File> resources, Boolean enabled, Map<String, String> displayNames, Map<String, String> tooltips, Update update) {
            this(key, identifier, config, resources, enabled, displayNames, tooltips, update, null);
        }
        public Item(String key, String identifier, File config, List<File> resources, Boolean enabled, Map<String, String> displayNames, Map<String, String> tooltips) {
            this(key, identifier, config, resources, enabled, displayNames, tooltips, null);
        }
        public Item(String key, String identifier, File config, List<File> resources, Boolean enabled, Map<String, String> displayNames) {
            this(key, identifier, config, resources, enabled, displayNames, Map.of());
        }
        public Item(String key, String identifier, File config, List<File> resources, Boolean enabled) {
            this(key, identifier, config, resources, enabled, Map.of(), Map.of());
        }
    }
    public record Row(String key, Item source, Item target, Direction direction, boolean replacement, boolean stateChanged) {}
    public record Comparison(List<Row> rows, Map<String, String> statuses) {
        public String status(Row row) { return statuses.get(row.key()); }
    }
    public record Result(int completed, boolean canceled, boolean requiresRestart, List<String> errors, List<String> succeeded) {}
    @FunctionalInterface public interface Peer extends AutoCloseable { JsonObject call(JsonObject request) throws IOException; @Override default void close() throws IOException {} }
    @FunctionalInterface public interface Progress { void update(String phase, String resource, long done, long total); }
    public interface Journal extends AutoCloseable {
        void capture(Path path) throws IOException;
        void rollback() throws IOException;
        void commit() throws IOException;
        @Override void close();
    }
    @FunctionalInterface public interface Quiesce { void await(BooleanSupplier canceled) throws Exception; }
    public interface Adapter {
        Path workDirectory();
        List<Item> manifest() throws IOException;
        default Item item(String key) throws IOException {
            for (Item item : manifest()) if (item.key().equals(key)) return item;
            throw new DlcSyncFailure("config_not_found", key);
        }
        Path resolve(String id) throws IOException;
        void validateKey(String key) throws IOException;
        default void validateIncoming(Item item) throws IOException {}
        default void writeSettings(String key, JsonObject settings, Journal journal) throws IOException { throw new DlcSyncFailure("settings_unavailable"); }
        Journal journal(Path directory) throws IOException;
        void prepare(String key, boolean force, BooleanSupplier canceled, Progress progress) throws Exception;
        boolean apply(String key, Boolean enabled, Journal journal, BooleanSupplier canceled) throws Exception;
        void reload() throws IOException;
        void recover() throws IOException;
        void recoverOperation(String operation) throws IOException;
        default void quiesce(BooleanSupplier canceled) throws Exception {}
    }
    public static boolean sharedState(Item source, Item target) {
        return source != null && target != null && source.enabled() != null && target.enabled() != null
            && source.identifier() != null && !source.identifier().isBlank() && source.identifier().equals(target.identifier()) && category(source) == category(target);
    }
    public static boolean hasStateDifferences(List<Item> local, List<Item> remote) {
        Map<String, Item> target = index(remote);
        return local.stream().anyMatch(item -> sharedState(item, target.get(item.key())) && !Objects.equals(item.enabled(), target.get(item.key()).enabled()));
    }
    public static List<Row> plan(List<Item> local, List<Item> server, Direction direction, boolean states) { return plan(local, server, direction, states, Mode.FILES); }
    public static List<Row> plan(List<Item> local, List<Item> server, Direction direction, boolean states, Mode mode) {
        List<Item> sourceItems = direction == Direction.PUSH ? local : server, targetItems = direction == Direction.PUSH ? server : local;
        Map<String, Item> left = index(sourceItems), right = index(targetItems);
        Set<String> eligible = eligibleUpdates(sourceItems, targetItems), reversedEligible = eligibleUpdates(targetItems, sourceItems);
        List<Row> rows = new ArrayList<>();
        for (Item source : left.values()) {
            if (versionState(source) || category(source) == Category.UPDATE && (mode != Mode.FILES || !eligible.contains(source.key()))) continue;
            Item target = right.get(source.key());
            boolean state = states && sharedState(source, target) && !Objects.equals(source.enabled(), target.enabled());
            if (target != null && same(source.config(), target.config()) && (!usesResources(source) || resourcesMatch(source.resources(), target.resources(), mode)) && !state) continue;
            boolean replacement = target != null && (!same(source.config(), target.config()) || state
                || usesResources(source) && target.resources().stream().anyMatch(file -> file.size() >= 0) && !resourcesMatch(source.resources(), target.resources(), mode));
            rows.add(new Row(source.key(), source, target, direction, replacement, state));
        }
        for (Item target : right.values()) if (!left.containsKey(target.key()) && !versionState(target)
            && (category(target) != Category.UPDATE || mode == Mode.FILES && reversedEligible.contains(target.key()))) rows.add(new Row(target.key(), target, null,
            direction == Direction.PUSH ? Direction.PULL : Direction.PUSH, false, false));
        rows.sort(Comparator.comparing(Row::key));
        return rows;
    }
    public static Comparison compare(List<Item> local, List<Item> server, Direction direction, boolean states, Mode mode) {
        List<Row> rows = plan(local, server, direction, states, mode);
        Map<String, String> statuses = new HashMap<>();
        for (Row row : rows) statuses.put(row.key(), status(row, mode));
        return new Comparison(List.copyOf(rows), Map.copyOf(statuses));
    }
    public static boolean same(File a, File b) {
        return a != null && b != null && a.size() >= 0 && a.size() == b.size() && a.id().equals(b.id()) && a.hash() != null && a.hash().equals(b.hash());
    }
    public static boolean sameResources(List<File> a, List<File> b) {
        if (a.isEmpty() || b.isEmpty() || a.size() != b.size()) return false;
        Map<String, File> indexed = new HashMap<>();
        for (File file : b) indexed.put(file.id(), file);
        for (File file : a) if (!same(file, indexed.get(file.id()))) return false;
        return true;
    }
    public static boolean resourcesMatch(List<File> source, List<File> target, Mode mode) {
        if (mode == Mode.FILES) return sameResources(source, target);
        return true;
    }
    public static String displayName(Item item, String language) {
        String localized = localized(item.displayNames(), language);
        if (!localized.isBlank()) return localized;
        if (item.identifier() != null && !item.identifier().isBlank()) return item.identifier();
        String name = item.key().substring(item.key().lastIndexOf('/') + 1);
        int extension = name.lastIndexOf('.');
        return extension > 0 ? name.substring(0, extension) : name;
    }
    public static String tooltip(Item item, String language) { return localized(item.tooltips(), language); }
    private static String localized(Map<String, String> names, String language) {
        if (names != null) {
            String localized = names.get(language == null ? "" : language.toLowerCase(Locale.ROOT).replace('-', '_'));
            if (localized != null && !localized.isBlank()) return localized;
            String english = names.get("en_us");
            if (english != null && !english.isBlank()) return english;
            for (String value : names.values()) if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
    public static String status(Row row, Mode mode) {
        if (row.target() == null) return row.direction() == Direction.PUSH ? "server_missing" : "client_missing";
        if (mode == Mode.FILES && usesResources(row.source()) && (row.source().resources().isEmpty() || row.target().resources().isEmpty()
            || row.source().resources().stream().anyMatch(file -> file.size() < 0)
            || row.target().resources().stream().anyMatch(file -> file.size() < 0)
            || row.source().resources().stream().anyMatch(file -> row.target().resources().stream().noneMatch(other -> other.id().equals(file.id()))))) return "resources_missing";
        if (same(row.source().config(), row.target().config()) && (!usesResources(row.source()) || resourcesMatch(row.source().resources(), row.target().resources(), mode))) return "state_changed";
        return "content_changed";
    }
    public static List<String> differences(Row row, Mode mode) {
        List<String> result = new ArrayList<>();
        if (row.target() == null) result.add("configuration_missing");
        else if (!same(row.source().config(), row.target().config())) result.add("configuration_changed");
        if (mode == Mode.FILES && usesResources(row.source()) && (row.target() == null || !resourcesMatch(row.source().resources(), row.target().resources(), mode))) {
            boolean missing = row.target() == null || row.target().resources().isEmpty() || row.target().resources().stream().anyMatch(file -> file.size() < 0) || row.source().resources().isEmpty() || row.source().resources().stream().anyMatch(file -> file.size() < 0);
            result.add(missing ? "resources_missing" : "resources_changed");
        }
        if (row.stateChanged()) result.add(Boolean.TRUE.equals(row.source().enabled()) ? "enable" : "disable");
        return result;
    }
    private static String relative(String key) { return key.substring(key.indexOf(':') + 1); }
    public static Category category(Item item) {
        if (item.key().equalsIgnoreCase("manager:version.dc")) return Category.UPDATE;
        if (item.key().startsWith("manager:")) return Category.MANAGER_CONFIG;
        String path = relative(item.key());
        if (path.startsWith("required/")) return Category.REQUIRED;
        if (path.startsWith("preset/")) return Category.PRESET;
        if (path.startsWith("update/") || path.equalsIgnoreCase("info/version.dc")) return Category.UPDATE;
        return Category.DLC;
    }
    public static boolean usesResources(Item item) { return category(item) == Category.DLC || category(item) == Category.REQUIRED; }
    public static boolean versionState(Item item) {
        return relative(item.key()).equalsIgnoreCase("info/version.dc") || item.key().equalsIgnoreCase("manager:version.dc");
    }
    private static String root(Item item) {
        if (item.key().equalsIgnoreCase("manager:version.dc")) return "0";
        int colon = item.key().indexOf(':');
        return colon < 0 ? "" : item.key().substring(0, colon);
    }
    private static String pair(String base, String dlc) { return base + "\n" + dlc; }
    private static boolean advances(Update update) {
        if (update == null || update.baseOld() == null || update.dlcOld() == null || update.baseNew() == null || update.dlcNew() == null) return false;
        int base = com.cozooo.dlc_manager.client.update.DlcUpdateService.compareVersions(update.baseNew(), update.baseOld());
        return base > 0 || base == 0 && com.cozooo.dlc_manager.client.update.DlcUpdateService.compareVersions(update.dlcNew(), update.dlcOld()) > 0;
    }
    public static Set<String> eligibleUpdates(List<Item> source, List<Item> target) {
        Map<String, Update> versions = new HashMap<>();
        Map<String, Set<String>> reachable = new HashMap<>();
        for (Item item : target) if (versionState(item) && item.update() != null) {
            versions.put(root(item), item.update());
            reachable.put(root(item), new HashSet<>(Set.of(pair(item.update().baseOld(), item.update().dlcOld()))));
        }
        Set<String> sourceKeys = new HashSet<>();
        List<Item> available = new ArrayList<>();
        for (Item item : source) if (category(item) == Category.UPDATE && !versionState(item)) { sourceKeys.add(item.key()); available.add(item); }
        for (Item item : target) if (category(item) == Category.UPDATE && !versionState(item) && !sourceKeys.contains(item.key())) available.add(item);
        Set<String> eligible = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (Item item : available) {
                Update current = versions.get(root(item)), update = item.update();
                if (current == null || !advances(update) || !("universal".equals(update.platform()) || Objects.equals(current.platform(), update.platform()))) continue;
                Set<String> reached = reachable.get(root(item));
                if (!reached.contains(pair(update.baseOld(), update.dlcOld()))) continue;
                if (sourceKeys.contains(item.key())) eligible.add(item.key());
                if (reached.add(pair(update.baseNew(), update.dlcNew()))) changed = true;
            }
        } while (changed);
        return eligible;
    }
    private static int applicationOrder(Item item) { return category(item) == Category.UPDATE ? 2 : category(item) == Category.PRESET ? 1 : 0; }
    public static List<Row> ordered(List<Row> rows) {
        List<Row> result = new ArrayList<>(rows);
        result.sort((a, b) -> {
            int left = applicationOrder(a.source()), right = applicationOrder(b.source());
            if (left != right) return Integer.compare(left, right);
            if (left == 1) return Boolean.compare(Boolean.TRUE.equals(a.source().enabled()), Boolean.TRUE.equals(b.source().enabled()));
            if (left != 2 || a.source().update() == null || b.source().update() == null) return 0;
            int base = com.cozooo.dlc_manager.client.update.DlcUpdateService.compareVersions(a.source().update().baseOld(), b.source().update().baseOld());
            return base != 0 ? base : com.cozooo.dlc_manager.client.update.DlcUpdateService.compareVersions(a.source().update().dlcOld(), b.source().update().dlcOld());
        });
        return result;
    }
    public static Map<String, Item> index(List<Item> items) {
        Map<String, Item> result = new TreeMap<>();
        for (Item item : items) if (result.put(item.key(), item) != null) throw new IllegalArgumentException("Duplicate DLC configuration: " + item.key());
        return result;
    }
}
