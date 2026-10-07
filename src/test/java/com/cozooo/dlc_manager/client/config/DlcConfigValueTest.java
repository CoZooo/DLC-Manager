package com.cozooo.dlc_manager.client.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DlcConfigValueTest {
    @Test
    void readingDefaultDoesNotMutateTheDraftValue() {
        var value = new DlcConfigValue.StringValue("path", "DLC", "", "path");
        value.setValueFromString("custom/path");
        assertEquals("DLC", value.getDefaultStringValue());
        assertEquals("custom/path", value.getStringValue());
        value.resetToDefault();
        assertEquals("DLC", value.getStringValue());
    }

    @Test
    void booleanInputRejectsInvalidValuesAndRestoresDefault() {
        var value = new DlcConfigValue.BooleanValue("enabled", false, "", "enabled");
        value.setValueFromString(" TRUE ");
        value.setValueFromString("invalid");
        assertTrue(value.getBooleanValue());
        value.resetToDefault();
        assertFalse(value.getBooleanValue());
    }

    @Test
    void integerValuesRespectBoundsAndIgnoreMalformedInput() {
        var value = new DlcConfigValue.IntegerValue("port", 29690, 1, 65535, "", "port");
        value.setIntegerValue(70000);
        assertEquals(65535, value.getIntegerValue());
        value.setValueFromString("-2");
        assertEquals(1, value.getIntegerValue());
        value.setValueFromString("2147483648");
        value.setValueFromString("not a number");
        assertEquals(1, value.getIntegerValue());
        value.setValueFromString(" 25575 ");
        assertEquals(25575, value.getIntegerValue());
        value.resetToDefault();
        assertEquals(29690, value.getIntegerValue());
    }

    @Test
    void invalidIntegerRangeFailsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new DlcConfigValue.IntegerValue("n", 1, 10, 0, "", "n"));
    }

    @Test
    void stringsNormalizeNullAndKeepWhitespace() {
        var value = new DlcConfigValue.StringValue("token", null, "", "token");
        assertEquals("", value.getStringValue());
        value.setValueFromString(" token ");
        assertEquals(" token ", value.getStringValue());
        value.setValueFromString(null);
        assertEquals("", value.getStringValue());
    }
}
