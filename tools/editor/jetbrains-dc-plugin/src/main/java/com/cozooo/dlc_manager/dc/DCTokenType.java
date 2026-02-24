package com.cozooo.dlc_manager.dc;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class DCTokenType extends IElementType {
    public DCTokenType(@NotNull @NonNls String debugName) {
        super(debugName, DCLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "DC:" + super.toString();
    }
}
