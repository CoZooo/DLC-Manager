package com.cozooo.dlc_manager.client.screen
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcRuntimeState
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.ArrayDeque
import java.util.LinkedHashSet

object DlcItemActions {

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

    private fun isDirty(item: DlcItem?): Boolean {
        return DlcRuntimeState.isBaselineDifferent(item?.getId(), item?.isEnabled() == true)
    }

    private fun requestEnable(item: DlcItem?) {
        if (item == null) {
            return
        }
        if (item.isAlarm()) {
            val returnScreen = Minecraft.getInstance().screen
            DlcReturnPositionHost.remember(returnScreen)
            val tip = resolveLocalized(item.getTooltips())
            Minecraft.getInstance().setScreen(
                DlcAlarmConfirmScreen(returnScreen, tip) {
                    attemptEnable(item)
                    if (returnScreen is DlcManagerScreen) {
                        returnScreen.refreshAfterStateChange()
                    }
                }
            )
            return
        }
        attemptEnable(item)
    }

    private fun attemptEnable(item: DlcItem) {
        val conflicts = getConflicts(item)
        if (conflicts.items.isNotEmpty()) {
            showConflictPrompt(item, conflicts)
            return
        }

        val dependencies = getMissingDependencies(item)
        if (dependencies.names.isNotEmpty()) {
            showDependencyPrompt(item, dependencies)
            return
        }

        item.setEnabled(true)
        DlcRuntimeState.setOverride(item.getId(), true)

        item.setDirty(isDirty(item))
    }

    private fun continueEnableAfterConflictResolution(item: DlcItem) {
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
            showDependencyPrompt(item, dependencies)
            return
        }

        item.setEnabled(true)
        DlcRuntimeState.setOverride(item.getId(), true)

        item.setDirty(isDirty(item))
    }

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

    private fun showBatchAlarmPrompt(alarmItems: List<DlcItem>?, onConfirm: Runnable?) {
        val returnScreen = Minecraft.getInstance().screen
        DlcReturnPositionHost.remember(returnScreen)
        val tip = DlcConfirmUtil.buildBatchAlarmTip(alarmItems)
        Minecraft.getInstance().setScreen(
            DlcAlarmConfirmScreen(returnScreen, tip) {
                onConfirm?.run()
                if (returnScreen is DlcManagerScreen) {
                    returnScreen.refreshAfterStateChange()
                }
            }
        )
    }

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

    private fun enableItems(items: Collection<DlcItem>?, enabled: Boolean) {
        if (items.isNullOrEmpty()) {
            return
        }
        for (item in items) {
            item.setEnabled(enabled)
            DlcRuntimeState.setOverride(item.getId(), enabled)
        }
    }

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

    private fun showBatchConflictPrompt(lines: List<DlcRuntimeState.ConflictLine>, onSkipConfirm: Runnable?) {
        val message = DlcConfirmUtil.buildConflictMessage(lines)
        val returnScreen = Minecraft.getInstance().screen
        if (returnScreen is DlcManagerScreen) {
            returnScreen.rememberListScroll()
        }
        val title = Component.translatable("dlc_manager.batch.conflicts_title").withStyle(ChatFormatting.RED)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(returnScreen)
                    if (!accepted) {
                        return@DlcConfirmScreen
                    }
                    onSkipConfirm?.run()
                    if (returnScreen is DlcManagerScreen) {
                        returnScreen.refreshAfterStateChange()
                    }
                },
                title,
                message,
                Component.translatable("dlc_manager.batch.conflicts_skip_enable"),
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun showBatchDependencyPrompt(
        lines: List<DlcRuntimeState.DependencyLine>,
        depsToEnable: List<DlcItem>?,
        targets: List<DlcItem>?,
        scopeLabel: Component,
    ) {
        val message = DlcConfirmUtil.buildDependencyMessage(lines)
        val returnScreen = Minecraft.getInstance().screen
        if (returnScreen is DlcManagerScreen) {
            returnScreen.rememberListScroll()
        }
        val title = Component.translatable("dlc_manager.batch.dependencies_title")
        val enableLabel = Component.translatable("dlc_manager.batch.enable_deps", scopeLabel)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(returnScreen)
                    if (!accepted) {
                        return@DlcConfirmScreen
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
                            if (returnScreen is DlcManagerScreen) {
                                returnScreen.refreshAfterStateChange()
                            }
                        }
                        return@DlcConfirmScreen
                    }
                    enableItems(planned, true)
                    if (returnScreen is DlcManagerScreen) {
                        returnScreen.refreshAfterStateChange()
                    }
                },
                title,
                message,
                enableLabel,
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun showDependencyPrompt(item: DlcItem, dependencies: DependencyCheck) {
        if (dependencies.names.isEmpty()) {
            return
        }
        val returnScreen = Minecraft.getInstance().screen
        if (returnScreen is DlcManagerScreen) {
            returnScreen.rememberListScroll()
        }
        val message = DlcConfirmUtil.buildDependencyPromptMessage(dependencies.names)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(returnScreen)
                    if (!accepted) {
                        return@DlcConfirmScreen
                    }
                    for (dep in dependencies.items) {
                        dep.setEnabled(true)
                        DlcRuntimeState.setOverride(dep.getId(), true)
                    }
                    item.setEnabled(true)
                    DlcRuntimeState.setOverride(item.getId(), true)

                    if (returnScreen is DlcManagerScreen) {
                        returnScreen.refreshAfterStateChange()
                    }
                },
                Component.translatable("dlc_manager.title"),
                message,
                Component.translatable("dlc_manager.dependency.enable"),
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun showConflictPrompt(item: DlcItem, conflicts: ConflictCheck) {
        if (conflicts.items.isEmpty()) {
            return
        }
        val returnScreen = Minecraft.getInstance().screen
        if (returnScreen is DlcManagerScreen) {
            returnScreen.rememberListScroll()
        }
        val message = DlcConfirmUtil.buildConflictPromptMessage(conflicts.names)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(returnScreen)
                    if (!accepted) {
                        return@DlcConfirmScreen
                    }
                    continueEnableAfterConflictResolution(item)
                    if (returnScreen is DlcManagerScreen) {
                        returnScreen.refreshAfterStateChange()
                    }
                },
                Component.translatable("dlc_manager.title"),
                message,
                Component.translatable("dlc_manager.conflict.resolve"),
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    @JvmStatic
    fun toggle(item: DlcItem?) {
        if (item == null) {
            return
        }
        if (item.isEnabled()) {
            disableItemsWithDependents(listOf(item))

            item.setDirty(isDirty(item))
            return
        }

        if (DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())) {
            return
        }

        requestEnable(item)
    }

    @JvmStatic
    fun toggleGroup(items: List<DlcItem>?) {
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

    @JvmStatic
    fun groupToggleLabel(items: List<DlcItem>?): String = getGroupToggleState(items).label
    @JvmStatic
    fun canToggleGroup(items: List<DlcItem>?): Boolean = getGroupToggleState(items).show
    @JvmStatic
    fun dependencyNames(item: DlcItem?): List<String> = collectDependencyNamesRecursive(item)
}
