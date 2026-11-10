package com.cozooo.dlc_manager.core

import java.util.Locale

object DlcVersionUtil {
    private const val DEFAULT_VERSION = "1.0.0"

    enum class Channel(private val priority: Int) {
        STABLE(0),
        BETA(1),
        ALPHA(2),
        NIGHTLY(3);

        fun priority(): Int = priority

        fun accepts(candidate: Channel?): Boolean = candidate != null && candidate.priority <= priority
    }

    @JvmStatic
    fun channelOf(version: String?): Channel {
        val parsed = ParsedVersion.parse(version)
        if (!parsed.knownQualifier) {
            return Channel.STABLE
        }
        return when (parsed.qualifierRank) {
            ParsedVersion.QUALIFIER_ALPHA -> Channel.ALPHA
            ParsedVersion.QUALIFIER_BETA, ParsedVersion.QUALIFIER_RC -> Channel.BETA
            ParsedVersion.QUALIFIER_BUILD -> Channel.NIGHTLY
            else -> Channel.STABLE
        }
    }

    @JvmStatic
    fun parseFilter(value: String?): Channel {
        if (value.isNullOrBlank()) {
            return Channel.NIGHTLY
        }
        return when (value.trim().lowercase(Locale.ROOT)) {
            "stable" -> Channel.STABLE
            "beta" -> Channel.BETA
            "alpha" -> Channel.ALPHA
            "nightly" -> Channel.NIGHTLY
            else -> Channel.NIGHTLY
        }
    }

    @JvmStatic
    fun channelByName(value: String?): Channel? {
        if (value.isNullOrBlank()) {
            return null
        }
        return when (value.trim().lowercase(Locale.ROOT)) {
            "stable" -> Channel.STABLE
            "beta" -> Channel.BETA
            "alpha" -> Channel.ALPHA
            "nightly" -> Channel.NIGHTLY
            else -> null
        }
    }

    @JvmStatic
    fun isWithinFilter(version: String?, filter: Channel?): Boolean =
        filter == null || filter.accepts(channelOf(version))

    @JvmStatic
    fun compare(a: String?, b: String?): Int {
        val left = ParsedVersion.parse(a)
        val right = ParsedVersion.parse(b)
        val max = maxOf(left.numericParts.size, right.numericParts.size)
        for (i in 0 until max) {
            val l = if (i < left.numericParts.size) left.numericParts[i] else 0
            val r = if (i < right.numericParts.size) right.numericParts[i] else 0
            if (l != r) {
                return l.compareTo(r)
            }
        }
        if (left.knownQualifier && right.knownQualifier) {
            if (left.qualifierRank != right.qualifierRank) {
                return left.qualifierRank.compareTo(right.qualifierRank)
            }
            if (left.qualifierNumber != right.qualifierNumber) {
                return left.qualifierNumber.compareTo(right.qualifierNumber)
            }
            if (left.qualifierRank == ParsedVersion.QUALIFIER_BUILD) {
                return 0
            }
            val qualifierTextCompare = left.qualifierText.compareTo(right.qualifierText, ignoreCase = true)
            if (qualifierTextCompare != 0) {
                return qualifierTextCompare
            }
            return left.normalized.compareTo(right.normalized, ignoreCase = true)
        }
        if (left.knownQualifier != right.knownQualifier) {
            return if (left.knownQualifier) 1 else -1
        }
        return left.normalized.compareTo(right.normalized, ignoreCase = true)
    }

    @JvmStatic
    fun isNewer(currentVersion: String?, candidateVersion: String?): Boolean =
        compare(currentVersion, candidateVersion) < 0

    @JvmStatic
    fun allows(currentVersion: String?, candidateVersion: String?): Boolean =
        channelOf(currentVersion).accepts(channelOf(candidateVersion))

