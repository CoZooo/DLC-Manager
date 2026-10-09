package com.cozooo.dclanguage.lsp;

import com.cozooo.dclanguage.DcDocuments;
import com.cozooo.dclanguage.DcLanguageService;
import com.cozooo.dclanguage.DcSchemaCatalog;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class DcLanguageServerProcessTest {
    @Test
    void initializesAndShutsDownUsingFramedProtocolOnly() throws Exception {
        try (Client client = new Client()) {
            client.request(1, "textDocument/completion", object());
            assertEquals(-32002, client.response(1).getAsJsonObject("error").get("code").getAsInt());
            client.initialize();
            assertEquals("utf-16", client.response(2).getAsJsonObject("result").getAsJsonObject("capabilities").get("positionEncoding").getAsString());
            client.request(3, "unknown/method", object());
            assertEquals(-32601, client.response(3).getAsJsonObject("error").get("code").getAsInt());
            client.stop();
            assertEquals("", new String(client.process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    @Test
    void publishesIndependentDiagnosticsAndClearsClosedDocuments() throws Exception {
        try (Client client = new Client()) {
            client.initializeReady();
            client.open("file:///first.dc", 1, "[basic]\nidentifier = 123\nmode='invalid'\n");
            client.open("file:///second.dc", 1, "[basic]\nidentifier='valid'\nunknown=true\n");
            assertEquals(2, client.diagnostics("file:///first.dc", 1).getAsJsonArray("diagnostics").size());
            assertEquals(0, client.diagnostics("file:///second.dc", 1).getAsJsonArray("diagnostics").size());
            client.notify("textDocument/didClose", object("textDocument", object("uri", "file:///first.dc")));
            var cleared = client.await(message -> method(message, "textDocument/publishDiagnostics") && message.getAsJsonObject("params").get("uri").getAsString().equals("file:///first.dc") && !message.getAsJsonObject("params").has("version"));
            assertEquals(0, cleared.getAsJsonObject("params").getAsJsonArray("diagnostics").size());
            client.stop();
        }
    }
    @Test
    void incrementalUtf16EditsIgnoreOutdatedVersions() throws Exception {
        try (Client client = new Client()) {
            client.initializeReady();
            String uri = "file:///unicode.dc";
            client.open(uri, 1, "[basic]\nidentifier='😀'\nmode='hash'\n");
            client.diagnostics(uri, 1);
            client.notify("textDocument/didChange", object("textDocument", object("uri", uri, "version", 2), "contentChanges", array(object("range", object("start", position(1, 12), "end", position(1, 14)), "rangeLength", 2, "text", "名字"))));
            assertEquals(0, client.diagnostics(uri, 2).getAsJsonArray("diagnostics").size());
            client.change(uri, 1, "broken = \"unterminated");
            client.request(10, "textDocument/formatting", object("textDocument", object("uri", uri), "options", object("tabSize", 2, "insertSpaces", true)));
            String text = client.response(10).getAsJsonArray("result").get(0).getAsJsonObject().get("newText").getAsString();
            assertTrue(text.contains("identifier = '名字'"));
            assertFalse(text.contains("broken"));
            client.stop();
        }
    }
    @Test
    void completionAndHoverUseSharedSchemaMetadata() throws Exception {
        try (Client client = new Client()) {
            client.initializeReady();
            String uri = "file:///completion.dc";
            client.open(uri, 1, "[basic]\nidentifier='x'\nmode = \"ha");
            client.request(20, "textDocument/completion", object("textDocument", object("uri", uri), "position", position(2, 10)));
            JsonArray items = client.response(20).getAsJsonArray("result");
            assertEquals("hash", items.get(0).getAsJsonObject().get("label").getAsString());
            assertEquals("\"hash\"", items.get(0).getAsJsonObject().getAsJsonObject("textEdit").get("newText").getAsString());
            client.change(uri, 2, "[basic]\nidentifier='x'\nmode='name'\n");
            client.request(21, "textDocument/hover", object("textDocument", object("uri", uri), "position", position(2, 1)));
            assertTrue(client.response(21).getAsJsonObject("result").getAsJsonObject("contents").get("value").getAsString().contains("Matching mode"));
            client.stop();
        }
    }
    @Test
    void formattingRetainsCommentsUnknownFieldsAndMultilineText() throws Exception {
        try (Client client = new Client()) {
            client.initializeReady();
            String uri = "file:///format.dc";
            String source = "# heading\n[ basic ] # category\nidentifier= 'x' # keep\nunknown= {number: 2}\nbody= \"\"\"\n [ not.a.header ]\n\"\"\"\n";
            client.open(uri, 1, source);
            client.request(30, "textDocument/formatting", object("textDocument", object("uri", uri), "options", object("tabSize", 2, "insertSpaces", true)));
            String formatted = client.response(30).getAsJsonArray("result").get(0).getAsJsonObject().get("newText").getAsString();
            assertTrue(formatted.contains("# heading"));
            assertTrue(formatted.contains("identifier = 'x' # keep"));
            assertTrue(formatted.contains(" [ not.a.header ]\n"));
            assertEquals(DcDocuments.readText(source).toJson(), DcDocuments.readText(formatted).toJson());
            client.change(uri, 2, "[basic]\nidentifier = \"broken");
            client.request(31, "textDocument/formatting", object("textDocument", object("uri", uri), "options", object("tabSize", 2, "insertSpaces", true)));
            assertEquals(0, client.response(31).getAsJsonArray("result").size());
            client.stop();
        }
    }
    @Test
    void rapidChangesNeverPublishDiagnosticsOlderThanAlreadyPublishedVersions() throws Exception {
        try (Client client = new Client()) {
            client.initializeReady();
            String uri = "file:///rapid.dc";
            client.open(uri, 1, "[basic]\nidentifier='first'\n");
            for (int version = 2; version <= 60; version++) client.change(uri, version, version == 60 ? "[basic]\nidentifier='final'\nmode = \"na" : "[basic]\nidentifier='v" + version + "'\n");
            client.diagnostics(uri, 60);
            client.request(40, "textDocument/completion", object("textDocument", object("uri", uri), "position", position(2, 10)));
            assertEquals("name", client.response(40).getAsJsonArray("result").get(0).getAsJsonObject().get("label").getAsString());
            int previous = 0;
            for (JsonObject message : client.history) if (method(message, "textDocument/publishDiagnostics")) {
                JsonObject params = message.getAsJsonObject("params");
                int version = params.get("version").getAsInt();
                assertTrue(version >= previous); previous = version;
            }
            client.stop();
        }
    }
    @Test
    void rejectsExcessiveMessagesBeforeAllocatingTheirBodies() throws Exception {
        try (Client client = new Client()) {
            client.process.getOutputStream().write("Content-Length: 999999999\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            client.process.getOutputStream().flush();
            assertTrue(client.process.waitFor(5, TimeUnit.SECONDS));
            assertEquals(1, client.process.exitValue());
            assertTrue(new String(client.process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).contains("Content-Length"));
        }
    }
    @Test
    void invalidJsonFrameReportsErrorAndAllowsFollowingInitialize() throws Exception {
        try (Client client = new Client()) {
            byte[] broken = "{ broken ".getBytes(StandardCharsets.UTF_8);
            client.process.getOutputStream().write(("Content-Length: " + broken.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            client.process.getOutputStream().write(broken); client.process.getOutputStream().flush();
            assertEquals(-32700, client.await(message -> message.has("error")).getAsJsonObject("error").get("code").getAsInt());
            client.initializeReady(); client.stop();
        }
    }
    @Test
    void exitWithoutShutdownReturnsFailureStatus() throws Exception {
        try (Client client = new Client()) {
            client.notify("exit", object());
            assertTrue(client.process.waitFor(5, TimeUnit.SECONDS));
            assertEquals(1, client.process.exitValue());
        }
    }
    @Test
    void productionMainLoadsTheMatchingGeneratedSchema() throws Exception {
        try (Client client = new Client(false)) {
            client.initializeReady();
            client.open("file:///DLC/remote/example.dc", 1, "[basic]\nidentifier='example'\nmode='hash'\n");
            assertTrue(client.diagnostics("file:///DLC/remote/example.dc", 1).getAsJsonArray("diagnostics").isEmpty());
            client.stop();
        }
    }
    public static final class TestServer {
        public static void main(String[] args) throws Exception {
            JsonObject schema;
            try (var input = new InputStreamReader(TestServer.class.getResourceAsStream("/service/language-definition.json"), StandardCharsets.UTF_8)) {
                schema = JsonParser.parseReader(input).getAsJsonObject();
            }
            System.exit(new DcLanguageServer(System.in, System.out, new DcLanguageService(DcSchemaCatalog.fromJson(schema))).run());
        }
    }
    private static boolean method(JsonObject message, String name) { return message.has("method") && message.get("method").getAsString().equals(name); }
    private static JsonObject position(int line, int character) { return object("line", line, "character", character); }
    private static JsonArray array(JsonElement... values) { JsonArray array = new JsonArray(); for (JsonElement value : values) array.add(value); return array; }
    private static JsonObject object(Object... pairs) {
        JsonObject result = new JsonObject(); Gson gson = new Gson();
        for (int index = 0; index < pairs.length; index += 2) result.add((String) pairs[index], gson.toJsonTree(pairs[index + 1]));
        return result;
    }
    private static final class Client implements AutoCloseable {
        final Process process;
        final boolean fixture;
        final MessageTransport transport;
        final BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();
        final List<JsonObject> history = new ArrayList<>();
        Client() throws IOException { this(true); }
        Client(boolean fixture) throws IOException {
            this.fixture = fixture;
            process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("lsp.testClasspath"), (fixture ? TestServer.class.getName() : DcLanguageServer.class.getName()), "--stdio").start();
            transport = new MessageTransport(process.getInputStream(), process.getOutputStream());
            Thread reader = new Thread(() -> {
                try { String text; while ((text = transport.read()) != null) messages.add(JsonParser.parseString(text).getAsJsonObject()); }
                catch (IOException ignored) { }
            });
            reader.setDaemon(true); reader.start();
        }
        void request(int id, String method, JsonObject params) throws IOException { transport.write(object("jsonrpc", "2.0", "id", id, "method", method, "params", params)); }
        void notify(String method, JsonObject params) throws IOException { transport.write(object("jsonrpc", "2.0", "method", method, "params", params)); }
        void initialize() throws IOException { request(2, "initialize", object("capabilities", object(), "initializationOptions", fixture ? object("profile", "test") : object())); }
        void initializeReady() throws Exception { initialize(); assertTrue(response(2).has("result")); notify("initialized", object()); }
        void open(String uri, int version, String text) throws IOException { notify("textDocument/didOpen", object("textDocument", object("uri", uri, "languageId", "dc", "version", version, "text", text))); }
        void change(String uri, int version, String text) throws IOException { notify("textDocument/didChange", object("textDocument", object("uri", uri, "version", version), "contentChanges", array(object("text", text)))); }
        JsonObject response(int id) throws Exception { return await(message -> message.has("id") && !message.get("id").isJsonNull() && message.get("id").getAsInt() == id); }
        JsonObject diagnostics(String uri, int version) throws Exception { return await(message -> method(message, "textDocument/publishDiagnostics") && message.getAsJsonObject("params").get("uri").getAsString().equals(uri) && message.getAsJsonObject("params").has("version") && message.getAsJsonObject("params").get("version").getAsInt() == version).getAsJsonObject("params"); }
        JsonObject await(Predicate<JsonObject> predicate) throws Exception {
            for (JsonObject message : history) if (predicate.test(message)) return message;
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < end) {
                JsonObject message = messages.poll(100, TimeUnit.MILLISECONDS);
                if (message != null) { history.add(message); if (predicate.test(message)) return message; }
                if (!process.isAlive() && messages.isEmpty()) fail("Server exited: " + new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            }
            fail("Protocol response timed out: " + history); return null;
        }
        void stop() throws Exception { request(999, "shutdown", object()); assertTrue(response(999).get("result").isJsonNull()); notify("exit", object()); assertTrue(process.waitFor(5, TimeUnit.SECONDS)); assertEquals(0, process.exitValue()); }
        public void close() { process.destroy(); try { if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } }
    }
}
