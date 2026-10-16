package com.cozooo.dlc_manager.dc;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.util.regex.Pattern;

public final class DCAutoPopupSupport {
    private static final Pattern COLOR_CONTEXT_PATTERN = Pattern.compile("(&|&#[0-9a-fA-F]{0,6})$");
    private DCAutoPopupSupport() { }
    public static boolean shouldAutoPopup(PsiFile file, Editor editor) { String prefix = prefix(file, editor); return prefix != null && matches(prefix); }
    public static boolean shouldAutoPopupAfterTyped(PsiFile file, Editor editor, char typed) { String prefix = prefix(file, editor); return prefix != null && matches(prefix + typed); }
    public static boolean isColorContext(PsiFile file, Editor editor) { String prefix = prefix(file, editor); return prefix != null && COLOR_CONTEXT_PATTERN.matcher(prefix).find(); }
    public static boolean isColorContextAfterTyped(PsiFile file, Editor editor, char typed) { String prefix = prefix(file, editor); return prefix != null && COLOR_CONTEXT_PATTERN.matcher(prefix + typed).find(); }
    private static boolean matches(String prefix) {
        if (COLOR_CONTEXT_PATTERN.matcher(prefix).find()) return true;
        var syntax = DCSyntaxDefinition.get();
        String trimmed = prefix.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(syntax.token("categoryOpen"))) return true;
        for (String name : new String[]{"assignment", "block", "objectSeparator"})
            if (trimmed.endsWith(syntax.token(name))) return true;
        return syntax.identifier.matcher(trimmed).matches();
    }
    private static String prefix(PsiFile file, Editor editor) {
        if (file == null || editor == null || !file.getLanguage().isKindOf(DCLanguage.INSTANCE)) return null;
        var document = editor.getDocument(); int offset = editor.getCaretModel().getOffset();
        return document.getText(new TextRange(document.getLineStartOffset(document.getLineNumber(offset)), offset));
    }
}
