package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class DlcSyncTestServer implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private final ServerSocket listener;
    private final ExecutorService executor;
    private final AtomicReference<Socket> connected = new AtomicReference<>();
    private final Future<?> completed;

    @FunctionalInterface
    interface Handler {
        void handle(Connection connection) throws Exception;
    }

    DlcSyncTestServer(Handler handler) throws Exception {
        listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "dlc-sync-test-server");
            thread.setDaemon(true);
            return thread;
        });
        completed = executor.submit(() -> {
            try (Socket socket = listener.accept()) {
                connected.set(socket);
                socket.setSoTimeout(5000);
                handler.handle(new Connection(socket));
            }
            return null;
        });
    }

    DlcServerEntry entry() {
        DlcServerEntry entry = new DlcServerEntry();
        entry.host = "127.0.0.1";
        entry.port = listener.getLocalPort();
        entry.password = "test-password";
        return entry;
    }

    void awaitCompleted() throws Exception {
        completed.get(5, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        listener.close();
        Socket socket = connected.get();
        if (socket != null) {
            socket.close();
        }
        executor.shutdownNow();
    }

    static final class Connection {
        private final BufferedReader reader;
        private final BufferedWriter writer;

        Connection(Socket socket) throws Exception {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        JsonObject read() throws Exception {
            String line = reader.readLine();
            return line == null ? null : GSON.fromJson(line, JsonObject.class);
        }

        void send(JsonObject response) throws Exception {
            writer.write(GSON.toJson(response));
            writer.newLine();
            writer.flush();
        }

        void reply(String type) throws Exception {
            JsonObject response = new JsonObject();
            response.addProperty("type", type);
            response.addProperty("ok", true);
            send(response);
        }
    }
}
