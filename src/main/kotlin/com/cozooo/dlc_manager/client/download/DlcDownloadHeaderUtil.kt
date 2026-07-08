package com.cozooo.dlc_manager.client.download

import java.net.HttpURLConnection
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

object DlcDownloadHeaderUtil {
    @JvmStatic
    fun extractFileNameFromHeaders(connection: HttpURLConnection?): String? {
        if (connection == null) {
            return null
        }
        return parseContentDisposition(connection.getHeaderField("Content-Disposition"))
    }

    @JvmStatic
    fun parseContentDisposition(disposition: String?): String? {
        if (disposition.isNullOrBlank()) {
            return null
        }
        val lower = disposition.lowercase(Locale.ROOT)
        var idx = lower.indexOf("filename=")
        var isStar = false
        if (idx < 0) {
            idx = lower.indexOf("filename*=")
            isStar = idx >= 0
        }
        if (idx < 0) {
            return null
        }
        var value = disposition.substring(idx)
        val equals = value.indexOf('=')
        if (equals >= 0) {
            value = value.substring(equals + 1).trim()
        }
        val semi = value.indexOf(';')
        if (semi >= 0) {
            value = value.substring(0, semi).trim()
        }
        value = value.replace("\"", "")
        val decoded = if (isStar) {
            decodeFilenameStar(value)
        } else {
            value
        }
        return DlcDownloadUtil.stripQuery(decoded)
    }

    private fun decodeFilenameStar(value: String?): String? {
        if (value == null) {
            return null
        }
        val trimmed = value.trim()
        var data = trimmed
        var charset: Charset = StandardCharsets.UTF_8
        val first = trimmed.indexOf('\'')
        val second = if (first >= 0) trimmed.indexOf('\'', first + 1) else -1
        if (first > 0 && second > first) {
            val charsetName = trimmed.substring(0, first).trim()
            data = trimmed.substring(second + 1)
            if (charsetName.isNotBlank()) {
                try {
                    charset = Charset.forName(charsetName)
                } catch (_: Exception) {
                }
            }
        }
        return try {
            URLDecoder.decode(data, charset)
        } catch (_: IllegalArgumentException) {
            data
        }
    }
}
