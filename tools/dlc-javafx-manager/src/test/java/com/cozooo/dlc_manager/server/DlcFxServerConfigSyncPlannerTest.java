package com.cozooo.dlc_manager.server;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxServerConfigSyncPlannerTest {
    @Test
    void updateUsesGlobalServerConfigVisibilityRules() {
        JsonObject local = config(true, 12000, "local-token", "LocalDLC");
        JsonObject server = config(false, 13000, "server-token", "ServerDLC");
        JsonObject serverOnly = new JsonObject();
        serverOnly.addProperty("serverSupport", false);
        serverOnly.addProperty("serverPort", 25570);
        serverOnly.addProperty("serverPassword", "server-password");
        serverOnly.addProperty("rconHost", "10.0.0.5");
        serverOnly.addProperty("rconPort", 25575);
        serverOnly.addProperty("rconPassword", "rcon-password");
        server.add("server", serverOnly);

        JsonObject updated = DlcFxServerConfigSyncPlanner.buildServerConfigUpdate(local, server);

        assertEquals(12000, updated.getAsJsonObject("api").get("apiPort").getAsInt());
        assertEquals("local-token", updated.getAsJsonObject("api").get("apiToken").getAsString());
        assertEquals("LocalDLC", updated.getAsJsonObject("paths").get("dlcRoot").getAsString());
        assertFalse(updated.getAsJsonObject("ui").get("serverSupport").getAsBoolean());
        assertEquals(25570, updated.getAsJsonObject("server").get("serverPort").getAsInt());
        assertEquals("server-password", updated.getAsJsonObject("server").get("serverPassword").getAsString());
        assertEquals("10.0.0.5", updated.getAsJsonObject("server").get("rconHost").getAsString());
        assertEquals("rcon-password", updated.getAsJsonObject("server").get("rconPassword").getAsString());
    }

    @Test
    void previewIgnoresFieldsHiddenByGlobalServerConfigModel() {
        JsonObject local = config(true, 12000, "same-token", "DLC");
        JsonObject server = config(false, 12000, "same-token", "DLC");

        DlcFxServerConfigSyncPlanner.Preview preview = DlcFxServerConfigSyncPlanner.preview(local, server);

        assertEquals(0, preview.changedCount());
        assertTrue(preview.sameCount() > 0);
    }

    private static JsonObject config(boolean serverSupport, int apiPort, String apiToken, String dlcRoot) {
        JsonObject root = new JsonObject();
        JsonObject api = new JsonObject();
        api.addProperty("apiEnabled", true);
        api.addProperty("apiPort", apiPort);
        api.addProperty("apiToken", apiToken);
        root.add("api", api);
        JsonObject paths = new JsonObject();
        paths.addProperty("dlcRoot", dlcRoot);
        root.add("paths", paths);
        JsonObject ui = new JsonObject();
        ui.addProperty("serverSupport", serverSupport);
        root.add("ui", ui);
        root.add("downloads", new JsonObject());
        root.add("modpack", new JsonObject());
        return root;
    }
}
