package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcType

class DlcListRow private constructor(
    private val type: RowType,
    private val label: String?,
    private val groupType: DlcType?,
    private val item: DlcItem?,
    private val items: List<DlcItem>?,
    private val redirectEntry: DlcRedirectEntry?,
    private val rootPath: Boolean,
    private val hideTopSeparator: Boolean,
) {
    private var pathKey: String? = null
    fun getPathKey(): String? = pathKey ?: label
    enum class RowType {
        GROUP_HEADER,
        PATH_HEADER,
        ITEM,
        REDIRECT_HEADER,
        REDIRECT_ITEM
    }

    fun getType(): RowType = type

    fun getLabel(): String? = label

    fun getGroupType(): DlcType? = groupType

    fun getItem(): DlcItem? = item

    fun getItems(): List<DlcItem>? = items

    fun getRedirectEntry(): DlcRedirectEntry? = redirectEntry

    fun isRootPath(): Boolean = rootPath

    fun shouldHideTopSeparator(): Boolean = hideTopSeparator

    companion object {
        @JvmStatic
        fun groupHeader(type: DlcType?): DlcListRow = DlcListRow(RowType.GROUP_HEADER, null, type, null, null, null, false, false)

        @JvmStatic
        fun pathHeader(label: String?, items: List<DlcItem>?, rootPath: Boolean, hideTopSeparator: Boolean): DlcListRow {
            return DlcListRow(RowType.PATH_HEADER, label, null, null, items, null, rootPath, hideTopSeparator)
        }

        @JvmStatic
        fun pathHeader(label: String?, items: List<DlcItem>?, rootPath: Boolean, hideTopSeparator: Boolean, key: String): DlcListRow {
            return pathHeader(label, items, rootPath, hideTopSeparator).also { it.pathKey = key }
        }

        @JvmStatic
        fun item(item: DlcItem?): DlcListRow = DlcListRow(RowType.ITEM, null, null, item, null, null, false, false)

        @JvmStatic
        fun redirectHeader(label: String?): DlcListRow = DlcListRow(RowType.REDIRECT_HEADER, label, null, null, null, null, false, false)

        @JvmStatic
        fun redirectItem(entry: DlcRedirectEntry?): DlcListRow = DlcListRow(RowType.REDIRECT_ITEM, null, null, null, null, entry, false, false)
    }
}
