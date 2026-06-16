package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcRuntimeState
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object DlcConfirmUtil {
    @JvmStatic
    fun buildConflictLine(left: String, right: String): Component {
        return Component.translatable(
            "dlc_manager.batch.conflicts_line_prefix",
            DlcTextFormatter.parse(left),
            DlcTextFormatter.parse(right),
        ).append(Component.translatable("dlc_manager.conflict.word").withStyle(ChatFormatting.DARK_RED))
    }

    @JvmStatic
    fun buildDependencyLine(item: String, dependency: String): Component {
        return Component.translatable(
            "dlc_manager.batch.dependencies_line",
            DlcTextFormatter.parse(item),
            DlcTextFormatter.parse(dependency),
        )
    }

    @JvmStatic
    fun buildConflictPromptMessage(list: String): Component {
        return Component.translatable("dlc_manager.conflict.prompt_prefix", DlcTextFormatter.parse(list))
            .append(Component.translatable("dlc_manager.conflict.word").withStyle(ChatFormatting.DARK_RED))
    }

    @JvmStatic
    fun buildConflictPromptMessage(names: List<String>?): Component {
        return buildConflictPromptMessage(joinNames(names))
    }

    @JvmStatic
    fun buildDependencyPromptMessage(list: String): Component {
        return Component.translatable("dlc_manager.dependency.prompt", DlcTextFormatter.parse(list))
    }

    @JvmStatic
    fun buildDependencyPromptMessage(names: List<String>?): Component {
        return buildDependencyPromptMessage(joinNames(names))
    }

    @JvmStatic
    fun buildConflictMessage(lines: List<DlcRuntimeState.ConflictLine>): Component {
        val components = ArrayList<Component>()
        for (line in lines) {
            components.add(
                buildConflictLine(
                    DlcDisplayNameUtil.resolveDisplayName(line.getLeftName()),
                    DlcDisplayNameUtil.resolveDisplayName(line.getRightName()),
                )
            )
        }
        return joinLines(components)
    }

    @JvmStatic
    fun buildDependencyMessage(lines: List<DlcRuntimeState.DependencyLine>): Component {
        val components = ArrayList<Component>()
        for (line in lines) {
            components.add(
                buildDependencyLine(
                    DlcDisplayNameUtil.resolveDisplayName(line.getItemName()),
                    DlcDisplayNameUtil.resolveDisplayName(line.getDependencyName()),
                )
            )
        }
        return joinLines(components)
    }

    fun buildPackUpdateIncompatibleConfirmMessage(check: DlcPackUpdateGuard.CheckResult?): Component {
        val lines = ArrayList<String>()
        if (check != null) {
            for (status in check.incompatiblePacks) {
                val reason = if (status.reasons.isNullOrEmpty()) {
                    ""
                } else {
                    " (${status.reasons.joinToString("; ")})"
                }
                lines.add(status.identifier() + reason)
            }
        }
        return Component.translatable("dlc_manager.pack.update_incompatible_confirm", lines.joinToString("\n"))
    }

    @JvmStatic
    fun describePackBlockingPrecheck(precheck: DlcPackInstaller.PrecheckResult): String {
        val messages = ArrayList<String>()
        if (precheck.incompatibleVersions.isNotEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.incompatible_versions", joinLimited(precheck.incompatibleVersions)).string)
        }
        if (precheck.missingDependencies.isNotEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.missing_dependencies", joinLimited(precheck.missingDependencies)).string)
        }
        if (precheck.dlcToEnable.isNotEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.enable_dlc", joinLimited(precheck.dlcToEnable)).string)
        }
        if (precheck.presetsToEnable.isNotEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.enable_presets", joinLimited(precheck.presetsToEnable)).string)
        }
        if (precheck.dlcToDisable.isNotEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.disable_dlc", joinLimited(precheck.dlcToDisable)).string)
        }
        if (precheck.presetsToDisable.isNotEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.disable_presets", joinLimited(precheck.presetsToDisable)).string)
        }
        if (messages.isNotEmpty()) {
            return messages.joinToString("\n")
        }
        return "unknown"
    }

    @JvmStatic
    fun confirmYes(): Component {
        return Component.translatable("dlc_manager.confirm_yes")
    }

    @JvmStatic
    fun confirmNo(): Component {
        return Component.translatable("dlc_manager.confirm_no")
    }

    @JvmStatic
    fun buildUpdateManagerTitle(): Component {
        return Component.translatable("dlc_manager.update.manager_title")
    }

    @JvmStatic
    fun buildUpdateConfirmApplyMessage(): Component {
        return Component.translatable("dlc_manager.update.confirm_apply")
    }

    @JvmStatic
    fun buildUpdateConfirmRollbackMessage(targetVersion: String?): Component {
        return Component.translatable("dlc_manager.update.confirm_rollback", targetVersion)
    }

    @JvmStatic
    fun buildUpdateConfirmReapplyMessage(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?): Component {
        val compact = compactTransition(baseOld, dlcOld, baseNew, dlcNew)
        return Component.translatable("dlc_manager.update.confirm_reapply", compact[0], compact[1])
    }

    @JvmStatic
    fun buildUpdateConfirmReapplyMessage(
        baseOld: String?,
        dlcOld: String?,
        baseNew: String?,
        dlcNew: String?,
        changelog: String?,
    ): Component {
        val base = buildUpdateConfirmReapplyMessage(baseOld, dlcOld, baseNew, dlcNew)
        if (changelog.isNullOrBlank()) {
            return base
        }
        return Component.empty()
            .append(base)
            .append("\n\n")
            .append(Component.translatable("dlc_manager.update.changelog_tooltip_title"))
            .append("\n")
            .append(DlcMarkdownRenderer.renderMarkdownComponent(changelog))
    }

    @JvmStatic
    fun buildHeaderConfirmMessage(enable: Boolean, activeFilterLabel: Component?): Component {
        if (activeFilterLabel == null) {
            val key = if (enable) "dlc_manager.confirm.enable_all" else "dlc_manager.confirm.disable_all"
            return Component.translatable(key)
        }
        val key = if (enable) "dlc_manager.confirm.enable_all_type" else "dlc_manager.confirm.disable_all_type"
        return Component.translatable(key, activeFilterLabel)
    }

    @JvmStatic
    fun buildBatchAlarmTip(alarmItems: Collection<DlcItem>?): String {
        if (alarmItems.isNullOrEmpty()) {
            return ""
        }
        val sb = StringBuilder()
        val lang = Minecraft.getInstance().options.languageCode
        for (item in alarmItems) {
            if (sb.isNotEmpty()) {
                sb.append("\n\n")
            }
            sb.append(DlcDisplayNameUtil.resolveDisplayName(item))
            val tip = resolveAlarmTip(item, lang)
            if (!tip.isNullOrBlank()) {
                sb.append("\n").append(tip)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun joinLines(lines: List<Component>): MutableComponent {
        var result: MutableComponent = Component.empty()
        for (i in lines.indices) {
            if (i > 0) {
                result = result.append(Component.literal("\n"))
            }
            result = result.append(lines[i])
        }
        return result
    }

    private fun joinNames(names: List<String>?): String {
        if (names.isNullOrEmpty()) {
            return ""
        }
        return names.filter { it.isNotBlank() }.joinToString(", ")
    }

    private fun joinLimited(values: List<String>): String {
        val limit = minOf(values.size, 6)
        var joined = values.subList(0, limit).joinToString(", ")
        if (values.size > limit) {
            joined += ", ..."
        }
        return joined
    }

    private fun normalizeVersion(value: String?): String {
        return if (value.isNullOrBlank()) "1.0.0" else value.trim()
    }

    private fun compactTransition(oldBase: String?, oldDlc: String?, newBase: String?, newDlc: String?): Array<String> {
        val sourceBase = normalizeVersion(oldBase)
        val sourceDlc = normalizeVersion(oldDlc)
        val targetBase = normalizeVersion(if (newBase.isNullOrBlank()) sourceBase else newBase)
        val targetDlc = normalizeVersion(if (newDlc.isNullOrBlank()) sourceDlc else newDlc)
        val baseChanged = sourceBase != targetBase
        val dlcChanged = sourceDlc != targetDlc
        if (baseChanged && dlcChanged) {
            return arrayOf("$sourceBase+$sourceDlc", "$targetBase+$targetDlc")
        }
        if (baseChanged) {
            return arrayOf(sourceBase, targetBase)
        }
        if (dlcChanged) {
            return arrayOf(sourceDlc, targetDlc)
        }
        return arrayOf("$sourceBase+$sourceDlc", "$targetBase+$targetDlc")
    }

    private fun resolveAlarmTip(item: DlcItem?, lang: String): String {
        if (item == null || item.getTooltips().isEmpty()) {
            return ""
        }
        if (item.getTooltips().containsKey(lang)) {
            return item.getTooltips()[lang] ?: ""
        }
        if (item.getTooltips().containsKey("en_us")) {
            return item.getTooltips()["en_us"] ?: ""
        }
        return item.getTooltips().values.firstOrNull() ?: ""
    }
}
