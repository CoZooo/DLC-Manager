package com.cozooo.dlc_manager.dc;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class DCSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey SECTION =
        TextAttributesKey.createTextAttributesKey("DC_SECTION", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey KEY =
        TextAttributesKey.createTextAttributesKey("DC_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey STRING =
        TextAttributesKey.createTextAttributesKey("DC_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey NUMBER =
        TextAttributesKey.createTextAttributesKey("DC_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey BOOLEAN =
        TextAttributesKey.createTextAttributesKey("DC_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey COMMENT =
        TextAttributesKey.createTextAttributesKey("DC_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey OPERATOR =
        TextAttributesKey.createTextAttributesKey("DC_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey PUNCT =
        TextAttributesKey.createTextAttributesKey("DC_PUNCT", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey BAD =
        TextAttributesKey.createTextAttributesKey("DC_BAD", HighlighterColors.BAD_CHARACTER);

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new DCLexer();
    }

    @Override
    public @NotNull TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType == DCTokenTypes.SECTION) {
            return pack(SECTION);
        }
        if (tokenType == DCTokenTypes.KEY || tokenType == DCTokenTypes.IDENTIFIER) {
            return pack(KEY);
        }
        if (tokenType == DCTokenTypes.STRING) {
            return pack(STRING);
        }
        if (tokenType == DCTokenTypes.NUMBER) {
            return pack(NUMBER);
        }
        if (tokenType == DCTokenTypes.BOOLEAN) {
            return pack(BOOLEAN);
        }
        if (tokenType == DCTokenTypes.COMMENT) {
            return pack(COMMENT);
        }
        if (tokenType == DCTokenTypes.OPERATOR) {
            return pack(OPERATOR);
        }
        if (tokenType == DCTokenTypes.BRACKET || tokenType == DCTokenTypes.COMMA) {
            return pack(PUNCT);
        }
        if (tokenType == DCTokenTypes.BAD_CHARACTER || tokenType == TokenType.BAD_CHARACTER) {
            return pack(BAD);
        }
        return TextAttributesKey.EMPTY_ARRAY;
    }
}
