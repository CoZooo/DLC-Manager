package com.cozooo.dlc_manager.dc;

import com.google.gson.*;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Service(Service.Level.PROJECT)
public final class DCLanguageServer implements Disposable {
    private static final Logger LOG = Logger.getInstance(DCLanguageServer.class);
    private final Project project;
    private final ExecutorService queue = AppExecutorUtil.createBoundedApplicationPoolExecutor("DC language server", 1);
    private final Map<String, Synced> documents = new ConcurrentHashMap<>();
    private final Map<String, DiagnosticState> diagnostics = new ConcurrentHashMap<>();
    private volatile DCStdioClient client;
    private volatile boolean disposed;
    private final CompletableFuture<DCStdioClient> ready;

    public DCLanguageServer(Project project) {
        this.project = project;
        ready = CompletableFuture.supplyAsync(() -> {
            try {
                Path plugin = Objects.requireNonNull(PluginManagerCore.getPlugin(PluginId.getId("com.cozooo.dlc_manager.dc"))).getPluginPath();
                Path server = plugin.resolve("server/dclanguage-lsp.jar");
                if (!Files.isRegularFile(server)) throw new IllegalStateException("Bundled DC language server is missing: " + server);
                Path java = javaExecutable();
                if (disposed) throw new IllegalStateException("Project disposed");
                client = new DCStdioClient(List.of(java.toString(), "-jar", server.toString(), "--stdio"), this::notification, LOG::warn);
                if (disposed) { client.close(); throw new IllegalStateException("Project disposed"); }
                client.initialize(project.getBasePath() == null ? null : Path.of(project.getBasePath()).toUri().toString()).get(15, TimeUnit.SECONDS);
                return client;
            } catch (Exception error) { if (client != null) client.close(); LOG.warn("Cannot start DC language server; Java 17+ is required", error); throw new CompletionException(error); }
        }, queue);
    }

    private static Path javaExecutable() throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<Path> candidates = new ArrayList<>();
        String configured = System.getenv("JAVA_HOME");
        if (configured != null && !configured.isBlank()) candidates.add(Path.of(configured, "bin", executable));
        candidates.add(Path.of(System.getProperty("java.home"), "bin", executable));
        for (Path candidate : candidates) {
            if (!Files.isExecutable(candidate)) continue;
            Process probe = new ProcessBuilder(candidate.toString(), "-version").redirectErrorStream(true).start();
            try {
                if (!probe.waitFor(3, TimeUnit.SECONDS)) continue;
                String output;
                try (var input = probe.getInputStream()) { output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
                var match = java.util.regex.Pattern.compile("(?:openjdk|java) version \"(\\d+)(?:\\.(\\d+))?").matcher(output);
                if (match.find() && Integer.parseInt(match.group(1).equals("1") ? match.group(2) : match.group(1)) >= 17) return candidate;
            } finally { if (probe.isAlive()) probe.destroyForcibly(); }

        }
        throw new IllegalStateException("Java 17+ was not found in JAVA_HOME or the IDE runtime");
    }

    public static DCLanguageServer get(Project project) { return project.getService(DCLanguageServer.class); }

    public static Snapshot snapshot(VirtualFile file, Document document) {
        return new Snapshot(Path.of(file.getPath()).toUri().toString(), document.getText(), document.getModificationStamp(), document);
    }

    public void sync(Snapshot snapshot) {
        if (!disposed) CompletableFuture.runAsync(() -> syncNow(snapshot), queue).exceptionally(error -> null);
    }

    private void syncNow(Snapshot snapshot) {
        if (disposed || snapshot.document.getModificationStamp() != snapshot.stamp) return;
        DCStdioClient connection = ready.join();
        Synced previous = documents.get(snapshot.uri);
        if (previous != null && previous.stamp == snapshot.stamp) return;
        int version = previous == null ? 1 : previous.version + 1;
        documents.put(snapshot.uri, new Synced(snapshot.stamp, version, snapshot.document));
        JsonObject identity = new JsonObject(); identity.addProperty("uri", snapshot.uri); identity.addProperty("version", version);
        JsonObject params = new JsonObject(); params.add("textDocument", identity);
        if (previous == null) {
            identity.addProperty("languageId", "dc"); identity.addProperty("text", snapshot.text);
            connection.notify("textDocument/didOpen", params);
        } else {
            JsonObject content = new JsonObject(); content.addProperty("text", snapshot.text);
            JsonArray changes = new JsonArray(); changes.add(content); params.add("contentChanges", changes);
            connection.notify("textDocument/didChange", params);
        }
    }

