package com.cozooo.dlc_manager.dc;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class DCStdioClient implements AutoCloseable {
    private final Process process;
    private final Map<Long, CompletableFuture<JsonElement>> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();
    private final BiConsumer<String, JsonObject> notifications;
    private final Consumer<String> log;
    private volatile boolean closed;
    private volatile Throwable failure;

    public DCStdioClient(List<String> command, BiConsumer<String, JsonObject> notifications, Consumer<String> log) throws IOException {
        this.notifications = notifications;
        this.log = log;
        process = new ProcessBuilder(command).start();
        daemon("DC language server responses", this::read);
        daemon("DC language server log", () -> {
            try (var reader = process.errorReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) log.accept(line);
            } catch (IOException ignored) { }
        });
    }

    private static void daemon(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        thread.start();
    }

    public CompletableFuture<JsonElement> initialize(String rootUri) {
        return initialize(rootUri, new JsonObject());
    }

    public CompletableFuture<JsonElement> initialize(String rootUri, JsonObject initializationOptions) {
        JsonObject params = new JsonObject();
        params.addProperty("processId", ProcessHandle.current().pid());
        if (rootUri == null) params.add("rootUri", JsonNull.INSTANCE); else params.addProperty("rootUri", rootUri);
        params.add("capabilities", JsonParser.parseString("{\"general\":{\"positionEncodings\":[\"utf-16\"]},\"textDocument\":{\"completion\":{\"completionItem\":{\"snippetSupport\":false}},\"publishDiagnostics\":{\"versionSupport\":true}}}"));
        params.add("initializationOptions", initializationOptions == null ? new JsonObject() : initializationOptions.deepCopy());
        return request("initialize", params, Duration.ofSeconds(15)).thenApply(result -> {
            notify("initialized", new JsonObject());
            return result;
        });
    }

    public CompletableFuture<JsonElement> request(String method, JsonElement params) {
        return request(method, params, Duration.ofSeconds(8));
    }

    public CompletableFuture<JsonElement> request(String method, JsonElement params, Duration timeout) {
        long id = nextId.incrementAndGet();
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        pending.put(id, future);
        JsonObject value = message(method, params);
        value.addProperty("id", id);
        future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).whenComplete((ignored, error) -> {
            pending.remove(id);
            if (error instanceof CancellationException || error instanceof TimeoutException) {
                JsonObject cancel = new JsonObject(); cancel.addProperty("id", id);
                try { notify("$/cancelRequest", cancel); } catch (RuntimeException ignoredError) { }
            }
        });
        try { send(value); } catch (RuntimeException error) { future.completeExceptionally(error); }
        return future;
    }

    public void notify(String method, JsonElement params) { send(message(method, params)); }

    private static JsonObject message(String method, JsonElement params) {
        JsonObject value = new JsonObject();
        value.addProperty("jsonrpc", "2.0"); value.addProperty("method", method); value.add("params", params);
        return value;
    }

    private synchronized void send(JsonObject value) {
        if (failure != null) throw new IllegalStateException("DC language server unavailable", failure);
        try {
            byte[] data = value.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream output = process.getOutputStream();
            output.write(("Content-Length: " + data.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(data); output.flush();
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private void read() {
        try (InputStream input = new BufferedInputStream(process.getInputStream())) {
            while (!closed) {
                ByteArrayOutputStream header = new ByteArrayOutputStream();
                int suffix = 0;
                while (suffix != 0x0d0a0d0a) {
                    int b = input.read();
                    if (b < 0) throw new EOFException("DC language server exited");
                    header.write(b); suffix = (suffix << 8) | b;
                    if (header.size() > 8192) throw new IOException("Language server frame header is too large");
                }
                int size = -1;
                for (String line : header.toString(StandardCharsets.US_ASCII).split("\r\n")) {
                    int colon = line.indexOf(':');
                    if (colon >= 0 && line.substring(0, colon).equalsIgnoreCase("Content-Length")) size = Integer.parseInt(line.substring(colon + 1).trim());
                }
                if (size < 0 || size > 16 * 1024 * 1024) throw new IOException("Invalid language server frame length");
                byte[] bytes = input.readNBytes(size);
                if (bytes.length != size) throw new EOFException("Incomplete language server frame");
                JsonObject value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                if (value.has("id") && !value.has("method")) {
                    CompletableFuture<JsonElement> future = pending.remove(value.get("id").getAsLong());
                    if (future != null) {
                        if (value.has("error")) future.completeExceptionally(new IOException(value.getAsJsonObject("error").get("message").getAsString()));
                        else future.complete(value.get("result"));
                    }
                } else if (value.has("method") && !value.has("id")) {
                    JsonElement params = value.get("params");
                    notifications.accept(value.get("method").getAsString(), params != null && params.isJsonObject() ? params.getAsJsonObject() : new JsonObject());
                } else if (value.has("id")) {
                    JsonObject answer = new JsonObject(); answer.addProperty("jsonrpc", "2.0"); answer.add("id", value.get("id"));
                    answer.add("error", JsonParser.parseString("{\"code\":-32601,\"message\":\"Client method not supported\"}")); send(answer);
                }
            }
        } catch (Exception error) {
            if (!closed) { failure = error; log.accept(error.getMessage()); process.destroyForcibly(); }
            pending.values().forEach(value -> value.completeExceptionally(error));
            pending.clear();
        }
    }

    public boolean isAlive() { return process.isAlive(); }

    @Override public void close() {
        if (closed) return;
        try { request("shutdown", JsonNull.INSTANCE, Duration.ofMillis(700)).get(800, TimeUnit.MILLISECONDS); notify("exit", JsonNull.INSTANCE); }
        catch (Exception ignored) { }
        closed = true;
        try { process.getOutputStream().close(); if (!process.waitFor(700, TimeUnit.MILLISECONDS)) process.destroyForcibly(); }
        catch (Exception ignored) { process.destroyForcibly(); }
        pending.values().forEach(value -> value.completeExceptionally(new IOException("DC language server stopped")));
        pending.clear();
    }
}
