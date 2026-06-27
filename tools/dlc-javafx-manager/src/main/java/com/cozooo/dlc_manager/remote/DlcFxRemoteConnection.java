package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.ui.DlcFxI18n;

import com.cozooo.dlc_manager.tools.externalsync.DlcTcpFacade;
import com.cozooo.dlc_manager.tools.externalsync.ExternalSyncException;
import com.cozooo.dlc_manager.tools.externalsync.SyncModels;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.function.Consumer;

public final class DlcFxRemoteConnection {

    public DlcFxRemoteConnection() {
    }

    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final StringProperty statusText = new SimpleStringProperty(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final StringProperty serverInfoText = new SimpleStringProperty("");

    private volatile DlcTcpFacade facade;
    private volatile SyncModels.ServerInfo lastServerInfo;
    private volatile String connectedHost;
    private volatile int connectedPort;

    BooleanProperty connectedProperty() { return connected; }
    public StringProperty statusTextProperty() { return statusText; }
    StringProperty serverInfoTextProperty() { return serverInfoText; }

    boolean isConnected() { return connected.get(); }
    DlcTcpFacade facade() { return facade; }
    SyncModels.ServerInfo lastServerInfo() { return lastServerInfo; }

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
                    statusText.set(DlcFxI18n.tr("dlc_manager.remote.connected_to", host, port));
                    serverInfoText.set(DlcFxI18n.tr("dlc_manager.remote.server_info_value",
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
        statusText.set(DlcFxI18n.tr("dlc_manager.status.disconnected"));
        serverInfoText.set("");
    }

    public void refreshLanguage() {
        if (connected.get()) {
            String host = connectedHost == null ? "" : connectedHost;
            statusText.set(DlcFxI18n.tr("dlc_manager.remote.connected_to", host, connectedPort));
            SyncModels.ServerInfo info = lastServerInfo;
            if (info != null) {
                serverInfoText.set(DlcFxI18n.tr("dlc_manager.remote.server_info_value",
                        info.baseVersion, info.dlcVersion, info.remoteMissing, info.requiredMissing));
            }
        } else {
            statusText.set(DlcFxI18n.tr("dlc_manager.status.disconnected"));
            serverInfoText.set("");
        }
    }

    /** Runs blocking protocol work off the UI thread and returns callbacks on the FX thread. */
    <T> void runAsync(AsyncAction<T> action,
                      Consumer<T> onSuccess, Consumer<Throwable> onError) {
        DlcTcpFacade f = facade;
        if (f == null) {
            Platform.runLater(() -> onError.accept(new IllegalStateException(DlcFxI18n.tr("dlc_manager.remote.not_connected"))));
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
