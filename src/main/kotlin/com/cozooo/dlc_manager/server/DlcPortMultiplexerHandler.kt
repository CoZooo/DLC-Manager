package com.cozooo.dlc_manager.server

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class DlcPortMultiplexerHandler : ChannelInboundHandlerAdapter() {
    private val probe = ByteArray(PREFACE.size)
    private val toBackend = LinkedBlockingQueue<ByteArray>()
    private val io: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "dlc-manager-port-multiplexer-connection").apply { isDaemon = true }
    }
    private var probeSize = 0
    private var mode = Mode.PROBING
    @Volatile private var closed = false
    @Volatile private var backend: Socket? = null
    @Volatile private var backendOutput: OutputStream? = null

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        if (message !is ByteBuf) {
            context.fireChannelRead(message)
            return
        }
        try {
            when (mode) {
                Mode.MINECRAFT -> context.fireChannelRead(message.retain())
                Mode.PROBING -> {
                    if (!consumeProbe(message)) return
                    if (!Arrays.equals(probe, PREFACE)) {
                        mode = Mode.MINECRAFT
                        removeSelf(context)
                        val replay = context.alloc().buffer(probe.size + message.readableBytes())
                        replay.writeBytes(probe)
                        replay.writeBytes(message, message.readerIndex(), message.readableBytes())
                        context.fireChannelRead(replay)
                        return
                    }
                    mode = Mode.DLC
                    startBackend(context)
                    if (message.isReadable) {
                        val bytes = ByteArray(message.readableBytes())
                        message.readBytes(bytes)
                        enqueue(bytes)
                    }
                }
                Mode.DLC -> {
                    if (message.isReadable) {
                        val bytes = ByteArray(message.readableBytes())
                        message.readBytes(bytes)
                        enqueue(bytes)
                    }
                }
            }
        } finally {
            message.release()
        }
    }

    private fun consumeProbe(input: ByteBuf): Boolean {
        val count = minOf(PREFACE.size - probeSize, input.readableBytes())
        input.readBytes(probe, probeSize, count)
        probeSize += count
        return probeSize == PREFACE.size
    }

    private fun startBackend(context: ChannelHandlerContext) {
        io.execute {
            var listener: ServerSocket? = null
            try {
                listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
                val client = Socket()
                client.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), listener.localPort), CONNECT_TIMEOUT_MILLIS)
                val accepted = listener.accept()
                listener.close()
                listener = null
                backend = client
                backendOutput = client.getOutputStream()
                io.execute { DlcServerService.handleClient(accepted) }
                io.execute { writeBackendLoop() }
                readBackendLoop(context, client)
            } catch (_: IOException) {
                closeChannel(context)
            } finally {
                try { listener?.close() } catch (_: IOException) {}
                closeBackend()
            }
        }
    }

    private fun enqueue(bytes: ByteArray) {
        if (!closed && bytes.isNotEmpty()) toBackend.offer(bytes)
    }

    private fun writeBackendLoop() {
        try {
            while (!closed) {
                val bytes = toBackend.poll(1, TimeUnit.SECONDS) ?: continue
                if (bytes === CLOSE_MARKER) return
                val output = backendOutput
                if (output == null) {
                    toBackend.offer(bytes)
                    Thread.sleep(10)
                    continue
                }
                output.write(bytes)
                output.flush()
            }
        } catch (_: IOException) {
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun readBackendLoop(context: ChannelHandlerContext, socket: Socket) {
        try {
            socket.getInputStream().use { input: InputStream ->
                val buffer = ByteArray(16 * 1024)
                while (!closed) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    val bytes = buffer.copyOf(count)
                    context.executor().execute {
                        writeRaw(context, bytes)
                    }
                }
            }
        } catch (_: IOException) {
        } finally {
            closeChannel(context)
        }
    }

    private fun removeSelf(context: ChannelHandlerContext) {
        if (context.pipeline().context(this) != null) context.pipeline().remove(this)
    }

    private fun closeChannel(context: ChannelHandlerContext) {
        context.executor().execute {
            if (context.channel().isOpen) context.close()
        }
    }

    private fun writeRaw(context: ChannelHandlerContext, bytes: ByteArray) {
        if (!context.channel().isActive) return
        context.channel().unsafe().write(Unpooled.wrappedBuffer(bytes), context.newPromise())
        context.channel().unsafe().flush()
    }

    private fun closeBackend() {
        if (closed) return
        closed = true
        toBackend.offer(CLOSE_MARKER)
        try { backendOutput?.close() } catch (_: IOException) {}
        backendOutput = null
        try { backend?.close() } catch (_: IOException) {}
        backend = null
        io.shutdownNow()
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        closeBackend()
        super.channelInactive(context)
    }

    override fun handlerRemoved(context: ChannelHandlerContext) {
        closeBackend()
        super.handlerRemoved(context)
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        closeBackend()
        context.close()
    }

    private enum class Mode { PROBING, MINECRAFT, DLC }

    companion object {
        @JvmField val PREFACE = byteArrayOf('D'.code.toByte(), 'L'.code.toByte(), 'C'.code.toByte(), 'M'.code.toByte(), 1)
        private val CLOSE_MARKER = ByteArray(0)
        private const val CONNECT_TIMEOUT_MILLIS = 5000
    }
}
