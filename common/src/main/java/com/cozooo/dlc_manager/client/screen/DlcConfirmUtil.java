package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller;
import com.cozooo.dlc_manager.client.pack.DlcPackPackage;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public final class DlcConfirmUtil {
    private DlcConfirmUtil() {
    }

    public static Component buildConflictLine(String left, String right) {
        return Component.translatable("dlc_manager.batch.conflicts_line_prefix",
                DlcTextFormatter.parse(left), DlcTextFormatter.parse(right))
            .append(Component.translatable("dlc_manager.conflict.word").withStyle(ChatFormatting.DARK_RED));
    }

    public static Component buildDependencyLine(String item, String dependency) {
        return Component.translatable("dlc_manager.batch.dependencies_line",
            DlcTextFormatter.parse(item), DlcTextFormatter.parse(dependency));
    }

    public static Component buildConflictPromptMessage(String list) {
        return Component.translatable("dlc_manager.conflict.prompt_prefix", DlcTextFormatter.parse(list))
            .append(Component.translatable("dlc_manager.conflict.word").withStyle(ChatFormatting.DARK_RED));
    }

    public static Component buildDependencyPromptMessage(String list) {
        return Component.translatable("dlc_manager.dependency.prompt", DlcTextFormatter.parse(list));
    }

    public static Component buildConflictMessage(List<DlcRuntimeState.ConflictLine> lines) {
        List<Component> components = new ArrayList<>();
        for (DlcRuntimeState.ConflictLine line : lines) {
            components.add(buildConflictLine(
                DlcDisplayNameUtil.resolveDisplayName(line.getLeftName()),
                DlcDisplayNameUtil.resolveDisplayName(line.getRightName())));
        }
        return joinLines(components);
    }

    public static Component buildDependencyMessage(List<DlcRuntimeState.DependencyLine> lines) {
        List<Component> components = new ArrayList<>();
        for (DlcRuntimeState.DependencyLine line : lines) {
            components.add(buildDependencyLine(
                DlcDisplayNameUtil.resolveDisplayName(line.getItemName()),
                DlcDisplayNameUtil.resolveDisplayName(line.getDependencyName())));
        }
        return joinLines(components);
    }

    public static Component buildPackRuntimeChangeMessage(DlcPackPackage pack, DlcPackInstaller.PrecheckResult precheck) {
        String packName = pack.metadata.resolveDisplayName(Minecraft.getInstance().options.languageCode);
        List<Component> lines = new ArrayList<>();
        for (String identifier : precheck.getDlcToEnable()) {
            lines.add(buildDependencyLine(packName, DlcDisplayNameUtil.resolveDisplayName(identifier)));
        }
        for (String identifier : precheck.getPresetsToEnable()) {
            lines.add(buildDependencyLine(packName, DlcDisplayNameUtil.resolveDisplayName(identifier)));
        }
        for (String identifier : precheck.getDlcToDisable()) {
            lines.add(buildConflictLine(packName, DlcDisplayNameUtil.resolveDisplayName(identifier)));
        }
        for (String identifier : precheck.getPresetsToDisable()) {
            lines.add(buildConflictLine(packName, DlcDisplayNameUtil.resolveDisplayName(identifier)));
        }
        return joinLines(lines);
    }

    public static MutableComponent joinLines(List<Component> lines) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.append(Component.literal("\n"));
            }
            result = result.append(lines.get(i));
        }
        return result;
    }
}
