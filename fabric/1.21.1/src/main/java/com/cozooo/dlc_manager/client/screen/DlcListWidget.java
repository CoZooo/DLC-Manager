package com.cozooo.dlc_manager.client.screen;

import fi.dy.masa.malilib.gui.widgets.WidgetListBase;

import java.util.Collection;
import java.util.List;

public class DlcListWidget extends WidgetListBase<DlcListRow, DlcListEntry> {
    private final List<DlcListRow> rows;

    public DlcListWidget(int x, int y, int width, int height, List<DlcListRow> rows) {
        super(x, y, width, height, null);
        this.rows = rows;
        this.browserEntryHeight = 22;
        this.allowMultiSelection = false;
        this.shouldSortList = false;
        this.browserPaddingX = 4;
        this.browserPaddingY = 0;
    }

    @Override
    protected Collection<DlcListRow> getAllEntries() {
        return rows;
    }

    @Override
    protected DlcListEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, DlcListRow entry) {
        return new DlcListEntry(x, y, this.browserEntryWidth, getBrowserEntryHeightFor(entry), isOdd, entry, listIndex);
    }

    @Override
    protected int getBrowserEntryHeightFor(DlcListRow entry) {
        if (entry.getType() == DlcListRow.RowType.GROUP_HEADER) {
            return 18;
        }
        if (entry.getType() == DlcListRow.RowType.PATH_HEADER) {
            return 18;
        }
        return 22;
    }

    @Override
    protected List<String> getEntryStringsForFilter(DlcListRow entry) {
        if (entry.getType() == DlcListRow.RowType.ITEM && entry.getItem() != null) {
            return List.of(entry.getItem().getName());
        }
        if (entry.getLabel() != null) {
            return List.of(entry.getLabel());
        }
        return List.of();
    }
}
