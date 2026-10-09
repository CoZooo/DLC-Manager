package com.cozooo.dlc_manager.dc;

import com.google.gson.*;
import com.intellij.lang.annotation.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class DCExternalAnnotator extends ExternalAnnotator<DCExternalAnnotator.Input, DCExternalAnnotator.Result> {
    @Override public Input collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        if (!(file instanceof DCPsiFile) || file.getVirtualFile() == null) return null;
        var snapshot = DCLanguageServer.snapshot(file.getVirtualFile(), editor.getDocument());
        var service = DCLanguageServer.get(file.getProject());
        service.sync(snapshot);
        return new Input(service, snapshot);
    }
    @Override public Result doAnnotate(Input input) { return new Result(input.snapshot, input.service.diagnostics(input.snapshot)); }
    @Override public void apply(@NotNull PsiFile file, Result result, @NotNull AnnotationHolder holder) {
        if (result == null || result.snapshot.document().getModificationStamp() != result.snapshot.stamp()) return;
        for (JsonElement value : result.values) {
            JsonObject diagnostic = value.getAsJsonObject();
            JsonObject range = diagnostic.getAsJsonObject("range");
            int start = DCLanguageServer.offset(result.snapshot.text(), range.getAsJsonObject("start"));
            int end = DCLanguageServer.offset(result.snapshot.text(), range.getAsJsonObject("end"));
            if (end == start && !result.snapshot.text().isEmpty()) { start = Math.min(start, result.snapshot.text().length() - 1); end = start + 1; }
            HighlightSeverity severity = switch (diagnostic.has("severity") ? diagnostic.get("severity").getAsInt() : 1) {
                case 2 -> HighlightSeverity.WARNING;
                case 3 -> HighlightSeverity.WEAK_WARNING;
                case 4 -> HighlightSeverity.INFORMATION;
                default -> HighlightSeverity.ERROR;
            };
            holder.newAnnotation(severity, diagnostic.get("message").getAsString()).range(new TextRange(start, Math.max(start, end))).create();
        }
    }
    public record Input(DCLanguageServer service, DCLanguageServer.Snapshot snapshot) { }
    public record Result(DCLanguageServer.Snapshot snapshot, JsonArray values) { }
}
