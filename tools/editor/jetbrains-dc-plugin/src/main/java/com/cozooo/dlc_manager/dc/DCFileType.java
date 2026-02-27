package com.cozooo.dlc_manager.dc;

import com.intellij.openapi.fileTypes.LanguageFileType;
import javax.swing.Icon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DCFileType extends LanguageFileType {
    public static final DCFileType INSTANCE = new DCFileType();

    private DCFileType() {
        super(DCLanguage.INSTANCE);
    }

    @Override
    public @NotNull @NonNls String getName() {
        return "DC";
    }

    @Override
    public @NotNull String getDescription() {
        return "DLC Manager DC config file";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "dc";
    }

    @Override
    public @Nullable Icon getIcon() {
        return DCIcons.FILE;
    }
}
