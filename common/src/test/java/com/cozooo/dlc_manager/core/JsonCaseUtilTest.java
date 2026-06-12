package com.cozooo.dlc_manager.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCaseUtilTest {
    @Test
    void getIgnoreCasePrefersExactKeyBeforeCaseInsensitiveMatch() {
        JsonObject object = new JsonObject();
        object.addProperty("Name", "upper");
        object.addProperty("name", "exact");

        assertEquals("exact", JsonCaseUtil.getStringIgnoreCase(object, "name"));
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

        assertSame(child, JsonCaseUtil.getObjectIgnoreCase(object, "child"));
        assertSame(array, JsonCaseUtil.getArrayIgnoreCase(object, "items"));
        assertEquals("Hello", JsonCaseUtil.getStringIgnoreCase(object, "title"));
        assertEquals(true, JsonCaseUtil.getBooleanIgnoreCase(object, "enabled"));
        assertTrue(JsonCaseUtil.getBooleanIgnoreCase(object, "missing", true));
        assertFalse(JsonCaseUtil.getBooleanIgnoreCase(object, "missing", false));
    }

    @Test
    void returnsNullForMissingOrWrongTypedValues() {
        JsonObject object = new JsonObject();
        object.addProperty("flag", "true");

        assertNull(JsonCaseUtil.getIgnoreCase(null, "flag"));
        assertNull(JsonCaseUtil.getIgnoreCase(object, null));
        assertNull(JsonCaseUtil.getBooleanIgnoreCase(object, "flag"));
        assertNull(JsonCaseUtil.getObjectIgnoreCase(object, "flag"));
        assertFalse(JsonCaseUtil.hasIgnoreCase(object, "missing"));
    }

    @Test
    void removeIgnoreCaseRemovesMatchingKey() {
        JsonObject object = new JsonObject();
        object.addProperty("Priority", "curseforge");

        JsonCaseUtil.removeIgnoreCase(object, "priority");

        assertFalse(object.has("Priority"));
    }

    @Test
    void normalizesLanguageKeysWithRootLocale() {
        assertEquals("zh_cn", JsonCaseUtil.normalizeLangKey("ZH_CN"));
        assertNull(JsonCaseUtil.normalizeLangKey(null));
    }
}
