package com.cozooo.dclanguage.lsp;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;

final class MessageTransport {
    static final int MAX_MESSAGE_BYTES = 8 * 1024 * 1024;
    private final InputStream input;
    private final OutputStream output;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    MessageTransport(InputStream input, OutputStream output) { this.input = input; this.output = output; }
    String read() throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int next = input.read();
            if (next < 0) { if (header.size() == 0) return null; throw new EOFException("Truncated protocol header"); }
            if (next > 127) throw new IOException("Protocol headers must be ASCII");
            header.write(next);
            if (header.size() > 8192) throw new IOException("Protocol header is too large");
            state = switch (state) { case 0 -> next == '\r' ? 1 : 0; case 1 -> next == '\n' ? 2 : next == '\r' ? 1 : 0; case 2 -> next == '\r' ? 3 : 0; default -> next == '\n' ? 4 : next == '\r' ? 1 : 0; };
        }
        int length = -1;
        for (String line : header.toString(StandardCharsets.US_ASCII).split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (name.equalsIgnoreCase("Content-Length")) {
                if (length >= 0 || !value.matches("[0-9]+")) throw new IOException("Invalid Content-Length");
                try { length = Integer.parseInt(value); } catch (NumberFormatException exception) { throw new IOException("Invalid Content-Length", exception); }
            } else if (name.equalsIgnoreCase("Content-Type") && value.toLowerCase(Locale.ROOT).contains("charset=")) {
                String encoding = value.toLowerCase(Locale.ROOT).substring(value.toLowerCase(Locale.ROOT).indexOf("charset=") + 8).trim();
                if (!encoding.equals("utf-8") && !encoding.equals("utf8")) throw new IOException("Unsupported protocol encoding");
            }
        }
        if (length < 0 || length > MAX_MESSAGE_BYTES) throw new IOException("Missing or excessive Content-Length");
        byte[] content = input.readNBytes(length);
        if (content.length != length) throw new EOFException("Truncated protocol content");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
    }
    synchronized void write(JsonObject message) throws IOException {
        byte[] content = gson.toJson(message).getBytes(StandardCharsets.UTF_8);
        output.write(("Content-Length: " + content.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(content);
        output.flush();
    }
}
