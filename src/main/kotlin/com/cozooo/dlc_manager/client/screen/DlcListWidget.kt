package com.cozooo.dlc_manager.client.screen

import fi.dy.masa.malilib.gui.widgets.WidgetListBase

open class DlcListWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val rows: List<DlcListRow>,
) : WidgetListBase<DlcListRow, DlcListEntry>(x, y, width, height, null) {
    init {
        browserEntryHeight = 22
        allowMultiSelection = false
        shouldSortList = false
        browserPaddingX = 4
        browserPaddingY = 0
    }

    override fun getAllEntries(): Collection<DlcListRow> = rows

    override fun createListEntryWidget(x: Int, y: Int, listIndex: Int, isOdd: Boolean, entry: DlcListRow): DlcListEntry {
        return DlcListEntry(x, y, browserEntryWidth, getBrowserEntryHeightFor(entry), isOdd, entry, listIndex)
    }

    override fun getBrowserEntryHeightFor(entry: DlcListRow?): Int {
        if (entry == null) {
            return 22
        }
        if (entry.getType() == DlcListRow.RowType.GROUP_HEADER) {
            return 18
        }
        if (entry.getType() == DlcListRow.RowType.REDIRECT_HEADER) {
            return 18
        }
        if (entry.getType() == DlcListRow.RowType.PATH_HEADER) {
            return 18
        }
        return 22
    }

    override fun getEntryStringsForFilter(entry: DlcListRow): List<String> {
        if (entry.getType() == DlcListRow.RowType.ITEM && entry.getItem() != null) {
            val name = entry.getItem()!!.getName()
            if (name != null) {
                return listOf(name)
            }
        }
        val label = entry.getLabel()
        if (label != null) {
            return listOf(label)
        }
        return emptyList()
    }
}
