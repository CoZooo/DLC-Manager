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
    private final StringProperty statusText = new SimpleStringProperty("未连接");
    private final StringProperty serverInfoText = new SimpleStringProperty("");

    private volatile DlcTcpFacade facade;
    private volatile SyncModels.ServerInfo lastServerInfo;

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
                Platform.runLater(() -> {
                    connected.set(true);
                    statusText.set("已连接 " + host + ":" + port);
                    serverInfoText.set(String.format("base=%s  dlc=%s  缺失: remote=%d required=%d",
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
        if (f != null) {
            try { f.close(); } catch (Exception ignored) {}
        }
        connected.set(false);
        statusText.set("未连接");
        serverInfoText.set("");
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
            Platform.runLater(() -> onError.accept(new IllegalStateException("未连接到服务器")));
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
