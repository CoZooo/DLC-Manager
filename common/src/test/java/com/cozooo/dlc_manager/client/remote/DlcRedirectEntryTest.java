package com.cozooo.dlc_manager.client.remote;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcRedirectEntryTest {
    @Test
    void availabilityDefaultsToVisibleAndSupportsLanguageLists() {
        DlcRedirectEntry entry = new DlcRedirectEntry();

        entry.setAvailable(null);
        assertTrue(entry.isAvailableFor("zh_cn"));

        entry.setAvailable(JsonParser.parseString("[\"zh_cn\", \"en_us\"]"));
        assertTrue(entry.isAvailableFor("zh_CN"));
        assertTrue(entry.isAvailableFor("en_us"));
        assertFalse(entry.isAvailableFor("ja_jp"));
        assertEquals("zh_cn,en_us", entry.getAvailable());
    }

    @Test
    void hiddenAvailabilityOverridesLanguages() {
        DlcRedirectEntry entry = new DlcRedirectEntry();

        entry.setAvailable(JsonParser.parseString("\"zh_cn, hidden, en_us\""));

        assertTrue(entry.isHidden());
        assertFalse(entry.isAvailableFor("zh_cn"));
        assertEquals("zh_cn, hidden, en_us", entry.getAvailable());
    }

    @Test
    void localizedTextFallsBackToEnglishThenFirstValueThenFallback() {
        DlcRedirectEntry entry = new DlcRedirectEntry();
        entry.setFallbackName("fallback");
        entry.getDisplayNames().put("en_us", "English");
        entry.getDisplayNames().put("zh_cn", "Chinese");
        assertEquals("Chinese", entry.resolveDisplayName("zh_cn"));
        assertEquals("English", entry.resolveDisplayName("ja_jp"));

        DlcRedirectEntry firstValue = new DlcRedirectEntry();
        firstValue.setFallbackName("fallback");
        firstValue.getDisplayNames().putAll(new LinkedHashMap<>(Map.of("fr_fr", "French")));
        assertEquals("French", firstValue.resolveDisplayName("ja_jp"));

        assertEquals("fallback", new DlcRedirectEntryWithFallback("fallback").resolveDisplayName("en_us"));
    }

    private static final class DlcRedirectEntryWithFallback extends DlcRedirectEntry {
        private DlcRedirectEntryWithFallback(String fallback) {
            setFallbackName(fallback);
        }
    }
}
