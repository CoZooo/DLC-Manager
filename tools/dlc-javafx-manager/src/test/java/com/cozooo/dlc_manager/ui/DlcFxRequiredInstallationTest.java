package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.ui.required.DlcFxRequiredInstallation;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

final class DlcFxRequiredInstallationTest {
    @TempDir Path game;

    @AfterEach void reset() {
        DlcPlatformServices.setProvider(null);
        DlcConfig.setDlcRoot("DLC");
        Thread.interrupted();
    }

    @Test void localInstallationCoversConfiguredRootsDependenciesAndDeletedTargets() throws Exception {
        DlcFxPlatformProvider provider = new DlcFxPlatformProvider(game);
        DlcPlatformServices.setProvider(provider);
        Path config = game.resolve("config/dlc_manager");
        Files.createDirectories(config);
        Files.writeString(config.resolve("config.dc"), "[paths]\ndlcRoot = \"first,second\"\n");
        resource("first", "main");
        resource("second", "other");
        Files.writeString(game.resolve("first/required/main.dc"), """
            identifier = "main"
            file_name = "main.jar"
            appliedTarget = "mods/main.jar"
            [dependencies.child]
            file_name = "child.jar"
            appliedTarget = "mods/child.jar"
            """);
        Files.writeString(game.resolve("first/required/child.jar"), "child");
        DlcFxRequiredInstallation.installLocal(game);
        assertEquals("main", Files.readString(game.resolve("mods/main.jar")));
        assertEquals("other", Files.readString(game.resolve("mods/other.jar")));
        assertEquals("child", Files.readString(game.resolve("mods/child.jar")));
        assertSame(provider, DlcPlatformServices.get());
        Files.delete(game.resolve("mods/child.jar"));
        DlcFxRequiredInstallation.installLocal(game);
        assertEquals("child", Files.readString(game.resolve("mods/child.jar")));
    }

    @Test void localInstallationFailuresCannotReportReadyAndRestoreProvider() throws Exception {
        DlcFxPlatformProvider provider = new DlcFxPlatformProvider(game);
        DlcPlatformServices.setProvider(provider);
        resource("DLC", "main");
        Files.writeString(game.resolve("mods"), "blocked target");
        assertThrows(IOException.class, () -> DlcFxRequiredInstallation.installLocal(game));
        assertSame(provider, DlcPlatformServices.get());
        assertFalse(Files.exists(game.resolve("mods/main.jar")));
        Files.delete(game.resolve("mods"));
        DlcFxRequiredInstallation.installLocal(game);
        assertEquals("main", Files.readString(game.resolve("mods/main.jar")));
    }

    @Test void invalidLocalConfigurationCannotBeSilentlySkipped() throws Exception {
        DlcPlatformServices.setProvider(new DlcFxPlatformProvider(game));
        Files.createDirectories(game.resolve("DLC/required"));
        Files.writeString(game.resolve("DLC/required/broken.dc"), "[unterminated\n");
        assertThrows(IOException.class, () -> DlcFxRequiredInstallation.installLocal(game));
    }

    @Test void readyServerDoesNotStartAnotherInstallation() throws Exception {
        FakeRemote remote = new FakeRemote(ready(), ready());
        DlcFxRequiredInstallation.installRemote(remote);
        assertNull(remote.started);
        assertEquals(1, remote.listCalls);
        assertEquals(0, remote.statusCalls);
    }

    @Test void remoteDownloadsMissingResourcesAndAppliesCachedResourcesThenVerifies() throws Exception {
        FakeRemote remote = new FakeRemote(json("""
            {"items":[
              {"id":"parent","hasFile":true,"needsApply":true},
              {"id":"child","hasFile":false,"needsApply":true},
              {"id":"ready","hasFile":true,"needsApply":false}
            ]}
            """), ready(), json("{\"status\":{\"finished\":false}}"), success());
        DlcFxRequiredInstallation.installRemote(remote);
        assertEquals(List.of("parent", "child"), remote.started);
        assertEquals(2, remote.statusCalls);
        assertEquals(2, remote.listCalls);
    }

    @Test void remoteDownloadFailurePreventsContinuing() {
        FakeRemote remote = new FakeRemote(missing(), ready(), json("{\"status\":{\"finished\":true,\"success\":false,\"error\":\"download failed\"}}"));
        IOException error = assertThrows(IOException.class, () -> DlcFxRequiredInstallation.installRemote(remote));
        assertTrue(error.getMessage().contains("download failed"));
        assertEquals(1, remote.listCalls);
    }

    @Test void serverSuccessIsInsufficientWhenResourcesAreStillMissing() {
        FakeRemote remote = new FakeRemote(missing(), missing(), success());
        assertThrows(IOException.class, () -> DlcFxRequiredInstallation.installRemote(remote));
        assertEquals(2, remote.listCalls);
    }

    @Test void malformedResponsesCannotBypassMandatoryInstallation() {
        for (String response : List.of("{}", "{\"items\":[{}]}", "{\"items\":[{\"hasFile\":false,\"needsApply\":true}]}")) {
            assertThrows(IOException.class, () -> DlcFxRequiredInstallation.installRemote(new FakeRemote(json(response), ready())));
        }
        for (String response : List.of("{}", "{\"status\":null}", "{\"status\":{}}", "{\"status\":{\"finished\":true}}")) {
            assertThrows(IOException.class, () -> DlcFxRequiredInstallation.installRemote(new FakeRemote(missing(), ready(), json(response))));
        }
    }

    @Test void interruptedRemoteInstallationCannotReportReady() {
        FakeRemote remote = new FakeRemote(ready(), ready());
        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> DlcFxRequiredInstallation.installRemote(remote));
        assertEquals(0, remote.listCalls);
    }

    private void resource(String root, String id) throws IOException {
        Path required = game.resolve(root).resolve("required");
        Files.createDirectories(required);
        Files.writeString(required.resolve(id + ".dc"), "identifier = \"" + id + "\"\nfile_name = \"" + id + ".jar\"\nappliedTarget = \"mods/" + id + ".jar\"\n");
        Files.writeString(required.resolve(id + ".jar"), id);
    }

    private static JsonObject ready() {
        return json("{\"items\":[{\"id\":\"main\",\"hasFile\":true,\"needsApply\":false}]}");
    }

    private static JsonObject missing() {
        return json("{\"items\":[{\"id\":\"main\",\"hasFile\":false,\"needsApply\":true}]}");
    }

    private static JsonObject success() {
        return json("{\"status\":{\"finished\":true,\"success\":true}}");
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static final class FakeRemote implements DlcFxRequiredInstallation.Remote {
        final JsonObject before;
        final JsonObject after;
        final ArrayDeque<JsonObject> statuses;
        List<String> started;
        int listCalls;
        int statusCalls;

        FakeRemote(JsonObject before, JsonObject after, JsonObject... statuses) {
            this.before = before;
            this.after = after;
            this.statuses = new ArrayDeque<>(List.of(statuses));
        }

        @Override public JsonObject list() { return ++listCalls == 1 ? before : after; }
        @Override public void start(List<String> ids) { started = List.copyOf(ids); }
        @Override public JsonObject status() { statusCalls++; return statuses.remove(); }
    }
}
