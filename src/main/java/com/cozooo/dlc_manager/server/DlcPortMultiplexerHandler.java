package com.cozooo.dlc_manager.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class DlcPortMultiplexerHandler extends ChannelInboundHandlerAdapter {
    public static final byte[] PREFACE = new byte[]{'D', 'L', 'C', 'M', 1};
    private static final byte[] CLOSE_MARKER = new byte[0];
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;

    private final byte[] probe = new byte[PREFACE.length];
    private final LinkedBlockingQueue<byte[]> toBackend = new LinkedBlockingQueue<>();
    private final ExecutorService io = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "dlc-manager-port-multiplexer-connection");
        thread.setDaemon(true);
        return thread;
    });
    private int probeSize;
    private Mode mode = Mode.PROBING;
    private volatile boolean closed;
    private volatile Socket backend;
    private volatile OutputStream backendOutput;

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        if (!(message instanceof ByteBuf)) {
            context.fireChannelRead(message);
            return;
        }
        ByteBuf input = (ByteBuf) message;
        try {
            if (mode == Mode.MINECRAFT) {
                context.fireChannelRead(input.retain());
                return;
            }
            if (mode == Mode.PROBING) {
                if (!consumeProbe(input)) {
                    return;
                }
                if (!Arrays.equals(probe, PREFACE)) {
                    mode = Mode.MINECRAFT;
                    removeSelf(context);
                    ByteBuf replay = context.alloc().buffer(probe.length + input.readableBytes());
                    replay.writeBytes(probe);
                    replay.writeBytes(input, input.readerIndex(), input.readableBytes());
                    context.fireChannelRead(replay);
                    return;
                }
                mode = Mode.DLC;
                startBackend(context);
                if (input.isReadable()) {
                    byte[] bytes = new byte[input.readableBytes()];
                    input.readBytes(bytes);
                    enqueue(bytes);
                }
            }
            if (mode == Mode.DLC && input.isReadable()) {
                byte[] bytes = new byte[input.readableBytes()];
                input.readBytes(bytes);
                enqueue(bytes);
            }
        } finally {
            input.release();
        }
    }

    private boolean consumeProbe(ByteBuf input) {
        int count = Math.min(PREFACE.length - probeSize, input.readableBytes());
        input.readBytes(probe, probeSize, count);
        probeSize += count;
        return probeSize == PREFACE.length;
    }

    private void startBackend(ChannelHandlerContext context) {
        io.execute(() -> {
            ServerSocket listener = null;
            try {
                listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Socket client = new Socket();
                client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort()), CONNECT_TIMEOUT_MILLIS);
                Socket accepted = listener.accept();
                listener.close();
                listener = null;
                backend = client;
                backendOutput = client.getOutputStream();
                io.execute(() -> DlcServerService.handleClient(accepted));
                io.execute(this::writeBackendLoop);
                readBackendLoop(context, client);
            } catch (IOException error) {
                closeChannel(context);
            } finally {
                if (listener != null) {
                    try { listener.close(); } catch (IOException ignored) { }
                }
                closeBackend();
            }
        });
    }

    private void enqueue(byte[] bytes) {
        if (!closed && bytes.length > 0) {
            toBackend.offer(bytes);
        }
    }

    private void writeBackendLoop() {
        try {
            while (!closed) {
                byte[] bytes = toBackend.poll(1, TimeUnit.SECONDS);
                if (bytes == null) continue;
                if (bytes == CLOSE_MARKER) return;
                OutputStream output = backendOutput;
                if (output == null) {
                    toBackend.offer(bytes);
                    Thread.sleep(10);
                    continue;
                }
                output.write(bytes);
                output.flush();
            }
        } catch (IOException ignored) {
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void readBackendLoop(ChannelHandlerContext context, Socket socket) {
        try (InputStream input = socket.getInputStream()) {
            byte[] buffer = new byte[16 * 1024];
            while (!closed) {
                int count = input.read(buffer);
                if (count < 0) break;
                if (count == 0) continue;
                byte[] bytes = Arrays.copyOf(buffer, count);
                context.executor().execute(() -> {
                    writeRaw(context, bytes);
                });
            }
        } catch (IOException ignored) {
        } finally {
            closeChannel(context);
        }
    }

    private void removeSelf(ChannelHandlerContext context) {
        if (context.pipeline().context(this) != null) context.pipeline().remove(this);
    }

    private void closeChannel(ChannelHandlerContext context) {
        context.executor().execute(() -> {
            if (context.channel().isOpen()) context.close();
        });
    }

    private void writeRaw(ChannelHandlerContext context, byte[] bytes) {
        if (!context.channel().isActive()) return;
        context.channel().unsafe().write(Unpooled.wrappedBuffer(bytes), context.newPromise());
        context.channel().unsafe().flush();
    }

    private void closeBackend() {
        if (closed) return;
        closed = true;
        toBackend.offer(CLOSE_MARKER);
        OutputStream output = backendOutput;
        backendOutput = null;
        if (output != null) {
            try { output.close(); } catch (IOException ignored) { }
        }
        Socket socket = backend;
        backend = null;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        io.shutdownNow();
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        closeBackend();
        super.channelInactive(context);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext context) throws Exception {
        closeBackend();
        super.handlerRemoved(context);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        closeBackend();
        context.close();
    }

    private enum Mode { PROBING, MINECRAFT, DLC }
}
