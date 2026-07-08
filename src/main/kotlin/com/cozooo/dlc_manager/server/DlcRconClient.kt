package com.cozooo.dlc_manager.server

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class DlcRconClient private constructor(host: String, port: Int, timeoutMs: Int) : Closeable {
    private val socket = Socket()
    private val input: DataInputStream
    private val output: DataOutputStream
    private var requestId = 1

    init {
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.soTimeout = timeoutMs
        input = DataInputStream(socket.getInputStream())
        output = DataOutputStream(socket.getOutputStream())
    }

    @Throws(IOException::class)
    private fun login(password: String): Boolean {
        val id = send(TYPE_LOGIN, password)
        val response = read()
        return response != null && response.id == id && response.id != -1
    }

    @Throws(IOException::class)
    private fun command(command: String): String? {
        val id = send(TYPE_COMMAND, command)
        val response = read()
        if (response == null || response.id != id) {
            return null
        }
        return response.payload
    }

    @Throws(IOException::class)
    private fun send(type: Int, payload: String?): Int {
        val id = requestId++
        val payloadBytes = payload?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        val length = 4 + 4 + payloadBytes.size + 2
        val buffer = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(length)
        buffer.putInt(id)
        buffer.putInt(type)
        buffer.put(payloadBytes)
        buffer.put(0.toByte())
        buffer.put(0.toByte())
        output.write(buffer.array())
        output.flush()
        return id
    }

    @Throws(IOException::class)
    private fun read(): RconResponse? {
        val length = try {
            Integer.reverseBytes(input.readInt())
        } catch (_: IOException) {
            return null
        }
        if (length <= 0) {
            return null
        }
        val data = ByteArray(length)
        input.readFully(data)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val id = buffer.int
        val type = buffer.int
        var payloadLength = length - 8 - 2
        if (payloadLength < 0) {
            payloadLength = 0
        }
        val payloadBytes = ByteArray(payloadLength)
        buffer.get(payloadBytes)
        val payload = String(payloadBytes, StandardCharsets.UTF_8)
        return RconResponse(id, type, payload)
    }

    @Throws(IOException::class)
    override fun close() {
        socket.close()
    }

    private data class RconResponse(val id: Int, val type: Int, val payload: String)

    companion object {
        private const val TYPE_RESPONSE = 0
        private const val TYPE_COMMAND = 2
        private const val TYPE_LOGIN = 3

        @JvmStatic
        fun execute(host: String?, port: Int, password: String?, command: String?, timeoutMs: Int): String? {
            if (host.isNullOrBlank()) {
                return null
            }
            if (command.isNullOrBlank()) {
                return null
            }
            return try {
                DlcRconClient(host, port, timeoutMs).use { client ->
                    if (!client.login(password ?: "")) {
                        null
                    } else {
                        client.command(command)
                    }
                }
            } catch (_: IOException) {
                null
            }
        }
    }
}
