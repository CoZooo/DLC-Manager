package com.cozooo.dlc_manager.client.screen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DlcUiInputBindingTest {
    @Test
    void frameSamplingCoalescesRapidEditsIntoTheLatestValue() {
        AtomicReference<String> field = new AtomicReference<>("");
        List<String> changes = new ArrayList<>();
        DlcUiInputBinding binding = new DlcUiInputBinding(field::get, () -> true, changes::add);
        field.set("s");
        field.set("se");
        field.set("search");
        binding.run();
        assertEquals(List.of("search"), changes);
        field.set("");
        binding.run();
        assertEquals(List.of("search", ""), changes);
    }
    @Test
    void observingAClearedValueRemovesTheFilterAndStoredQuery() {
        AtomicReference<String> field = new AtomicReference<>("");
        AtomicReference<String> query = new AtomicReference<>("");
        DlcUiInputBinding binding = new DlcUiInputBinding(field::get, () -> true, query::set);
        field.set("server");
        binding.run();
        assertFalse(DlcUiSearch.matches(query.get(), "download"));
        field.set("serve");
        binding.run();
        assertEquals("serve", query.get());
        field.set("");
        binding.run();
        assertTrue(DlcUiSearch.matches(query.get(), "download"));
        assertEquals("", query.get());
    }

    @Test
    void syncingBeforeRebuildingKeepsTheClearedValue() {
        AtomicReference<String> field = new AtomicReference<>("old search");
        AtomicReference<String> storedQuery = new AtomicReference<>(field.get());
        DlcUiInputBinding binding = new DlcUiInputBinding(field::get, () -> true, storedQuery::set);
        field.set("");
        binding.run();
        AtomicReference<String> rebuiltField = new AtomicReference<>(storedQuery.get());
        assertEquals("", rebuiltField.get());
    }

    @Test
    void repeatedSynchronizationDeliversEachChangeOnce() {
        AtomicReference<String> field = new AtomicReference<>("");
        List<String> changes = new ArrayList<>();
        DlcUiInputBinding binding = new DlcUiInputBinding(field::get, () -> true, changes::add);
        binding.run();
        field.set("new");
        binding.run();
        binding.run();
        field.set(null);
        binding.run();
        field.set("");
        binding.run();
        assertEquals(List.of("new", ""), changes);
    }

    @Test
    void detachedOrReplacedFieldsCannotRestoreOldSearches() {
        AtomicReference<String> field = new AtomicReference<>("current");
        AtomicBoolean current = new AtomicBoolean(true);
        List<String> changes = new ArrayList<>();
        DlcUiInputBinding binding = new DlcUiInputBinding(field::get, current::get, changes::add);
        current.set(false);
        field.set("stale");
        binding.run();
        assertTrue(changes.isEmpty());
    }
}
