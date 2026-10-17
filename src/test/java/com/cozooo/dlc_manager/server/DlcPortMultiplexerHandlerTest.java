package com.cozooo.dlc_manager.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPortMultiplexerHandlerTest {
    @Test
    void ordinaryMinecraftBytesArePassedThroughWithoutModification() {
        AtomicInteger downstreamReads = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel(
                new DlcPortMultiplexerHandler(),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(io.netty.channel.ChannelHandlerContext context, Object message) {
                        downstreamReads.incrementAndGet();
                        context.fireChannelRead(message);
                    }
                });
        byte[] handshake = new byte[]{0, 7, 1, 2, 3, 4, 5};

        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(handshake)));
        ByteBuf forwarded = channel.readInbound();
        assertNotNull(forwarded);
        byte[] actual = new byte[forwarded.readableBytes()];
        forwarded.readBytes(actual);
        forwarded.release();
        assertArrayEquals(handshake, actual);
        assertEquals(1, downstreamReads.get());
        channel.finishAndReleaseAll();
    }

    @Test
    void dlcPrefaceIsConsumedBeforeDownstreamNetworkHandlers() {
        AtomicInteger downstreamReads = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel(
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(io.netty.channel.ChannelHandlerContext context, Object message) {
                        context.fireChannelRead(message);
                    }
                },
                new DlcPortMultiplexerHandler(),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(io.netty.channel.ChannelHandlerContext context, Object message) {
                        downstreamReads.incrementAndGet();
                        ReferenceCountUtil.release(message);
                    }
                });

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(DlcPortMultiplexerHandler.PREFACE)));
        assertEquals(0, downstreamReads.get());
        channel.finishAndReleaseAll();
    }

    @Test
    void dlcPrefaceIsConsumedAndBackendTrafficIsBridged() throws Exception {
        AtomicInteger outboundTransforms = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel(
                new DlcPortMultiplexerHandler(),
                new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(io.netty.channel.ChannelHandlerContext context, Object message,
                                      io.netty.channel.ChannelPromise promise) {
                        outboundTransforms.incrementAndGet();
                        context.write(message, promise);
                    }
                });
        byte[] request = "{\"type\":\"hello\",\"password\":\"\"}\n".getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[DlcPortMultiplexerHandler.PREFACE.length + request.length];
        System.arraycopy(DlcPortMultiplexerHandler.PREFACE, 0, payload, 0, DlcPortMultiplexerHandler.PREFACE.length);
        System.arraycopy(request, 0, payload, DlcPortMultiplexerHandler.PREFACE.length, request.length);

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(payload)));
        ByteBuf response = null;
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (response == null && System.nanoTime() < deadline) {
            channel.runPendingTasks();
            Object outbound = channel.readOutbound();
            if (outbound instanceof ByteBuf) {
                response = (ByteBuf) outbound;
            } else if (outbound != null) {
                throw new AssertionError("unexpected outbound message: " + outbound);
            } else {
                Thread.sleep(10);
            }
        }
        assertNotNull(response);
        byte[] actual = new byte[response.readableBytes()];
        response.readBytes(actual);
        response.release();
        assertTrue(new String(actual, StandardCharsets.UTF_8).contains("\"type\":\"hello\""));
        assertTrue(new String(actual, StandardCharsets.UTF_8).contains("\"ok\":true"));
        assertEquals(0, outboundTransforms.get());
        channel.finishAndReleaseAll();
    }

}