    public CompletableFuture<JsonElement> request(Snapshot snapshot, String method, JsonObject params) {
        if (disposed) return CompletableFuture.failedFuture(new IllegalStateException("Project disposed"));
        CompletableFuture<JsonElement> result = new CompletableFuture<>();
        var response = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<JsonElement>>();
        Future<?> task;
        try {
            task = queue.submit(() -> {
                try {
                    if (result.isCancelled()) return;
                    if (snapshot.document.getModificationStamp() != snapshot.stamp) { result.complete(JsonNull.INSTANCE); return; }
                    syncNow(snapshot);
                    JsonObject identity = new JsonObject(); identity.addProperty("uri", snapshot.uri); params.add("textDocument", identity);
                    var pending = ready.join().request(method, params);
                    response.set(pending);
                    if (result.isCancelled()) pending.cancel(true);
                    else pending.whenComplete((value, error) -> { if (error == null) result.complete(value); else result.completeExceptionally(error); });
                } catch (Exception error) { result.completeExceptionally(error); }
            });
        } catch (RejectedExecutionException error) { return CompletableFuture.failedFuture(error); }
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) { task.cancel(false); if (response.get() != null) response.get().cancel(true); }
        });
        return result;
    }

    public void close(String uri) {
        if (disposed) return;
        CompletableFuture.runAsync(() -> {
            if (documents.remove(uri) == null) return;
            diagnostics.remove(uri);
            JsonObject identity = new JsonObject(); identity.addProperty("uri", uri);
            JsonObject params = new JsonObject(); params.add("textDocument", identity);
            ready.join().notify("textDocument/didClose", params);
        }, queue).exceptionally(error -> null);
    }

    private void notification(String method, JsonObject params) {
        if (!method.equals("textDocument/publishDiagnostics") || disposed || project.isDisposed()) return;
        String uri = params.get("uri").getAsString();
        Synced state = documents.get(uri);
        if (state == null || params.has("version") && params.get("version").getAsInt() != state.version) return;
        JsonArray values = params.getAsJsonArray("diagnostics");
        diagnostics.put(uri, new DiagnosticState(state.stamp, values));
        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed || project.isDisposed()) return;
            var file = PsiDocumentManager.getInstance(project).getPsiFile(state.document);
            if (file != null && file.isValid()) DaemonCodeAnalyzer.getInstance(project).restart(file);
        });
    }

    public JsonArray diagnostics(Snapshot snapshot) {
        DiagnosticState state = diagnostics.get(snapshot.uri);
        return state != null && state.stamp == snapshot.stamp ? state.values : new JsonArray();
    }

    public static JsonObject position(String text, int offset) {
        int safe = Math.max(0, Math.min(offset, text.length()));
        int line = 0, start = 0;
        for (int i = 0; i < safe; i++) {
            if (text.charAt(i) == '\r' || text.charAt(i) == '\n') {
                if (text.charAt(i) == '\r' && i + 1 < safe && text.charAt(i + 1) == '\n') i++;
                line++; start = i + 1;
            }
        }
        JsonObject value = new JsonObject(); value.addProperty("line", line); value.addProperty("character", safe - start); return value;
    }

    public static int offset(String text, JsonObject position) {
        int line = Math.max(0, position.get("line").getAsInt()), character = Math.max(0, position.get("character").getAsInt());
        int start = 0, current = 0;
        while (start < text.length() && current < line) {
            char value = text.charAt(start++);
            if (value == '\r' || value == '\n') {
                if (value == '\r' && start < text.length() && text.charAt(start) == '\n') start++;
                current++;
            }
        }
        int end = start;
        while (end < text.length() && text.charAt(end) != '\r' && text.charAt(end) != '\n') end++;
        return Math.min(start + character, end);
    }

    public static JsonElement await(CompletableFuture<JsonElement> future) {
        if (ApplicationManager.getApplication().isDispatchThread()) { future.cancel(true); return JsonNull.INSTANCE; }
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        try {
            while (System.nanoTime() < end) {
                ProgressManager.checkCanceled();
                try { return future.get(50, TimeUnit.MILLISECONDS); } catch (TimeoutException ignored) { }
            }
            return JsonNull.INSTANCE;
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); return JsonNull.INSTANCE; }
        catch (ExecutionException error) { return JsonNull.INSTANCE; }
        finally { if (!future.isDone()) future.cancel(true); }
    }

    @Override public void dispose() {
        disposed = true;
        queue.shutdownNow();
        ready.whenComplete((ignored, error) -> AppExecutorUtil.getAppExecutorService().execute(() -> { if (client != null) client.close(); }));
        documents.clear(); diagnostics.clear();
    }

    public record Snapshot(String uri, String text, long stamp, Document document) { }
    private record Synced(long stamp, int version, Document document) { }
    private record DiagnosticState(long stamp, JsonArray values) { }
}
