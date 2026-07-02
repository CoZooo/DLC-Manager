package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;

import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerSession;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.function.Consumer;

public final class DlcFxServerConnection {
    private static final String PLAYER_NAME = "DLC JavaFX Manager";
    private static final String PLAYER_UUID = "dlc-javafx-manager";

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FAILED
    }

    public DlcFxServerConnection() {
    }

    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final ReadOnlyObjectWrapper<ConnectionState> connectionState =
            new ReadOnlyObjectWrapper<>(ConnectionState.DISCONNECTED);
    private final StringProperty statusText = new SimpleStringProperty(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final StringProperty serverInfoText = new SimpleStringProperty("");

    private volatile DlcServerSession session;
    private volatile DlcServerInfo lastServerInfo;
    private volatile String connectedHost;
    private volatile int connectedPort;

    BooleanProperty connectedProperty() { return connected; }
    public ReadOnlyObjectProperty<ConnectionState> connectionStateProperty() { return connectionState.getReadOnlyProperty(); }
    public StringProperty statusTextProperty() { return statusText; }
    StringProperty serverInfoTextProperty() { return serverInfoText; }

    boolean isConnected() { return connected.get(); }
    DlcServerSession facade() { return session; }
    DlcServerInfo lastServerInfo() { return lastServerInfo; }
    void rememberServerInfo(DlcServerInfo info) { lastServerInfo = info; }

    void connectAsync(String host, int port, String password, int timeoutMs, Consumer<Throwable> onDone) {
        DlcServerSession previous = session;
        session = null;
        if (previous != null) {
            try { previous.close(); } catch (Exception ignored) {}
        }
        connectionState.set(ConnectionState.CONNECTING);
        connected.set(false);
        statusText.set(DlcFxI18n.tr("dlc_manager.server.connecting"));
        Thread thread = new Thread(() -> {
            try {
                DlcServerSession created = DlcServerSession.connect(
                        host,
                        port,
                        password,
                        timeoutMs,
                        CrossPlatformRuntime.languageCode(),
                        PLAYER_NAME,
                        PLAYER_UUID);
                DlcServerInfo info = created.fetchList();
                session = created;
                lastServerInfo = info;
                connectedHost = host;
                connectedPort = port;
                Platform.runLater(() -> {
                    connected.set(true);
                    connectionState.set(ConnectionState.CONNECTED);
                    statusText.set(DlcFxI18n.tr("dlc_manager.server.connected_to", host, port));
                    serverInfoText.set(DlcFxI18n.tr("dlc_manager.server.connect_success"));
                    onDone.accept(null);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    connected.set(false);
                    connectionState.set(ConnectionState.FAILED);
                    statusText.set(DlcFxI18n.tr("dlc_manager.server.connect_failed") + ": " + safeMessage(t));
                    serverInfoText.set("");
                    onDone.accept(t);
                });
            }
        }, "dlc-connect");
        thread.setDaemon(true);
        thread.start();
    }

    void disconnect() {
        DlcServerSession f = session;
        session = null;
        lastServerInfo = null;
        connectedHost = null;
        connectedPort = 0;
        if (f != null) {
            try { f.close(); } catch (Exception ignored) {}
        }
        connected.set(false);
        connectionState.set(ConnectionState.DISCONNECTED);
        statusText.set(DlcFxI18n.tr("dlc_manager.status.disconnected"));
        serverInfoText.set("");
    }

    public void refreshLanguage() {
        ConnectionState state = connectionState.get();
        if (state == ConnectionState.CONNECTED) {
            String host = connectedHost == null ? "" : connectedHost;
            int port = connectedPort;
            statusText.set(DlcFxI18n.tr("dlc_manager.server.connected_to", host, port));
            serverInfoText.set(DlcFxI18n.tr("dlc_manager.server.connect_success"));
        } else if (state == ConnectionState.CONNECTING) {
            statusText.set(DlcFxI18n.tr("dlc_manager.server.connecting"));
            serverInfoText.set("");
        } else if (state == ConnectionState.FAILED) {
            if (statusText.get() == null || statusText.get().isBlank()
                    || DlcFxI18n.tr("dlc_manager.status.disconnected").equals(statusText.get())) {
                statusText.set(DlcFxI18n.tr("dlc_manager.server.connect_failed"));
            }
            serverInfoText.set("");
        } else {
            statusText.set(DlcFxI18n.tr("dlc_manager.status.disconnected"));
            serverInfoText.set("");
        }
    }

    /** Runs blocking protocol work off the UI thread and returns callbacks on the FX thread. */
    public <T> void runAsync(AsyncAction<T> action,
                             Consumer<T> onSuccess, Consumer<Throwable> onError) {
        DlcServerSession f = session;
        if (f == null) {
            Platform.runLater(() -> onError.accept(new IllegalStateException(DlcFxI18n.tr("dlc_manager.server.not_connected"))));
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
    public interface AsyncAction<T> {
        T apply(DlcServerSession facade) throws Exception;
    }

    static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }
}
