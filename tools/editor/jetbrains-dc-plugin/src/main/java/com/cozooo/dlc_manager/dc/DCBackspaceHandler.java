package com.cozooo.dlc_manager.dc;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class DCBackspaceHandler extends BackspaceHandlerDelegate {
    @Override
    public void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {}

    @Override
    public boolean charDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
        Project project = editor.getProject();
        if (project == null || !DCAutoPopupSupport.shouldAutoPopup(file, editor)) {
            return false;
        }
        AutoPopupController controller = AutoPopupController.getInstance(project);
        controller.scheduleAutoPopup(editor, CompletionType.BASIC, psiFile -> true);
        return false;
    }
}
