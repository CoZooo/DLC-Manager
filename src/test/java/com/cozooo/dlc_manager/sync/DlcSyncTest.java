package com.cozooo.dlc_manager.sync;

import com.cozooo.dlc_manager.core.DlcFileMatch;
import com.cozooo.dlc_manager.sync.DlcSyncModel.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcSyncTest {
    @TempDir Path temp;
    private final Progress quiet = (phase, file, done, total) -> {};
    private File file(String id, String hash) { return new File(id, 3, hash); }
    private Item item(String key, String hash, boolean state) { return new Item(key, key, file(key, hash), List.of(file("data/" + key, "data")), state); }

    @Test void resolvesLocalizedNamesAndShowsOneStatusForEachDifference() {
        Item named = new Item("remote/a.dc", "internal-id", file("remote/a.dc", "same"), List.of(file("data/a", "data")), true,
            Map.of("zh_cn", "中文名称", "en_us", "English name"));
        Item transported = DlcSyncEndpoint.JSON.fromJson(DlcSyncEndpoint.JSON.toJson(named), Item.class);
        assertEquals("中文名称", DlcSyncModel.displayName(transported, "zh_CN"));
        assertEquals("English name", DlcSyncModel.displayName(transported, "fr_fr"));
        assertEquals("internal-id", DlcSyncModel.displayName(new Item(named.key(), named.identifier(), named.config(), named.resources(), true), "zh_cn"));
        Item unnamed = new Item("0:remote/folder/sample.dc", null, named.config(), named.resources(), true);
        assertEquals("sample", DlcSyncModel.displayName(unnamed, "zh_cn"));
        assertEquals("server_missing", DlcSyncModel.status(DlcSyncModel.plan(List.of(named), List.of(), Direction.PUSH, true).get(0), Mode.CONFIG));
        assertEquals("client_missing", DlcSyncModel.status(DlcSyncModel.plan(List.of(), List.of(named), Direction.PUSH, true).get(0), Mode.CONFIG));
        Item disabled = new Item(named.key(), named.identifier(), named.config(), named.resources(), false);
        assertEquals("state_changed", DlcSyncModel.status(DlcSyncModel.plan(List.of(named), List.of(disabled), Direction.PUSH, true).get(0), Mode.CONFIG));
        Item different = new Item(named.key(), named.identifier(), file(named.key(), "other"), named.resources(), true);
        assertEquals("content_changed", DlcSyncModel.status(DlcSyncModel.plan(List.of(named), List.of(different), Direction.PUSH, true).get(0), Mode.CONFIG));
    }
    @Test void hidesIdenticalItemsAndIncludesStateOnlyDifferencesOnlyWhenRequested() {
        Item a = item("remote/a.dc", "same", true), b = item("remote/a.dc", "same", false);
        assertTrue(DlcSyncModel.plan(List.of(a), List.of(a), Direction.PUSH, true).isEmpty());
        assertTrue(DlcSyncModel.plan(List.of(a), List.of(b), Direction.PUSH, false).isEmpty());
        Row difference = DlcSyncModel.plan(List.of(a), List.of(b), Direction.PUSH, true).get(0);
        assertTrue(difference.stateChanged()); assertTrue(difference.replacement());
        assertEquals(Direction.PULL, DlcSyncModel.plan(List.of(), List.of(a), Direction.PUSH, true).get(0).direction());
        assertEquals(a, DlcSyncModel.plan(List.of(), List.of(a), Direction.PULL, true).get(0).source());
        Item unknown = new Item(a.key(), a.identifier(), a.config(), List.of(new File(a.resources().get(0).id(), -1, null)), a.enabled());
        assertTrue(DlcSyncModel.plan(List.of(unknown), List.of(a), Direction.PUSH, false, Mode.FILES).get(0).replacement());
        assertTrue(DlcSyncModel.plan(List.of(unknown), List.of(a), Direction.PUSH, false, Mode.CONFIG).isEmpty());
    }
    @Test void comparisonCachesStatusesWithoutReadingResourcesAgainForDisplay() {
        AtomicBoolean displayed = new AtomicBoolean();
        List<File> resources = new AbstractList<>() {
            @Override public File get(int index) {
                assertFalse(displayed.get(), "Opening a tooltip must not compare resources again");
                return List.of(file("data/a", "new")).get(index);
            }
            @Override public int size() {
                assertFalse(displayed.get(), "Displaying a comparison must not traverse resources again");
                return 1;
            }
        };
        Item local = new Item("remote/a.dc", "a", file("remote/a.dc", "same"), resources, true);
        Item remote = new Item(local.key(), "a", local.config(), List.of(file("data/a", "old")), true);
        Comparison comparison = DlcSyncModel.compare(List.of(local), List.of(remote), Direction.PUSH, true, Mode.FILES);
        assertEquals(1, comparison.rows().size());
        Row row = comparison.rows().get(0);
        displayed.set(true);
        for (int hover = 0; hover < 100; hover++) assertEquals("content_changed", comparison.status(row));
        assertThrows(UnsupportedOperationException.class, () -> comparison.statuses().clear());
    }
    @Test void rebuildingAComparisonRefreshesCachedStatusesForModeDirectionAndNewManifests() {
        Item local = item("remote/a.dc", "same", true);
        Item remote = new Item(local.key(), local.identifier(), local.config(), List.of(new File(local.resources().get(0).id(), -1, null)), false);
        Comparison config = DlcSyncModel.compare(List.of(local), List.of(remote), Direction.PUSH, true, Mode.CONFIG);
        Comparison files = DlcSyncModel.compare(List.of(local), List.of(remote), Direction.PUSH, true, Mode.FILES);
        Comparison pull = DlcSyncModel.compare(List.of(), List.of(remote), Direction.PUSH, true, Mode.FILES);
        assertEquals("state_changed", config.status(config.rows().get(0)));
        assertEquals("resources_missing", files.status(files.rows().get(0)));
        assertEquals("client_missing", pull.status(pull.rows().get(0)));
        assertTrue(DlcSyncModel.compare(List.of(local), List.of(remote), Direction.PUSH, false, Mode.CONFIG).rows().isEmpty());
        Comparison unchanged = DlcSyncModel.compare(List.of(local), List.of(local), Direction.PULL, true, Mode.FILES);
        assertTrue(unchanged.rows().isEmpty());
        assertTrue(unchanged.statuses().isEmpty());
        assertEquals("state_changed", config.status(config.rows().get(0)), "A new comparison must not mutate a previous snapshot");
    }
    private Item update(String key, String from, String to, String platform) {
        return new Item(key, key, file(key, key), List.of(), null, Map.of(), Map.of(), new Update("1.0.0", from, "1.0.0", to, platform));
    }
    @Test void classifiesNewTypesAndHidesInstalledVersionRecords() {
        Item manager = new Item("manager:config.dc", "config", file("manager:config.dc", "same"), List.of(), null);
        Item preset = new Item("0:preset/main.dc", "preset", file("0:preset/main.dc", "same"), List.of(), null);
        Item required = item("0:required/main.dc", "same", false);
        Item version = update("0:info/version.dc", "1.0.0", "1.0.0", "server");
        assertEquals(Category.MANAGER_CONFIG, DlcSyncModel.category(manager));
        assertEquals(Category.PRESET, DlcSyncModel.category(preset));
        assertEquals(Category.REQUIRED, DlcSyncModel.category(required));
        assertEquals(Category.UPDATE, DlcSyncModel.category(version));
        assertEquals(Category.DLC, DlcSyncModel.category(item("0:remote/main.dc", "same", false)));
        assertEquals(2, DlcSyncModel.plan(List.of(manager, preset, version), List.of(), Direction.PUSH, true, Mode.CONFIG).size());
        assertTrue(DlcSyncModel.plan(List.of(manager, preset, version), List.of(manager, preset), Direction.PUSH, true, Mode.FILES).isEmpty());
    }
    @Test void appliesDlcBeforePresetsAndDisablesOldPresetsBeforeEnablingNewOnes() {
        Item off = new Item("0:preset/z-old.dc", "old", file("0:preset/z-old.dc", "off"), List.of(), false);
        Item on = new Item("0:preset/a-new.dc", "new", file("0:preset/a-new.dc", "on"), List.of(), true);
        Item dlc = item("0:remote/main.dc", "main", true);
        List<Row> rows = List.of(new Row(on.key(), on, null, Direction.PUSH, false, true), new Row(off.key(), off, null, Direction.PUSH, false, true), new Row(dlc.key(), dlc, null, Direction.PUSH, false, true));
        assertEquals(List.of(dlc.key(), off.key(), on.key()), DlcSyncModel.ordered(rows).stream().map(Row::key).toList());
    }
    @Test void offersOnlyReachablePlatformCompatibleUpdatesInFilesMode() {
        Item installed = update("0:info/version.dc", "1.0.0", "1.0.0", "server");
        Item first = update("0:update/z-first.zip", "1.0.0", "1.1.0", "server");
        Item second = update("0:update/a-second.zip", "1.1.0", "1.2.0", "universal");
        List<Item> packages = List.of(second, first,
            update("0:update/unreachable.zip", "2.0.0", "2.1.0", "server"),
            update("0:update/client.zip", "1.0.0", "1.9.0", "client"),
            update("0:update/downgrade.zip", "1.0.0", "0.9.0", "server"),
            update("1:update/other-root.zip", "1.0.0", "1.1.0", "server"));
        List<Row> rows = DlcSyncModel.plan(packages, List.of(installed), Direction.PUSH, true, Mode.FILES);
        assertEquals(Set.of(first.key(), second.key()), rows.stream().map(Row::key).collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of(first.key(), second.key()), DlcSyncModel.ordered(rows).stream().map(Row::key).toList());
        assertTrue(DlcSyncModel.plan(packages, List.of(installed), Direction.PUSH, true, Mode.CONFIG).isEmpty());
        assertTrue(DlcSyncModel.plan(packages, List.of(), Direction.PUSH, true, Mode.FILES).isEmpty());
        assertEquals(Set.of(second.key()), DlcSyncModel.eligibleUpdates(List.of(second), List.of(installed, first)));
        assertTrue(DlcSyncModel.eligibleUpdates(List.of(second), List.of(installed)).isEmpty());
        assertTrue(DlcSyncModel.plan(List.of(first), List.of(installed, first), Direction.PUSH, true, Mode.FILES).isEmpty());
        assertEquals(Direction.PULL, DlcSyncModel.plan(List.of(installed), List.of(first), Direction.PUSH, true, Mode.FILES).get(0).direction());
        Item baseOnly = new Item("0:update/base.zip", "base", file("0:update/base.zip", "base"), List.of(), null, Map.of(), Map.of(), new Update("1.0.0", "1.0.0", "2.0.0", "1.0.0", "server"));
        assertEquals(Set.of(baseOnly.key()), DlcSyncModel.eligibleUpdates(List.of(baseOnly), List.of(installed)));
    }
    @Test void showsTheStateOptionOnlyForDifferentStatesOfSharedItems() {
        Item enabled = item("remote/a.dc", "same", true), disabled = item("remote/a.dc", "same", false);
        assertFalse(DlcSyncModel.hasStateDifferences(List.of(enabled), List.of(enabled)));
        assertTrue(DlcSyncModel.hasStateDifferences(List.of(enabled), List.of(disabled)));
        assertFalse(DlcSyncModel.hasStateDifferences(List.of(enabled), List.of()));
        Item renamed = new Item(disabled.key(), "different-dlc", disabled.config(), disabled.resources(), false);
        assertFalse(DlcSyncModel.hasStateDifferences(List.of(enabled), List.of(renamed)));
        assertFalse(DlcSyncModel.plan(List.of(enabled), List.of(), Direction.PUSH, true).get(0).stateChanged());
        assertTrue(DlcSyncModel.hasStateDifferences(List.of(enabled), List.of(disabled)), "Visibility must not depend on whether the state option is currently selected");
    }
    @Test void anUnappliedStateIsSkippedWithoutFailingOrUndoingTransferredContent() throws Exception {
        for (boolean contentChanged : List.of(false, true)) {
            Fixture left = new Fixture(temp.resolve("local-skip-" + contentChanged)), right = new Fixture(temp.resolve("server-skip-" + contentChanged));
            left.add("a", contentChanged ? "new config" : "same", "same data", true);
            right.add("a", contentChanged ? "old config" : "same", "same data", false); right.failEnable = true;
            try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
                Result result = new DlcSyncClient(source, target, target).execute(plan(source, target, true), Mode.CONFIG, true, quiet);
                assertTrue(result.errors().isEmpty(), result.errors().toString());
                assertEquals(contentChanged ? 1 : 0, result.completed());
                assertEquals(contentChanged ? "new config" : "same", Files.readString(right.root.resolve("remote/a.dc")));
                assertEquals("true", Files.readString(right.root.resolve("state/a")));
                assertEquals(Files.readString(right.root.resolve("data/a")), Files.readString(right.root.resolve("applied/a")), "Partial state changes remain until the user requests rollback");
            }
        }
    }
    @Test void acceptingOnlyPartOfAStateDoesNotRequireAnIdenticalFinalFlag() throws Exception {
        Fixture left = new Fixture(temp.resolve("local-part")), right = new Fixture(temp.resolve("server-part"));
        left.add("a", "same", "same data", true); right.add("a", "same", "same data", false);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint endpoint = new DlcSyncEndpoint(right)) {
            Peer target = request -> {
                if (request.get("action").getAsString().equals("apply")) { request = request.deepCopy(); request.remove("enabled"); }
                return endpoint.call(request);
            };
            Result result = new DlcSyncClient(source, target, endpoint).execute(plan(source, endpoint, true), Mode.CONFIG, true, quiet);
            assertTrue(result.errors().isEmpty()); assertEquals("false", Files.readString(right.root.resolve("state/a")));
        }
    }
    @Test void configurationModeIgnoresResourcesWhileKeepingTheirMetadataAvailable() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "same", null, true); right.add("a", "same", "server data", true);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            List<Item> local = DlcSyncClient.manifest(source), remote = DlcSyncClient.manifest(target);
            assertEquals(-1, local.get(0).resources().get(0).size());
            assertTrue(remote.get(0).resources().get(0).size() > 0);
            assertTrue(DlcSyncModel.plan(local, remote, Direction.PUSH, true, Mode.CONFIG).isEmpty());
            Row missing = DlcSyncModel.plan(local, remote, Direction.PUSH, true, Mode.FILES).get(0);
            assertEquals("resources_missing", DlcSyncModel.status(missing, Mode.FILES));
            assertFalse(DlcSyncModel.differences(missing, Mode.CONFIG).contains("resources_missing"));
        }
    }
    @Test void sendsOnlyChangedFilesAndDoesNotTransferForStateOnlyChanges() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            Fixture left = new Fixture(temp.resolve("local" + variant)), right = new Fixture(temp.resolve("server" + variant));
            left.add("a", "new config", "new data", true);
            right.add("a", variant == 1 ? "old config" : "new config", variant == 0 ? "old data" : "new data", variant != 2);
            List<String> reads = new ArrayList<>(), writes = new ArrayList<>();
            try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
                Peer trackedSource = request -> { if (request.get("action").getAsString().equals("read")) reads.add(request.get("id").getAsString()); return source.call(request); };
                Peer trackedTarget = request -> { if (request.get("action").getAsString().equals("write")) writes.add(request.getAsJsonObject("file").get("id").getAsString()); return target.call(request); };
                Result result = new DlcSyncClient(trackedSource, trackedTarget, target).execute(plan(source, target, true), Mode.FILES, true, quiet);
                assertTrue(result.errors().isEmpty(), result.errors().toString()); assertEquals(1, result.completed());
                List<String> expected = variant == 0 ? List.of("data/a") : variant == 1 ? List.of("remote/a.dc") : List.of();
                assertEquals(expected, reads); assertEquals(expected, writes);
            }
        }
    }
    @Test void failedBackupPreventsOverwritingTheExistingResource() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "same", "new data", true); right.add("a", "same", "old data", true);
        right.captureFailure = right.root.resolve("data/a");
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            Result result = new DlcSyncClient(source, target, target).execute(plan(source, target, true), Mode.FILES, true, quiet);
            assertEquals(0, result.completed()); assertEquals(1, result.errors().size());
            assertEquals("old data", Files.readString(right.root.resolve("data/a")));
        }
    }
    @Test void filesModePreparesOnSourceAndAppliesOnTargetWithoutDownloadingThere() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "config", null, true);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            Result result = new DlcSyncClient(source, target, target).execute(plan(source, target, true), Mode.FILES, true, quiet);
            assertEquals(1, result.completed()); assertTrue(result.errors().isEmpty(), result.errors().toString());
            assertEquals(1, left.downloads); assertEquals(0, right.downloads);
            assertEquals("downloaded", Files.readString(right.root.resolve("data/a")));
            assertFalse(Files.exists(right.root.resolve("applied/a")));
            assertEquals("false", Files.readString(right.root.resolve("state/a")));
        }
    }
    @Test void configurationModeDownloadsOnlyOnTargetAndCanKeepTargetState() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "new config", null, true); right.add("a", "old config", "old", false);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            Result result = new DlcSyncClient(source, target, target).execute(plan(source, target, false), Mode.CONFIG, false, quiet);
            assertEquals(1, result.completed()); assertEquals(0, left.downloads);
            assertEquals("false", Files.readString(right.root.resolve("state/a")));
            assertFalse(Files.exists(right.root.resolve("applied/a")));
        }
    }
    @Test void cancelAfterOneCompletedItemUndoesEveryFileAndEnabledState() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "new", null, true); left.add("b", "new", "new data", true);
        right.add("a", "old", "old data", false);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            DlcSyncClient client = new DlcSyncClient(source, target, target);
            Result result = client.execute(plan(source, target, true), Mode.FILES, true, (phase, resource, done, total) -> {
                if (phase.equals("preparing") && resource.equals("remote/b.dc")) client.cancel();
            });
            assertTrue(result.canceled()); assertEquals(0, result.completed());
            assertEquals("old", Files.readString(right.root.resolve("remote/a.dc")));
            assertEquals("old data", Files.readString(right.root.resolve("data/a")));
            assertEquals("false", Files.readString(right.root.resolve("state/a")));
            assertFalse(Files.exists(right.root.resolve("applied/a")));
            assertFalse(Files.exists(right.root.resolve("remote/b.dc")));
            assertFalse(Files.exists(left.root.resolve("data/a")));
        }
    }
    @Test void corruptedUploadDoesNotReplaceOriginalAndOtherItemsCanContinue() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "new", "new data", true); left.add("b", "new", "new data", true);
        right.add("a", "old", "old data", false);
        AtomicBoolean corrupt = new AtomicBoolean(true);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint endpoint = new DlcSyncEndpoint(right)) {
            Peer target = request -> {
                if (request.get("action").getAsString().equals("write_chunk") && corrupt.getAndSet(false)) {
                    request = request.deepCopy(); request.addProperty("data", Base64.getEncoder().encodeToString("bad".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
                return endpoint.call(request);
            };
            Result result = new DlcSyncClient(source, target, endpoint).execute(plan(source, target, true), Mode.FILES, true, quiet);
            assertEquals(1, result.completed()); assertEquals(1, result.errors().size());
            assertEquals("old", Files.readString(right.root.resolve("remote/a.dc")));
            assertEquals("old data", Files.readString(right.root.resolve("data/a")));
            assertEquals("new", Files.readString(right.root.resolve("remote/b.dc")));
        }
    }
    @Test void applicationFailureKeepsChangesUntilExplicitRollback() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "new", "new data", true); right.add("a", "old", "old data", false); right.failApply = true;
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            DlcSyncClient client = new DlcSyncClient(source, target, target);
            Result result = client.execute(plan(source, target, true), Mode.FILES, true, quiet);
            assertEquals(0, result.completed()); assertEquals(1, result.errors().size());
            assertEquals("new", Files.readString(right.root.resolve("remote/a.dc")));
            assertEquals("new data", Files.readString(right.root.resolve("data/a")));
            assertTrue(client.canRollback());
            client.rollback(source, target);
            assertFalse(client.canRollback());
            assertEquals("old", Files.readString(right.root.resolve("remote/a.dc")));
            assertEquals("old data", Files.readString(right.root.resolve("data/a")));
            assertFalse(Files.exists(right.root.resolve("applied/a")));
            assertEquals("false", Files.readString(right.root.resolve("state/a")));
        }
    }
    @Test void refusesUnscopedPathsAndDoesNotOverwriteOnTruncation() throws Exception {
        Fixture right = new Fixture(temp.resolve("server")); right.add("a", "config", "old", false);
        try (DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", UUID.randomUUID().toString()); DlcSyncClient.checked(target, begin);
            JsonObject choose = DlcSyncClient.request("item"); choose.addProperty("key", "mods/other.jar"); assertFalse(target.call(choose).get("ok").getAsBoolean());
            choose.addProperty("key", "remote/a.dc"); DlcSyncClient.checked(target, choose);
            JsonObject write = DlcSyncClient.request("write"); write.add("file", DlcSyncEndpoint.JSON.toJsonTree(new File("data/a", 99, "unused"))); write.addProperty("hash", "incorrect"); DlcSyncClient.checked(target, write);
            assertFalse(target.call(DlcSyncClient.request("write_end")).get("ok").getAsBoolean());
            assertEquals("old", Files.readString(right.root.resolve("data/a")));
            DlcSyncClient.checked(target, DlcSyncClient.request("rollback"));
        }
    }
    @Test void rejectsTargetChangesMadeAfterComparison() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "new", "new data", true); right.add("a", "old", "old data", false);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            List<Row> rows = plan(source, target, true);
            right.write("remote/a.dc", "edited after comparison");
            assertThrows(IOException.class, () -> new DlcSyncClient(source, target, target).execute(rows, Mode.FILES, true, quiet));
            assertEquals("edited after comparison", Files.readString(right.root.resolve("remote/a.dc")));
            assertEquals("old data", Files.readString(right.root.resolve("data/a")));
        }
    }
    @Test void supportsStateOnlyUpdatesWithoutChangingDirectionOrDownloading() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "same", "same data", true); right.add("a", "same", "same data", false);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            Result result = new DlcSyncClient(source, target, target).execute(plan(source, target, true), Mode.CONFIG, true, quiet);
            assertEquals(1, result.completed()); assertEquals(0, left.downloads + right.downloads);
            assertEquals("true", Files.readString(right.root.resolve("state/a")));
            assertEquals("same data", Files.readString(right.root.resolve("applied/a")));
        }
    }
    @Test void pullsResourcesAndPreservesStateUnlessStateSyncIsSelected() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "old", "old data", false); right.add("a", "new", "new data", true);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            List<Row> rows = DlcSyncModel.plan(DlcSyncClient.manifest(source), DlcSyncClient.manifest(target), Direction.PULL, false);
            Result result = new DlcSyncClient(source, target, target).execute(rows, Mode.FILES, false, quiet);
            assertEquals(List.of("remote/a.dc"), result.succeeded());
            assertEquals("new data", Files.readString(left.root.resolve("data/a")));
            assertEquals("false", Files.readString(left.root.resolve("state/a")));
            assertEquals("true", Files.readString(right.root.resolve("state/a")));
        }
    }
    @Test void lostItemAcknowledgmentKeepsEarlierChangesAndAllowsRollback() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "new", "new data", true); right.add("a", "old", "old data", false);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            Peer lost = request -> {
                JsonObject response = source.call(request);
                if (request.get("action").getAsString().equals("item_done")) throw new IOException("Connection lost after item acknowledgment");
                return response;
            };
            DlcSyncClient client = new DlcSyncClient(lost, target, target);
            assertThrows(IOException.class, () -> client.execute(plan(source, target, true), Mode.FILES, true, quiet));
            assertEquals("new", Files.readString(right.root.resolve("remote/a.dc")));
            assertEquals("new data", Files.readString(right.root.resolve("data/a")));
            client.rollback(source, target);
            assertEquals("old", Files.readString(right.root.resolve("remote/a.dc")));
            assertEquals("old data", Files.readString(right.root.resolve("data/a")));
            assertEquals("false", Files.readString(right.root.resolve("state/a")));
            assertFalse(Files.exists(right.root.resolve("applied/a")));
        }
    }
    @Test void successfulSyncCanBeUndoneAfterConnectionsClose() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "new", "new data", true); right.add("a", "old", "old data", false);
        DlcSyncClient client;
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            client = new DlcSyncClient(source, target, target);
            assertEquals(1, client.execute(plan(source, target, true), Mode.FILES, true, quiet).completed());
        }
        assertEquals("new data", Files.readString(right.root.resolve("data/a")));
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            client.rollback(source, target);
        }
        assertEquals("old data", Files.readString(right.root.resolve("data/a")));
        assertEquals("false", Files.readString(right.root.resolve("state/a")));
        assertFalse(client.canRollback());
    }
    @Test void cancelingALaterOperationDoesNotUndoAnEarlierKeptSynchronization() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "first", "first data", false); right.add("a", "old", "old data", false);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            new DlcSyncClient(source, target, target).execute(plan(source, target, true), Mode.FILES, true, quiet);
            left.add("a", "second", "second data", false);
            DlcSyncClient client = new DlcSyncClient(source, target, target);
            Result result = client.execute(plan(source, target, true), Mode.FILES, true, (phase, name, done, total) -> {
                if (phase.equals("applying")) client.cancel();
            });
            assertTrue(result.canceled());
            assertEquals("first", Files.readString(right.root.resolve("remote/a.dc")));
            assertEquals("first data", Files.readString(right.root.resolve("data/a")));
        }
    }
    @Test void changedSourceAndBusyLegacyOperationsCannotSilentlyOverwriteTarget() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        left.add("a", "new", "new data", true); right.add("a", "old", "old data", false);
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint target = new DlcSyncEndpoint(right)) {
            Peer changing = request -> {
                if (request.get("action").getAsString().equals("read") && request.get("id").getAsString().equals("data/a")) left.write("data/a", "changed after comparison");
                return source.call(request);
            };
            Result result = new DlcSyncClient(changing, target, target).execute(plan(source, target, true), Mode.FILES, true, quiet);
            assertEquals(0, result.completed()); assertEquals(1, result.errors().size());
            assertEquals("old data", Files.readString(right.root.resolve("data/a")));
            JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", UUID.randomUUID().toString()); DlcSyncClient.checked(target, begin);
            assertThrows(IOException.class, () -> DlcSyncEndpoint.guard(right.workDirectory()));
            DlcSyncClient.checked(target, DlcSyncClient.request("rollback"));
            try (var guard = DlcSyncEndpoint.guard(right.workDirectory())) { assertFalse(DlcSyncEndpoint.busy(right.workDirectory())); }
        }
    }
    @Test void transfersMultipleChunksWithFullIntegrityVerification() throws Exception {
        Fixture left = new Fixture(temp.resolve("local")), right = new Fixture(temp.resolve("server"));
        String payload = "0123456789".repeat(250000);
        left.add("a", "config", payload, true);
        java.util.concurrent.atomic.AtomicInteger chunks = new java.util.concurrent.atomic.AtomicInteger();
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint endpoint = new DlcSyncEndpoint(right)) {
            Peer target = request -> { if (request.get("action").getAsString().equals("write_chunk")) chunks.incrementAndGet(); return endpoint.call(request); };
            Result result = new DlcSyncClient(source, target, endpoint).execute(plan(source, endpoint, true), Mode.FILES, true, quiet);
            assertEquals(1, result.completed()); assertTrue(result.errors().isEmpty()); assertTrue(chunks.get() >= 4);
            assertEquals(DlcFileMatch.fullHash(left.root.resolve("data/a")), DlcFileMatch.fullHash(right.root.resolve("data/a")));
            assertEquals(payload, Files.readString(right.root.resolve("data/a")));
            assertFalse(Files.exists(right.root.resolve("applied/a")));
        }
    }
    @Test void lightFingerprintDoesNotReplaceFullContentVerification() throws Exception {
        Path path = temp.resolve("large.jar"); byte[] bytes = new byte[200000]; Files.write(path, bytes);
        String light = DlcFileMatch.lightHash(path), full = DlcFileMatch.hash(path);
        long modified = Files.getLastModifiedTime(path).toMillis();
        bytes[100000] = 1; Files.write(path, bytes);
        Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.fromMillis(modified + 1000));
        assertEquals(light, DlcFileMatch.lightHash(path));
        assertNotEquals(full, DlcFileMatch.hash(path));
    }

    @Test void transfersUpdateChainInOrderWithoutInstallingAndRejectsMissingPrerequisiteBeforeSending() throws Exception {
        Map<String, Update> definitions = Map.of(
            "info/version.dc", new Update("1.0.0", "1.0.0", "1.0.0", "1.0.0", "server"),
            "update/z-first.zip", new Update("1.0.0", "1.0.0", "1.0.0", "1.1.0", "server"),
            "update/a-second.zip", new Update("1.0.0", "1.1.0", "1.0.0", "1.2.0", "universal"));
        UpdateFixture left = new UpdateFixture(temp.resolve("local-updates"), definitions), right = new UpdateFixture(temp.resolve("server-updates"), definitions);
        left.write("info/version.dc", "installed 1.0.0"); right.write("info/version.dc", "installed 1.0.0");
        left.write("update/z-first.zip", "first update payload"); left.write("update/a-second.zip", "second update payload");
        List<String> written = new ArrayList<>();
        try (DlcSyncEndpoint source = new DlcSyncEndpoint(left); DlcSyncEndpoint endpoint = new DlcSyncEndpoint(right)) {
            Peer target = request -> { if (request.get("action").getAsString().equals("write")) written.add(request.getAsJsonObject("file").get("id").getAsString()); return endpoint.call(request); };
            List<Row> rows = plan(source, endpoint, false);
            assertEquals(2, rows.size());
            Row later = rows.stream().filter(row -> row.key().endsWith("a-second.zip")).findFirst().orElseThrow();
            Result rejected = new DlcSyncClient(source, target, endpoint).execute(List.of(later), Mode.FILES, false, quiet);
            assertEquals(0, rejected.completed()); assertEquals(1, rejected.errors().size()); assertTrue(written.isEmpty());
            assertFalse(Files.exists(right.resolve(later.key())));
            Result wrongMode = new DlcSyncClient(source, target, endpoint).execute(rows, Mode.CONFIG, false, quiet);
            assertEquals(0, wrongMode.completed()); assertEquals(2, wrongMode.errors().size()); assertTrue(written.isEmpty());
            Result result = new DlcSyncClient(source, target, endpoint).execute(rows, Mode.FILES, false, quiet);
            assertEquals(2, result.completed()); assertTrue(result.errors().isEmpty()); assertFalse(result.requiresRestart());
            assertEquals(List.of("update/z-first.zip", "update/a-second.zip"), written);
            assertEquals("first update payload", Files.readString(right.resolve("update/z-first.zip")));
            assertEquals("second update payload", Files.readString(right.resolve("update/a-second.zip")));
            assertEquals("installed 1.0.0", Files.readString(right.resolve("info/version.dc")));
            assertEquals(0, right.downloads); assertFalse(Files.exists(right.root.resolve("applied")));
        }
    }
    private static final class UpdateFixture extends Fixture {
        private final Map<String, Update> definitions;
        UpdateFixture(Path root, Map<String, Update> definitions) throws IOException { super(root); this.definitions = definitions; }
        @Override public void validateKey(String key) throws IOException {
            if (!key.startsWith("update/") || !definitions.containsKey(key)) throw new IOException("Invalid update key"); resolve(key);
        }
        @Override public List<Item> manifest() throws IOException {
            List<Item> result = new ArrayList<>();
            for (var entry : definitions.entrySet()) {
                Path path = resolve(entry.getKey());
                if (Files.isRegularFile(path)) result.add(new Item(entry.getKey(), entry.getKey(), new File(entry.getKey(), Files.size(path), DlcFileMatch.hash(path)), List.of(), null, Map.of(), Map.of(), entry.getValue()));
            }
            return result;
        }
        @Override public void validateIncoming(Item item) throws IOException {
            if (!DlcSyncModel.eligibleUpdates(List.of(item), manifest()).contains(item.key())) throw new IOException("Missing update prerequisite");
        }
        @Override public void prepare(String key, boolean force, BooleanSupplier canceled, Progress progress) {}
        @Override public boolean apply(String key, Boolean enabled, Journal journal, BooleanSupplier canceled) throws IOException { validateIncoming(item(key)); return false; }
    }

    private List<Row> plan(Peer a, Peer b, boolean states) throws IOException { return DlcSyncModel.plan(DlcSyncClient.manifest(a), DlcSyncClient.manifest(b), Direction.PUSH, states); }

    private static class Fixture implements Adapter {
        final Map<Path, Journal> journals = new LinkedHashMap<>();
        final Set<Path> restored = new HashSet<>();
        final Path root; int downloads; boolean failApply, failEnable; Journal active; Path captureFailure;
        Fixture(Path root) throws IOException { this.root = root; Files.createDirectories(root.resolve("remote")); }
        void write(String path, String text) throws IOException { Path file = root.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, text); }
        void add(String name, String config, String data, boolean enabled) throws IOException {
            write("remote/" + name + ".dc", config); if (data != null) write("data/" + name, data); write("state/" + name, Boolean.toString(enabled));
        }
        public Path workDirectory() { return root.resolve("work"); }
        public Path resolve(String id) throws IOException {
            Path path = root.resolve(id).normalize(); if (!path.startsWith(root) || path.equals(root)) throw new IOException("Outside root"); return path;
        }
        public void validateKey(String key) throws IOException { if (!key.startsWith("remote/") || !key.endsWith(".dc")) throw new IOException("Invalid DLC key"); resolve(key); }
        public List<Item> manifest() throws IOException {
            List<Item> result = new ArrayList<>();
            try (var files = Files.list(root.resolve("remote"))) {
                for (Path config : files.sorted().toList()) {
                    String name = config.getFileName().toString().replace(".dc", ""); Path data = root.resolve("data/" + name), state = root.resolve("state/" + name);
                    result.add(new Item("remote/" + name + ".dc", name, new File("remote/" + name + ".dc", Files.size(config), DlcFileMatch.fullHash(config)),
                        List.of(new File("data/" + name, Files.exists(data) ? Files.size(data) : -1, Files.exists(data) ? DlcFileMatch.hash(data) : null)), Files.exists(state) && Boolean.parseBoolean(Files.readString(state))));
                }
            }
            return result;
        }
        public Journal journal(Path directory) {
            active = new Journal() {
                final Map<Path, byte[]> originals = new LinkedHashMap<>();
                public void capture(Path path) throws IOException { if (path.equals(captureFailure)) throw new IOException("Backup failed"); if (!originals.containsKey(path)) originals.put(path, Files.exists(path) ? Files.readAllBytes(path) : null); }
                public void rollback() throws IOException { if (!restored.add(directory)) return; for (var value : originals.entrySet()) { if (value.getValue() == null) Files.deleteIfExists(value.getKey()); else { Files.createDirectories(value.getKey().getParent()); Files.write(value.getKey(), value.getValue()); } } }
                public void commit() {}
                public void close() {}
            };
            journals.put(directory, active); return active;
        }
        public void prepare(String key, boolean force, BooleanSupplier canceled, Progress progress) throws IOException {
            String name = key.substring(7).replace(".dc", "");
            if (force || !Files.exists(root.resolve("data/" + name))) { active.capture(root.resolve("data/" + name)); downloads++; write("data/" + name, "downloaded"); }
        }
        public boolean apply(String key, Boolean enabled, Journal journal, BooleanSupplier canceled) throws IOException {
            String name = key.substring(7).replace(".dc", ""); Path state = root.resolve("state/" + name), target = root.resolve("applied/" + name);
            journal.capture(state); journal.capture(target);
            boolean desired = enabled == null ? Files.exists(state) && Boolean.parseBoolean(Files.readString(state)) : enabled;
            write("state/" + name, Boolean.toString(desired));
            if (desired) write("applied/" + name, Files.readString(root.resolve("data/" + name))); else Files.deleteIfExists(target);
            if (failApply || failEnable && Boolean.TRUE.equals(enabled)) throw new IOException("Target is locked");
            return desired;
        }
        public void reload() {}
        public void recover() {}
        public void recoverOperation(String operation) throws IOException {
            List<Path> paths = new ArrayList<>(journals.keySet()); Collections.reverse(paths);
            for (Path path : paths) if (path.startsWith(workDirectory().resolve(operation))) journals.get(path).rollback();
        }
    }
}
