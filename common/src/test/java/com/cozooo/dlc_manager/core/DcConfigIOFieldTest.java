package com.cozooo.dlc_manager.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DcConfigIOFieldTest {
    @Test
    void getIgnoreCasePrefersExactKeyBeforeCaseInsensitiveMatch() {
        JsonObject object = new JsonObject();
        object.addProperty("Name", "upper");
        object.addProperty("name", "exact");

        assertEquals("exact", DcConfigIO.getStringIgnoreCase(object, "name"));
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

        assertSame(child, DcConfigIO.getObjectIgnoreCase(object, "child"));
        assertSame(array, DcConfigIO.getArrayIgnoreCase(object, "items"));
        assertEquals("Hello", DcConfigIO.getStringIgnoreCase(object, "title"));
        assertEquals(true, DcConfigIO.getBooleanIgnoreCase(object, "enabled"));
        assertTrue(DcConfigIO.getBooleanIgnoreCase(object, "missing", true));
        assertFalse(DcConfigIO.getBooleanIgnoreCase(object, "missing", false));
    }

    @Test
    void returnsNullForMissingOrWrongTypedValues() {
        JsonObject object = new JsonObject();
        object.addProperty("flag", "true");

        assertNull(DcConfigIO.getIgnoreCase(null, "flag"));
        assertNull(DcConfigIO.getIgnoreCase(object, null));
        assertNull(DcConfigIO.getBooleanIgnoreCase(object, "flag"));
        assertNull(DcConfigIO.getObjectIgnoreCase(object, "flag"));
        assertFalse(DcConfigIO.hasIgnoreCase(object, "missing"));
    }

    @Test
    void removeIgnoreCaseRemovesMatchingKey() {
        JsonObject object = new JsonObject();
        object.addProperty("Priority", "curseforge");

        DcConfigIO.removeIgnoreCase(object, "priority");

        assertFalse(object.has("Priority"));
    }

    @Test
    void normalizesLanguageKeysWithRootLocale() {
        assertEquals("zh_cn", DcConfigIO.normalizeLangKey("ZH_CN"));
        assertNull(DcConfigIO.normalizeLangKey(null));
    }
}
