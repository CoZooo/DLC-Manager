package com.cozooo.dlc_manager.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxServerFileSearchModelTest {

    @Test
    void blankQueryKeepsCurrentDirectoryEntries() {
        List<Item> items = List.of(new Item("config"), new Item("mods"));

        List<Item> filtered = DlcFxServerFileSearchModel.filter(items, " ", Item::name);

        assertEquals(items, filtered);
    }

    @Test
    void filtersCurrentDirectoryEntriesByNameIgnoringCase() {
        Item config = new Item("config");
        Item readme = new Item("README.txt");
        Item mods = new Item("mods");

        List<Item> filtered = DlcFxServerFileSearchModel.filter(List.of(config, readme, mods), "read", Item::name);

        assertEquals(List.of(readme), filtered);
    }

    private record Item(String name) {
    }
}
