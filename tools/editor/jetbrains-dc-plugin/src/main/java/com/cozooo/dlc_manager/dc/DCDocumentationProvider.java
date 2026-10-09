package com.cozooo.dlc_manager.dc;

import com.google.gson.*;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;

public final class DCDocumentationProvider extends AbstractDocumentationProvider {
    @Override public String generateDoc(PsiElement element, PsiElement originalElement) {
        PsiElement target = originalElement == null ? element : originalElement;
        if (target == null || !(target.getContainingFile() instanceof DCPsiFile file) || file.getVirtualFile() == null) return null;
        var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return null;
        var snapshot = DCLanguageServer.snapshot(file.getVirtualFile(), document);
        JsonObject params = new JsonObject(); params.add("position", DCLanguageServer.position(snapshot.text(), target.getTextOffset()));
        JsonElement response = DCLanguageServer.await(DCLanguageServer.get(file.getProject()).request(snapshot, "textDocument/hover", params));
        if (response == null || !response.isJsonObject() || document.getModificationStamp() != snapshot.stamp()) return null;
        JsonElement contents = response.getAsJsonObject().get("contents");
        if (contents == null || contents.isJsonNull()) return null;
        String text = contents.isJsonObject() ? contents.getAsJsonObject().get("value").getAsString() : contents.getAsString();
        return "<pre>" + StringUtil.escapeXmlEntities(text) + "</pre>";
    }
}
