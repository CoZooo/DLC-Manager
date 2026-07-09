package com.cozooo.dlc_manager.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class DlcRconClient implements AutoCloseable {
    private static final int TYPE_RESPONSE = 0;
    private static final int TYPE_COMMAND = 2;
    private static final int TYPE_LOGIN = 3;

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private int requestId = 1;

    private DlcRconClient(String host, int port, int timeoutMs) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), timeoutMs);
        this.socket.setSoTimeout(timeoutMs);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    public static String execute(String host, int port, String password, String command, int timeoutMs) {
        if (host == null || host.isBlank()) {
            return null;
        }
        if (command == null || command.isBlank()) {
            return null;
        }
        try (DlcRconClient client = new DlcRconClient(host, port, timeoutMs)) {
            if (!client.login(password == null ? "" : password)) {
                return null;
            }
            return client.command(command);
        } catch (IOException ignored) {
            return null;
        }
    }

    private boolean login(String password) throws IOException {
        int id = send(TYPE_LOGIN, password);
        RconResponse response = read();
        return response != null && response.id == id && response.id != -1;
    }

    private String command(String command) throws IOException {
        int id = send(TYPE_COMMAND, command);
        RconResponse response = read();
        if (response == null || response.id != id) {
            return null;
        }
        return response.payload;
    }

    private int send(int type, String payload) throws IOException {
        int id = requestId++;
        byte[] payloadBytes = payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
        int length = 4 + 4 + payloadBytes.length + 2;
        ByteBuffer buffer = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(length);
        buffer.putInt(id);
        buffer.putInt(type);
        buffer.put(payloadBytes);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        out.write(buffer.array());
        out.flush();
        return id;
    }

    private RconResponse read() throws IOException {
        int length;
        try {
            length = Integer.reverseBytes(in.readInt());
        } catch (IOException e) {
            return null;
        }
        if (length <= 0) {
            return null;
        }
        byte[] data = new byte[length];
        in.readFully(data);
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int id = buffer.getInt();
        int type = buffer.getInt();
        int payloadLength = length - 8 - 2;
        if (payloadLength < 0) {
            payloadLength = 0;
        }
        byte[] payloadBytes = new byte[payloadLength];
        buffer.get(payloadBytes);
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        return new RconResponse(id, type, payload);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private static final class RconResponse {
        private final int id;
        private final int type;
        private final String payload;

        private RconResponse(int id, int type, String payload) {
            this.id = id;
            this.type = type;
            this.payload = payload;
        }
    }
}
