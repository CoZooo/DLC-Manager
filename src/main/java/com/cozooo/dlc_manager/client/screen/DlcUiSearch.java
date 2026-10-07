package com.cozooo.dlc_manager.client.screen;

import java.util.Locale;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.ibm.icu.text.Transliterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class DlcUiSearch {
    private DlcUiSearch() {}
    private static final ThreadLocal<Transliterator> PINYIN = ThreadLocal.withInitial(() -> Transliterator.getInstance("Han-Latin; Latin-ASCII"));
    private static final Map<String, String> PINYIN_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 1024; }
    });
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private record Query(String raw, String[] words) {}
    private static final ThreadLocal<Query> QUERY = ThreadLocal.withInitial(() -> new Query("", new String[0]));
    private static final Set<String> WARMING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService WARMUP = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "dlc-search-warmup"); thread.setDaemon(true); return thread;
    });

    public static void warmup(String... fields) {
        String text = normalize(fields);
        if (!hasHan(text) || PINYIN_CACHE.containsKey(text) || !WARMING.add(text)) return;
        WARMUP.execute(() -> {
            try { pinyinFor(text); } finally { WARMING.remove(text); }
        });
    }

    private static String normalize(String... fields) {
        StringBuilder text = new StringBuilder();
        for (String field : fields) if (field != null) text.append(field).append(' ');
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean hasHan(String text) {
        return text.codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
    }

    private static String pinyinFor(String text) {
        String cached = PINYIN_CACHE.get(text);
        if (cached != null) return cached;
        String latin = PINYIN.get().transliterate(text).toLowerCase(Locale.ROOT);
        String result = latin + " " + WHITESPACE.matcher(latin).replaceAll("");
        PINYIN_CACHE.put(text, result);
        return result;
    }

    public static boolean matches(String query, String... fields) {
        if (query == null || query.isBlank()) { warmup(fields); return true; }
        String haystack = normalize(fields);
        Query parsed = QUERY.get();
        if (!query.equals(parsed.raw())) {
            parsed = new Query(query, WHITESPACE.split(query.strip().toLowerCase(Locale.ROOT)));
            QUERY.set(parsed);
        }
        String pinyin = null;
        for (String word : parsed.words()) {
            if (haystack.contains(word)) continue;
            if (pinyin == null) {
                if (!hasHan(haystack)) return false;
                pinyin = pinyinFor(haystack);
            }
            if (!pinyin.contains(word)) return false;
        }
        return true;
    }
}
