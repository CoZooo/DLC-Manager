package com.cozooo.dlc_manager.client.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class DlcUpdateDcParser {
    private static final Gson GSON = new GsonBuilder().create();

    private DlcUpdateDcParser() {
    }

    static JsonObject parse(String text) {
        if (text == null) {
            return null;
        }
        String[] raw = text.split("\\R", -1);
        List<String> lines = new ArrayList<>(raw.length);
        for (String line : raw) {
            lines.add(line);
        }

        JsonObject root = new JsonObject();
        ArrayDeque<Frame> stack = new ArrayDeque<>();
        stack.push(Frame.root(root));

        int index = 0;
        while (index < lines.size()) {
            String rawLine = lines.get(index);
            if (rawLine == null) {
                index++;
                continue;
            }
            if (index == 0 && rawLine.startsWith("\uFEFF")) {
                rawLine = rawLine.substring(1);
            }
            String noComment = stripComment(rawLine);
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

            while (stack.size() > 1 && indent <= stack.peek().indent) {
                stack.pop();
            }
            Frame current = stack.peek();
            if (current == null) {
                index++;
                continue;
            }

            if (content.startsWith("[") && content.endsWith("]") && content.length() >= 3) {
                String header = content.substring(1, content.length() - 1).trim();
                if (header.isBlank() || current.object == null) {
                    index++;
                    continue;
                }
                if ("del".equalsIgnoreCase(header)) {
                    JsonArray arr = getOrCreateArray(current.object, header);
                    stack.push(Frame.array(indent, arr));
                } else {
                    JsonObject child = getOrCreateObject(current.object, header);
                    stack.push(Frame.object(indent, child, current.object, header));
                }
                index++;
                continue;
            }

            if (current.array != null) {
                String item = content;
                if (item.endsWith(",")) {
                    item = item.substring(0, item.length() - 1).trim();
                }
                if (!item.isBlank()) {
                    ParseValue value = parseValue(lines, index, item);
                    current.array.add(value.value);
                    index = value.nextIndex + 1;
                } else {
                    index++;
                }
                continue;
            }

            if (content.endsWith(":") && !content.contains("=") && current.object != null) {
                String key = unquoteKey(content.substring(0, content.length() - 1).trim());
                if (!key.isBlank()) {
                    JsonObject child = getOrCreateObject(current.object, key);
                    stack.push(Frame.object(indent, child, current.object, key));
                }
                index++;
                continue;
            }

            int eq = content.indexOf('=');
            if (eq >= 0 && current.object != null) {
                String key = unquoteKey(content.substring(0, eq).trim());
                if (!key.isBlank()) {
                    String valueText = content.substring(eq + 1).trim();
                    ParseValue value = parseValue(lines, index, valueText);
                    current.object.add(key, value.value);
                    index = value.nextIndex + 1;
                    continue;
                }
            }

            index++;
        }

        return root;
    }

    private static JsonObject getOrCreateObject(JsonObject parent, String key) {
        JsonElement existing = parent.get(key);
        if (existing != null && existing.isJsonObject()) {
            return existing.getAsJsonObject();
        }
        JsonObject child = new JsonObject();
        parent.add(key, child);
        return child;
    }

    private static JsonArray getOrCreateArray(JsonObject parent, String key) {
        JsonElement existing = parent.get(key);
        if (existing != null && existing.isJsonArray()) {
            return existing.getAsJsonArray();
        }
        JsonArray arr = new JsonArray();
        parent.add(key, arr);
        return arr;
    }

    private static String unquoteKey(String key) {
        if (key == null) {
            return "";
        }
        String trimmed = key.trim();
        if (trimmed.length() >= 2) {
            if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
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
        if ("null".equalsIgnoreCase(value)) {
            return JsonNull.INSTANCE;
        }
        if ("{}".equals(value)) {
            return new JsonObject();
        }
        if ("[]".equals(value)) {
            return new JsonArray();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            try {
                JsonElement parsed = GSON.fromJson(value, JsonElement.class);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            try {
                JsonElement parsed = GSON.fromJson(value, JsonElement.class);
                if (parsed != null) {
                    return parsed;
                }
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

    private static final class ParseValue {
        final JsonElement value;
        final int nextIndex;

        private ParseValue(JsonElement value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }

    private static final class Frame {
        final int indent;
        final JsonObject object;
        final JsonArray array;
        final JsonObject parentObject;
        final String keyInParent;

        private Frame(int indent, JsonObject object, JsonArray array, JsonObject parentObject, String keyInParent) {
            this.indent = indent;
            this.object = object;
            this.array = array;
            this.parentObject = parentObject;
            this.keyInParent = keyInParent;
        }

        static Frame root(JsonObject root) {
            return new Frame(-1, root, null, null, null);
        }

        static Frame object(int indent, JsonObject object, JsonObject parent, String keyInParent) {
            return new Frame(indent, object, null, parent, keyInParent);
        }

        static Frame array(int indent, JsonArray array) {
            return new Frame(indent, null, array, null, null);
        }
    }
}
