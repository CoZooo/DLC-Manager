package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.server.DlcServerItem
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcType
import fi.dy.masa.malilib.gui.button.ButtonBase
import fi.dy.masa.malilib.gui.button.ButtonGeneric
import fi.dy.masa.malilib.gui.button.IButtonActionListener
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.Component
import java.util.ArrayDeque
import java.util.Locale

class DlcServerListEntry(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    isOdd: Boolean,
    private val row: DlcServerListRow,
    private val rowIndex: Int,
    private val screen: DlcServerMaLiLibScreen?,
) : WidgetListEntryBase<DlcServerListRow>(x, y, width, height, row, rowIndex) {
    private var toggleButton: ButtonGeneric? = null
    private var groupToggleButton: ButtonGeneric? = null
    private var groupToggleWidth = 0
    private var groupToggleHeight = 0

    init {
        if (row.getType() == DlcServerListRow.RowType.ITEM) {
            createToggleButton(width, height, row.getItem())
        } else if (row.getType() == DlcServerListRow.RowType.PATH_HEADER && !row.isRootPath()) {
            createGroupToggleButton(width, height, row.getItems())
        }
    }

    private fun createToggleButton(width: Int, height: Int, item: DlcServerItem?) {
        val buttonWidth = 64
        val buttonHeight = 18
        val bx = x + width - buttonWidth - 6
        val by = getAlignedButtonY(y, height, buttonHeight)
        toggleButton = ButtonGeneric(bx, by, buttonWidth, buttonHeight, getToggleLabel(item)).also {
            addButton(it, ToggleListener(item))
        }
    }

    private fun createGroupToggleButton(width: Int, height: Int, items: List<DlcServerItem>?) {
        if (items.isNullOrEmpty()) {
            return
        }
        val state = getGroupToggleState(items)
        groupToggleWidth = maxOf(86, Minecraft.getInstance().font.width(state.label) + 16)
        groupToggleHeight = 16
        val bx = x + width - groupToggleWidth - 6 - 64 - 6
        val by = getGroupToggleButtonY(y, height, groupToggleHeight)
        groupToggleButton = ButtonGeneric(bx, by, groupToggleWidth, groupToggleHeight, state.label).also {
            addButton(it, GroupToggleListener(items))
        }
    }

    override fun render(mouseX: Int, mouseY: Int, hovered: Boolean, guiGraphics: GuiGraphics) {
        if (row.getType() == DlcServerListRow.RowType.GROUP_HEADER) {
            drawGroupSeparator(guiGraphics)
            val label = Component.translatable(row.getGroupType()?.getDisplayKey() ?: "").string
            drawStringWithShadow(x + 6, y + 4, COLOR_GROUP, label, guiGraphics)
        } else if (row.getType() == DlcServerListRow.RowType.PATH_HEADER) {
            drawPathSeparator(guiGraphics)
            drawString(x + 10, y + 4, COLOR_PATH, row.getLabel(), guiGraphics)
            groupToggleButton?.let { button ->
                val state = getGroupToggleState(row.getItems())
                if (state.show) {
                    groupToggleWidth = maxOf(86, Minecraft.getInstance().font.width(state.label) + 16)
                    val bx = x + width - groupToggleWidth - 6 - 64 - 6
                    val by = getGroupToggleButtonY(y, height, groupToggleHeight)
                    button.setX(bx)
                    button.setY(by)
                    button.setWidth(groupToggleWidth)
                    button.setHeight(groupToggleHeight)
                    button.setDisplayString(state.label)
                    button.setEnabled(state.enabled)
                } else {
                    button.setDisplayString("")
                    button.setEnabled(false)
                    button.setWidth(0)
                    button.setX(-10000)
                }
            }
        } else if (row.getType() == DlcServerListRow.RowType.ITEM) {
            val screen = owner() ?: return
            drawItemSeparator(guiGraphics)
            val item = row.getItem()
            val dirty = screen.isDirty(item)
            val displayName = screen.resolveDisplayName(item)
            val textY = y + (height - Minecraft.getInstance().font.lineHeight) / 2
            if (dirty) {
                drawPendingAccent(guiGraphics)
            } else if (item?.enabled == true) {
                drawEnabledAccent(guiGraphics)
            }
            guiGraphics.drawString(
                Minecraft.getInstance().font,
                DlcTextFormatter.parse(displayName),
                x + 10,
                textY,
                COLOR_ITEM,
                false,
            )
            if (dirty) {
                drawPendingBadge(guiGraphics, screen.getPendingSuffix(item))
            }
            toggleButton?.let { button ->
                button.setDisplayString(getToggleLabel(item))
                val blocked = DlcPresetRuntimeState.isDlcBlocked(item?.identifier)
                button.setEnabled(!blocked)
            }
        }
        super.render(mouseX, mouseY, hovered, guiGraphics)
        if (row.getType() == DlcServerListRow.RowType.ITEM && isMouseWithin(mouseX, mouseY) && !isMouseOverButton(mouseX, mouseY)) {
            val screen = owner() ?: return
            val item = row.getItem()
            val font = Minecraft.getInstance().font
            val maxWidth = maxOf(160, minOf(width - 20, 280))
            val lines = screen.buildTooltipLines(font, item, maxWidth)
            if (lines.isNotEmpty()) {
                DlcTooltipContext.set(lines, mouseX, mouseY)
            }
        }
    }

    private fun drawPendingAccent(guiGraphics: GuiGraphics) {
        guiGraphics.fill(x + 1, y + 2, x + 4, y + height - 2, COLOR_PENDING_BAR)
    }

    private fun drawEnabledAccent(guiGraphics: GuiGraphics) {
        guiGraphics.fill(x + 1, y + 2, x + 4, y + height - 2, COLOR_ENABLED_BAR)
    }

    private fun drawGroupSeparator(guiGraphics: GuiGraphics) {
        if (rowIndex > 0) {
            guiGraphics.fill(x + 2, y + 1, x + width - 2, y + 3, COLOR_GROUP_SEPARATOR)
        }
    }

    private fun drawPathSeparator(guiGraphics: GuiGraphics) {
        if (!row.shouldHideTopSeparator()) {
            guiGraphics.fill(x + 6, y + 1, x + width - 6, y + 2, COLOR_PATH_SEPARATOR)
        }
    }

    private fun drawItemSeparator(guiGraphics: GuiGraphics) {
        guiGraphics.fill(x + 10, y + height - 1, x + width - 6, y + height, COLOR_ITEM_SEPARATOR)
    }

    private fun getAlignedButtonY(rowY: Int, rowHeight: Int, buttonHeight: Int): Int {
        return rowY + (rowHeight - buttonHeight) / 2
    }

    private fun getGroupToggleButtonY(rowY: Int, rowHeight: Int, buttonHeight: Int): Int {
        return getAlignedButtonY(rowY, rowHeight, buttonHeight) + 2
    }

    private fun drawPendingBadge(guiGraphics: GuiGraphics, pendingText: String?) {
        if (pendingText.isNullOrBlank()) {
            return
        }
        val label = pendingText.trim()
        val font = Minecraft.getInstance().font
        val textWidth = font.width(label)
        val badgeHeight = 14
        val badgeWidth = textWidth + 10
        val rightAnchor = toggleButton?.getX()?.minus(6) ?: (x + width - 6)
        val badgeX = rightAnchor - badgeWidth
        val badgeY = y + (height - badgeHeight) / 2
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, COLOR_PENDING_BG)
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 1, COLOR_PENDING_BORDER)
        guiGraphics.fill(badgeX, badgeY + badgeHeight - 1, badgeX + badgeWidth, badgeY + badgeHeight, COLOR_PENDING_BORDER)
        guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeHeight, COLOR_PENDING_BORDER)
        guiGraphics.fill(badgeX + badgeWidth - 1, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, COLOR_PENDING_BORDER)
        guiGraphics.drawString(font, label, badgeX + 5, badgeY + 3, COLOR_PENDING_TEXT, false)
    }

    private fun isMouseOverButton(mouseX: Int, mouseY: Int): Boolean {
        val toggle = toggleButton
        if (toggle == null) {
            return groupToggleButton?.let { isMouseOverButton(it, mouseX, mouseY) } == true
        }
        if (isMouseOverButton(toggle, mouseX, mouseY)) {
            return true
        }
        return groupToggleButton?.let { isMouseOverButton(it, mouseX, mouseY) } == true
    }

    private fun isMouseOverButton(button: ButtonGeneric, mouseX: Int, mouseY: Int): Boolean {
        val bx = button.getX()
        val by = button.getY()
        val bw = button.getWidth()
        val bh = button.getHeight()
        return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh
    }

    private fun getToggleLabel(item: DlcServerItem?): String {
        return if (item != null && item.enabled) {
            "\u00A7a" + Component.translatable("dlc_manager.toggle.on").string
        } else {
            Component.translatable("dlc_manager.toggle.off").string
        }
    }

    private fun getGroupToggleState(items: List<DlcServerItem>?): GroupToggleState {
        if (items.isNullOrEmpty()) {
            return GroupToggleState("", false, false)
        }
        val anyEligibleDisabled = items.any { item ->
            !DlcPresetRuntimeState.isDlcBlocked(item.identifier) && !item.enabled
        }
        val anyEnabled = items.any { item -> item.enabled }
        if (anyEligibleDisabled) {
            return GroupToggleState(Component.translatable("dlc_manager.group.enable").string, true, true)
        }
        if (anyEnabled) {
            return GroupToggleState(Component.translatable("dlc_manager.group.disable").string, true, true)
        }
        return GroupToggleState("", false, false)
    }

    private fun isMouseWithin(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }

    private fun owner(): DlcServerMaLiLibScreen? {
        return screen
    }

    private inner class ToggleListener(private val item: DlcServerItem?) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            owner()?.handleToggle(item, button as ButtonGeneric)
        }
    }

    private inner class GroupToggleListener(private val items: List<DlcServerItem>?) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            val screen = owner() ?: return
            if (items.isNullOrEmpty()) {
                return
            }
            var state = getGroupToggleState(items)
            if (!state.show) {
                return
            }
            val anyEligibleDisabled = items.any { item ->
                !DlcPresetRuntimeState.isDlcBlocked(item.identifier) && !item.enabled
            }
            val targetEnable = anyEligibleDisabled
            if (targetEnable) {
                val targets = items.filter { item ->
                    !DlcPresetRuntimeState.isDlcBlocked(item.identifier) && !item.enabled
                }
                val deps = collectDependencies(screen, targets)
                val conflicts = collectConflicts(screen, targets)
                if (conflicts.names.isNotEmpty()) {
                    val message = DlcConfirmUtil.buildConflictPromptMessage(conflicts.names)
                    Minecraft.getInstance().setScreen(
                        ConfirmScreen(
                            { accepted ->
                                Minecraft.getInstance().setScreen(screen)
                                if (accepted) {
                                    screen.disableItemsWithDependents(conflicts.items)
                                    enableItems(screen, deps.missingItems, targets)
                                }
                            },
                            Component.translatable("dlc_manager.conflict.word"),
                            message,
                            Component.translatable("dlc_manager.conflict.resolve"),
                            Component.translatable("gui.cancel"),
                        )
                    )
                    return
                }
                if (deps.names.isNotEmpty()) {
                    val message = DlcConfirmUtil.buildDependencyPromptMessage(deps.names)
                    Minecraft.getInstance().setScreen(
                        ConfirmScreen(
                            { accepted ->
                                Minecraft.getInstance().setScreen(screen)
                                if (accepted) {
                                    enableItems(screen, deps.missingItems, targets)
                                }
                            },
                            Component.translatable("dlc_manager.dependency.enable"),
                            message,
                            Component.translatable("dlc_manager.dependency.enable"),
                            Component.translatable("gui.cancel"),
                        )
                    )
                    return
                }
                enableItems(screen, deps.missingItems, targets)
            } else {
                screen.disableItemsWithDependents(items)
                screen.updateApplyState()
            }
            if (button is ButtonGeneric) {
                state = getGroupToggleState(items)
                button.setDisplayString(state.label)
                button.setEnabled(state.enabled)
            }
        }

        private fun enableItems(screen: DlcServerMaLiLibScreen, missing: List<DlcServerItem>?, targets: List<DlcServerItem>) {
            if (missing != null) {
                for (item in missing) {
                    item.enabled = true
                }
            }
            for (item in targets) {
                item.enabled = true
            }
            screen.updateApplyState()
        }

        private fun collectDependencies(screen: DlcServerMaLiLibScreen, targets: List<DlcServerItem>?): DependencyBatch {
            val batch = DependencyBatch()
            if (targets.isNullOrEmpty()) {
                return batch
            }
            val plannedIds = LinkedHashSet<String>()
            val visited = LinkedHashSet<String>()
            val queue = ArrayDeque<DlcServerItem>()
            for (item in targets) {
                val identifier = item.identifier
                if (!identifier.isNullOrBlank()) {
                    val key = normalizeIdentifier(identifier)
                    if (key.isNotBlank() && plannedIds.add(key)) {
                        queue.add(item)
                    }
                }
            }
            while (!queue.isEmpty()) {
                val item = queue.poll()
                val itemIdentifier = item.identifier
                if (itemIdentifier.isNullOrBlank()) {
                    continue
                }
                val itemKey = normalizeIdentifier(itemIdentifier)
                if (itemKey.isBlank() || !visited.add(itemKey)) {
                    continue
                }
                if (item.dependencies.isNullOrEmpty()) {
                    continue
                }
                for (depId in item.dependencies) {
                    if (depId.isNullOrBlank()) {
                        continue
                    }
                    val depKey = normalizeIdentifier(depId)
                    if (depKey.isBlank()) {
                        continue
                    }
                    val dep = screen.getItemByIdentifier(depId)
                    if (dep == null || !dep.enabled) {
                        if (batch.missingIds.add(depKey)) {
                            var name = screen.resolveDisplayName(dep)
                            if (name.isNullOrBlank()) {
                                name = depId
                            }
                            batch.names.add(name)
                            if (dep != null) {
                                batch.missingItems.add(dep)
                            }
                        }
                    }
                    val resolvedIdentifier = dep?.identifier
                    if (!resolvedIdentifier.isNullOrBlank()) {
                        val resolved = normalizeIdentifier(resolvedIdentifier)
                        if (resolved.isNotBlank() && plannedIds.add(resolved)) {
                            queue.add(dep)
                        }
                    }
                }
            }
            return batch
        }

        private fun collectConflicts(screen: DlcServerMaLiLibScreen, targets: List<DlcServerItem>?): ConflictBatch {
            val batch = ConflictBatch()
            if (targets.isNullOrEmpty()) {
                return batch
            }
            val plannedIds = LinkedHashSet<String>()
            val visited = LinkedHashSet<String>()
            val queue = ArrayDeque<DlcServerItem>()
            for (item in targets) {
                val identifier = item.identifier
                if (!identifier.isNullOrBlank()) {
                    val key = normalizeIdentifier(identifier)
                    if (key.isNotBlank() && plannedIds.add(key)) {
                        queue.add(item)
                    }
                }
            }
            while (!queue.isEmpty()) {
                val item = queue.poll()
                val itemIdentifier = item.identifier
                if (itemIdentifier.isNullOrBlank()) {
                    continue
                }
                val itemKey = normalizeIdentifier(itemIdentifier)
                if (itemKey.isBlank() || !visited.add(itemKey)) {
                    continue
                }
                for (depId in item.dependencies) {
                    if (depId.isNullOrBlank()) {
                        continue
                    }
                    val dep = screen.getItemByIdentifier(depId)
                    val depIdentifier = dep?.identifier
                    if (!depIdentifier.isNullOrBlank()) {
                        val depKey = normalizeIdentifier(depIdentifier)
                        if (depKey.isNotBlank() && plannedIds.add(depKey)) {
                            queue.add(dep)
                        }
                    }
                }
                if (item.conflicts.isNullOrEmpty()) {
                    continue
                }
                for (conflictId in item.conflicts) {
                    if (conflictId.isNullOrBlank()) {
                        continue
                    }
                    val conflictKey = normalizeIdentifier(conflictId)
                    if (conflictKey.isBlank() || plannedIds.contains(conflictKey)) {
                        continue
                    }
                    val conflict = screen.getItemByIdentifier(conflictId)
                    if (conflict != null && conflict.enabled) {
                        if (batch.conflictIds.add(conflictKey)) {
                            batch.names.add(screen.resolveDisplayName(conflict))
                            batch.items.add(conflict)
                        }
                    }
                }
            }
            for (existing in screen.getItemsSnapshot()) {
                val existingIdentifier = existing.identifier
                if (existingIdentifier.isNullOrBlank() || !existing.enabled) {
                    continue
                }
                val existingKey = normalizeIdentifier(existingIdentifier)
                if (existingKey.isBlank() || plannedIds.contains(existingKey)) {
                    continue
                }
                if (existing.conflicts.isNullOrEmpty()) {
                    continue
                }
                var hit = false
                for (conflictId in existing.conflicts) {
                    if (conflictId.isNullOrBlank()) {
                        continue
                    }
                    val conflictKey = normalizeIdentifier(conflictId)
                    if (conflictKey.isNotBlank() && plannedIds.contains(conflictKey)) {
                        hit = true
                        break
                    }
                }
                if (hit && batch.conflictIds.add(existingKey)) {
                    batch.names.add(screen.resolveDisplayName(existing))
                    batch.items.add(existing)
                }
            }
            return batch
        }
    }

    private data class GroupToggleState(
        val label: String,
        val enabled: Boolean,
        val show: Boolean,
    )

    private class DependencyBatch {
        val missingIds: MutableSet<String> = LinkedHashSet()
        val names: MutableList<String> = ArrayList()
        val missingItems: MutableList<DlcServerItem> = ArrayList()
    }

    private class ConflictBatch {
        val conflictIds: MutableSet<String> = LinkedHashSet()
        val names: MutableList<String> = ArrayList()
        val items: MutableList<DlcServerItem> = ArrayList()
    }

    companion object {
        private const val COLOR_GROUP = -0x402801
        private const val COLOR_PATH = -0x65584f
        private const val COLOR_ITEM = -0x19191a
        private const val COLOR_ENABLED_BAR = -0xb350b0
        private const val COLOR_PENDING_BAR = -0x3db6
        private const val COLOR_PENDING_BG = -0x33b3cbf0
        private const val COLOR_PENDING_BORDER = -0x3db6
        private const val COLOR_PENDING_TEXT = -0x1d5a
        private const val COLOR_GROUP_SEPARATOR = -0x33957748
        private const val COLOR_PATH_SEPARATOR = -0x77857463
        private const val COLOR_ITEM_SEPARATOR = 0x33485460

        @JvmStatic
        fun getFilterStrings(item: DlcServerItem?): List<String> {
            if (item == null) {
                return emptyList()
            }
            val result = ArrayList<String>()
            item.identifier?.let { result.add(it) }
            item.name?.let { result.add(it) }
            item.displayNames?.values?.let { result.addAll(it) }
            item.tips?.values?.let { result.addAll(it) }
            item.type?.let {
                result.add(it)
                val mapped = screenType(item)
                if (mapped != null) {
                    result.add(typeId(mapped))
                }
            }
            return result
        }

        private fun screenType(item: DlcServerItem?): DlcType? {
            val type = item?.type ?: return null
            val lower = type.lowercase(Locale.ROOT)
            for (dlcType in DlcType.entries) {
                if (dlcType == DlcType.DLC_PACK) {
                    continue
                }
                if (typeId(dlcType).equals(lower, ignoreCase = true)) {
                    return dlcType
                }
            }
            return null
        }

        private fun typeId(type: DlcType): String {
            return when (type) {
                DlcType.MOD -> "mod"
                DlcType.RESOURCE_PACK -> "resource"
                DlcType.DATA_PACK -> "data"
                DlcType.TACZ_PACK -> "tacz"
                DlcType.DLC_PACK -> "dlcpack"
                DlcType.UNKNOWN -> "other"
            }
        }

        private fun normalizeIdentifier(identifier: String?): String {
            if (identifier == null) {
                return ""
            }
            return identifier.trim().lowercase(Locale.ROOT)
        }
    }
}
