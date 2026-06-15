package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcRuntimeState
import fi.dy.masa.malilib.gui.button.ButtonBase
import fi.dy.masa.malilib.gui.button.ButtonGeneric
import fi.dy.masa.malilib.gui.button.IButtonActionListener
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.util.FormattedCharSequence
import java.util.ArrayDeque
import java.util.LinkedHashSet

class DlcListEntry(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    isOdd: Boolean,
    private val row: DlcListRow,
    private val rowIndex: Int,
) : WidgetListEntryBase<DlcListRow>(x, y, width, height, row, rowIndex) {
    private var toggleButton: ButtonGeneric? = null
    private var groupToggleButton: ButtonGeneric? = null
    private var redirectButton: ButtonGeneric? = null
    private var groupToggleWidth = 0
    private var groupToggleHeight = 0
    private var lastNameClickTime = 0L
    private var lastNameClickButton = -1

    init {
        when (row.getType()) {
            DlcListRow.RowType.ITEM -> createToggleButton(width, height, row.getItem())
            DlcListRow.RowType.REDIRECT_ITEM -> createRedirectButton(width, height, row.getRedirectEntry())
            DlcListRow.RowType.PATH_HEADER -> if (!row.isRootPath()) createGroupToggleButton(width, height, row.getItems())
            else -> Unit
        }
    }

    private fun createToggleButton(width: Int, height: Int, item: DlcItem?) {
        val buttonWidth = 64
        val buttonHeight = 18
        val bx = x + width - buttonWidth - 6
        val by = getAlignedButtonY(y, height, buttonHeight)
        toggleButton = ButtonGeneric(bx, by, buttonWidth, buttonHeight, getToggleLabel(item)).also {
            addButton(it, ToggleListener(item))
        }
    }

    private fun createGroupToggleButton(width: Int, height: Int, items: List<DlcItem>?) {
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

    private fun createRedirectButton(width: Int, height: Int, entry: DlcRedirectEntry?) {
        if (entry == null) {
            return
        }
        val buttonWidth = 64
        val buttonHeight = 18
        val bx = x + width - buttonWidth - 6
        val by = getAlignedButtonY(y, height, buttonHeight)
        redirectButton = ButtonGeneric(
            bx,
            by,
            buttonWidth,
            buttonHeight,
            Component.translatable("dlc_manager.redirect.open").string,
        ).also {
            addButton(it, RedirectListener(entry))
        }
    }

    override fun render(mouseX: Int, mouseY: Int, hovered: Boolean, guiGraphics: GuiGraphics) {
        when (row.getType()) {
            DlcListRow.RowType.GROUP_HEADER -> {
                drawGroupSeparator(guiGraphics)
                val label = Component.translatable(row.getGroupType()?.getDisplayKey() ?: "").string
                drawStringWithShadow(x + 6, y + 4, COLOR_GROUP, label, guiGraphics)
            }
            DlcListRow.RowType.REDIRECT_HEADER -> {
                drawGroupSeparator(guiGraphics)
                drawStringWithShadow(x + 6, y + 4, COLOR_REDIRECT, row.getLabel(), guiGraphics)
            }
            DlcListRow.RowType.PATH_HEADER -> {
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
            }
            DlcListRow.RowType.REDIRECT_ITEM -> {
                drawItemSeparator(guiGraphics)
                val entry = row.getRedirectEntry()
                val lang = Minecraft.getInstance().options.languageCode
                val label = entry?.resolveDisplayName(lang) ?: ""
                val textY = y + (height - Minecraft.getInstance().font.lineHeight) / 2
                guiGraphics.drawString(Minecraft.getInstance().font, DlcTextFormatter.parse(label), x + 10, textY, COLOR_ITEM, false)
                redirectButton?.let { button ->
                    val bx = x + width - button.getWidth() - 6
                    val by = getAlignedButtonY(y, height, button.getHeight())
                    button.setX(bx)
                    button.setY(by)
                }
            }
            DlcListRow.RowType.ITEM -> {
                drawItemSeparator(guiGraphics)
                val item = row.getItem() ?: return
                val dirty = isDirty(item)
                item.setDirty(dirty)
                val displayName = DlcDisplayNameUtil.resolveDisplayName(item)
                val textY = y + (height - Minecraft.getInstance().font.lineHeight) / 2
                if (dirty) {
                    drawPendingAccent(guiGraphics)
                } else if (item.isEnabled()) {
                    drawEnabledAccent(guiGraphics)
                }
                guiGraphics.drawString(Minecraft.getInstance().font, DlcTextFormatter.parse(displayName), x + 10, textY, COLOR_ITEM, false)
                if (dirty) {
                    drawPendingBadge(guiGraphics, DlcMaLiLibScreen.getPendingSuffix(item))
                }
                toggleButton?.let { button ->
                    button.setDisplayString(getToggleLabel(item))
                    val missing = DlcRuntimeState.hasMissingDependencies(item)
                    val blocked = DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
                    button.setEnabled(!blocked && (item.isEnabled() || !missing))
                }
            }
        }
        super.render(mouseX, mouseY, hovered, guiGraphics)
        if (isMouseWithin(mouseX, mouseY) && !isMouseOverButton(mouseX, mouseY)) {
            if (row.getType() == DlcListRow.RowType.ITEM) {
                val item = row.getItem()
                val font = Minecraft.getInstance().font
                val maxWidth = maxOf(160, minOf(width - 20, 280))
                val lines = buildTooltipLines(font, item, maxWidth)
                if (lines.isNotEmpty()) {
                    DlcTooltipContext.set(lines, mouseX, mouseY)
                }
            } else if (row.getType() == DlcListRow.RowType.REDIRECT_ITEM) {
                val entry = row.getRedirectEntry()
                val tip = entry?.resolveTip(Minecraft.getInstance().options.languageCode)
                if (!tip.isNullOrBlank()) {
                    val font = Minecraft.getInstance().font
                    val maxWidth = maxOf(160, minOf(width - 20, 280))
                    val lines = ArrayList(DlcTextFormatter.splitMultiline(font, tip, maxWidth))
                    if (lines.isNotEmpty()) {
                        DlcTooltipContext.set(lines, mouseX, mouseY)
                    }
                }
            }
        }
    }

    override fun onMouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true
        }
        if (mouseButton != 0 || row.getType() != DlcListRow.RowType.ITEM) {
            return false
        }
        val item = row.getItem()
        if (item == null || item.getRedirectUrl().isNullOrBlank()) {
            return false
        }
        if (!isMouseWithin(mouseX, mouseY) || isMouseOverButton(mouseX, mouseY) || !isMouseOverItemLabel(mouseX, mouseY, item)) {
            return false
        }
        val now = System.currentTimeMillis()
        val isDoubleClick = lastNameClickButton == mouseButton && now - lastNameClickTime <= DOUBLE_CLICK_INTERVAL_MS
        lastNameClickTime = now
        lastNameClickButton = mouseButton
        if (isDoubleClick) {
            lastNameClickTime = 0L
            openItemRedirect(item)
            return true
        }
        return false
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
        if (toggleButton?.let { isMouseOverButton(it, mouseX, mouseY) } == true) {
            return true
        }
        if (redirectButton?.let { isMouseOverButton(it, mouseX, mouseY) } == true) {
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

    private fun isMouseOverItemLabel(mouseX: Int, mouseY: Int, item: DlcItem?): Boolean {
        if (item == null) {
            return false
        }
        val font = Minecraft.getInstance().font
        val displayName = DlcDisplayNameUtil.resolveDisplayName(item)
        val textX = x + 10
        val textY = y + (height - font.lineHeight) / 2
        val textWidth = font.width(DlcTextFormatter.parse(displayName))
        return mouseX >= textX && mouseX < textX + textWidth && mouseY >= textY && mouseY < textY + font.lineHeight
    }

    private fun getToggleLabel(item: DlcItem?): String {
        return if (item?.isEnabled() == true) {
            "\u00A7a" + Component.translatable("dlc_manager.toggle.on").string
        } else {
            Component.translatable("dlc_manager.toggle.off").string
        }
    }

    private fun openRedirect(entry: DlcRedirectEntry?) {
        if (entry == null || entry.getRedirectUrl().isNullOrBlank()) {
            return
        }
        val parent = Minecraft.getInstance().screen
        val lang = Minecraft.getInstance().options.languageCode
        val notice = entry.resolveNotice(lang)
        Minecraft.getInstance().setScreen(DlcRedirectConfirmScreen(parent, entry.getRedirectUrl(), notice))
    }

    private fun openItemRedirect(item: DlcItem?) {
        if (item == null || item.getRedirectUrl().isNullOrBlank()) {
            return
        }
        val parent = Minecraft.getInstance().screen
        Minecraft.getInstance().setScreen(DlcRedirectConfirmScreen(parent, item.getRedirectUrl(), ""))
    }

    private fun getTooltipText(item: DlcItem?): String? {
        val tips = item?.getTooltips() ?: return null
        if (tips.isEmpty()) {
            return null
        }
        val lang = Minecraft.getInstance().options.languageCode
        if (tips.containsKey(lang)) {
            return tips[lang]
        }
        if (tips.containsKey("en_us")) {
            return tips["en_us"]
        }
        return tips.values.firstOrNull()
    }

    private fun isMouseWithin(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }

    private fun buildTooltipLines(font: Font, item: DlcItem?, maxWidth: Int): List<FormattedCharSequence> {
        val lines = ArrayList<FormattedCharSequence>()
        val tooltip = getTooltipText(item)
        var redirectHint: String? = null
        if (!item?.getRedirectUrl().isNullOrBlank()) {
            redirectHint = "\u00A7b" + Component.translatable("dlc_manager.tooltip.double_click_redirect").string + "\u00A7r"
        }
        if (!tooltip.isNullOrBlank()) {
            lines.addAll(DlcTextFormatter.splitMultiline(font, tooltip, maxWidth))
        } else if (redirectHint != null) {
            lines.addAll(DlcTextFormatter.splitMultiline(font, redirectHint, maxWidth))
        }

        if (redirectHint != null && !tooltip.isNullOrBlank()) {
            if (lines.isNotEmpty()) {
                lines.add(FormattedCharSequence.EMPTY)
            }
            lines.addAll(DlcTextFormatter.splitMultiline(font, redirectHint, maxWidth))
        }

        val deps = collectDependencyNamesRecursive(item)
        val conflicts = item?.getConflicts()
        val missing = DlcRuntimeState.collectMissingDependencyIdentifiers(item)
        val presetBlockers = DlcPresetRuntimeState.getDlcBlockerNames(item?.getIdentifier(), Minecraft.getInstance().options.languageCode)
        if (presetBlockers.isNotEmpty()) {
            if (lines.isNotEmpty()) {
                lines.add(FormattedCharSequence.EMPTY)
            }
            for (blocker in presetBlockers) {
                val line = Component.translatable("dlc_manager.preset.conflict", blocker).withStyle(ChatFormatting.DARK_RED)
                lines.addAll(font.split(line, maxWidth))
            }
        }
        if (missing.isNotEmpty()) {
            if (lines.isNotEmpty()) {
                lines.add(FormattedCharSequence.EMPTY)
            }
            val missingLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.missing_dependencies").withStyle(ChatFormatting.RED))
                .append(":")
            lines.addAll(font.split(missingLabel, maxWidth))
            for (dep in missing) {
                lines.addAll(font.split(DlcTextFormatter.parse(DlcDisplayNameUtil.resolveDisplayName(dep)), maxWidth))
            }
        }
        if (deps.isNotEmpty()) {
            if (lines.isNotEmpty()) {
                lines.add(FormattedCharSequence.EMPTY)
            }
            val depLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.dependencies").withStyle(ChatFormatting.GOLD))
                .append(":")
            lines.addAll(font.split(depLabel, maxWidth))
            for (dep in deps) {
                lines.addAll(font.split(DlcTextFormatter.parse(DlcDisplayNameUtil.resolveDisplayName(dep)), maxWidth))
            }
        }
        if (!conflicts.isNullOrEmpty()) {
            if (lines.isNotEmpty()) {
                lines.add(FormattedCharSequence.EMPTY)
            }
            val conflictLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.conflicts").withStyle(ChatFormatting.DARK_RED))
                .append(":")
            lines.addAll(font.split(conflictLabel, maxWidth))
            for (conflict in conflicts) {
                lines.addAll(font.split(DlcTextFormatter.parse(DlcDisplayNameUtil.resolveDisplayName(conflict)), maxWidth))
            }
        }
        return lines
    }

    private class ToggleListener(private val item: DlcItem?) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            if (item == null) {
                return
            }
            if (item.isEnabled()) {
                disableItemsWithDependents(listOf(item))
                updateToggleButtonLabel(button, item)
                item.setDirty(isDirty(item))
                return
            }

            if (DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())) {
                return
            }

            requestEnable(item, button)
        }
    }

    private inner class RedirectListener(private val entry: DlcRedirectEntry) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            openRedirect(entry)
        }
    }

    private class GroupToggleListener(private val items: List<DlcItem>?) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            if (items.isNullOrEmpty()) {
                return
            }
            var state = getGroupToggleState(items)
            if (!state.show) {
                return
            }
            val anyEligibleDisabled = items.any { item ->
                item.AllowEnableByAll()
                    && !DlcRuntimeState.hasMissingDependencies(item)
                    && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
                    && !item.isEnabled()
            }
            if (anyEligibleDisabled) {
                val targets = items.filter { item ->
                    item.AllowEnableByAll()
                        && !DlcRuntimeState.hasMissingDependencies(item)
                        && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
                        && !item.isEnabled()
                }
                val conflicts = DlcRuntimeState.collectConflicts(targets)
                if (conflicts.getLines().isNotEmpty()) {
                    val filteredTargets = filterConflictTargets(targets, conflicts)
                    showBatchConflictPrompt(conflicts.getLines()) { continueBatchEnable(filteredTargets) }
                    return
                }
                continueBatchEnable(targets)
            } else {
                disableItemsWithDependents(items)
            }
            if (button is ButtonGeneric) {
                state = getGroupToggleState(items)
                button.setDisplayString(state.label)
                button.setEnabled(state.enabled)
            }
        }
    }

    private data class DependencyCheck(
        val names: List<String>,
        val items: List<DlcItem>,
    )

    private data class ConflictCheck(
        val names: List<String>,
        val items: List<DlcItem>,
    )

    private data class GroupToggleState(
        val label: String,
        val enabled: Boolean,
        val show: Boolean,
    )

    companion object {
        private const val COLOR_GROUP = -0x402801
        private const val COLOR_PATH = -0x65584f
        private const val COLOR_ITEM = -0x19191a
        private const val COLOR_REDIRECT = -0x402801
        private const val COLOR_ENABLED_BAR = -0xb350b0
        private const val COLOR_PENDING_BAR = -0x3db6
        private const val COLOR_PENDING_BG = -0x33b3cbf0
        private const val COLOR_PENDING_BORDER = -0x3db6
        private const val COLOR_PENDING_TEXT = -0x1d5a
        private const val COLOR_GROUP_SEPARATOR = -0x33957748
        private const val COLOR_PATH_SEPARATOR = -0x77857463
        private const val COLOR_ITEM_SEPARATOR = 0x33485460
        private const val DOUBLE_CLICK_INTERVAL_MS = 250L

        @JvmStatic
        private fun getGroupToggleState(items: List<DlcItem>?): GroupToggleState {
            if (items.isNullOrEmpty()) {
                return GroupToggleState("", false, false)
            }
            val anyEligibleDisabled = items.any { item ->
                item.AllowEnableByAll()
                    && !DlcRuntimeState.hasMissingDependencies(item)
                    && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
                    && !item.isEnabled()
            }
            val anyEnabled = items.any { it.isEnabled() }
            if (anyEligibleDisabled) {
                return GroupToggleState(Component.translatable("dlc_manager.group.enable").string, true, true)
            }
            if (anyEnabled) {
                return GroupToggleState(Component.translatable("dlc_manager.group.disable").string, true, true)
            }
            return GroupToggleState("", false, false)
        }

        @JvmStatic
        private fun collectDependencyNamesRecursive(item: DlcItem?): List<String> {
            if (item == null) {
                return emptyList()
            }
            val result = LinkedHashSet<String>()
            val queue = ArrayDeque<String>()
            addDependencyNames(item.getDependencies(), result, queue)
            while (!queue.isEmpty()) {
                val name = queue.poll()
                if (name.isNullOrBlank()) {
                    continue
                }
                val depItem = DlcRuntimeState.getItemByIdentifier(name) ?: continue
                addDependencyNames(depItem.getDependencies(), result, queue)
            }
            return ArrayList(result)
        }

        @JvmStatic
        private fun addDependencyNames(names: List<String>?, result: MutableSet<String>, queue: ArrayDeque<String>) {
            if (names.isNullOrEmpty()) {
                return
            }
            for (name in names) {
                if (name.isNullOrBlank()) {
                    continue
                }
                if (result.add(name)) {
                    queue.add(name)
                }
            }
        }

        @JvmStatic
        private fun continueBatchEnable(targets: List<DlcItem>?) {
            if (targets.isNullOrEmpty()) {
                return
            }
            val deps = DlcRuntimeState.collectMissingDependencies(targets)
            if (deps.getLines().isNotEmpty()) {
                val scopeLabel = Component.translatable("dlc_manager.scope.group")
                showBatchDependencyPrompt(deps.getLines(), deps.getItemsToEnable(), targets, scopeLabel)
                return
            }
            val alarmItems = targets.filter { it.isAlarm() }
            if (alarmItems.isNotEmpty()) {
                showBatchAlarmPrompt(alarmItems) { enableItems(targets, true) }
                return
            }
            enableItems(targets, true)
        }

        @JvmStatic
        private fun isDirty(item: DlcItem?): Boolean {
            return DlcRuntimeState.isBaselineDifferent(item?.getId(), item?.isEnabled() == true)
        }

        @JvmStatic
        private fun updateToggleButtonLabel(button: ButtonBase, item: DlcItem) {
            if (button is ButtonGeneric) {
                button.setDisplayString(
                    if (item.isEnabled()) {
                        "\u00A7a" + Component.translatable("dlc_manager.toggle.on").string
                    } else {
                        Component.translatable("dlc_manager.toggle.off").string
                    }
                )
            }
        }

        @JvmStatic
        private fun requestEnable(item: DlcItem?, button: ButtonBase) {
            if (item == null) {
                return
            }
            if (item.isAlarm()) {
                val returnScreen = Minecraft.getInstance().screen
                DlcReturnPositionHost.remember(returnScreen)
                val tip = resolveLocalized(item.getTooltips())
                Minecraft.getInstance().setScreen(
                    DlcAlarmConfirmScreen(returnScreen, tip) {
                        attemptEnable(item, button)
                        if (returnScreen is DlcMaLiLibScreen) {
                            returnScreen.refreshAfterStateChange()
                        }
                    }
                )
                return
            }
            attemptEnable(item, button)
        }

        @JvmStatic
        private fun attemptEnable(item: DlcItem, button: ButtonBase) {
            val conflicts = getConflicts(item)
            if (conflicts.items.isNotEmpty()) {
                showConflictPrompt(item, conflicts, button)
                return
            }

            val dependencies = getMissingDependencies(item)
            if (dependencies.names.isNotEmpty()) {
                showDependencyPrompt(item, dependencies, button)
                return
            }

            item.setEnabled(true)
            DlcRuntimeState.setOverride(item.getId(), true)
            updateToggleButtonLabel(button, item)
            item.setDirty(isDirty(item))
        }

        @JvmStatic
        private fun continueEnableAfterConflictResolution(item: DlcItem, button: ButtonBase) {
            var guard = 0
            while (guard++ < 64) {
                val conflicts = getConflicts(item)
                if (conflicts.items.isEmpty()) {
                    break
                }
                disableItemsWithDependents(conflicts.items)
            }

            val dependencies = getMissingDependencies(item)
            if (dependencies.names.isNotEmpty()) {
                showDependencyPrompt(item, dependencies, button)
                return
            }

            item.setEnabled(true)
            DlcRuntimeState.setOverride(item.getId(), true)
            updateToggleButtonLabel(button, item)
            item.setDirty(isDirty(item))
        }

        @JvmStatic
        private fun resolveLocalized(values: Map<String, String>?): String {
            if (values.isNullOrEmpty()) {
                return ""
            }
            val lang = Minecraft.getInstance().options.languageCode
            if (values.containsKey(lang)) {
                return values[lang] ?: ""
            }
            if (values.containsKey("en_us")) {
                return values["en_us"] ?: ""
            }
            return values.values.firstOrNull() ?: ""
        }

        @JvmStatic
        private fun showBatchAlarmPrompt(alarmItems: List<DlcItem>?, onConfirm: Runnable?) {
            val returnScreen = Minecraft.getInstance().screen
            DlcReturnPositionHost.remember(returnScreen)
            val tip = buildBatchAlarmTip(alarmItems)
            Minecraft.getInstance().setScreen(
                DlcAlarmConfirmScreen(returnScreen, tip) {
                    onConfirm?.run()
                    if (returnScreen is DlcMaLiLibScreen) {
                        returnScreen.refreshAfterStateChange()
                    }
                }
            )
        }

        @JvmStatic
        private fun buildBatchAlarmTip(alarmItems: List<DlcItem>?): String {
            if (alarmItems.isNullOrEmpty()) {
                return ""
            }
            val sb = StringBuilder()
            for (item in alarmItems) {
                if (sb.isNotEmpty()) {
                    sb.append("\n\n")
                }
                sb.append(DlcDisplayNameUtil.resolveDisplayName(item))
                val tip = resolveLocalized(item.getTooltips())
                if (tip.isNotBlank()) {
                    sb.append("\n").append(tip)
                }
            }
            return sb.toString()
        }

        @JvmStatic
        private fun filterConflictTargets(targets: List<DlcItem>?, conflicts: DlcRuntimeState.ConflictBatchResult?): List<DlcItem> {
            if (targets.isNullOrEmpty()) {
                return emptyList()
            }
            val targetIds = HashSet<String>()
            for (item in targets) {
                val identifier = item.getIdentifier()
                if (!identifier.isNullOrBlank()) {
                    targetIds.add(identifier)
                }
            }
            if (targetIds.isEmpty()) {
                return emptyList()
            }
            if (conflicts == null || conflicts.getLines().isEmpty()) {
                return targets
            }
            val skipped = HashSet<String>()
            for (line in conflicts.getLines()) {
                val left = line.getLeftName()
                val right = line.getRightName()
                if (targetIds.contains(left)) {
                    skipped.add(left)
                }
                if (targetIds.contains(right)) {
                    skipped.add(right)
                }
            }
            if (skipped.isEmpty()) {
                return targets
            }
            return targets.filter { item ->
                val identifier = item.getIdentifier()
                identifier == null || !skipped.contains(identifier)
            }
        }

        @JvmStatic
        private fun getMissingDependencies(item: DlcItem): DependencyCheck {
            val result = DlcRuntimeState.collectMissingDependencies(listOf(item))
            if (result.getLines().isEmpty()) {
                return DependencyCheck(emptyList(), emptyList())
            }
            val names = LinkedHashSet<String>()
            for (line in result.getLines()) {
                names.add(DlcDisplayNameUtil.resolveDisplayName(line.getDependencyName()))
            }
            return DependencyCheck(ArrayList(names), result.getItemsToEnable())
        }

        @JvmStatic
        private fun getConflicts(item: DlcItem?): ConflictCheck {
            if (item == null) {
                return ConflictCheck(emptyList(), emptyList())
            }
            val batch = DlcRuntimeState.collectConflicts(listOf(item))
            if (batch.getLines().isEmpty()) {
                return ConflictCheck(emptyList(), emptyList())
            }
            val rootId = item.getIdentifier()
            val conflictIds = LinkedHashSet<String>()
            for (line in batch.getLines()) {
                val left = line.getLeftName()
                val right = line.getRightName()
                if (!left.isNullOrBlank() && (rootId == null || left != rootId)) {
                    conflictIds.add(left)
                }
                if (!right.isNullOrBlank() && (rootId == null || right != rootId)) {
                    conflictIds.add(right)
                }
            }
            val toDisable = ArrayList<DlcItem>()
            val names = ArrayList<String>()
            for (identifier in conflictIds) {
                if (identifier.isBlank()) {
                    continue
                }
                val conflictItem = DlcRuntimeState.getItemByIdentifier(identifier)
                if (conflictItem != null && conflictItem.isEnabled()) {
                    toDisable.add(conflictItem)
                    names.add(DlcDisplayNameUtil.resolveDisplayName(identifier))
                }
            }
            return ConflictCheck(names, toDisable)
        }

        @JvmStatic
        private fun enableItems(items: Collection<DlcItem>?, enabled: Boolean) {
            if (items.isNullOrEmpty()) {
                return
            }
            for (item in items) {
                item.setEnabled(enabled)
                DlcRuntimeState.setOverride(item.getId(), enabled)
            }
        }

        @JvmStatic
        private fun disableItemsWithDependents(items: Collection<DlcItem>?) {
            if (items.isNullOrEmpty()) {
                return
            }
            val toDisable = LinkedHashSet<DlcItem>()
            for (item in items) {
                toDisable.addAll(DlcRuntimeState.collectDependentsRecursive(item))
            }
            enableItems(toDisable, false)
            val hiddenDeps = DlcRuntimeState.collectHiddenDependenciesToDisable(toDisable)
            enableItems(hiddenDeps, false)
        }

        @JvmStatic
        private fun showBatchConflictPrompt(lines: List<DlcRuntimeState.ConflictLine>, onSkipConfirm: Runnable?) {
            val message = DlcConfirmUtil.buildConflictMessage(lines)
            val returnScreen = Minecraft.getInstance().screen
            if (returnScreen is DlcMaLiLibScreen) {
                returnScreen.rememberListScroll()
            }
            val title = Component.translatable("dlc_manager.batch.conflicts_title").withStyle(ChatFormatting.RED)
            Minecraft.getInstance().setScreen(
                ConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(returnScreen)
                        if (!accepted) {
                            return@ConfirmScreen
                        }
                        onSkipConfirm?.run()
                        if (returnScreen is DlcMaLiLibScreen) {
                            returnScreen.refreshAfterStateChange()
                        }
                    },
                    title,
                    message,
                    Component.translatable("dlc_manager.batch.conflicts_skip_enable"),
                    Component.translatable("dlc_manager.confirm_no"),
                )
            )
        }

        @JvmStatic
        private fun showBatchDependencyPrompt(
            lines: List<DlcRuntimeState.DependencyLine>,
            depsToEnable: List<DlcItem>?,
            targets: List<DlcItem>?,
            scopeLabel: Component,
        ) {
            val message = DlcConfirmUtil.buildDependencyMessage(lines)
            val returnScreen = Minecraft.getInstance().screen
            if (returnScreen is DlcMaLiLibScreen) {
                returnScreen.rememberListScroll()
            }
            val title = Component.translatable("dlc_manager.batch.dependencies_title")
            val enableLabel = Component.translatable("dlc_manager.batch.enable_deps", scopeLabel)
            Minecraft.getInstance().setScreen(
                ConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(returnScreen)
                        if (!accepted) {
                            return@ConfirmScreen
                        }
                        val planned = ArrayList<DlcItem>()
                        if (depsToEnable != null) {
                            planned.addAll(depsToEnable)
                        }
                        if (targets != null) {
                            planned.addAll(targets)
                        }
                        val conflicts = DlcRuntimeState.collectConflicts(planned)
                        if (conflicts.getLines().isNotEmpty()) {
                            val filtered = filterConflictTargets(planned, conflicts)
                            showBatchConflictPrompt(conflicts.getLines()) {
                                if (filtered.isNotEmpty()) {
                                    enableItems(filtered, true)
                                }
                                if (returnScreen is DlcMaLiLibScreen) {
                                    returnScreen.refreshAfterStateChange()
                                }
                            }
                            return@ConfirmScreen
                        }
                        enableItems(planned, true)
                        if (returnScreen is DlcMaLiLibScreen) {
                            returnScreen.refreshAfterStateChange()
                        }
                    },
                    title,
                    message,
                    enableLabel,
                    Component.translatable("dlc_manager.confirm_no"),
                )
            )
        }

        @JvmStatic
        private fun showDependencyPrompt(item: DlcItem, dependencies: DependencyCheck, button: ButtonBase) {
            if (dependencies.names.isEmpty()) {
                return
            }
            val returnScreen = Minecraft.getInstance().screen
            if (returnScreen is DlcMaLiLibScreen) {
                returnScreen.rememberListScroll()
            }
            val list = dependencies.names.joinToString(", ")
            val message = DlcConfirmUtil.buildDependencyPromptMessage(list)
            Minecraft.getInstance().setScreen(
                ConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(returnScreen)
                        if (!accepted) {
                            return@ConfirmScreen
                        }
                        for (dep in dependencies.items) {
                            dep.setEnabled(true)
                            DlcRuntimeState.setOverride(dep.getId(), true)
                        }
                        item.setEnabled(true)
                        DlcRuntimeState.setOverride(item.getId(), true)
                        updateToggleButtonLabel(button, item)
                        if (returnScreen is DlcMaLiLibScreen) {
                            returnScreen.refreshAfterStateChange()
                        }
                    },
                    Component.translatable("dlc_manager.title"),
                    message,
                    Component.translatable("dlc_manager.dependency.enable"),
                    Component.translatable("dlc_manager.confirm_no"),
                )
            )
        }

        @JvmStatic
        private fun showConflictPrompt(item: DlcItem, conflicts: ConflictCheck, button: ButtonBase) {
            if (conflicts.items.isEmpty()) {
                return
            }
            val returnScreen = Minecraft.getInstance().screen
            if (returnScreen is DlcMaLiLibScreen) {
                returnScreen.rememberListScroll()
            }
            val list = conflicts.names.joinToString(", ")
            val message = DlcConfirmUtil.buildConflictPromptMessage(list)
            Minecraft.getInstance().setScreen(
                ConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(returnScreen)
                        if (!accepted) {
                            return@ConfirmScreen
                        }
                        continueEnableAfterConflictResolution(item, button)
                        if (returnScreen is DlcMaLiLibScreen) {
                            returnScreen.refreshAfterStateChange()
                        }
                    },
                    Component.translatable("dlc_manager.title"),
                    message,
                    Component.translatable("dlc_manager.conflict.resolve"),
                    Component.translatable("dlc_manager.confirm_no"),
                )
            )
        }

        @JvmStatic
        private fun resolvePackId(repo: PackRepository?, fileName: String): String? {
            if (repo == null) {
                return null
            }
            val fileId = "file/$fileName"
            if (repo.isAvailable(fileId)) {
                return fileId
            }
            if (repo.isAvailable(fileName)) {
                return fileName
            }
            for (id in repo.availableIds) {
                if (id.endsWith("/$fileName") || id.endsWith(fileName)) {
                    return id
                }
            }
            return null
        }
    }
}
