package com.cozooo.dlc_manager.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUiTextTest {
    @Test
    void explicitLegacyAndHexColorsOverrideInheritedThemeColors() {
        var runs = DlcUiText.runs("默认 §c红色 &#336699蓝色");

        assertEquals("", runs.get(0).getCss());
        assertEquals("color:#ff5555;", runs.get(1).getCss());
        assertEquals("color:#336699;", runs.get(2).getCss());
        assertEquals("红色 ", runs.get(1).getText());
    }

    @Test
    void resetRestoresThemeDefaultsWithoutLeakingFormattingToNextName() {
        var runs = DlcUiText.runs("&a&l依赖 A&r, 依赖 B");

        assertTrue(runs.get(0).getCss().contains("color:#55ff55;"));
        assertTrue(runs.get(0).getCss().contains("font-weight:700;"));
        assertEquals("", runs.get(1).getCss());
        assertEquals(", 依赖 B", runs.get(1).getText());
    }

    @Test
    void htmlAndUnknownMarkersRemainLiteralText() {
        var runs = DlcUiText.runs("&x <script> & 中文");

        assertEquals(1, runs.size());
        assertEquals("&x <script> & 中文", runs.get(0).getText());
        assertEquals("", runs.get(0).getCss());
    }
}
