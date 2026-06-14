package com.cozooo.dlc_manager.client.rcon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcRconQuickCommandCatalogTest {
    @Test
    void commandItemsAreOrderedAndImmutable() {
        List<DlcRconQuickCommandCatalog.QuickCommandItem> items = DlcRconQuickCommandCatalog.commandItems();

        assertEquals(20, items.size());
        assertEquals(3, items.stream().filter(DlcRconQuickCommandCatalog.QuickCommandItem::separator).count());
        assertEquals(17, items.stream().filter(item -> !item.separator()).count());
        assertTrue(items.get(0).separator());
        assertEquals("dlc_manager.server.rcon_group_time", items.get(0).labelKey());
        assertEquals("", items.get(0).command());
        assertFalse(items.get(1).separator());
        assertEquals("time set day", items.get(1).command());
        assertEquals("gamerule doFireTick false", items.get(items.size() - 1).command());
        assertThrows(UnsupportedOperationException.class,
            () -> items.add(DlcRconQuickCommandCatalog.QuickCommandItem.command("x", "y")));
    }
}
