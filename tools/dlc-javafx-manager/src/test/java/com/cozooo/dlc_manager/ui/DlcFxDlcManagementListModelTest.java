package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxDlcManagementListModelTest {
    @Test
    void groupsByTypeAndFolderWhenAllTypesAreVisible() {
        DlcItem root = item("root", DlcType.MOD, "root");
        DlcItem nested = item("nested", DlcType.MOD, "mods/addons");

        List<DlcFxDlcManagementListModel.Row<DlcItem>> rows = rows(List.of(root, nested), null, key -> false);

        assertEquals(List.of(
                DlcFxDlcManagementListModel.RowType.GROUP_HEADER,
                DlcFxDlcManagementListModel.RowType.PATH_HEADER,
                DlcFxDlcManagementListModel.RowType.ITEM,
                DlcFxDlcManagementListModel.RowType.PATH_HEADER,
                DlcFxDlcManagementListModel.RowType.ITEM
        ), rows.stream().map(row -> row.type).toList());
        assertEquals("MOD:__root__", rows.get(1).folderKey);
        assertEquals("MOD:mods/addons", rows.get(3).folderKey);
    }

    @Test
    void rootFolderDoesNotCreateHeaderWhenItIsTheOnlyFolder() {
        DlcItem root = item("root", DlcType.MOD, "root");

        List<DlcFxDlcManagementListModel.Row<DlcItem>> rows = rows(List.of(root), null, key -> false);

        assertEquals(List.of(
                DlcFxDlcManagementListModel.RowType.GROUP_HEADER,
                DlcFxDlcManagementListModel.RowType.ITEM
        ), rows.stream().map(row -> row.type).toList());
    }

    @Test
    void typeFilterRemovesGroupHeadersAndOtherTypes() {
        DlcItem mod = item("mod", DlcType.MOD, "root");
        DlcItem resource = item("resource", DlcType.RESOURCE_PACK, "root");

        List<DlcFxDlcManagementListModel.Row<DlcItem>> rows = rows(List.of(mod, resource), DlcType.RESOURCE_PACK, key -> false);

        assertEquals(List.of(DlcFxDlcManagementListModel.RowType.ITEM), rows.stream().map(row -> row.type).toList());
        assertEquals(resource, rows.get(0).item);
    }

    @Test
    void collapsedFolderHidesItems() {
        DlcItem nested = item("nested", DlcType.MOD, "mods/addons");

        List<DlcFxDlcManagementListModel.Row<DlcItem>> rows = rows(
                List.of(nested),
                null,
                key -> "MOD:mods/addons".equals(key)
        );

        assertEquals(List.of(
                DlcFxDlcManagementListModel.RowType.GROUP_HEADER,
                DlcFxDlcManagementListModel.RowType.PATH_HEADER
        ), rows.stream().map(row -> row.type).toList());
        assertEquals(true, rows.get(1).collapsed);
    }

    @Test
    void dlcPackTypeIsExcludedByVisibleTypePredicate() {
        DlcItem pack = item("pack", DlcType.DLC_PACK, "root");

        List<DlcFxDlcManagementListModel.Row<DlcItem>> rows = rows(List.of(pack), null, key -> false);

        assertEquals(List.of(), rows);
    }

    @Test
    void genericRowsUseTheSameTypeAndFolderGroupingForRemoteItems() {
        RemoteItem root = new RemoteItem("root", "Root", DlcType.MOD, "__root__");
        RemoteItem nested = new RemoteItem("nested", "Nested", DlcType.MOD, "mods/addons");

        List<DlcFxDlcManagementListModel.Row<RemoteItem>> rows = DlcFxDlcManagementListModel.rows(
                List.of(root, nested),
                "",
                null,
                type -> type != DlcType.DLC_PACK,
                RemoteItem::folder,
                RemoteItem::name,
                RemoteItem::type,
                raw -> raw,
                key -> false,
                item -> false
        );

        assertEquals(List.of(
                DlcFxDlcManagementListModel.RowType.GROUP_HEADER,
                DlcFxDlcManagementListModel.RowType.PATH_HEADER,
                DlcFxDlcManagementListModel.RowType.ITEM,
                DlcFxDlcManagementListModel.RowType.PATH_HEADER,
                DlcFxDlcManagementListModel.RowType.ITEM
        ), rows.stream().map(row -> row.type).toList());
        assertEquals(root, rows.get(2).item);
        assertEquals(nested, rows.get(4).item);
    }

    private static List<DlcFxDlcManagementListModel.Row<DlcItem>> rows(List<DlcItem> items,
                                                                       DlcType typeFilter,
                                                                       java.util.function.Predicate<String> collapsed) {
        return DlcFxDlcManagementListModel.rows(
                items,
                List.of(),
                "",
                typeFilter,
                type -> type != DlcType.DLC_PACK,
                item -> item.getSourcePath().toString().contains("addons") ? "mods/addons" : "__root__",
                DlcItem::getName,
                raw -> raw,
                collapsed,
                item -> false,
                "Links"
        );
    }

    private static DlcItem item(String id, DlcType type, String folder) {
        return new DlcItem(
                id,
                id,
                id,
                type,
                Path.of(folder, id + ".jar"),
                "",
                false,
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.List.of(),
                java.util.List.of(),
                true,
                false
        );
    }

    private record RemoteItem(String id, String name, DlcType type, String folder) {
    }
}
