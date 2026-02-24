package com.cozooo.dlc_manager.dc;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class DCPsiFile extends PsiFileBase {
    public DCPsiFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, DCLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return DCFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "DC File";
    }
}
