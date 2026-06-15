package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller
import com.cozooo.dlc_manager.client.pack.DlcPackPackage
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
    fun buildDependencyPromptMessage(list: String): Component {
        return Component.translatable("dlc_manager.dependency.prompt", DlcTextFormatter.parse(list))
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

    @JvmStatic
    fun buildPackRuntimeChangeMessage(pack: DlcPackPackage, precheck: DlcPackInstaller.PrecheckResult): Component {
        val packName = pack.metadata.resolveDisplayName(Minecraft.getInstance().options.languageCode)
        val lines = ArrayList<Component>()
        for (identifier in precheck.dlcToEnable) {
            lines.add(buildDependencyLine(packName, DlcDisplayNameUtil.resolveDisplayName(identifier)))
        }
        for (identifier in precheck.presetsToEnable) {
            lines.add(buildDependencyLine(packName, DlcDisplayNameUtil.resolveDisplayName(identifier)))
        }
        for (identifier in precheck.dlcToDisable) {
            lines.add(buildConflictLine(packName, DlcDisplayNameUtil.resolveDisplayName(identifier)))
        }
        for (identifier in precheck.presetsToDisable) {
            lines.add(buildConflictLine(packName, DlcDisplayNameUtil.resolveDisplayName(identifier)))
        }
        return joinLines(lines)
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
}
