package com.cozooo.dlc_manager.dc;

import com.google.gson.*;
import com.intellij.formatting.service.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

public final class DCFormattingService extends AsyncDocumentFormattingService {
    @Override public @NotNull Set<Feature> getFeatures() { return Set.of(Feature.AD_HOC_FORMATTING); }
    @Override public boolean canFormat(@NotNull PsiFile file) { return file instanceof DCPsiFile; }
    @Override protected @NotNull String getNotificationGroupId() { return "DC Language Server"; }
    @Override protected @NotNull String getName() { return "DC Language Server"; }
    @Override protected Duration getTimeout() { return Duration.ofSeconds(10); }
    @Override protected FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest request) {
        var file = request.getContext().getContainingFile();
        var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (file.getVirtualFile() == null || document == null) return null;
        var snapshot = new DCLanguageServer.Snapshot(java.nio.file.Path.of(file.getVirtualFile().getPath()).toUri().toString(),
            request.getDocumentText(), document.getModificationStamp(), document);
        var indent = request.getContext().getCodeStyleSettings().getIndentOptions(file.getFileType());
        int tabSize = indent.TAB_SIZE;
        boolean insertSpaces = !indent.USE_TAB_CHARACTER;
        return new FormattingTask() {
            private volatile CompletableFuture<JsonElement> pending;
            private volatile boolean cancelled;
            @Override public void run() {
                if (cancelled) return;
                JsonObject options = new JsonObject(); options.addProperty("tabSize", tabSize); options.addProperty("insertSpaces", insertSpaces);
                JsonObject params = new JsonObject(); params.add("options", options);
                pending = DCLanguageServer.get(file.getProject()).request(snapshot, "textDocument/formatting", params);
                pending.whenComplete((result, error) -> {
                    if (cancelled) return;
                    if (error != null) { request.onError("DC formatting failed", error.getMessage()); return; }
                    if (result == null || !result.isJsonArray()) { request.onError("DC formatting failed", "Language server returned no edits"); return; }
                    List<JsonObject> edits = new ArrayList<>(); result.getAsJsonArray().forEach(value -> edits.add(value.getAsJsonObject()));
                    edits.sort(Comparator.comparingInt((JsonObject edit) -> DCLanguageServer.offset(snapshot.text(), edit.getAsJsonObject("range").getAsJsonObject("start"))).reversed());
                    StringBuilder text = new StringBuilder(snapshot.text());
                    for (JsonObject edit : edits) {
                        JsonObject range = edit.getAsJsonObject("range");
                        text.replace(DCLanguageServer.offset(snapshot.text(), range.getAsJsonObject("start")),
                            DCLanguageServer.offset(snapshot.text(), range.getAsJsonObject("end")), edit.get("newText").getAsString());
                    }
                    request.onTextReady(text.toString());
                });
            }
            @Override public boolean cancel() { cancelled = true; if (pending != null) pending.cancel(true); return true; }
        };
    }
}
