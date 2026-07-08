package com.cozooo.dlc_manager.client.screen

import fi.dy.masa.malilib.gui.widgets.WidgetListBase

open class DlcServerListWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val rows: List<DlcServerListRow>,
    private val screen: DlcServerMaLiLibScreen?,
) : WidgetListBase<DlcServerListRow, DlcServerListEntry>(x, y, width, height, null) {
    init {
        browserEntryHeight = 22
        allowMultiSelection = false
        shouldSortList = false
        browserPaddingX = 4
        browserPaddingY = 0
    }

    override fun getAllEntries(): Collection<DlcServerListRow> = rows

    override fun createListEntryWidget(x: Int, y: Int, listIndex: Int, isOdd: Boolean, entry: DlcServerListRow): DlcServerListEntry {
        return DlcServerListEntry(x, y, browserEntryWidth, getBrowserEntryHeightFor(entry), isOdd, entry, listIndex, screen)
    }

    override fun getBrowserEntryHeightFor(entry: DlcServerListRow?): Int {
        if (entry == null) {
            return 22
        }
        if (entry.getType() == DlcServerListRow.RowType.GROUP_HEADER || entry.getType() == DlcServerListRow.RowType.PATH_HEADER) {
            return 16
        }
        return 22
    }

    override fun getEntryStringsForFilter(entry: DlcServerListRow): List<String> {
        if (entry.getType() == DlcServerListRow.RowType.ITEM && entry.getItem() != null) {
            return DlcServerListEntry.getFilterStrings(entry.getItem())
        }
        return emptyList()
    }
}
