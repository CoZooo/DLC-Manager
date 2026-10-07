package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.server.DlcServerFileEntry;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Change;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Direction;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Plan;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Row;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Status;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncSnapshot.FileState;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncSnapshot.Item;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncSnapshot.Kind;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxDlcSyncPlannerTest {
    private static FileState state(String rel, long size, String hash) {
        return new FileState(rel, size, 1L, hash);
    }

    private static DlcFxDlcSyncSnapshot snapshot(List<Item> items, Map<String, FileState> local, Map<String, FileState> server) {
        return new DlcFxDlcSyncSnapshot(Path.of("DLC"), "DLC", "host:1", items, local, server);
    }

    private static Row row(Plan plan, String key) {
        return plan.rows().stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void pushTransfersDataBeforeConfigAndClassifiesRows() {
        Map<String, FileState> local = new HashMap<>();
        Map<String, FileState> server = new HashMap<>();
        local.put("mods/new.jar", state("mods/new.jar", 10, "h-new"));
        local.put("mods/same.jar", state("mods/same.jar", 20, "h-same"));
        server.put("mods/same.jar", state("mods/same.jar", 20, "h-same"));
        local.put("mods/size.jar", state("mods/size.jar", 30, "h-a"));
        server.put("mods/size.jar", state("mods/size.jar", 31, null));
        local.put("mods/blind.jar", state("mods/blind.jar", 40, "h-b"));
        server.put("mods/blind.jar", state("mods/blind.jar", 40, null));

        List<Item> items = List.of(
                new Item("remote/mods/new.dc", Kind.REMOTE, "new", state("remote/mods/new.dc", 5, "c1"), null, "mods/new.jar", null),
                new Item("remote/mods/same.dc", Kind.REMOTE, "same", state("remote/mods/same.dc", 5, "c2"), state("remote/mods/same.dc", 5, "c2"), "mods/same.jar", "mods/same.jar"),
                new Item("remote/mods/size.dc", Kind.REMOTE, "size", state("remote/mods/size.dc", 5, "c3"), state("remote/mods/size.dc", 5, "c3"), "mods/size.jar", "mods/size.jar"),
                new Item("remote/mods/blind.dc", Kind.REMOTE, "blind", state("remote/mods/blind.dc", 5, "c4"), state("remote/mods/blind.dc", 5, "c4"), "mods/blind.jar", "mods/blind.jar"),
                new Item("remote/mods/cfg.dc", Kind.REMOTE, "cfg", state("remote/mods/cfg.dc", 5, "c5"), state("remote/mods/cfg.dc", 6, "c5-old"), "mods/same.jar", "mods/same.jar"),
                new Item("remote/mods/nofile.dc", Kind.REMOTE, "nofile", state("remote/mods/nofile.dc", 5, "c6"), null, "mods/absent.jar", null),
                new Item("required/server.dc", Kind.REQUIRED, "server", null, state("required/server.dc", 5, "c7"), null, "required/server.jar"));

        Plan plan = DlcFxDlcSyncPlanner.plan(snapshot(items, local, server), Direction.PUSH);

        Row created = row(plan, "remote/mods/new.dc");
        assertEquals(Status.TRANSFER, created.status());
        assertEquals(List.of("mods/new.jar", "remote/mods/new.dc"), created.transfers().stream().map(DlcFxDlcSyncPlanner.Transfer::rel).toList());
        assertEquals(15L, created.transferBytes());

        assertEquals(Status.SYNCED, row(plan, "remote/mods/same.dc").status());
        assertEquals(Change.CHANGED, row(plan, "remote/mods/size.dc").dataChange());
        assertEquals(Status.TRANSFER, row(plan, "remote/mods/size.dc").status());

        Row blind = row(plan, "remote/mods/blind.dc");
        assertEquals(Status.TRANSFER, blind.status());
        assertEquals(Change.UNVERIFIED, blind.dataChange());
        assertEquals(List.of("mods/blind.jar"), blind.transfers().stream().map(DlcFxDlcSyncPlanner.Transfer::rel).toList());

        Row cfg = row(plan, "remote/mods/cfg.dc");
        assertEquals(Change.CHANGED, cfg.configChange());
        assertEquals(List.of("remote/mods/cfg.dc"), cfg.transfers().stream().map(DlcFxDlcSyncPlanner.Transfer::rel).toList());

        Row noFile = row(plan, "remote/mods/nofile.dc");
        assertEquals(Status.SOURCE_DATA_MISSING, noFile.status());
        assertTrue(noFile.transfers().isEmpty());

        assertEquals(Status.TARGET_ONLY, row(plan, "required/server.dc").status());
        assertEquals(4, plan.count(Status.TRANSFER));
        assertEquals(1, plan.unverifiedCount());
        assertEquals(Status.TRANSFER, plan.rows().get(0).status());
    }

    @Test
    void pullUsesServerSideAsSource() {
        Map<String, FileState> server = Map.of("required/lib.jar", state("required/lib.jar", 9, "h-lib"));
        List<Item> items = List.of(
                new Item("required/lib.dc", Kind.REQUIRED, "lib", null, state("required/lib.dc", 4, "c-lib"), null, "required/lib.jar"),
                new Item("remote/local_only.dc", Kind.REMOTE, "local", state("remote/local_only.dc", 4, "c-l"), null, "local.jar", null));

        Plan plan = DlcFxDlcSyncPlanner.plan(snapshot(items, Map.of(), server), Direction.PULL);

        Row lib = row(plan, "required/lib.dc");
        assertEquals(Status.TRANSFER, lib.status());
        assertEquals(List.of("required/lib.jar", "required/lib.dc"), lib.transfers().stream().map(DlcFxDlcSyncPlanner.Transfer::rel).toList());
        assertEquals(Status.TARGET_ONLY, row(plan, "remote/local_only.dc").status());
    }

    @Test
    void serverDlcRootComesFromServerConfigAndStaysInsideGameDir() throws IOException {
        assertEquals("DLC", DlcFxDlcSyncSnapshot.resolveServerDlcRoot(new JsonObject()));
        assertEquals("custom/DLC", DlcFxDlcSyncSnapshot.resolveServerDlcRoot(configWithRoot("./custom\\DLC/, other")));
        assertThrows(IOException.class, () -> DlcFxDlcSyncSnapshot.resolveServerDlcRoot(configWithRoot("C:/abs/DLC")));
        assertThrows(IOException.class, () -> DlcFxDlcSyncSnapshot.resolveServerDlcRoot(configWithRoot("../outside")));
    }

    @Test
    void requiredFileMatchingFollowsGlobalResolution() {
        DlcRequiredEntry entry = new DlcRequiredEntry(Path.of("DLC/required/lib.dc"), Path.of("DLC/required"));
        Map<String, DlcServerFileEntry> files = new LinkedHashMap<>();
        files.put("lib.dc", file("lib.dc"));
        files.put("lib.jar.part", file("lib.jar.part"));
        assertNull(DlcFxDlcSyncSnapshot.matchRequiredFile(entry, files));

        files.put("lib.jar", file("lib.jar"));
        assertEquals("lib.jar", DlcFxDlcSyncSnapshot.matchRequiredFile(entry, files));

        files.put("explicit-1.0.jar", file("explicit-1.0.jar"));
        entry.setRequiredFileName("explicit-1.0.jar");
        assertEquals("explicit-1.0.jar", DlcFxDlcSyncSnapshot.matchRequiredFile(entry, files));
    }

    @Test
    void pathHelpersRejectEscapes() {
        assertEquals("DLC/remote/a.dc", DlcFxDlcSyncSnapshot.join("DLC/", "/remote/a.dc"));
        assertEquals("remote", DlcFxDlcSyncSnapshot.parentOf("remote/a.dc"));
        assertEquals("a.dc", DlcFxDlcSyncSnapshot.nameOf("remote/a.dc"));
        DlcFxDlcSyncSnapshot snapshot = snapshot(List.of(), Map.of(), Map.of());
        assertThrows(IOException.class, () -> snapshot.localPath("../escape.jar"));
    }

    @Test
    void verifyChecksConfigHashAndDataFileExistence() {
        Map<String, FileState> local = new HashMap<>();
        Map<String, FileState> server = new HashMap<>();
        local.put("remote/a.dc", state("remote/a.dc", 5, "c-a"));
        server.put("remote/a.dc", state("remote/a.dc", 5, "c-a"));
        local.put("mods/a.jar", state("mods/a.jar", 10, "d-a"));
        server.put("mods/a.jar", state("mods/a.jar", 10, "d-a"));
        local.put("remote/changed.dc", state("remote/changed.dc", 5, "c-old"));
        server.put("remote/changed.dc", state("remote/changed.dc", 5, "c-new"));
        local.put("mods/changed.jar", state("mods/changed.jar", 20, "d-old"));
        server.put("mods/changed.jar", state("mods/changed.jar", 21, "d-new"));
        local.put("remote/redirect.dc", state("remote/redirect.dc", 5, "c-r"));
        server.put("remote/redirect.dc", state("remote/redirect.dc", 5, "c-r"));
        local.put("mods/redirect.jar", state("mods/redirect.jar", 30, "d-r"));
        server.put("mods/other.jar", state("mods/other.jar", 30, "d-r"));
        local.put("remote/missing.dc", state("remote/missing.dc", 5, "c-m"));
        server.put("remote/missing.dc", state("remote/missing.dc", 5, "c-m"));
        local.put("mods/missing.jar", state("mods/missing.jar", 40, "d-m"));

        List<Item> items = List.of(
                new Item("remote/a.dc", Kind.REMOTE, "a", state("remote/a.dc", 5, "c-a"), state("remote/a.dc", 5, "c-a"), "mods/a.jar", "mods/a.jar"),
                new Item("remote/changed.dc", Kind.REMOTE, "changed", state("remote/changed.dc", 5, "c-old"), state("remote/changed.dc", 5, "c-new"), "mods/changed.jar", "mods/changed.jar"),
                new Item("remote/redirect.dc", Kind.REMOTE, "redirect", state("remote/redirect.dc", 5, "c-r"), state("remote/redirect.dc", 5, "c-r"), "mods/redirect.jar", "mods/other.jar"),
                new Item("remote/missing.dc", Kind.REMOTE, "missing", state("remote/missing.dc", 5, "c-m"), state("remote/missing.dc", 5, "c-m"), "mods/missing.jar", null));

        DlcFxDlcSyncSnapshot snapshot = snapshot(items, local, server);
        List<DlcFxDlcSyncPlanner.Verification> results = DlcFxDlcSyncPlanner.verify(snapshot, Direction.PUSH, List.of("remote/a.dc", "remote/changed.dc", "remote/redirect.dc", "remote/missing.dc"));

        assertEquals(4, results.size());
        DlcFxDlcSyncPlanner.Verification a = results.stream().filter(r -> r.identifier().equals("a")).findFirst().orElseThrow();
        assertEquals(DlcFxDlcSyncPlanner.VerifyStatus.OK, a.status());
        assertEquals("ok", a.reason());

        DlcFxDlcSyncPlanner.Verification changed = results.stream().filter(r -> r.identifier().equals("changed")).findFirst().orElseThrow();
        assertEquals(DlcFxDlcSyncPlanner.VerifyStatus.MISMATCH, changed.status());
        assertEquals("config_changed", changed.reason());

        DlcFxDlcSyncPlanner.Verification redirect = results.stream().filter(r -> r.identifier().equals("redirect")).findFirst().orElseThrow();
        assertEquals(DlcFxDlcSyncPlanner.VerifyStatus.MISMATCH, redirect.status());
        assertEquals("data_redirected", redirect.reason());

        DlcFxDlcSyncPlanner.Verification missing = results.stream().filter(r -> r.identifier().equals("missing")).findFirst().orElseThrow();
        assertEquals(DlcFxDlcSyncPlanner.VerifyStatus.MISSING, missing.status());
        assertEquals("data_missing", missing.reason());
    }

    private static JsonObject configWithRoot(String root) {
        JsonObject paths = new JsonObject();
        paths.addProperty("dlcRoot", root);
        JsonObject config = new JsonObject();
        config.add("paths", paths);
        JsonObject response = new JsonObject();
        response.add("config", config);
        return response;
    }

    private static DlcServerFileEntry file(String name) {
        DlcServerFileEntry entry = new DlcServerFileEntry();
        entry.name = name;
        return entry;
    }
}
