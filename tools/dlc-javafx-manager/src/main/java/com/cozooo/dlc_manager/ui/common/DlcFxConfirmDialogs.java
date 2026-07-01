package com.cozooo.dlc_manager.ui.common;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.awt.Desktop;
import java.net.URI;

public final class DlcFxConfirmDialogs {
    private DlcFxConfirmDialogs() {
    }

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                new ButtonType(DlcFxI18n.tr("dlc_manager.confirm_no"), ButtonData.CANCEL_CLOSE),
                new ButtonType(DlcFxI18n.tr("dlc_manager.confirm_yes"), ButtonData.OK_DONE));
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL).getButtonData() == ButtonData.OK_DONE;
    }

    public static boolean openLink(String title, String message, String url, Runnable copied) {
        ButtonType cancel = new ButtonType(DlcFxI18n.tr("dlc_manager.common.cancel"), ButtonData.CANCEL_CLOSE);
        ButtonType copy = new ButtonType(DlcFxI18n.tr("dlc_manager.redirect.copy"), ButtonData.OTHER);
        ButtonType open = new ButtonType(DlcFxI18n.tr("dlc_manager.redirect.open"), ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, cancel, copy, open);
        alert.setTitle(title);
        alert.setHeaderText(null);
        ButtonType result = alert.showAndWait().orElse(cancel);
        if (result == copy) {
            ClipboardContent content = new ClipboardContent();
            content.putString(url == null ? "" : url);
            Clipboard.getSystemClipboard().setContent(content);
            if (copied != null) {
                copied.run();
            }
            return false;
        }
        if (result == open) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI.create(url));
                }
            } catch (Exception ignored) {
                return false;
            }
            return true;
        }
        return false;
    }
}
