package com.cozooo.dclanguage;

import java.io.IOException;

public final class DcParseException extends IOException {
    private static final long serialVersionUID = 1L;
    private final int line;
    private final int column;
    DcParseException(String message, String text, int offset) {
        this(message, location(text, offset));
    }
    private DcParseException(String message, Location location) {
        super(message + " at line " + location.line + ", column " + location.column);
        this.line = location.line;
        this.column = location.column;
    }
    private static Location location(String text, int offset) {
        int line = 1, column = 1;
        int limit = Math.max(0, Math.min(offset, text.length()));
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                line++;
                column = 1;
                if (i + 1 < limit && text.charAt(i + 1) == '\n') i++;
            } else if (c == '\n') {
                line++;
                column = 1;
            } else if (i != 0 || c != '\uFEFF') {
                column++;
            }
        }
        return new Location(line, column);
    }
    private record Location(int line, int column) { }
    public int line() { return line; }
    public int column() { return column; }
}
