package com.cozooo.dclanguage.lsp;

import com.cozooo.dclanguage.DcLanguageService;
import com.cozooo.dclanguage.DcSchemaCatalog;
import static com.cozooo.dclanguage.generated.DcGeneratedSyntax.*;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DcLanguageServer {
    private record Document(String uri, String text, int version) { }
    private static final class Pending {
        final JsonElement id;
        final AtomicBoolean completed = new AtomicBoolean();
        Future<?> future;
        Pending(JsonElement id) { this.id = id; }
    }
    private static final int MAX_DOCUMENT_CHARS = 2_000_000;
    private static final int MAX_OPEN_CHARS = 8_000_000;
    private DcLanguageService service;
    private final MessageTransport transport;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Object state = new Object();
    private final Map<String, Document> documents = new HashMap<>();
    private final Map<String, Future<?>> diagnostics = new HashMap<>();
    private final ConcurrentMap<String, Pending> requests = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "dclanguage-analysis");
        thread.setDaemon(true);
        return thread;
    });
    private boolean initialized;
    private volatile boolean shutdown;
    private boolean exited;
    private String profile;
    public DcLanguageServer(InputStream input, OutputStream output, DcLanguageService service) {
        this.transport = new MessageTransport(input, output);
        this.service = Objects.requireNonNull(service);
    }
    public static void main(String[] args) {
        if (args.length > 1 || args.length == 1 && !args[0].equals("--stdio")) { System.err.println("Usage: dclanguage-lsp [--stdio]"); System.exit(2); }
        try { System.exit(new DcLanguageServer(System.in, System.out, new DcLanguageService()).run()); }
        catch (Exception exception) { System.err.println("DC language server: " + exception.getMessage()); System.exit(1); }
    }
    public int run() throws IOException {
        try {
            while (!exited) {
                String content = transport.read();
                if (content == null) break;
                JsonObject message;
                try {
                    JsonElement parsed = JsonParser.parseString(content);
                    if (!parsed.isJsonObject()) { error(JsonNull.INSTANCE, -32600, "Invalid request"); continue; }
                    message = parsed.getAsJsonObject();
                } catch (JsonParseException exception) { error(JsonNull.INSTANCE, -32700, "Parse error"); continue; }
                dispatch(message);
            }
            return shutdown ? 0 : 1;
        } finally { workers.shutdownNow(); }
    }
    private void dispatch(JsonObject message) throws IOException {
        JsonElement id = message.get("id");
        String method;
        try {
            if (!"2.0".equals(string(message, "jsonrpc"))) throw new IllegalArgumentException("Invalid JSON-RPC version");
            method = string(message, "method");
            if (method == null) throw new IllegalArgumentException("Missing method");
        } catch (RuntimeException exception) { error(id, -32600, "Invalid request"); return; }
        JsonObject params;
        try { params = message.has("params") && !message.get("params").isJsonNull() ? message.getAsJsonObject("params") : new JsonObject(); }
        catch (RuntimeException exception) { if (id != null) error(id, -32602, "Invalid parameters"); return; }
        if (method.equals("exit")) { exited = true; return; }
        if (shutdown) { if (id != null) error(id, -32600, "Server has shut down"); return; }
        if (!initialized && !method.equals("initialize")) { if (id != null) error(id, -32002, "Server not initialized"); return; }
        try {
            switch (method) {
                case "initialize" -> initialize(id, params);
                case "initialized", "textDocument/didSave", "$/setTrace" -> { }
                case "shutdown" -> {
                    synchronized (state) { shutdown = true; }
                    for (Pending pending : requests.values()) cancel(pending.id);
                    workers.shutdownNow();
                    result(id, JsonNull.INSTANCE);
                }
                case "$/cancelRequest" -> cancel(params.get("id"));
                case "textDocument/didOpen" -> open(params.getAsJsonObject("textDocument"));
                case "textDocument/didChange" -> change(params);
                case "textDocument/didClose" -> close(string(params.getAsJsonObject("textDocument"), "uri"));
                case "textDocument/completion", "textDocument/hover", "textDocument/formatting" -> analyze(id, method, params);
                default -> { if (id != null) error(id, -32601, "Method not found: " + method); }
            }
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
            if (id != null) error(id, -32602, "Invalid parameters: " + exception.getMessage());
            else System.err.println("DC language server ignored invalid notification: " + method);
        }
    }
    private void initialize(JsonElement id, JsonObject params) throws IOException {
        if (initialized) { error(id, -32600, "Initialize may only be sent once"); return; }
        JsonObject options = params.has("initializationOptions") && params.get("initializationOptions").isJsonObject() ? params.getAsJsonObject("initializationOptions") : new JsonObject();
        DcLanguageService registered = options.has("definition")
            ? new DcLanguageService(DcSchemaCatalog.fromJson(options.getAsJsonObject("definition"))) : service;
        String requestedProfile = string(options, "profile");
        if (requestedProfile != null) registered.catalog().select("", null, requestedProfile);
        service = registered;
        profile = requestedProfile;
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("positionEncoding", "utf-16");
        JsonObject sync = new JsonObject(); sync.addProperty("openClose", true); sync.addProperty("change", 2); capabilities.add("textDocumentSync", sync);
        JsonObject completion = new JsonObject(); completion.addProperty("resolveProvider", false); completion.add("triggerCharacters", gson.toJsonTree(java.util.stream.Stream.concat(java.util.stream.Stream.of(TOKEN_CATEGORY_OPEN, TOKEN_CATEGORY_SEPARATOR, TOKEN_ASSIGNMENT), STRING_RULES.stream().filter(rule -> !rule.multiline()).map(rule -> rule.delimiter())).filter(value -> !value.isEmpty()).map(value -> new String(Character.toChars(value.codePointBefore(value.length())))).distinct().toList())); capabilities.add("completionProvider", completion);
        capabilities.addProperty("hoverProvider", true); capabilities.addProperty("documentFormattingProvider", true);
        JsonObject response = new JsonObject(); response.add("capabilities", capabilities);
        JsonObject info = new JsonObject(); info.addProperty("name", "DC Language Server"); info.addProperty("version", "0.1.1"); response.add("serverInfo", info);
        initialized = true;
        result(id, response);
    }
    private void open(JsonObject value) {
        String uri = required(value, "uri"), text = required(value, "text");
        int version = value.get("version").getAsInt();
        synchronized (state) { store(new Document(uri, text, version)); }
    }
    private void change(JsonObject params) {
        JsonObject reference = params.getAsJsonObject("textDocument");
        String uri = required(reference, "uri");
        int version = reference.get("version").getAsInt();
        synchronized (state) {
            Document previous = documents.get(uri);
            if (previous == null || version <= previous.version()) return;
            String text = previous.text();
            for (JsonElement item : params.getAsJsonArray("contentChanges")) {
                JsonObject change = item.getAsJsonObject();
                String replacement = required(change, "text");
                if (!change.has("range")) text = replacement;
                else {
                    JsonObject range = change.getAsJsonObject("range");
                    int start = DcLanguageService.offset(text, position(range.getAsJsonObject("start")));
                    int end = DcLanguageService.offset(text, position(range.getAsJsonObject("end")));
                    if (start > end) throw new IllegalArgumentException("Reversed edit range");
                    if (change.has("rangeLength") && change.get("rangeLength").getAsInt() != end - start) throw new IllegalArgumentException("Edit length does not match its range");
                    text = text.substring(0, start) + replacement + text.substring(end);
                }
                if (text.length() > MAX_DOCUMENT_CHARS) throw new IllegalArgumentException("Document is too large");
            }
            store(new Document(uri, text, version));
        }
    }
    private void store(Document document) {
        int total = document.text().length();
        for (Document other : documents.values()) if (!other.uri().equals(document.uri())) total += other.text().length();
        if (document.text().length() > MAX_DOCUMENT_CHARS || total > MAX_OPEN_CHARS || !documents.containsKey(document.uri()) && documents.size() >= 128) throw new IllegalArgumentException("Open document limit exceeded");
        documents.put(document.uri(), document);
        Future<?> previous = diagnostics.remove(document.uri());
        if (previous != null) previous.cancel(true);
        diagnostics.put(document.uri(), workers.submit(() -> {
            var issues = service.diagnostics(document.uri(), document.text(), profile);
            synchronized (state) {
                if (shutdown || Thread.currentThread().isInterrupted() || documents.get(document.uri()) != document) return;
                JsonObject params = new JsonObject(); params.addProperty("uri", document.uri()); params.addProperty("version", document.version());
                JsonArray values = gson.toJsonTree(issues).getAsJsonArray();
                for (JsonElement value : values) value.getAsJsonObject().addProperty("source", "dclanguage");
                params.add("diagnostics", values);
                notify("textDocument/publishDiagnostics", params);
            }
        }));
    }
    private void close(String uri) {
        synchronized (state) {
            documents.remove(uri);
            Future<?> previous = diagnostics.remove(uri);
            if (previous != null) previous.cancel(true);
            JsonObject params = new JsonObject(); params.addProperty("uri", uri); params.add("diagnostics", new JsonArray());
            notify("textDocument/publishDiagnostics", params);
        }
    }
    private void analyze(JsonElement id, String method, JsonObject params) {
        if (id == null) return;
        String uri = required(params.getAsJsonObject("textDocument"), "uri");
        Document snapshot;
        synchronized (state) { snapshot = documents.get(uri); }
        Pending request = new Pending(id.deepCopy());
        String key = id.toString();
        request.future = new FutureTask<Void>(() -> {
            try {
                JsonElement response;
                if (snapshot == null) response = method.endsWith("hover") ? JsonNull.INSTANCE : new JsonArray();
                else if (method.endsWith("completion")) {
                    JsonArray values = new JsonArray();
                    for (var item : service.completions(uri, snapshot.text(), position(params.getAsJsonObject("position")), profile)) {
                        JsonObject value = new JsonObject(); value.addProperty("label", item.label()); value.addProperty("detail", item.detail()); value.addProperty("kind", item.kind());
                        JsonObject edit = new JsonObject(); edit.add("range", gson.toJsonTree(item.range())); edit.addProperty("newText", item.insertText()); value.add("textEdit", edit); values.add(value);
                    }
                    response = values;
                } else if (method.endsWith("hover")) {
                    var hover = service.hover(uri, snapshot.text(), position(params.getAsJsonObject("position")), profile);
                    if (hover == null) response = JsonNull.INSTANCE;
                    else { JsonObject value = new JsonObject(); JsonObject contents = new JsonObject(); contents.addProperty("kind", "markdown"); contents.addProperty("value", hover.markdown()); value.add("contents", contents); value.add("range", gson.toJsonTree(hover.range())); response = value; }
                } else {
                    JsonObject options = params.getAsJsonObject("options");
                    response = gson.toJsonTree(service.format(snapshot.text(), options.get("tabSize").getAsInt(), options.get("insertSpaces").getAsBoolean()));
                }
                synchronized (state) {
                    if (!request.completed.compareAndSet(false, true)) return null;
                    if (documents.get(uri) != snapshot) error(id, -32801, "Document changed while request was running");
                    else result(id, response);
                }
            } catch (Exception exception) {
                if (request.completed.compareAndSet(false, true)) try { error(id, -32602, "Cannot process request: " + exception.getMessage()); } catch (IOException ignored) { }
            } finally { requests.remove(key, request); }
            return null;
        });
        Pending old = requests.putIfAbsent(key, request);
        if (old != null) throw new IllegalArgumentException("Request id is already pending");
        workers.execute((Runnable) request.future);
    }
    private void cancel(JsonElement id) throws IOException {
        if (id == null) return;
        Pending request = requests.remove(id.toString());
        if (request != null && request.completed.compareAndSet(false, true)) {
            request.future.cancel(true);
            error(request.id, -32800, "Request cancelled");
        }
    }
    private void result(JsonElement id, JsonElement value) throws IOException {
        if (id == null) return;
        JsonObject message = new JsonObject(); message.addProperty("jsonrpc", "2.0"); message.add("id", id); message.add("result", value); transport.write(message);
    }
    private void error(JsonElement id, int code, String message) throws IOException {
        JsonObject response = new JsonObject(); response.addProperty("jsonrpc", "2.0"); response.add("id", id == null ? JsonNull.INSTANCE : id);
        JsonObject error = new JsonObject(); error.addProperty("code", code); error.addProperty("message", message); response.add("error", error); transport.write(response);
    }
    private void notify(String method, JsonObject params) {
        JsonObject message = new JsonObject(); message.addProperty("jsonrpc", "2.0"); message.addProperty("method", method); message.add("params", params);
        try { transport.write(message); } catch (IOException exception) { System.err.println("DC language server output closed"); }
    }
    private static DcLanguageService.Position position(JsonObject value) { return new DcLanguageService.Position(value.get("line").getAsInt(), value.get("character").getAsInt()); }
    private static String string(JsonObject value, String name) { return value.has(name) && !value.get(name).isJsonNull() ? value.get(name).getAsString() : null; }
    private static String required(JsonObject value, String name) { return Objects.requireNonNull(string(value, name), name); }
}
