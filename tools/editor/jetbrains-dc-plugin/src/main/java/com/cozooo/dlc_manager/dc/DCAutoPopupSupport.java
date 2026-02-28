package com.cozooo.dlc_manager.dc;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DCAutoPopupSupport {
    private static final Pattern COLOR_CONTEXT_PATTERN = Pattern.compile("(&|&#[0-9a-fA-F]{0,6})$");
    private static final Pattern KEY_EQUALS_CONTEXT_PATTERN = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*=\\s*$");
    private static final Map<String, List<String>> SECTION_KEYS = DCCompletionContributor.sectionKeys();

    private DCAutoPopupSupport() {}

    public static boolean shouldAutoPopup(PsiFile file, Editor editor) {
        PrefixState state = readPrefixState(file, editor);
        if (state == null) {
            return false;
        }
        return shouldAutoPopupForPrefix(state.fullText, state.line, state.prefix);
    }

    public static boolean shouldAutoPopupAfterTyped(PsiFile file, Editor editor, char charTyped) {
        PrefixState state = readPrefixState(file, editor);
        if (state == null) {
            return false;
        }
        String projectedPrefix = state.prefix + charTyped;
        return shouldAutoPopupForPrefix(state.fullText, state.line, projectedPrefix);
    }

    public static boolean isColorContext(PsiFile file, Editor editor) {
        PrefixState state = readPrefixState(file, editor);
        return state != null && isColorContextPrefix(state.prefix);
    }

    public static boolean isColorContextAfterTyped(PsiFile file, Editor editor, char charTyped) {
        PrefixState state = readPrefixState(file, editor);
        return state != null && isColorContextPrefix(state.prefix + charTyped);
    }

    private static boolean shouldAutoPopupForPrefix(String fullText, int line, String prefix) {
        if (prefix == null) {
            return false;
        }
        if (isColorContextPrefix(prefix)) {
            return true;
        }

        if (KEY_EQUALS_CONTEXT_PATTERN.matcher(prefix).matches()) {
            return true;
        }

        String trimmed = prefix.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("[")) {
            return true;
        }

        if (prefix.contains("=") || prefix.contains(":")) {
            return false;
        }

        if (!prefix.matches("^\\s*[A-Za-z_][A-Za-z0-9_.-]*\\s*$")) {
            return false;
        }

        String section = currentSection(fullText, line);
        List<String> keys = SECTION_KEYS.getOrDefault(section, List.of());
        return !keys.isEmpty();
    }

    private static PrefixState readPrefixState(PsiFile file, Editor editor) {
        if (file == null || editor == null || !file.getLanguage().isKindOf(DCLanguage.INSTANCE)) {
            return null;
        }
        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        int line = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(line);
        String prefix = document.getText(new TextRange(lineStart, offset));
        String fullText = document.getText();
        return new PrefixState(fullText, line, prefix);
    }

    private static boolean isColorContextPrefix(String prefix) {
        return COLOR_CONTEXT_PATTERN.matcher(prefix).find();
    }

    private static String currentSection(String fullText, int lineNumber) {
        String[] lines = fullText.split("\\R", -1);
        int idx = Math.min(lineNumber, lines.length - 1);
        for (int i = idx; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("[") && line.endsWith("]") && line.length() > 2) {
                return line.substring(1, line.length() - 1);
            }
        }
        return "basic";
    }

    private record PrefixState(String fullText, int line, String prefix) {
    }
}
