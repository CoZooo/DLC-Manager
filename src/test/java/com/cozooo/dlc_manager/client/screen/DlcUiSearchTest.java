package com.cozooo.dlc_manager.client.screen;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiSearchTest {
    @Test
    void blankSearchIncludesUnnamedEntries() {
        assertTrue(DlcUiSearch.matches(null, (String) null));
        assertTrue(DlcUiSearch.matches("  ", ""));
    }

    @Test
    void everyWordMustMatchAcrossNameIdentifierAndPath() {
        assertTrue(DlcUiSearch.matches("  ENHANCED\tweapons  ", "Enhanced combat", "addon", "DLC/weapons"));
        assertFalse(DlcUiSearch.matches("enhanced missing", "Enhanced combat", "addon", "DLC/weapons"));
        assertTrue(DlcUiSearch.matches("材质 高清", "高清材质包", null));
    }

    @Test
    void caseMatchingDoesNotDependOnSystemLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertTrue(DlcUiSearch.matches("ITEM", "item"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
