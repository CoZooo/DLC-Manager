package com.cozooo.dlc_manager.ui.packs;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;

import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.awt.Desktop;
import java.net.URI;
import java.util.function.Consumer;

public final class DlcFxPackLinkActions {
    private DlcFxPackLinkActions() {
    }

    public static void showExternalOpenConfirm(Scene scene,
                                               String title,
                                               String message,
                                               Runnable onConfirm,
                                               Consumer<String> statusSink) {
        String url = extractLastUrlLine(message);
        DlcFxModalOverlay.show(
            scene,
            title,
            DlcFxRichTextRenderer.formatted(message),
            onConfirm,
            DlcFxI18n.tr("dlc_manager.common.confirm"),
            () -> {},
            DlcFxI18n.tr("dlc_manager.common.cancel"),
            url == null ? null : () -> {
                copyLink(url);
                if (statusSink != null) {
                    statusSink.accept(DlcFxI18n.tr("dlc_manager.redirect.copied"));
                }
            },
            url == null ? null : DlcFxI18n.tr("dlc_manager.redirect.copy")
        );
    }

    public static void showOpenLinkConfirm(Scene scene, String url, Consumer<String> statusSink) {
        if (url == null || url.isBlank()) {
            return;
        }
        showExternalOpenConfirm(
            scene,
            DlcFxI18n.tr("dlc_manager.pack.open_link_title"),
            DlcFxI18n.tr("dlc_manager.pack.open_link_prompt", url),
            () -> openLink(url, statusSink),
            statusSink
        );
    }

    public static String extractLastUrlLine(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String[] lines = message.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.startsWith("http://") || line.startsWith("https://")) {
                return line;
            }
        }
        return null;
    }

    public static void copyLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(url);
        Clipboard.getSystemClipboard().setContent(content);
    }

    public static void openLink(String url, Consumer<String> statusSink) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ex) {
            if (statusSink != null) {
                String msg = ex.getMessage();
                statusSink.accept(DlcFxI18n.tr("dlc_manager.status.open_failed",
                    msg == null || msg.isBlank() ? ex.getClass().getSimpleName() : msg));
            }
        }
    }
}
