package com.cozooo.dlc_manager.client.screen

import java.util.Locale
import java.util.Collections
import com.ibm.icu.text.Transliterator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object DlcUiSearch {
    private val pinyin = ThreadLocal.withInitial { Transliterator.getInstance("Han-Latin; Latin-ASCII") }
    private val whitespace = Regex("\\s+")
    private val pinyinCache = Collections.synchronizedMap(object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 1024
    })
    private data class Query(val raw: String, val words: List<String>)
    private val parsedQuery = ThreadLocal.withInitial { Query("", emptyList()) }
    private val warming = ConcurrentHashMap.newKeySet<String>()
    private val warmupExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "dlc-search-warmup").apply { isDaemon = true }
    }

    @JvmStatic
    fun warmup(vararg fields: String?) {
        val text = normalize(fields)
        if (!hasHan(text) || pinyinCache.containsKey(text) || !warming.add(text)) return
        warmupExecutor.execute { try { pinyinFor(text) } finally { warming.remove(text) } }
    }

    private fun normalize(fields: Array<out String?>): String = fields.filterNotNull().joinToString(" ").lowercase(Locale.ROOT)
    private fun hasHan(text: String): Boolean = text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }
    private fun pinyinFor(text: String): String {
        pinyinCache[text]?.let { return it }
        val latin = pinyin.get().transliterate(text).lowercase(Locale.ROOT)
        val result = latin + " " + latin.replace(whitespace, "")
        pinyinCache[text] = result
        return result
    }
    @JvmStatic
    fun matches(query: String?, vararg fields: String?): Boolean {
        if (query.isNullOrBlank()) { warmup(*fields); return true }
        val haystack = normalize(fields)
        var parsed = parsedQuery.get()
        if (parsed.raw != query) {
            parsed = Query(query, query.trim().lowercase(Locale.ROOT).split(whitespace))
            parsedQuery.set(parsed)
        }
        var latin: String? = null
        for (word in parsed.words) {
            if (haystack.contains(word)) continue
            if (latin == null) {
                if (!hasHan(haystack)) return false
                latin = pinyinFor(haystack)
            }
            if (!latin.contains(word)) return false
        }
        return true
    }
}
