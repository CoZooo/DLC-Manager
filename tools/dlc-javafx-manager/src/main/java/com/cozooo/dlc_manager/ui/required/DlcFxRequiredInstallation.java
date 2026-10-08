package com.cozooo.dlc_manager.ui.required;

import com.cozooo.dlc_manager.bootstrap.DlcEarlyInstaller;
import com.cozooo.dlc_manager.client.server.DlcServerSession;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DlcFxRequiredInstallation {
    private DlcFxRequiredInstallation() {}

    public static void installLocal(Path gameDir) throws IOException, InterruptedException {
        DlcPlatformServices.Provider provider = DlcPlatformServices.get();
        DlcEarlyInstaller.install(gameDir, gameDir.resolve("config"), provider.loaderName(), provider.mcVersion(),
                false, message -> System.out.println("[dlc-manager] " + message));
    }

    public interface Remote {
        JsonObject list() throws IOException;
        void start(List<String> ids) throws IOException;
        JsonObject status() throws IOException;
    }

    public static void installRemote(DlcServerSession session) throws IOException, InterruptedException {
        installRemote(new Remote() {
            @Override public JsonObject list() throws IOException { return session.requiredList(); }
            @Override public void start(List<String> ids) throws IOException { session.requiredDownloadStart(ids); }
            @Override public JsonObject status() throws IOException { return session.requiredDownloadStatus(); }
        });
    }

    public static void installRemote(Remote remote) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Required resource installation interrupted");
        List<String> missing = pending(remote.list());
        if (missing.isEmpty()) return;
        remote.start(missing);
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Required resource installation interrupted");
            JsonObject response = remote.status();
            if (response == null || !response.has("status") || !response.get("status").isJsonObject()) {
                throw new IOException("Missing required resource installation status");
            }
            JsonObject status = response.getAsJsonObject("status");
            if (flag(status, "finished")) {
                if (!flag(status, "success")) {
                    JsonElement error = status.get("error");
                    throw new IOException("Required resource installation failed: " + (error == null || error.isJsonNull() ? "unknown error" : error.getAsString()));
                }
                break;
            }
            Thread.sleep(250);
        }
        if (!pending(remote.list()).isEmpty()) {
            throw new IOException("Required resource installation incomplete");
        }
    }

    private static List<String> pending(JsonObject response) throws IOException {
        if (response == null || !response.has("items") || !response.get("items").isJsonArray()) {
            throw new IOException("Missing required resource list");
        }
        List<String> ids = new ArrayList<>();
        for (JsonElement element : response.getAsJsonArray("items")) {
            if (!element.isJsonObject()) throw new IOException("Invalid required resource entry");
            JsonObject item = element.getAsJsonObject();
            boolean hasFile = flag(item, "hasFile");
            boolean needsApply = flag(item, "needsApply");
            if (!hasFile || needsApply) {
                JsonElement id = item.get("id");
                if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString() || id.getAsString().isBlank()) {
                    throw new IOException("Missing required resource identifier");
                }
                ids.add(id.getAsString());
            }
        }
        return ids;
    }

    private static boolean flag(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IOException("Invalid required resource field: " + key);
        }
        return value.getAsBoolean();
    }
}
