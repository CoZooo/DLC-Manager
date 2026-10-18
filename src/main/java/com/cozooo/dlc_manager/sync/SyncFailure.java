package com.cozooo.dlc_manager.sync;

import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.google.gson.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SyncFailure extends IOException {
    private static final long serialVersionUID = 1L;
    private static final String PREFIX = "dlc_manager.sync.error.";
    private static final Map<String, JsonObject> LANGUAGES = new ConcurrentHashMap<>();
    private final String code;
    private final String[] arguments;

    public SyncFailure(String code, Object... arguments) {
        super(PREFIX + code);
        this.code = code;
        this.arguments = Arrays.stream(arguments).map(String::valueOf).toArray(String[]::new);
    }
    public SyncFailure causedBy(Throwable cause) { initCause(cause); return this; }
    public static boolean is(Throwable error, String code) { return error instanceof SyncFailure failure && failure.code.equals(code); }
    @Override public String getMessage() {
        String language = DlcPlatformServices.languageCode();
        language = language == null || language.isBlank() ? "en_us" : language.toLowerCase(Locale.ROOT).replace('-', '_');
        if (!language.matches("[a-z]{2,3}_[a-z]{2,3}")) language = "en_us";
        String key = PREFIX + code;
        JsonElement value = load(language).get(key);
        if (value == null) value = load("en_us").get(key);
        String pattern = value == null ? key : value.getAsString();
        try { return String.format(Locale.ROOT, pattern, (Object[]) arguments); }
        catch (IllegalFormatException ignored) { return pattern; }
    }
    public void write(JsonObject response) {
        response.addProperty("reasonKey", PREFIX + code);
        JsonArray values = new JsonArray();
        for (String argument : arguments) values.add(argument);
        response.add("reasonArgs", values);
    }
    public static IOException read(JsonObject response) {
        if (response != null && response.has("reasonKey") && response.get("reasonKey").isJsonPrimitive()) {
            String key = response.get("reasonKey").getAsString();
            if (key.startsWith(PREFIX) && key.substring(PREFIX.length()).matches("[a-z_]+")) {
                List<String> arguments = new ArrayList<>();
                if (response.has("reasonArgs")) for (JsonElement value : response.getAsJsonArray("reasonArgs")) arguments.add(value.getAsString());
                return new SyncFailure(key.substring(PREFIX.length()), arguments.toArray());
            }
        }
        return response != null && response.has("reason") ? new IOException(response.get("reason").getAsString()) : new SyncFailure("unsupported");
    }
    private static JsonObject load(String language) {
        return LANGUAGES.computeIfAbsent(language, name -> {
            try (var input = SyncFailure.class.getResourceAsStream("/assets/dlc_manager/lang/" + name + ".json")) {
                return input == null ? new JsonObject() : JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException ignored) { return new JsonObject(); }
        });
    }
}
