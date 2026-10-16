package com.cozooo.dlc_manager.core;

import com.cozooo.dclanguage.DcLanguage;
import com.cozooo.dclanguage.DcSchemaCatalog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class DlcDcDefinition {
    private static final DcSchemaCatalog CATALOG = load();

    private DlcDcDefinition() { }

    public static DcSchemaCatalog catalog() { return CATALOG; }

    public static DcLanguage register(String profile) { return register(profile, null); }

    public static DcLanguage register(String profile, JsonObject values) {
        var selected = CATALOG.profile(profile);
        if (selected == null) throw new IllegalArgumentException("Unknown DLC Manager profile: " + profile);
        return CATALOG.language(selected, values);
    }

    private static DcSchemaCatalog load() {
        try (var input = DlcDcDefinition.class.getResourceAsStream("/META-INF/dlc-manager/dlc-definition.json")) {
            if (input == null) throw new IllegalStateException("Missing DLC Manager DC definition");
            return DcSchemaCatalog.fromJson(JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load DLC Manager DC definition", exception);
        }
    }
}
