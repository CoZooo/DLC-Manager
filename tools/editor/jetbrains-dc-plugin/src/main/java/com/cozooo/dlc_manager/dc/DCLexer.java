package com.cozooo.dlc_manager.dc;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DCLexer extends LexerBase {
    private final DCSyntaxDefinition syntax;
    private CharSequence buffer = "";
    private int endOffset, tokenStart, tokenEnd, state, nextState;
    private IElementType tokenType;

    public DCLexer() { this(DCSyntaxDefinition.get()); }
    public DCLexer(DCSyntaxDefinition syntax) { this.syntax = syntax; }
    @Override public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer; this.endOffset = endOffset; this.tokenStart = startOffset; this.state = initialState; locateToken();
    }
    @Override public int getState() { return state; }
    @Override public @Nullable IElementType getTokenType() { return tokenType; }
    @Override public int getTokenStart() { return tokenStart; }
    @Override public int getTokenEnd() { return tokenEnd; }
    @Override public void advance() { tokenStart = tokenEnd; state = nextState; locateToken(); }
    @Override public @NotNull CharSequence getBufferSequence() { return buffer; }
    @Override public int getBufferEnd() { return endOffset; }

    private void locateToken() {
        nextState = state;
        if (tokenStart >= endOffset) { tokenType = null; tokenEnd = tokenStart; return; }
        if (state > 0 && state <= syntax.strings.size()) { stringToken(state - 1, tokenStart); return; }
        if (Character.isWhitespace(buffer.charAt(tokenStart)) || tokenStart == 0 && syntax.allowBom && buffer.charAt(0) == '\ufeff') {
            tokenType = TokenType.WHITE_SPACE; tokenEnd = tokenStart + 1;
            while (tokenEnd < endOffset && Character.isWhitespace(buffer.charAt(tokenEnd))) tokenEnd++;
            return;
        }
        for (String comment : syntax.comments) if (startsWith(tokenStart, comment)) {
            tokenType = DCTokenTypes.COMMENT; tokenEnd = lineEnd(tokenStart); return;
        }
        for (int index = 0; index < syntax.strings.size(); index++) {
            String delimiter = syntax.strings.get(index).delimiter();
            if (startsWith(tokenStart, delimiter)) { stringToken(index, tokenStart + delimiter.length()); return; }
        }
        String categoryOpen = syntax.token("categoryOpen"), categoryClose = syntax.token("categoryClose");
        if (startsWith(tokenStart, categoryOpen) && atLineStart()) {
            tokenType = DCTokenTypes.SECTION; tokenEnd = tokenStart + categoryOpen.length();
            while (tokenEnd < endOffset && buffer.charAt(tokenEnd) != '\n' && buffer.charAt(tokenEnd) != '\r') {
                if (startsWith(tokenEnd, categoryClose)) { tokenEnd += categoryClose.length(); return; }
                tokenEnd++;
            }
            return;
        }
        for (String literal : syntax.literals) if (literalMatches(literal)) {
            tokenType = DCTokenTypes.BOOLEAN; tokenEnd = tokenStart + literal.length(); return;
        }
        int numberEnd = matchingEnd(syntax.number);
        if (numberEnd > tokenStart) { tokenType = DCTokenTypes.NUMBER; tokenEnd = numberEnd; return; }
        for (String operator : syntax.operators) if (startsWith(tokenStart, operator)) {
            tokenType = DCTokenTypes.OPERATOR; tokenEnd = tokenStart + operator.length(); return;
        }
        if (startsWith(tokenStart, syntax.token("itemSeparator"))) {
            tokenType = DCTokenTypes.COMMA; tokenEnd = tokenStart + syntax.token("itemSeparator").length(); return;
        }
        for (String bracket : syntax.brackets) if (startsWith(tokenStart, bracket)) {
            tokenType = DCTokenTypes.BRACKET; tokenEnd = tokenStart + bracket.length(); return;
        }
        int identifierEnd = matchingEnd(syntax.identifier);
        if (identifierEnd > tokenStart) { tokenType = DCTokenTypes.IDENTIFIER; tokenEnd = identifierEnd; return; }
        tokenType = DCTokenTypes.BAD_CHARACTER; tokenEnd = tokenStart + 1;
    }

    private void stringToken(int index, int offset) {
        var rule = syntax.strings.get(index);
        String escape = syntax.token("escape");
        tokenType = DCTokenTypes.STRING; tokenEnd = offset;
        while (tokenEnd < endOffset) {
            if (startsWith(tokenEnd, rule.delimiter())) { tokenEnd += rule.delimiter().length(); nextState = 0; return; }
            if (!rule.multiline() && (buffer.charAt(tokenEnd) == '\n' || buffer.charAt(tokenEnd) == '\r')) { nextState = 0; return; }
            if (rule.escaped() && startsWith(tokenEnd, escape)) tokenEnd = Math.min(endOffset, tokenEnd + escape.length() + 1);
            else tokenEnd++;
        }
        nextState = rule.multiline() ? index + 1 : 0;
    }
    private boolean atLineStart() {
        for (int i = tokenStart - 1; i >= 0 && buffer.charAt(i) != '\n' && buffer.charAt(i) != '\r'; i--)
            if (!Character.isWhitespace(buffer.charAt(i))) return false;
        return true;
    }
    private int lineEnd(int start) { int index = start; while (index < endOffset && buffer.charAt(index) != '\n' && buffer.charAt(index) != '\r') index++; return index; }
    private int matchingEnd(Pattern pattern) {
        var matcher = pattern.matcher(buffer).region(tokenStart, endOffset);
        return matcher.lookingAt() ? matcher.end() : tokenStart;
    }
    private boolean literalMatches(String literal) {
        if (tokenStart + literal.length() > endOffset) return false;
        String value = buffer.subSequence(tokenStart, tokenStart + literal.length()).toString();
        if (!(syntax.ignoreBooleanCase ? value.equalsIgnoreCase(literal) : value.equals(literal))) return false;
        if (tokenStart + literal.length() == endOffset) return true;
        char next = buffer.charAt(tokenStart + literal.length());
        return Character.isWhitespace(next) || syntax.brackets.stream().anyMatch(token -> startsWith(tokenStart + literal.length(), token))
            || startsWith(tokenStart + literal.length(), syntax.token("itemSeparator")) || syntax.comments.stream().anyMatch(token -> startsWith(tokenStart + literal.length(), token));
    }
    private boolean startsWith(int offset, String token) {
        if (token.isEmpty() || offset + token.length() > endOffset) return false;
        for (int i = 0; i < token.length(); i++) if (buffer.charAt(offset + i) != token.charAt(i)) return false;
        return true;
    }
}
