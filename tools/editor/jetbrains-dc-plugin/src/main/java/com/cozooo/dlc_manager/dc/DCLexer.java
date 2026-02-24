package com.cozooo.dlc_manager.dc;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DCLexer extends LexerBase {
    private static final int STATE_DEFAULT = 0;
    private static final int STATE_TRIPLE_STRING = 1;

    private CharSequence buffer = "";
    private int endOffset;
    private int state;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.endOffset = endOffset;
        this.state = initialState;
        this.tokenStart = startOffset;
        locateToken();
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        tokenStart = tokenEnd;
        locateToken();
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }

    private void locateToken() {
        if (tokenStart >= endOffset) {
            tokenType = null;
            tokenEnd = tokenStart;
            return;
        }

        if (state == STATE_TRIPLE_STRING) {
            int close = indexOf(tokenStart, "\"\"\"");
            if (close >= 0) {
                tokenType = DCTokenTypes.STRING;
                tokenEnd = close + 3;
                state = STATE_DEFAULT;
            } else {
                tokenType = DCTokenTypes.STRING;
                tokenEnd = endOffset;
            }
            return;
        }

        char c = buffer.charAt(tokenStart);

        if (Character.isWhitespace(c)) {
            tokenType = TokenType.WHITE_SPACE;
            tokenEnd = tokenStart + 1;
            while (tokenEnd < endOffset && Character.isWhitespace(buffer.charAt(tokenEnd))) {
                tokenEnd++;
            }
            return;
        }

        if (startsWith(tokenStart, "\"\"\"")) {
            int close = indexOf(tokenStart + 3, "\"\"\"");
            tokenType = DCTokenTypes.STRING;
            if (close >= 0) {
                tokenEnd = close + 3;
            } else {
                tokenEnd = endOffset;
                state = STATE_TRIPLE_STRING;
            }
            return;
        }

        if (c == '#' || (c == '/' && tokenStart + 1 < endOffset && buffer.charAt(tokenStart + 1) == '/')) {
            tokenType = DCTokenTypes.COMMENT;
            tokenEnd = tokenStart + 1;
            while (tokenEnd < endOffset) {
                char ch = buffer.charAt(tokenEnd);
                if (ch == '\n' || ch == '\r') {
                    break;
                }
                tokenEnd++;
            }
            return;
        }

        if (c == '[') {
            tokenType = DCTokenTypes.SECTION;
            tokenEnd = tokenStart + 1;
            while (tokenEnd < endOffset) {
                char ch = buffer.charAt(tokenEnd);
                tokenEnd++;
                if (ch == ']') {
                    break;
                }
                if (ch == '\n' || ch == '\r') {
                    break;
                }
            }
            return;
        }

        if (c == '"') {
            tokenType = DCTokenTypes.STRING;
            tokenEnd = tokenStart + 1;
            while (tokenEnd < endOffset) {
                char ch = buffer.charAt(tokenEnd);
                if (ch == '\\') {
                    tokenEnd += 2;
                    continue;
                }
                tokenEnd++;
                if (ch == '"') {
                    break;
                }
            }
            tokenEnd = Math.min(tokenEnd, endOffset);
            return;
        }

        if (c == '=' || c == ':') {
            tokenType = DCTokenTypes.OPERATOR;
            tokenEnd = tokenStart + 1;
            return;
        }

        if (c == ',' ) {
            tokenType = DCTokenTypes.COMMA;
            tokenEnd = tokenStart + 1;
            return;
        }

        if (c == '{' || c == '}' || c == '(' || c == ')' || c == ']') {
            tokenType = DCTokenTypes.BRACKET;
            tokenEnd = tokenStart + 1;
            return;
        }

        if (c == '-' || Character.isDigit(c)) {
            int i = tokenStart + 1;
            boolean hasDot = false;
            while (i < endOffset) {
                char ch = buffer.charAt(i);
                if (Character.isDigit(ch)) {
                    i++;
                    continue;
                }
                if (!hasDot && ch == '.') {
                    hasDot = true;
                    i++;
                    continue;
                }
                break;
            }
            tokenType = DCTokenTypes.NUMBER;
            tokenEnd = i;
            return;
        }

        if (isIdentifierStart(c)) {
            int i = tokenStart + 1;
            while (i < endOffset && isIdentifierPart(buffer.charAt(i))) {
                i++;
            }
            String word = buffer.subSequence(tokenStart, i).toString();
            if ("true".equals(word) || "false".equals(word)) {
                tokenType = DCTokenTypes.BOOLEAN;
            } else {
                tokenType = DCTokenTypes.IDENTIFIER;
            }
            tokenEnd = i;
            return;
        }

        tokenType = DCTokenTypes.BAD_CHARACTER;
        tokenEnd = tokenStart + 1;
    }

    private boolean startsWith(int offset, String text) {
        if (offset + text.length() > endOffset) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (buffer.charAt(offset + i) != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private int indexOf(int from, String needle) {
        if (needle.isEmpty()) {
            return from;
        }
        for (int i = Math.max(from, 0); i <= endOffset - needle.length(); i++) {
            if (startsWith(i, needle)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-';
    }
}
