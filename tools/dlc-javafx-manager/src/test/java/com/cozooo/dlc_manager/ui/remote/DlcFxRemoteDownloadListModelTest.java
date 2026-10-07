package com.cozooo.dlc_manager.ui.remote;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import com.cozooo.dlc_manager.core.DlcType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxRemoteDownloadListModelTest {
    @Test
    void rootEntriesDoNotCreateAGroupWhenThereAreNoSubFolders() {
        List<DlcFxRemoteDownloadListModel.Row<Item>> rows = DlcFxRemoteDownloadListModel.rows(
                List.of(new Item("root", "__root__", DlcType.MOD, false)),
                null,
                item -> item.folder,
                item -> item.type,
                item -> false,
                key -> false,
                key -> key
        );

        assertEquals(List.of(DlcFxRemoteDownloadListModel.RowType.ITEM), rows.stream().map(row -> row.type).toList());
    }

    @Test
    void folderGroupsHideItemsWhenCollapsed() {
        List<DlcFxRemoteDownloadListModel.Row<Item>> rows = DlcFxRemoteDownloadListModel.rows(
                List.of(new Item("nested", "mods/addons", DlcType.MOD, false)),
                null,
                item -> item.folder,
                item -> item.type,
                item -> false,
                key -> true,
                key -> key
        );

        assertEquals(List.of(DlcFxRemoteDownloadListModel.RowType.GROUP), rows.stream().map(row -> row.type).toList());
        assertEquals("mods/addons", rows.get(0).groupLabel);
        assertEquals(true, rows.get(0).collapsed);
    }

    @Test
    void typeFilterExcludesOtherTypes() {
        List<DlcFxRemoteDownloadListModel.Row<Item>> rows = DlcFxRemoteDownloadListModel.rows(
                List.of(
                        new Item("mod", "__root__", DlcType.MOD, false),
                        new Item("pack", "__root__", DlcType.DLC_PACK, false)
                ),
                DlcType.DLC_PACK,
                item -> item.folder,
                item -> item.type,
                item -> false,
                key -> false,
                key -> key
        );

        assertEquals(List.of("pack"), rows.stream().filter(row -> row.item != null).map(row -> row.item.id).toList());
    }

    private record Item(String id, String folder, DlcType type, boolean hidden) {
    }
}
