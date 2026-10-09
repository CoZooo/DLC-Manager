package com.cozooo.dlc_manager.dc;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DCStdioClientTest {
    private static String java() { return Path.of(System.getProperty("java.home"), "bin", "java").toString(); }
    private DCStdioClient fake() throws Exception {
        String classpath = System.getProperty("dc.test.classpath");
        return new DCStdioClient(List.of(java(), "-cp", classpath, FakeServer.class.getName()), (method, params) -> {}, ignored -> {});
    }

    @Test void unicodeFramesTimeoutsAndShutdown() throws Exception {
        DCStdioClient client = fake();
        try {
            assertNotNull(client.initialize("file:///workspace").get(5, TimeUnit.SECONDS));
            JsonObject params = new JsonObject(); params.addProperty("text", "中文😀");
            assertEquals(params, client.request("echo", params).get(5, TimeUnit.SECONDS));
            var failure = assertThrows(ExecutionException.class, () -> client.request("never", params, Duration.ofMillis(80)).get(2, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, failure.getCause());
            var cancelled = client.request("never", params);
            assertTrue(cancelled.cancel(true));
            assertThrows(CancellationException.class, cancelled::join);
            assertEquals(params, client.request("echo", params).get(5, TimeUnit.SECONDS));
        } finally { client.close(); }
        assertFalse(client.isAlive());
    }

    @Test void processFailureRejectsWaiters() throws Exception {
        try (DCStdioClient client = fake()) {
            client.initialize(null).get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> client.request("crash", new JsonObject()).get(5, TimeUnit.SECONDS));
        }
    }

    @Test void actualSharedServerDiagnosticsCompletionHoverFormatting() throws Exception {
        Path jar = Path.of(System.getProperty("dc.language.server"));
        assertTrue(Files.isRegularFile(jar), "Build the shared language server before editor integration tests");
        BlockingQueue<JsonObject> diagnostics = new LinkedBlockingQueue<>();
        try (DCStdioClient client = new DCStdioClient(List.of(java(), "-jar", jar.toString(), "--stdio"),
            (method, params) -> { if (method.equals("textDocument/publishDiagnostics")) diagnostics.add(params); }, ignored -> {})) {
            client.initialize("file:///workspace").get(5, TimeUnit.SECONDS);
            String uri = "file:///workspace/DLC/info/example.dc";
            JsonObject identity = new JsonObject(); identity.addProperty("uri", uri); identity.addProperty("version", 1); identity.addProperty("languageId", "dc");
            identity.addProperty("text", "[basic]\nidentifier = \"中文😀\"\nmode = \"invalid\"\n");
            JsonObject open = new JsonObject(); open.add("textDocument", identity); client.notify("textDocument/didOpen", open);
            JsonObject result = diagnostics.poll(5, TimeUnit.SECONDS); assertNotNull(result); assertEquals(uri, result.get("uri").getAsString());
            assertFalse(result.getAsJsonArray("diagnostics").isEmpty());
            JsonObject params = new JsonObject(); JsonObject doc = new JsonObject(); doc.addProperty("uri", uri); params.add("textDocument", doc);
            params.add("position", JsonParser.parseString("{\"line\":3,\"character\":0}"));
            JsonElement completions = client.request("textDocument/completion", params).get(5, TimeUnit.SECONDS);
            JsonArray items = completions.isJsonArray() ? completions.getAsJsonArray() : completions.getAsJsonObject().getAsJsonArray("items");
            assertFalse(items.isEmpty());
            params.add("position", JsonParser.parseString("{\"line\":1,\"character\":3}"));
            assertTrue(client.request("textDocument/hover", params).get(5, TimeUnit.SECONDS).getAsJsonObject().has("contents"));
            params.add("options", JsonParser.parseString("{\"tabSize\":4,\"insertSpaces\":true}"));
            assertTrue(client.request("textDocument/formatting", params).get(5, TimeUnit.SECONDS).isJsonArray());
            client.notify("textDocument/didClose", params);
        }
    }

    public static final class FakeServer {
        public static void main(String[] args) throws Exception {
            InputStream input = System.in;
            while (true) {
                StringBuilder header = new StringBuilder(); int value;
                while (!header.toString().endsWith("\r\n\r\n") && (value = input.read()) >= 0) header.append((char) value);
                if (header.isEmpty()) return;
                int length = Integer.parseInt(header.toString().split(":", 2)[1].trim());
                JsonObject request = JsonParser.parseString(new String(input.readNBytes(length), StandardCharsets.UTF_8)).getAsJsonObject();
                String method = request.get("method").getAsString();
                if (method.equals("exit")) return;
                if (method.equals("crash")) System.exit(7);
                if (!request.has("id") || method.equals("never")) continue;
                JsonObject response = new JsonObject(); response.addProperty("jsonrpc", "2.0"); response.add("id", request.get("id"));
                response.add("result", method.equals("echo") ? request.get("params") : new JsonObject());
                byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
                System.out.write("Content-Len".getBytes(StandardCharsets.US_ASCII)); System.out.flush();
                System.out.write(("gth: " + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); System.out.write(bytes); System.out.flush();
            }
        }
    }
}
