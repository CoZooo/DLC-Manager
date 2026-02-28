package com.cozooo.dlc_manager.dc;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.ElementColorProvider;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DCElementColorProvider implements ElementColorProvider {
    private static final Pattern COLOR_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");

    @Override
    public @Nullable Color getColorFrom(@NotNull PsiElement element) {
        if (!element.getLanguage().isKindOf(DCLanguage.INSTANCE)) {
            return null;
        }
        if (element.getFirstChild() != null) {
            return null;
        }
        IElementType type = element.getNode() == null ? null : element.getNode().getElementType();
        if (type != DCTokenTypes.STRING && type != DCTokenTypes.IDENTIFIER) {
            return null;
        }
        String text = element.getText();
        if (text == null || text.isEmpty() || text.length() > 256) {
            return null;
        }
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return null;
        }
        Matcher matcher = COLOR_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String hex = matcher.group(1);
        try {
            return new Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public void setColorTo(@NotNull PsiElement element, @NotNull Color color) {
        if (!element.getLanguage().isKindOf(DCLanguage.INSTANCE)) {
            return;
        }
        if (element.getFirstChild() != null) {
            return;
        }
        IElementType type = element.getNode() == null ? null : element.getNode().getElementType();
        if (type != DCTokenTypes.STRING && type != DCTokenTypes.IDENTIFIER) {
            return;
        }
        String text = element.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = COLOR_PATTERN.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String replacement = String.format(
            "&#%02X%02X%02X",
            color.getRed(),
            color.getGreen(),
            color.getBlue()
        );
        String updated = text.substring(0, matcher.start()) + replacement + text.substring(matcher.end());

        Project project = element.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }
        PsiFile file = element.getContainingFile();
        if (file == null || file.getVirtualFile() == null) {
            return;
        }
        var document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        if (document == null) {
            return;
        }
        int start = element.getTextRange().getStartOffset();
        int end = element.getTextRange().getEndOffset();
        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.replaceString(start, end, updated);
            FileDocumentManager.getInstance().saveDocument(document);
        });
    }
}
