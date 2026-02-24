package com.cozooo.dlc_manager.dc;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;

public final class DCTokenTypes {
    public static final IFileElementType FILE = new IFileElementType(DCLanguage.INSTANCE);

    public static final IElementType SECTION = new DCTokenType("SECTION");
    public static final IElementType KEY = new DCTokenType("KEY");
    public static final IElementType STRING = new DCTokenType("STRING");
    public static final IElementType NUMBER = new DCTokenType("NUMBER");
    public static final IElementType BOOLEAN = new DCTokenType("BOOLEAN");
    public static final IElementType COMMENT = new DCTokenType("COMMENT");
    public static final IElementType OPERATOR = new DCTokenType("OPERATOR");
    public static final IElementType COMMA = new DCTokenType("COMMA");
    public static final IElementType BRACKET = new DCTokenType("BRACKET");
    public static final IElementType IDENTIFIER = new DCTokenType("IDENTIFIER");
    public static final IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

    private DCTokenTypes() {
    }
}
