package com.cozooo.dlc_manager.javafx;

import com.cozooo.dlc_manager.tools.externalsync.DlcTcpFacade;
import com.cozooo.dlc_manager.tools.externalsync.ExternalSyncException;
import com.cozooo.dlc_manager.tools.externalsync.SyncModels;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.function.Consumer;

/**
 * Shared connection state for all remote-server tabs.
 * <p>
 * Holds the current {@link DlcTcpFacade} (or {@code null} when disconnected),
 * exposes observable connection status for the UI, and runs blocking protocol
 * operations on a background thread, marshalling results back via
 * {@link Platform#runLater}.
 */
final class RemoteConnection {

    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final StringProperty statusText = new SimpleStringProperty(DlcFxI18n.tr("dlc_manager.javafx.status.disconnected"));
    private final StringProperty serverInfoText = new SimpleStringProperty("");

    private volatile DlcTcpFacade facade;
    private volatile SyncModels.ServerInfo lastServerInfo;
    private volatile String connectedHost;
    private volatile int connectedPort;

    BooleanProperty connectedProperty() { return connected; }
    StringProperty statusTextProperty() { return statusText; }
    StringProperty serverInfoTextProperty() { return serverInfoText; }

    boolean isConnected() { return connected.get(); }
    DlcTcpFacade facade() { return facade; }
    SyncModels.ServerInfo lastServerInfo() { return lastServerInfo; }

    /** Connect on a background thread.  {@code onDone} runs on the FX thread. */
    void connectAsync(String host, int port, String password, int timeoutMs, Consumer<Throwable> onDone) {
        Thread thread = new Thread(() -> {
            try {
                DlcTcpFacade f = DlcTcpFacade.connect(host, port, password, timeoutMs);
                SyncModels.ServerInfo info = f.fetchList();
                facade = f;
                lastServerInfo = info;
                connectedHost = host;
                connectedPort = port;
                Platform.runLater(() -> {
                    connected.set(true);
                    statusText.set(DlcFxI18n.tr("dlc_manager.javafx.remote.connected_to", host, port));
                    serverInfoText.set(DlcFxI18n.tr("dlc_manager.javafx.remote.server_info_value",
                            info.baseVersion, info.dlcVersion, info.remoteMissing, info.requiredMissing));
                    onDone.accept(null);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> onDone.accept(t));
            }
        }, "dlc-connect");
        thread.setDaemon(true);
        thread.start();
    }

    /** Disconnect and reset state. */
    void disconnect() {
        DlcTcpFacade f = facade;
        facade = null;
        lastServerInfo = null;
        connectedHost = null;
        connectedPort = 0;
        if (f != null) {
            try { f.close(); } catch (Exception ignored) {}
        }
        connected.set(false);
        statusText.set(DlcFxI18n.tr("dlc_manager.javafx.status.disconnected"));
        serverInfoText.set("");
    }

    void refreshLanguage() {
        if (connected.get()) {
            String host = connectedHost == null ? "" : connectedHost;
            statusText.set(DlcFxI18n.tr("dlc_manager.javafx.remote.connected_to", host, connectedPort));
            SyncModels.ServerInfo info = lastServerInfo;
            if (info != null) {
                serverInfoText.set(DlcFxI18n.tr("dlc_manager.javafx.remote.server_info_value",
                        info.baseVersion, info.dlcVersion, info.remoteMissing, info.requiredMissing));
            }
        } else {
            statusText.set(DlcFxI18n.tr("dlc_manager.javafx.status.disconnected"));
            serverInfoText.set("");
        }
    }

    /**
     * Run a blocking operation on a background thread.
     * @param action the operation to run (receives the current facade)
     * @param onSuccess runs on FX thread with the result
     * @param onError runs on FX thread with the exception
     */
    <T> void runAsync(AsyncAction<T> action,
                      Consumer<T> onSuccess, Consumer<Throwable> onError) {
        DlcTcpFacade f = facade;
        if (f == null) {
            Platform.runLater(() -> onError.accept(new IllegalStateException(DlcFxI18n.tr("dlc_manager.javafx.remote.not_connected"))));
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                T result = action.apply(f);
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable t) {
                Platform.runLater(() -> onError.accept(t));
            }
        }, "dlc-remote-op");
        thread.setDaemon(true);
        thread.start();
    }

    /** Functional interface that allows throwing checked exceptions. */
    @FunctionalInterface
    interface AsyncAction<T> {
        T apply(DlcTcpFacade facade) throws Exception;
    }

    static String safeMessage(Throwable t) {
        if (t instanceof ExternalSyncException) {
            return t.getMessage();
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }
}
