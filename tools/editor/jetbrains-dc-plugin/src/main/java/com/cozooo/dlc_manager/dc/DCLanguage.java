package com.cozooo.dlc_manager.dc;

import com.intellij.lang.Language;

public final class DCLanguage extends Language {
    public static final DCLanguage INSTANCE = new DCLanguage();

    private DCLanguage() {
        super("DC");
    }
}