    private class ParsedVersion(
        val normalized: String,
        val numericParts: IntArray,
        val knownQualifier: Boolean,
        val qualifierRank: Int,
        val qualifierNumber: Int,
        val qualifierText: String
    ) {
        companion object {
            const val QUALIFIER_ALPHA = 0
            const val QUALIFIER_BETA = 1
            const val QUALIFIER_RC = 2
            const val QUALIFIER_RELEASE = 3
            const val QUALIFIER_HOTFIX = 4
            const val QUALIFIER_BUILD = 5

            private fun safeVersion(value: String?): String =
                if (value.isNullOrBlank()) DEFAULT_VERSION else value.trim()

            private fun parseVersionPart(part: String?): Int {
                if (part == null) {
                    return 0
                }
                var normalized = part.trim()
                val idx = normalized.indexOf('-')
                if (idx >= 0) {
                    normalized = normalized.substring(0, idx)
                }
                return normalized.toIntOrNull() ?: 0
            }

            fun parse(value: String?): ParsedVersion {
                val normalized = safeVersion(value)
                val split = findSuffixStart(normalized)
                val core = if (split < 0) normalized else normalized.substring(0, split)
                val suffix = if (split < 0) "" else normalized.substring(split)
                val numericParts = parseNumericParts(core)
                val qualifier = parseQualifier(suffix)
                return ParsedVersion(normalized, numericParts, qualifier.known, qualifier.rank, qualifier.number, qualifier.text)
            }

            private fun findSuffixStart(value: String): Int {
                for (i in value.indices) {
                    val ch = value[i]
                    if (ch in '0'..'9' || ch == '.') {
                        continue
                    }
                    return i
                }
                return -1
            }

            private fun parseNumericParts(core: String): IntArray {
                val parts = safeVersion(core).split(".")
                return IntArray(parts.size) { parseVersionPart(parts[it]) }
            }

            private fun parseQualifier(suffix: String): Qualifier {
                if (suffix.isBlank()) {
                    return Qualifier(true, QUALIFIER_RELEASE, 0, "release")
                }
                var normalized = suffix.trim()
                while (normalized.isNotEmpty()) {
                    val ch = normalized[0]
                    if (ch == '-' || ch == '_' || ch == '.') {
                        normalized = normalized.substring(1)
                        continue
                    }
                    break
                }
                if (normalized.isBlank()) {
                    return Qualifier(true, QUALIFIER_RELEASE, 0, "release")
                }
                val lower = normalized.lowercase(Locale.ROOT)
                for (name in arrayOf("alpha", "beta", "rc", "hotfix", "fix", "build", "nightly")) {
                    if (!lower.startsWith(name)) {
                        continue
                    }
                    var rest = normalized.substring(name.length)
                    var number = 0
                    if (rest.isNotBlank()) {
                        rest = rest.trim()
                        while (rest.isNotEmpty()) {
                            val ch = rest[0]
                            if (ch == '-' || ch == '_' || ch == '.') {
                                rest = rest.substring(1)
                                continue
                            }
                            break
                        }
                        if (rest.isNotBlank()) {
                            val digits = StringBuilder()
                            var index = 0
                            while (index < rest.length && rest[index].isDigit()) {
                                digits.append(rest[index])
                                index++
                            }
                            if (digits.isNotEmpty()) {
                                number = digits.toString().toIntOrNull() ?: 0
                            }
                        }
                    }
                    val rank = when (name) {
                        "alpha" -> QUALIFIER_ALPHA
                        "beta" -> QUALIFIER_BETA
                        "rc" -> QUALIFIER_RC
                        "hotfix", "fix" -> QUALIFIER_HOTFIX
                        "build", "nightly" -> QUALIFIER_BUILD
                        else -> QUALIFIER_RELEASE
                    }
                    val text = when (name) {
                    "fix" -> "hotfix"
                    "nightly" -> "build"
                    else -> name
                }
                return Qualifier(true, rank, number, text)
                }
                return Qualifier(false, QUALIFIER_RELEASE, 0, normalized)
            }
        }
    }

    private class Qualifier(
        val known: Boolean,
        val rank: Int,
        val number: Int,
        val text: String
    )
}
