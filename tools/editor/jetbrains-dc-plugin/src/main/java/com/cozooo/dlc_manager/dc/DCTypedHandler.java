package com.cozooo.dlc_manager.dc;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class DCTypedHandler extends TypedHandlerDelegate {
    @Override
    public @NotNull Result checkAutoPopup(
        char charTyped,
        @NotNull Project project,
        @NotNull Editor editor,
        @NotNull PsiFile file
    ) {
        if (!DCAutoPopupSupport.shouldAutoPopupAfterTyped(file, editor, charTyped)) {
            return Result.CONTINUE;
        }
        AutoPopupController controller = AutoPopupController.getInstance(project);
        controller.scheduleAutoPopup(editor, CompletionType.BASIC, psiFile -> true);
        return Result.STOP;
    }
}
