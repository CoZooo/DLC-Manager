package com.cozooo.dlc_manager.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcDcConfigIOFieldTest {
    @Test
    void getIgnoreCasePrefersExactKeyBeforeCaseInsensitiveMatch() {
        JsonObject object = new JsonObject();
        object.addProperty("Name", "upper");
        object.addProperty("name", "exact");

        assertEquals("exact", DlcDcConfigIO.getStringIgnoreCase(object, "name"));
    }

    @Test
    void readsTypedValuesIgnoringCase() {
        JsonObject object = new JsonObject();
        JsonObject child = new JsonObject();
        JsonArray array = new JsonArray();
        array.add("value");
        object.add("Child", child);
        object.add("Items", array);
        object.addProperty("Enabled", true);
        object.addProperty("Title", "Hello");

        assertSame(child, DlcDcConfigIO.getObjectIgnoreCase(object, "child"));
        assertSame(array, DlcDcConfigIO.getArrayIgnoreCase(object, "items"));
        assertEquals("Hello", DlcDcConfigIO.getStringIgnoreCase(object, "title"));
        assertEquals(true, DlcDcConfigIO.getBooleanIgnoreCase(object, "enabled"));
        assertTrue(DlcDcConfigIO.getBooleanIgnoreCase(object, "missing", true));
        assertFalse(DlcDcConfigIO.getBooleanIgnoreCase(object, "missing", false));
    }

    @Test
    void returnsNullForMissingOrWrongTypedValues() {
        JsonObject object = new JsonObject();
        object.addProperty("flag", "true");

        assertNull(DlcDcConfigIO.getIgnoreCase(null, "flag"));
        assertNull(DlcDcConfigIO.getIgnoreCase(object, null));
        assertNull(DlcDcConfigIO.getBooleanIgnoreCase(object, "flag"));
        assertNull(DlcDcConfigIO.getObjectIgnoreCase(object, "flag"));
        assertFalse(DlcDcConfigIO.hasIgnoreCase(object, "missing"));
    }

    @Test
    void removeIgnoreCaseRemovesMatchingKey() {
        JsonObject object = new JsonObject();
        object.addProperty("Priority", "curseforge");

        DlcDcConfigIO.removeIgnoreCase(object, "priority");

        assertFalse(object.has("Priority"));
    }

    @Test
    void normalizesLanguageKeysWithRootLocale() {
        assertEquals("zh_cn", DlcDcConfigIO.normalizeLangKey("ZH_CN"));
        assertNull(DlcDcConfigIO.normalizeLangKey(null));
    }
}
