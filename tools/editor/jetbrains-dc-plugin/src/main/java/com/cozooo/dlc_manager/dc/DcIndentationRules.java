package com.cozooo.dlc_manager.dc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class DcIndentationRules {
    private static final Pattern KEY_VALUE_LINE = Pattern.compile("^(?:\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_.-]*)\\s*=\\s*.*$");
    private static final Pattern MAP_ENTRY_LINE = Pattern.compile("^(?:\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_.-]*)\\s*:\\s*$");
    private static final Pattern SECTION_LINE = Pattern.compile("^\\[[^\\]]+\\]$");
    private static final Pattern COMMENT_LINE = Pattern.compile("^(#|//).*");
    private static final Pattern LIST_ITEM_QUOTED = Pattern.compile("^\"[^\"]*\"\\s*,?$");
    private static final Pattern LIST_ITEM_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]*\\s*,?$");

    private DcIndentationRules() {
    }

    static List<IndentationIssue> collectIssues(String[] lines) {
        List<LineState> states = buildLineStates(lines);
        List<IndentationIssue> issues = new ArrayList<>();
        issues.addAll(collectUpdateIndentationIssues(states));
        issues.addAll(collectDependenciesIndentationIssues(states));
        return issues;
    }

    private static List<LineState> buildLineStates(String[] lines) {
        List<LineState> out = new ArrayList<>(lines.length);
        boolean tripleQuoteOpen = false;
        for (int i = 0; i < lines.length; i++) {
            String text = lines[i] == null ? "" : lines[i];
            String trimmed = text.trim();
            int tripleMatches = countOccurrences(text, "\"\"\"");
            boolean wasTripleOpen = tripleQuoteOpen;
            if (tripleMatches > 0 && (tripleMatches % 2 == 1)) {
                tripleQuoteOpen = !tripleQuoteOpen;
            }
            boolean inTriple = wasTripleOpen || tripleMatches > 0;
            boolean ignored =
                inTriple ||
                trimmed.isEmpty() ||
                COMMENT_LINE.matcher(trimmed).matches();
            out.add(new LineState(i, text, trimmed, leadingWhitespace(text), ignored));
        }
        return out;
    }

    private static List<IndentationIssue> collectUpdateIndentationIssues(List<LineState> states) {
        List<IndentationIssue> issues = new ArrayList<>();

        List<String> rootHeaders = List.of("version", "changelog", "file", "update");
        List<String> fileSubHeaders = List.of("del", "add", "move", "modify");
        List<String> updateSubHeaders = List.of("remote", "required");

        String root = null;
        String sub = null;
        boolean inMapEntry = false;

        boolean hasUpdateShape = false;
        for (LineState s : states) {
            if (s.ignored) {
                continue;
            }
            String sec = sectionName(s.trimmed);
            if ("file".equals(sec) || "update".equals(sec)) {
                hasUpdateShape = true;
                break;
            }
        }
        if (!hasUpdateShape) {
            return issues;
        }

        for (LineState s : states) {
            if (s.ignored) {
                continue;
            }

            String sec = sectionName(s.trimmed);
            if (sec != null) {
                if (rootHeaders.contains(sec)) {
                    pushIndentIssue(issues, s.line, s.indent, 0, "update");
                    root = sec;
                    sub = null;
                    inMapEntry = false;
                    continue;
                }
                if ("file".equals(root) && fileSubHeaders.contains(sec)) {
                    pushIndentIssue(issues, s.line, s.indent, 2, "update");
                    sub = sec;
                    inMapEntry = false;
                    continue;
                }
                if ("update".equals(root) && updateSubHeaders.contains(sec)) {
                    pushIndentIssue(issues, s.line, s.indent, 2, "update");
                    sub = sec;
                    inMapEntry = false;
                    continue;
                }
                inMapEntry = false;
                continue;
            }

            if (root == null) {
                continue;
            }

            if ("version".equals(root) || "changelog".equals(root)) {
                if (isKeyValueLine(s.trimmed)) {
                    pushIndentIssue(issues, s.line, s.indent, 0, "update");
                }
                continue;
            }

            if ("file".equals(root)) {
                if ("del".equals(sub)) {
                    if (looksLikeListItem(s.trimmed) || isKeyValueLine(s.trimmed)) {
                        pushIndentIssue(issues, s.line, s.indent, 4, "update");
                    }
                    inMapEntry = false;
                    continue;
                }
                if ("move".equals(sub)) {
                    if (isKeyValueLine(s.trimmed)) {
                        pushIndentIssue(issues, s.line, s.indent, 4, "update");
                    }
                    inMapEntry = false;
                    continue;
                }
                if ("add".equals(sub) || "modify".equals(sub)) {
                    if (isMapEntryLine(s.trimmed)) {
                        pushIndentIssue(issues, s.line, s.indent, 4, "update");
                        inMapEntry = true;
                        continue;
                    }
                    if (isKeyValueLine(s.trimmed)) {
                        pushIndentIssue(issues, s.line, s.indent, inMapEntry ? 6 : 4, "update");
                        continue;
                    }
                    inMapEntry = false;
                    continue;
                }
                inMapEntry = false;
                continue;
            }

            if ("update".equals(root) && ("remote".equals(sub) || "required".equals(sub))) {
                if (isMapEntryLine(s.trimmed)) {
                    pushIndentIssue(issues, s.line, s.indent, 4, "update");
                    inMapEntry = true;
                    continue;
                }
                if (isKeyValueLine(s.trimmed)) {
                    pushIndentIssue(issues, s.line, s.indent, inMapEntry ? 6 : 4, "update");
                    continue;
                }
                inMapEntry = false;
            }
        }

        return issues;
    }

    private static List<IndentationIssue> collectDependenciesIndentationIssues(List<LineState> states) {
        List<IndentationIssue> issues = new ArrayList<>();
        List<String> nodeSections = List.of(
            "download",
            "basic",
            "displayname",
            "tip",
            "notice",
            "contents",
            "contenturl"
        );

        boolean hasDependencies = false;
        for (LineState s : states) {
            if (!s.ignored && "dependencies".equals(sectionName(s.trimmed))) {
                hasDependencies = true;
                break;
            }
        }
        if (!hasDependencies) {
            return issues;
        }

        List<StackNode> stack = new ArrayList<>();

        for (LineState s : states) {
            if (s.ignored) {
                continue;
            }

            String secRaw = sectionNameRaw(s.trimmed);
            String sec = secRaw == null ? null : secRaw.toLowerCase();
            boolean depEntry = isMapEntryLine(s.trimmed);

            if (sec == null && !depEntry) {
                continue;
            }

            while (!stack.isEmpty() && s.indent <= stack.get(stack.size() - 1).indent) {
                stack.remove(stack.size() - 1);
            }

            if ("dependencies".equals(sec)) {
                int expected =
                    !stack.isEmpty() && "depNode".equals(stack.get(stack.size() - 1).type)
                        ? stack.get(stack.size() - 1).indent + 2
                        : 0;
                pushIndentIssue(issues, s.line, s.indent, expected, "dependencies");
                stack.add(new StackNode("depContainer", expected));
                continue;
            }

            if (depEntry && !stack.isEmpty() && "depContainer".equals(stack.get(stack.size() - 1).type)) {
                int expected = stack.get(stack.size() - 1).indent + 2;
                pushIndentIssue(issues, s.line, s.indent, expected, "dependencies");
                stack.add(new StackNode("depNode", expected));
                continue;
            }

            if (sec != null &&
                !stack.isEmpty() &&
                "depContainer".equals(stack.get(stack.size() - 1).type) &&
                !nodeSections.contains(sec)) {
                int expected = stack.get(stack.size() - 1).indent + 2;
                pushIndentIssue(issues, s.line, s.indent, expected, "dependencies");
                stack.add(new StackNode("depNode", expected));
                continue;
            }

            if (sec != null &&
                !stack.isEmpty() &&
                "depNode".equals(stack.get(stack.size() - 1).type) &&
                nodeSections.contains(sec)) {
                int expected = stack.get(stack.size() - 1).indent + 2;
                pushIndentIssue(issues, s.line, s.indent, expected, "dependencies");
            }
        }

        return issues;
    }

    private static void pushIndentIssue(List<IndentationIssue> items, int line, int actual, int expected, String scope) {
        if (actual == expected) {
            return;
        }
        String label = "update".equals(scope) ? "热更新结构" : "dependencies结构";
        items.add(new IndentationIssue(
            line,
            expected,
            label + "缩进不正确：期望 " + expected + " 个空格，当前为 " + actual + "。"
        ));
    }

    private static boolean isKeyValueLine(String trimmed) {
        return KEY_VALUE_LINE.matcher(trimmed).matches();
    }

    private static boolean isMapEntryLine(String trimmed) {
        return MAP_ENTRY_LINE.matcher(trimmed).matches();
    }

    private static boolean looksLikeListItem(String trimmed) {
        return LIST_ITEM_QUOTED.matcher(trimmed).matches() || LIST_ITEM_IDENTIFIER.matcher(trimmed).matches();
    }

    private static String sectionNameRaw(String trimmed) {
        if (trimmed == null || !SECTION_LINE.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed.substring(1, trimmed.length() - 1).trim();
    }

    private static String sectionName(String trimmed) {
        String raw = sectionNameRaw(trimmed);
        return raw == null ? null : raw.toLowerCase();
    }

    private static int leadingWhitespace(String line) {
        int idx = 0;
        while (idx < line.length() && Character.isWhitespace(line.charAt(idx))) {
            idx++;
        }
        return idx;
    }

    private static int countOccurrences(String text, String token) {
        if (text == null || text.isEmpty() || token == null || token.isEmpty()) {
            return 0;
        }
        int idx = 0;
        int count = 0;
        while (idx < text.length()) {
            int found = text.indexOf(token, idx);
            if (found < 0) {
                break;
            }
            count++;
            idx = found + token.length();
        }
        return count;
    }

    static final class IndentationIssue {
        final int line;
        final int expectedIndent;
        final String message;

        IndentationIssue(int line, int expectedIndent, String message) {
            this.line = line;
            this.expectedIndent = expectedIndent;
            this.message = message;
        }
    }

    private static final class LineState {
        final int line;
        final String trimmed;
        final int indent;
        final boolean ignored;

        LineState(int line, String text, String trimmed, int indent, boolean ignored) {
            this.line = line;
            this.trimmed = trimmed;
            this.indent = indent;
            this.ignored = ignored;
        }
    }

    private static final class StackNode {
        final String type;
        final int indent;

        StackNode(String type, int indent) {
            this.type = type;
            this.indent = indent;
        }
    }
}
