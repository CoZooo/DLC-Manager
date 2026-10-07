package com.cozooo.dlc_manager.client.screen;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiSearchTest {
    @Test
    void cachedQueriesSwitchAndClearWithoutKeepingPreviousTokens() {
        assertTrue(DlcUiSearch.matches("gao", "高清材质包"));
        assertFalse(DlcUiSearch.matches("missing", "高清材质包"));
        assertTrue(DlcUiSearch.matches("", "高清材质包"));
        assertTrue(DlcUiSearch.matches("de", "的"));
        DlcUiSearch.warmup("高清材质包");
        assertTrue(DlcUiSearch.matches("gaoqing", "高清材质包"));
    }
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

    @Test
    void matchesChineseUsingPinyinWithOrWithoutSpaces() {
        assertTrue(DlcUiSearch.matches("de", "的"));
        assertTrue(DlcUiSearch.matches("gaoqingcaizhi", "高清材质包"));
        assertTrue(DlcUiSearch.matches("gao qing cai zhi", "高清材质包"));
        assertTrue(DlcUiSearch.matches("GAOQING", "高清材质包"));
        assertFalse(DlcUiSearch.matches("missing", "高清材质包"));
    }

    @Test
    void combinesChinesePinyinAndOriginalFields() {
        assertTrue(DlcUiSearch.matches("材质 gaoqing weapons", "高清材质包", "weapons"));
        assertTrue(DlcUiSearch.matches("peizhi", "", null, "配置选项"));
        assertFalse(DlcUiSearch.matches("材质 missing", "高清材质包"));
    }
}
