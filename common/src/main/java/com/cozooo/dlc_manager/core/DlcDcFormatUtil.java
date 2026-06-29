package com.cozooo.dlc_manager.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DlcDcFormatUtil {
    public static final String DC_EXTENSION = ".dc";
    private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();

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
        if (path == null || !Files.exists(path)) {
            return null;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException ignored) {
            return null;
        }
        DlcNode root = new DlcNode();
        ArrayDeque<Frame> stack = new ArrayDeque<>();
        stack.push(Frame.root(root));
        int index = 0;
        while (index < lines.size()) {
            String raw = lines.get(index);
            if (raw == null) {
                index++;
                continue;
            }
            if (index == 0 && raw.startsWith("\uFEFF")) {
                raw = raw.substring(1);
            }
            String noComment = stripComment(raw);
            if (noComment.isBlank()) {
                index++;
                continue;
            }
            int indent = countLeadingSpaces(noComment);
            String content = noComment.substring(indent).trim();
            if (content.isBlank()) {
                index++;
                continue;
            }
            if (content.startsWith("[") && content.endsWith("]") && content.length() >= 3) {
                String header = content.substring(1, content.length() - 1).trim();
                while (stack.size() > 1 && stack.peek().indent >= indent) {
                    stack.pop();
                }
                if (indent == 0 && (header.equals("dependencies")
                    || header.equals("conflicts")
                    || (!isQuotedKey(header) && header.startsWith("dependencies.")))) {
                    while (stack.size() > 1) {
                        stack.pop();
                    }
                }
                if (!isQuotedKey(header) && header.startsWith("dependencies.")) {
                    DlcNode node = findNearestNode(stack);
                    String depKey = unquoteKey(header.substring("dependencies.".length()));
                    if (node != null && !depKey.isBlank()) {
                        DlcNode child = node.dependencies.computeIfAbsent(depKey, key -> new DlcNode());
                        stack.push(Frame.node(indent, child));
                    }
                } else if (header.equals("dependencies")) {
                    DlcNode node = findNearestNode(stack);
                    if (node != null) {
                        stack.push(Frame.relationContainer(indent, node, "dependencies"));
                    }
                } else if (header.equals("conflicts")) {
                    DlcNode node = findNearestNode(stack);
                    if (node != null) {
                        stack.push(Frame.relationContainer(indent, node, "conflicts"));
                    }
                } else if (isSectionHeader(header)) {
                    DlcNode node = findNearestNode(stack);
                    if (node != null) {
                        stack.push(Frame.section(indent, node, header));
                    }
                } else if (stack.peek().type == FrameType.RELATION_CONTAINER) {
                    header = unquoteKey(header);
                    DlcNode parent = stack.peek().node;
                    if ("dependencies".equals(stack.peek().section)) {
                        DlcNode child = parent.dependencies.computeIfAbsent(header, key -> new DlcNode());
                        stack.push(Frame.node(indent, child));
                    } else if ("conflicts".equals(stack.peek().section)) {
                        parent.conflictEntries.add(header);
                    }
                }
                index++;
                continue;
            }

            if (content.endsWith(":") && content.length() >= 2 && !content.contains("=")) {
                while (stack.size() > 1 && stack.peek().indent >= indent) {
                    stack.pop();
                }
                if (stack.peek().type == FrameType.RELATION_CONTAINER) {
                    String rawKey = content.substring(0, content.length() - 1).trim();
                    String depKey = unquoteKey(rawKey);
                    if (!depKey.isBlank()) {
                        DlcNode parent = stack.peek().node;
                        if ("dependencies".equals(stack.peek().section)) {
                            DlcNode child = parent.dependencies.computeIfAbsent(depKey, key -> new DlcNode());
                            stack.push(Frame.node(indent, child));
                        } else if ("conflicts".equals(stack.peek().section)) {
                            parent.conflictEntries.add(depKey);
                        }
                    }
                }
                index++;
                continue;
            }

            if (stack.peek().type == FrameType.RELATION_CONTAINER && !content.contains("=")) {
                String relation = stack.peek().section;
                DlcNode node = stack.peek().node;
                String token = unquoteKey(content);
                if (!token.isBlank()) {
                    if ("dependencies".equals(relation)) {
                        node.dependencyEntries.add(token);
                    } else if ("conflicts".equals(relation)) {
                        node.conflictEntries.add(token);
                    }
                }
                index++;
                continue;
            }

            int eq = content.indexOf('=');
            if (eq < 0) {
                index++;
                continue;
            }
            String key = content.substring(0, eq).trim();
            String valueText = content.substring(eq + 1).trim();
            ParseValue parsed = parseValue(lines, index, valueText);
            index = parsed.nextIndex + 1;
            assignValue(stack, key, parsed.value);
        }
        return root.toJsonObject();
    }

    public static String write(JsonObject ordered) {
        if (ordered == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(512);
        writeNode(out, ordered, 0, true);
        return out.toString();
    }

    public static boolean write(Path path, JsonObject ordered) {
        if (path == null || ordered == null) {
            return false;
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, write(ordered));
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static JsonObject readGeneric(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException ignored) {
            return null;
        }
        return readGenericLines(lines);
    }

    public static JsonElement readElement(Path path) {
        if (path == null || !Files.exists(path) || !isDcName(path.getFileName() == null ? null : path.getFileName().toString())) {
            return null;
        }
        return readGeneric(path);
    }

    public static JsonObject readObject(Path path) {
        JsonElement element = readElement(path);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return null;
    }

    public static JsonObject readGenericText(String text) {
        if (text == null) {
            return null;
        }
        String[] raw = text.split("\\R", -1);
        List<String> lines = new ArrayList<>(raw.length);
        for (String line : raw) {
            lines.add(line);
        }
        return readGenericLines(lines);
    }

    private static JsonObject readGenericLines(List<String> lines) {
        if (lines == null) {
            return null;
        }
        JsonObject root = new JsonObject();
        ArrayDeque<GenericFrame> stack = new ArrayDeque<>();
        stack.push(new GenericFrame(-1, root, null));
        int index = 0;
        while (index < lines.size()) {
            String raw = lines.get(index);
            if (raw == null) {
                index++;
                continue;
            }
            if (index == 0 && raw.startsWith("\uFEFF")) {
                raw = raw.substring(1);
            }
            String noComment = stripComment(raw);
            if (noComment.isBlank()) {
                index++;
                continue;
            }
            int indent = countLeadingSpaces(noComment);
            String content = noComment.substring(indent).trim();
            if (content.isBlank()) {
                index++;
                continue;
            }
            if (content.startsWith("[") && content.endsWith("]") && content.length() >= 3) {
                String rawHeader = content.substring(1, content.length() - 1).trim();
                String header = unquoteKey(rawHeader);
                if (!isQuotedKey(rawHeader) && header.contains(".")) {
                    JsonObject child = ensureGenericPath(root, header);
                    stack.clear();
                    stack.push(new GenericFrame(-1, root, null));
                    stack.push(new GenericFrame(indent, child, header.substring(header.lastIndexOf('.') + 1)));
                    index++;
                    continue;
                }
                while (stack.size() > 1 && stack.peek().indent >= indent) {
                    stack.pop();
                }
                JsonObject parent = stack.peek().obj;
                JsonObject child;
                JsonElement existing = parent.get(header);
                if (existing != null && existing.isJsonObject()) {
                    child = existing.getAsJsonObject();
                } else {
                    child = new JsonObject();
                    parent.add(header, child);
                }
                stack.push(new GenericFrame(indent, child, header));
                index++;
                continue;
            }
            if (isGenericRelationSection(stack.peek().name) && !content.contains("=")) {
                String token = unquoteKey(content);
                if (!token.isBlank()) {
                    stack.peek().obj.add(token, new JsonObject());
                }
                index++;
                continue;
            }
            int eq = content.indexOf('=');
            if (eq < 0) {
                index++;
                continue;
            }
            String key = unquoteKey(content.substring(0, eq).trim());
            String valueText = content.substring(eq + 1).trim();
            ParseValue parsed = parseValue(lines, index, valueText);
            index = parsed.nextIndex + 1;
            if (!key.isBlank()) {
                stack.peek().obj.add(key, parsed.value);
            }
        }
        return root;
    }

    public static String writeGeneric(JsonObject obj) {
        if (obj == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(512);
        writeGenericRoot(out, obj);
        return out.toString();
    }

    public static boolean writeGeneric(Path path, JsonObject obj) {
        if (path == null || obj == null) {
            return false;
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, writeGeneric(obj));
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void writeGenericRoot(StringBuilder out, JsonObject obj) {
        boolean wroteAny = false;
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject()) {
                continue;
            }
            out.append(formatGenericKey(entry.getKey()))
                .append(" = ")
                .append(formatGenericValue(entry.getKey(), value))
                .append('\n');
            wroteAny = true;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonObject()) {
                continue;
            }
            if (wroteAny) {
                out.append('\n');
            }
            writeGenericSection(out, entry.getKey(), value.getAsJsonObject(), 0);
            wroteAny = true;
        }
    }

    private static void writeGenericSection(StringBuilder out, String name, JsonObject obj, int sectionIndent) {
        indentLine(out, sectionIndent);
        out.append('[').append(formatGenericKey(name)).append(']').append('\n');
        boolean relationSection = isGenericRelationSection(name);
        boolean hasNestedSections = hasNestedGenericSections(obj);
        int bodyIndent = (sectionIndent > 0 || hasNestedSections) ? sectionIndent + 2 : 0;
        boolean wroteAny = false;
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject()) {
                continue;
            }
            indentLine(out, bodyIndent);
            if (relationSection && isGenericRelationEntry(entry.getKey(), value)) {
                out.append(formatStringValue(entry.getKey())).append('\n');
            } else {
                out.append(formatGenericKey(entry.getKey()))
                    .append(" = ")
                    .append(formatGenericValue(entry.getKey(), value))
                    .append('\n');
            }
            wroteAny = true;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonObject()) {
                continue;
            }
            if (relationSection && value.getAsJsonObject().entrySet().isEmpty()) {
                indentLine(out, bodyIndent);
                out.append(formatStringValue(entry.getKey())).append('\n');
                wroteAny = true;
                continue;
            }
            if (wroteAny) {
                out.append('\n');
            }
            writeGenericSection(out, entry.getKey(), value.getAsJsonObject(), sectionIndent + 2);
            wroteAny = true;
        }
    }

    private static boolean hasNestedGenericSections(JsonObject obj) {
        if (obj == null || obj.entrySet().isEmpty()) {
            return false;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGenericRelationSection(String name) {
        return "dependencies".equalsIgnoreCase(name) || "conflicts".equalsIgnoreCase(name);
    }

    private static boolean isGenericRelationEntry(String key, JsonElement value) {
        if (key == null || key.isBlank() || value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        return primitive.isString() && primitive.getAsString().trim().equals(key.trim());
    }

    private static JsonObject ensureGenericPath(JsonObject root, String header) {
        JsonObject current = root;
        for (String rawPart : header.split("\\.")) {
            String part = rawPart == null ? "" : rawPart.trim();
            if (part.isBlank()) {
                continue;
            }
            JsonElement existing = current.get(part);
            if (existing != null && existing.isJsonObject()) {
                current = existing.getAsJsonObject();
            } else {
                JsonObject child = new JsonObject();
                current.add(part, child);
                current = child;
            }
        }
        return current;
    }

    private static boolean isQuotedKey(String text) {
        if (text == null) {
            return false;
        }
        String key = text.trim();
        return key.length() >= 2
            && ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'")));
    }

    private static String formatGenericKey(String key) {
        if (key == null) {
            return "\"\"";
        }
        if (key.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
            return key;
        }
        return "\"" + escapeString(key) + "\"";
    }

    private static String formatGenericValue(String key, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean() ? "true" : "false";
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber().toString();
            }
            return formatStringValue(primitive.getAsString());
        }
        if (value.isJsonArray() && isVerticalArrayKey(key)) {
            return formatValue(value);
        }
        return COMPACT_GSON.toJson(value);
    }

    private static boolean isVerticalArrayKey(String key) {
        if (key == null) {
            return false;
        }
        return "enable".equalsIgnoreCase(key)
            || "conflicts".equalsIgnoreCase(key);
    }

    private static void writeNode(StringBuilder out, JsonObject node, int indent, boolean root) {
        JsonElement depsElement = node.get("dependencies");
        java.util.List<String> dependencyEntries = collectRelationEntries(depsElement);
        boolean hasNestedDependencies = depsElement != null
            && depsElement.isJsonObject()
            && !depsElement.getAsJsonObject().entrySet().isEmpty();
        java.util.List<String> conflictEntries = collectRelationEntries(node.get("conflicts"));
        boolean hasConflicts = !conflictEntries.isEmpty();
        boolean hasDownloadSection = hasDownloadSection(node);
        if (hasDownloadSection) {
            if (!root) {
                indentLine(out, indent);
                out.append("[download]\n");
            } else {
                out.append("[download]\n");
            }
            writeDownload(out, node, indent);
        }

        if (hasDownloadSection) {
            indentLine(out, indent);
            out.append("\n");
        }
        indentLine(out, indent);
        out.append("[basic]\n");
        writeBasic(out, node, indent);

        if (!dependencyEntries.isEmpty() || hasNestedDependencies) {
            indentLine(out, indent);
            out.append("\n");
            indentLine(out, indent);
            out.append("[dependencies]\n");
            for (String entry : dependencyEntries) {
                indentLine(out, indent);
                out.append(formatStringValue(entry)).append('\n');
            }
            if (depsElement != null && depsElement.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : depsElement.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    indentLine(out, indent);
                    out.append("[dependencies.").append(entry.getKey()).append("]\n");
                    writeNode(out, entry.getValue().getAsJsonObject(), indent + 2, false);
                }
            }
        }

        if (hasConflicts) {
            indentLine(out, indent);
            out.append("\n");
            indentLine(out, indent);
            out.append("[conflicts]\n");
            for (String entry : conflictEntries) {
                indentLine(out, indent);
                out.append(formatStringValue(entry)).append('\n');
            }
        }

        indentLine(out, indent);
        out.append("\n");
        indentLine(out, indent);
        out.append("[DisplayName]\n");
        JsonObject display = asObject(node.get("DisplayName"));
        for (Map.Entry<String, JsonElement> entry : display.entrySet()) {
            out.append(formatKeyValue(entry.getKey(), entry.getValue())).append('\n');
        }

        indentLine(out, indent);
        out.append("\n");
        indentLine(out, indent);
        out.append("[tip]\n");
        JsonObject tip = asObject(node.get("tip"));
        for (Map.Entry<String, JsonElement> entry : tip.entrySet()) {
            out.append(formatKeyValue(entry.getKey(), entry.getValue())).append('\n');
        }
    }

    private static void writeDownload(StringBuilder out, JsonObject node, int indent) {
        String[] keys = new String[] {
            "modrinth_projectid",
            "modrinth_versionid",
            "curseforge_projectid",
            "curseforge_fileid",
            "directUrl",
            "priority"
        };
        for (String key : keys) {
            JsonElement value = node.get(key);
            if (value == null) {
                continue;
            }
            indentLine(out, indent);
            out.append(formatKeyValue(key, value)).append('\n');
        }
    }

    private static boolean hasDownloadSection(JsonObject node) {
        if (node == null) {
            return false;
        }
        return hasMeaningfulDownloadValue(node.get("modrinth_projectid"))
            || hasMeaningfulDownloadValue(node.get("modrinth_versionid"))
            || hasMeaningfulDownloadValue(node.get("curseforge_projectid"))
            || hasMeaningfulDownloadValue(node.get("curseforge_fileid"))
            || hasMeaningfulDownloadValue(node.get("directUrl"))
            || hasMeaningfulDownloadValue(node.get("priority"));
    }

    private static boolean hasMeaningfulDownloadValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isString()) {
                String text = primitive.getAsString();
                return text != null && !text.isBlank();
            }
            return true;
        }
        if (value.isJsonArray()) {
            return !value.getAsJsonArray().isEmpty();
        }
        if (value.isJsonObject()) {
            return !value.getAsJsonObject().entrySet().isEmpty();
        }
        return false;
    }

    private static void writeBasic(StringBuilder out, JsonObject node, int indent) {
        String[] keys = new String[] {
            "identifier",
            "type",
            "file_name",
            "appliedTarget",
            "isHidden",
            "AllowEnableByAll",
            "alarm"
        };
        for (String key : keys) {
            if (!node.has(key)) {
                continue;
            }
            indentLine(out, indent);
            out.append(formatKeyValue(key, node.get(key))).append('\n');
        }

        for (Map.Entry<String, JsonElement> entry : node.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            if ("dependencies".equals(key)
                || "conflicts".equals(key)
                || "DisplayName".equals(key)
                || "tip".equals(key)
                || isKnownBasicKey(key)
                || isDownloadKey(key)) {
                continue;
            }
            indentLine(out, indent);
            out.append(formatKeyValue(key, entry.getValue())).append('\n');
        }

    }

    private static boolean isKnownBasicKey(String key) {
        return "identifier".equals(key)
            || "type".equals(key)
            || "file_name".equals(key)
            || "appliedTarget".equals(key)
            || "isHidden".equals(key)
            || "AllowEnableByAll".equals(key)
            || "alarm".equals(key);
    }

    private static boolean isDownloadKey(String key) {
        return "modrinth_projectid".equals(key)
            || "modrinth_versionid".equals(key)
            || "curseforge_projectid".equals(key)
            || "curseforge_fileid".equals(key)
            || "directUrl".equals(key)
            || "priority".equals(key);
    }

    private static java.util.List<String> collectRelationEntries(JsonElement element) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        collectRelationEntries(element, names);
        return new java.util.ArrayList<>(names);
    }

    private static void collectRelationEntries(JsonElement element, java.util.Set<String> names) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive() || element.isJsonArray()) {
            collectPrimitiveNames(element, names);
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                if (key != null && !key.isBlank()) {
                    names.add(key.trim());
                }
                continue;
            }
            if (value.isJsonPrimitive() || value.isJsonArray()) {
                collectPrimitiveNames(value, names);
                continue;
            }
        }
    }

    private static void collectPrimitiveNames(JsonElement element, java.util.Set<String> names) {
        if (element == null || names == null) {
            return;
        }
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (raw == null) {
                return;
            }
            for (String part : raw.split("[,\\n\\r]+")) {
                String trimmed = part == null ? "" : part.trim();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectPrimitiveNames(child, names);
            }
        }
    }

    private static String formatKeyValue(String key, JsonElement value) {
        return key + " = " + formatValue(value);
    }

    private static String formatValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "\"\"";
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean() ? "true" : "false";
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber().toString();
            }
            return formatStringValue(primitive.getAsString());
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.isEmpty()) {
                return "[]";
            }
            StringBuilder out = new StringBuilder();
            out.append("[\n");
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                out.append("  ").append(formatValue(element));
                if (i + 1 < array.size()) {
                    out.append(',');
                }
                out.append('\n');
            }
            out.append("]");
            return out.toString();
        }
        return "{}";
    }

    private static String formatStringValue(String value) {
        if (value == null) {
            return "\"\"";
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
            String delimiter = "\"\"\"";
            if (normalized.contains(delimiter)) {
                if (!normalized.contains("'''")) {
                    delimiter = "'''";
                } else {
                    return "\"" + escapeString(value) + "\"";
                }
            }
            return delimiter + normalized + delimiter;
        }
        return "\"" + escapeString(value) + "\"";
    }

    private static String escapeString(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    private static void assignValue(ArrayDeque<Frame> stack, String key, JsonElement value) {
        DlcNode node = findNearestNode(stack);
        if (node == null || key == null || key.isBlank()) {
            return;
        }
        Frame section = findNearestSection(stack);
        String sectionName = section == null ? "basic" : section.section;
        switch (sectionName) {
            case "download" -> node.download.add(key, value);
            case "DisplayName" -> node.displayName.add(key, value);
            case "tip" -> node.tip.add(key, value);
            case "dependencies" -> collectPrimitiveNames(value, node.dependencyEntries);
            case "conflicts" -> collectPrimitiveNames(value, node.conflictEntries);
            case "basic" -> node.basic.add(key, value);
            default -> node.basic.add(key, value);
        }
    }

    private static boolean isSectionHeader(String header) {
        return "download".equals(header)
            || "basic".equals(header)
            || "DisplayName".equals(header)
            || "tip".equals(header);
    }

    private static DlcNode findNearestNode(ArrayDeque<Frame> stack) {
        for (Frame frame : stack) {
            if (frame.type == FrameType.NODE || frame.type == FrameType.ROOT) {
                return frame.node;
            }
        }
        return null;
    }

    private static Frame findNearestSection(ArrayDeque<Frame> stack) {
        for (Frame frame : stack) {
            if (frame.type == FrameType.SECTION) {
                return frame;
            }
        }
        return null;
    }

    private static ParseValue parseValue(List<String> lines, int startIndex, String first) {
        String value = first == null ? "" : first.trim();
        int index = startIndex;
        if (value.startsWith("\"\"\"") || value.startsWith("'''")) {
            String delimiter = value.startsWith("\"\"\"") ? "\"\"\"" : "'''";
            StringBuilder text = new StringBuilder();
            String remaining = value.substring(3);
            while (true) {
                int end = remaining.indexOf(delimiter);
                if (end >= 0) {
                    text.append(remaining, 0, end);
                    return new ParseValue(new JsonPrimitive(text.toString()), index);
                }
                text.append(remaining);
                if (index + 1 >= lines.size()) {
                    return new ParseValue(new JsonPrimitive(text.toString()), index);
                }
                text.append('\n');
                index++;
                remaining = lines.get(index);
            }
        }
        if (value.startsWith("[") || value.startsWith("{")) {
            StringBuilder raw = new StringBuilder(value);
            int delta = containerDelta(value);
            while (delta > 0 && index + 1 < lines.size()) {
                index++;
                String next = stripComment(lines.get(index)).trim();
                if (next.isEmpty()) {
                    continue;
                }
                raw.append('\n').append(next);
                delta += containerDelta(next);
            }
            return new ParseValue(parseScalar(raw.toString().trim()), index);
        }
        return new ParseValue(parseScalar(value), index);
    }

    private static int containerDelta(String text) {
        int delta = 0;
        boolean inString = false;
        char quote = 0;
        boolean escaping = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (c == '\\') {
                    escaping = true;
                    continue;
                }
                if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                continue;
            }
            if (c == '[' || c == '{') {
                delta++;
            } else if (c == ']' || c == '}') {
                delta--;
            }
        }
        return delta;
    }

    private static JsonElement parseScalar(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return new JsonPrimitive("");
        }
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return new JsonPrimitive(unescapeQuoted(value.substring(1, value.length() - 1)));
        }
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
            return new JsonPrimitive(value.substring(1, value.length() - 1));
        }
        if ("true".equalsIgnoreCase(value)) {
            return new JsonPrimitive(true);
        }
        if ("false".equalsIgnoreCase(value)) {
            return new JsonPrimitive(false);
        }
        if ("{}".equals(value)) {
            return new JsonObject();
        }
        if ("[]".equals(value)) {
            return new JsonArray();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            return parseArray(value.substring(1, value.length() - 1));
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            try {
                return COMPACT_GSON.fromJson(value, JsonObject.class);
            } catch (Exception ignored) {
            }
        }
        try {
            if (value.contains(".")) {
                return new JsonPrimitive(Double.parseDouble(value));
            }
            return new JsonPrimitive(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
        }
        return new JsonPrimitive(value);
    }

    private static JsonArray parseArray(String inner) {
        JsonArray result = new JsonArray();
        List<String> tokens = splitArrayTokens(inner);
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            result.add(parseScalar(trimmed));
        }
        return result;
    }

    private static List<String> splitArrayTokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        char quote = 0;
        boolean escaping = false;
        int bracketDepth = 0;
        int braceDepth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                current.append(c);
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (c == '\\') {
                    escaping = true;
                    continue;
                }
                if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                current.append(c);
                continue;
            }
            if (c == '[') {
                bracketDepth++;
                current.append(c);
                continue;
            }
            if (c == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
                current.append(c);
                continue;
            }
            if (c == '{') {
                braceDepth++;
                current.append(c);
                continue;
            }
            if (c == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
                current.append(c);
                continue;
            }
            if (bracketDepth == 0 && braceDepth == 0 && (c == ',' || c == '\n' || c == '\r')) {
                String token = current.toString().trim();
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String token = current.toString().trim();
        if (!token.isEmpty()) {
            tokens.add(token);
        }
        return tokens;
    }

    private static String unescapeQuoted(String text) {
        return text
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    private static String stripComment(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(line.length());
        boolean inString = false;
        char quote = 0;
        boolean escaping = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inString) {
                out.append(c);
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (c == '\\') {
                    escaping = true;
                    continue;
                }
                if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                out.append(c);
                continue;
            }
            if (c == '#') {
                break;
            }
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                break;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static int countLeadingSpaces(String text) {
        int count = 0;
        while (count < text.length() && text.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static void indentLine(StringBuilder out, int indent) {
        for (int i = 0; i < indent; i++) {
            out.append(' ');
        }
    }

    private static JsonObject asObject(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return new JsonObject();
    }

    private static String unquoteKey(String text) {
        if (text == null) {
            return "";
        }
        String key = text.trim();
        if (isQuotedKey(key)) {
            return unescapeQuoted(key.substring(1, key.length() - 1));
        }
        return key;
    }

    private static final class ParseValue {
        final JsonElement value;
        final int nextIndex;

        private ParseValue(JsonElement value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }

    private static final class GenericFrame {
        final int indent;
        final JsonObject obj;
        final String name;

        private GenericFrame(int indent, JsonObject obj, String name) {
            this.indent = indent;
            this.obj = obj;
            this.name = name;
        }
    }

    private enum FrameType {
        ROOT,
        NODE,
        RELATION_CONTAINER,
        SECTION
    }

    private static final class Frame {
        final int indent;
        final FrameType type;
        final DlcNode node;
        final String section;

        private Frame(int indent, FrameType type, DlcNode node, String section) {
            this.indent = indent;
            this.type = type;
            this.node = node;
            this.section = section;
        }

        static Frame root(DlcNode node) {
            return new Frame(-1, FrameType.ROOT, node, null);
        }

        static Frame node(int indent, DlcNode node) {
            return new Frame(indent, FrameType.NODE, node, null);
        }

        static Frame relationContainer(int indent, DlcNode node, String section) {
            return new Frame(indent, FrameType.RELATION_CONTAINER, node, section);
        }

        static Frame section(int indent, DlcNode node, String section) {
            return new Frame(indent, FrameType.SECTION, node, section);
        }
    }

    private static final class DlcNode {
        final JsonObject download = new JsonObject();
        final JsonObject basic = new JsonObject();
        final JsonObject displayName = new JsonObject();
        final JsonObject tip = new JsonObject();
        final Set<String> dependencyEntries = new java.util.LinkedHashSet<>();
        final Set<String> conflictEntries = new java.util.LinkedHashSet<>();
        final Map<String, DlcNode> dependencies = new LinkedHashMap<>();

        JsonObject toJsonObject() {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : download.entrySet()) {
                out.add(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, JsonElement> entry : basic.entrySet()) {
                out.add(entry.getKey(), entry.getValue());
            }
            JsonElement basicDeps = out.get("dependencies");
            if (basicDeps != null && !basicDeps.isJsonNull()) {
                collectPrimitiveNames(basicDeps, dependencyEntries);
                out.remove("dependencies");
            }
            JsonElement basicConflicts = out.get("conflicts");
            if (basicConflicts != null && !basicConflicts.isJsonNull()) {
                collectPrimitiveNames(basicConflicts, conflictEntries);
                out.remove("conflicts");
            }
            if (!dependencyEntries.isEmpty() || !dependencies.isEmpty()) {
                JsonObject deps = new JsonObject();
                for (String name : dependencyEntries) {
                    if (name != null && !name.isBlank()) {
                        deps.add(name, new JsonPrimitive(name));
                    }
                }
                for (Map.Entry<String, DlcNode> entry : dependencies.entrySet()) {
                    deps.add(entry.getKey(), entry.getValue().toJsonObject());
                }
                out.add("dependencies", deps);
            } else if (!out.has("dependencies")) {
                out.addProperty("dependencies", "");
            }
            if (!conflictEntries.isEmpty()) {
                JsonObject conflicts = new JsonObject();
                for (String name : conflictEntries) {
                    if (name != null && !name.isBlank()) {
                        conflicts.add(name, new JsonPrimitive(name));
                    }
                }
                out.add("conflicts", conflicts);
            } else if (!out.has("conflicts")) {
                out.addProperty("conflicts", "");
            }
            if (!displayName.entrySet().isEmpty()) {
                out.add("DisplayName", displayName);
            } else if (!out.has("DisplayName")) {
                out.add("DisplayName", new JsonObject());
            }
            if (!tip.entrySet().isEmpty()) {
                out.add("tip", tip);
            } else if (!out.has("tip")) {
                out.add("tip", new JsonObject());
            }
            return out;
        }
    }
}
