package com.cozooo.dlc_manager.client.screen;

import fi.dy.masa.malilib.gui.widgets.WidgetListBase;

import java.util.Collection;
import java.util.List;

public class DlcServerListWidget extends WidgetListBase<DlcServerListRow, DlcServerListEntry> {
    private final List<DlcServerListRow> rows;
    private final DlcServerMaLiLibScreen screen;

    public DlcServerListWidget(int x, int y, int width, int height, List<DlcServerListRow> rows, DlcServerMaLiLibScreen screen) {
        super(x, y, width, height, null);
        this.rows = rows;
        this.screen = screen;
        this.browserEntryHeight = 22;
        this.allowMultiSelection = false;
        this.shouldSortList = false;
        this.browserPaddingX = 4;
        this.browserPaddingY = 0;
    }

    @Override
    protected Collection<DlcServerListRow> getAllEntries() {
        return rows;
    }

    @Override
    protected DlcServerListEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, DlcServerListRow entry) {
        return new DlcServerListEntry(x, y, this.browserEntryWidth, getBrowserEntryHeightFor(entry), isOdd, entry, listIndex, screen);
    }

    @Override
    protected int getBrowserEntryHeightFor(DlcServerListRow entry) {
        if (entry.getType() == DlcServerListRow.RowType.GROUP_HEADER
            || entry.getType() == DlcServerListRow.RowType.PATH_HEADER) {
            return 16;
        }
        return 22;
    }

    @Override
    protected List<String> getEntryStringsForFilter(DlcServerListRow entry) {
        if (entry.getType() == DlcServerListRow.RowType.ITEM && entry.getItem() != null) {
            return DlcServerListEntry.getFilterStrings(entry.getItem());
        }
        return List.of();
    }
}
