package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerItem
import com.cozooo.dlc_manager.core.DlcType

class DlcServerListRow private constructor(
    private val type: RowType,
    private val label: String?,
    private val groupType: DlcType?,
    private val item: DlcServerItem?,
    private val items: List<DlcServerItem>?,
    private val rootPath: Boolean,
    private val hideTopSeparator: Boolean,
) {
    enum class RowType {
        GROUP_HEADER,
        PATH_HEADER,
        ITEM
    }

    fun getType(): RowType = type

    fun getLabel(): String? = label

    fun getGroupType(): DlcType? = groupType

    fun getItem(): DlcServerItem? = item

    fun getItems(): List<DlcServerItem>? = items

    fun isRootPath(): Boolean = rootPath

    fun shouldHideTopSeparator(): Boolean = hideTopSeparator

    companion object {
        @JvmStatic
        fun groupHeader(type: DlcType?): DlcServerListRow = DlcServerListRow(RowType.GROUP_HEADER, null, type, null, null, false, false)

        @JvmStatic
        fun pathHeader(label: String?, items: List<DlcServerItem>?, rootPath: Boolean, hideTopSeparator: Boolean): DlcServerListRow {
            return DlcServerListRow(RowType.PATH_HEADER, label, null, null, items, rootPath, hideTopSeparator)
        }

        @JvmStatic
        fun item(item: DlcServerItem?): DlcServerListRow = DlcServerListRow(RowType.ITEM, null, null, item, null, false, false)
    }
}
