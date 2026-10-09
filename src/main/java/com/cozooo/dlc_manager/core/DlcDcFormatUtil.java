package com.cozooo.dlc_manager.core;

import com.cozooo.dclanguage.DcCategoryHeader;
import com.cozooo.dclanguage.DcDocument;
import com.cozooo.dclanguage.DcDocuments;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DlcDcFormatUtil {
    public static final String DC_EXTENSION = ".dc";
    private static final Set<String> DOWNLOAD_KEYS = Set.of(
        "modrinth_projectid", "modrinth_versionid", "curseforge_projectid", "curseforge_fileid", "directurl", "priority"
    );

    private DlcDcFormatUtil() {
    }

    public static boolean isDcName(String name) {
        if (name == null) {
            return false;
        }
        return name.toLowerCase(Locale.ROOT).endsWith(DC_EXTENSION);
    }

    public static Path toDcPath(Path path) {
        if (path == null) {
            return null;
        }
        String fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        if (isDcName(fileName)) {
            return path;
        }
        String base = stripConfigExtension(fileName);
        Path parent = path.getParent();
        if (parent == null) {
            return Path.of(base + DC_EXTENSION);
        }
        return parent.resolve(base + DC_EXTENSION);
    }

    public static Path resolveConfigPath(Path dir, String baseName) {
        if (dir == null || baseName == null || baseName.isBlank()) {
            return null;
        }
        return dir.resolve(baseName + DC_EXTENSION);
    }

    public static String stripConfigExtension(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(DC_EXTENSION)) {
            return name.substring(0, name.length() - DC_EXTENSION.length());
        }
        return name;
    }

    public static JsonObject read(Path path) {
        if (path == null || !Files.exists(path)) return null;
        try {
            return readNode(readStructuredDocument(path).root());
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String write(JsonObject ordered) {
        if (ordered == null) return "";
        JsonObject root = new JsonObject();
        updateNode(root, ordered);
        return DcDocuments.writeText(DcDocuments.fromJson(root));
    }

    public static boolean write(Path path, JsonObject ordered) {
        return writeDocument(path, ordered, true);
    }

    public static JsonObject readGeneric(Path path) {
        if (path == null || !Files.exists(path)) return null;
        try {
            return DcDocuments.read(path).toJson();
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static JsonElement readElement(Path path) {
        if (path == null || !isDcName(path.getFileName() == null ? null : path.getFileName().toString())) return null;
        return readGeneric(path);
    }

    public static JsonObject readObject(Path path) {
        JsonElement element = readElement(path);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static JsonObject readGenericText(String text) {
        if (text == null) return null;
        try {
            return DcDocuments.readText(text).toJson();
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String writeGeneric(JsonObject obj) {
        return obj == null ? "" : DcDocuments.writeText(DcDocuments.fromJson(obj));
    }

    public static boolean writeGeneric(Path path, JsonObject obj) {
        return writeDocument(path, obj, false);
    }

    private static boolean writeDocument(Path path, JsonObject value, boolean structured) {
        if (path == null || value == null) return false;
        try {
            DcDocument document = Files.exists(path)
                ? (structured ? readStructuredDocument(path) : DcDocuments.read(path)) : DcDocuments.create();
            if (structured) {
                updateNode(document.root(), value);
            } else {
                document.root().entrySet().clear();
                for (var entry : value.entrySet()) document.root().add(entry.getKey(), entry.getValue().deepCopy());
            }
            DcDocuments.write(path, document);
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException ignored) {
            return false;
        }
    }

    private static DcDocument readStructuredDocument(Path path) throws IOException {
        String text = Files.readString(path);
        List<DcCategoryHeader> headers = DcDocuments.categoryHeaders(text);
        ArrayDeque<HeaderFrame> frames = new ArrayDeque<>();
        Set<List<String>> branches = new HashSet<>();
        StringBuilder rewritten = new StringBuilder(text.length());
        int copied = 0;
        for (int index = 0; index < headers.size(); index++) {
            DcCategoryHeader header = headers.get(index);
            while (!frames.isEmpty() && frames.peek().indentation() >= header.indentation()) frames.pop();
            HeaderFrame parent = frames.peek();
            List<String> node = parent == null ? List.of() : parent.node();
            List<String> names = header.names();
            String raw = text.substring(header.start(), header.end());
            boolean dependency = names.size() > 1 && names.get(0).equalsIgnoreCase("dependencies");
            boolean nestedHeader = index + 1 < headers.size()
                && headers.get(index + 1).indentation() > header.indentation()
                && headers.get(index + 1).names().size() == 1
                && isSection(headers.get(index + 1).names().get(0));
            boolean quotedPath = raw.indexOf('"') >= 0 || raw.indexOf('\'') >= 0;
            boolean completePath = names.size() > 2 && (quotedPath
                || names.subList(2, names.size()).stream().anyMatch(DlcDcFormatUtil::isSection)
                || branches.stream().anyMatch(branch -> startsWith(names, branch) && names.size() > branch.size()));
            boolean relationChild = parent != null && parent.relation() != null
                && !isSection(names.get(0)) && !dependency;
            String replacement = null;
            String relation = null;
            if (dependency && (names.size() == 2 || !completePath || nestedHeader && !quotedPath)) {
                String identifier = String.join(".", names.subList(1, names.size()));
                node = append(node, names.get(0), identifier);
                branches.add(node);
                replacement = formatHeader(node);
            } else if (relationChild) {
                node = append(node, parent.relation(), String.join(".", names));
                branches.add(node);
                if (header.bracketed()) replacement = formatHeader(node);
            } else if (names.size() > 1) {
                String last = names.get(names.size() - 1);
                if (isSection(last)) {
                    node = List.copyOf(names.subList(0, names.size() - 1));
                    if (node.size() > 1 && node.get(0).equalsIgnoreCase("dependencies")) branches.add(node);
                    if (last.equalsIgnoreCase("dependencies") || last.equalsIgnoreCase("conflicts")) relation = last;
                } else if (names.get(names.size() - 2).equalsIgnoreCase("dependencies")) {
                    node = names;
                    branches.add(node);
                } else {
                    int depth = -1;
                    for (List<String> branch : branches) {
                        if (startsWith(names, branch) && branch.size() > depth) {
                            node = branch;
                            depth = branch.size();
                        }
                    }
                }
            } else if (names.get(0).equalsIgnoreCase("dependencies") || names.get(0).equalsIgnoreCase("conflicts")) {
                relation = names.get(0);
            }
            frames.push(new HeaderFrame(header.indentation(), node, relation));
            if (replacement != null && !replacement.equals(raw)) {
                rewritten.append(text, copied, header.start()).append(replacement);
                copied = header.end();
            }
        }
        return DcDocuments.readText(copied == 0 ? text : rewritten.append(text, copied, text.length()).toString());
    }

    private static List<String> append(List<String> path, String category, String identifier) {
        List<String> result = new ArrayList<>(path);
        result.add(category);
        result.add(identifier);
        return List.copyOf(result);
    }

    private static boolean startsWith(List<String> path, List<String> prefix) {
        return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
    }

    private static String formatHeader(List<String> path) {
        List<String> names = new ArrayList<>();
        for (String name : path) names.add(name.matches("[A-Za-z_][A-Za-z0-9_-]*") ? name : new JsonPrimitive(name).toString());
        return "[" + String.join(".", names) + "]";
    }

    private record HeaderFrame(int indentation, List<String> node, String relation) {
    }

    private static JsonObject readNode(JsonObject document) {
        JsonObject out = new JsonObject();
        for (var entry : document.entrySet()) {
            if (!isSection(entry.getKey())) out.add(entry.getKey(), entry.getValue().deepCopy());
        }
        for (String section : List.of("download", "basic")) {
            JsonObject fields = object(document, section);
            if (fields != null) {
                for (var entry : fields.entrySet()) put(out, entry.getKey(), entry.getValue().deepCopy());
            }
        }
        for (String relation : List.of("dependencies", "conflicts")) {
            JsonObject entries = new JsonObject();
            collectRelation(get(out, relation), entries);
            collectRelation(get(document, relation), entries);
            put(out, relation, entries.entrySet().isEmpty() ? new JsonPrimitive("") : entries);
        }
        for (String section : List.of("DisplayName", "tip")) {
            JsonElement value = get(document, section);
            if (value != null) put(out, section, value.deepCopy());
            else if (get(out, section) == null) out.add(section, new JsonObject());
        }
        return out;
    }

    private static void collectRelation(JsonElement value, JsonObject entries) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonPrimitive()) {
            for (String name : value.getAsString().split("[,\\n\\r]+")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) entries.addProperty(trimmed, trimmed);
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) collectRelation(child, entries);
        } else {
            for (var entry : value.getAsJsonObject().entrySet()) {
                JsonElement child = entry.getValue();
                if (child.isJsonObject()) {
                    entries.add(entry.getKey(), child.getAsJsonObject().entrySet().isEmpty()
                        ? new JsonPrimitive(entry.getKey()) : readNode(child.getAsJsonObject()));
                } else if (child.isJsonNull()) {
                    entries.addProperty(entry.getKey(), entry.getKey());
                } else {
                    collectRelation(child, entries);
                }
            }
        }
    }

    private static void updateNode(JsonObject document, JsonObject value) {
        JsonObject previous = readNode(document);
        for (var entry : previous.entrySet()) {
            if (get(value, entry.getKey()) == null) removeField(document, entry.getKey());
        }
        for (var entry : value.entrySet()) {
            String key = entry.getKey();
            JsonElement current = entry.getValue();
            JsonElement before = get(previous, key);
            if (before != null && before.toString().equals(current.toString())) continue;
            if (key.equalsIgnoreCase("dependencies") || key.equalsIgnoreCase("conflicts")) {
                JsonObject relation = writeRelation(current, object(document, key));
                String spelling = actualKey(document, key);
                removeField(document, key);
                if (!relation.entrySet().isEmpty()) document.add(spelling, relation);
                continue;
            }
            JsonObject container = fieldContainer(document, key);
            if (container == null) {
                if (current.isJsonObject() && !isSection(key)) {
                    container = document;
                } else if (key.equalsIgnoreCase("DisplayName") || key.equalsIgnoreCase("tip")) {
                    if (current.isJsonObject() && current.getAsJsonObject().entrySet().isEmpty()) continue;
                    container = document;
                } else {
                    String section = DOWNLOAD_KEYS.contains(key.toLowerCase(Locale.ROOT)) ? "download" : "basic";
                    container = object(document, section);
                    if (container == null) {
                        container = new JsonObject();
                        document.add(actualKey(document, section), container);
                    }
                }
            }
            put(container, key, current.deepCopy());
        }
    }

    private static JsonObject writeRelation(JsonElement value, JsonObject original) {
        JsonObject semantic = new JsonObject();
        collectRelation(value, semantic);
        JsonObject out = new JsonObject();
        for (var entry : semantic.entrySet()) {
            JsonElement child = entry.getValue();
            JsonElement before = original == null ? null : original.get(entry.getKey());
            if (child.isJsonObject()) {
                JsonObject branch = before != null && before.isJsonObject() ? before.deepCopy().getAsJsonObject() : new JsonObject();
                updateNode(branch, child.getAsJsonObject());
                out.add(entry.getKey(), branch);
            } else {
                out.add(entry.getKey(), before != null && (!before.isJsonObject() || before.getAsJsonObject().entrySet().isEmpty())
                    ? before.deepCopy() : new JsonObject());
            }
        }
        return out;
    }

    private static JsonObject fieldContainer(JsonObject document, String key) {
        for (String section : List.of("basic", "download")) {
            JsonObject fields = object(document, section);
            if (fields != null && get(fields, key) != null) return fields;
        }
        return !key.equalsIgnoreCase("basic") && !key.equalsIgnoreCase("download") && get(document, key) != null ? document : null;
    }

    private static void removeField(JsonObject document, String key) {
        for (String section : List.of("basic", "download")) {
            JsonObject fields = object(document, section);
            if (fields != null) fields.remove(actualKey(fields, key));
        }
        if (!key.equalsIgnoreCase("basic") && !key.equalsIgnoreCase("download")) document.remove(actualKey(document, key));
    }

    private static boolean isSection(String key) {
        return key.equalsIgnoreCase("basic") || key.equalsIgnoreCase("download")
            || key.equalsIgnoreCase("dependencies") || key.equalsIgnoreCase("conflicts")
            || key.equalsIgnoreCase("DisplayName") || key.equalsIgnoreCase("tip");
    }

    private static JsonObject object(JsonObject value, String key) {
        JsonElement found = get(value, key);
        return found != null && found.isJsonObject() ? found.getAsJsonObject() : null;
    }

    private static JsonElement get(JsonObject value, String key) {
        return value.get(actualKey(value, key));
    }

    private static void put(JsonObject value, String key, JsonElement element) {
        value.add(actualKey(value, key), element);
    }

    private static String actualKey(JsonObject value, String key) {
        if (value.has(key)) return key;
        for (String candidate : value.keySet()) if (candidate.equalsIgnoreCase(key)) return candidate;
        return key;
    }
}
