package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collection;
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

    public static Component buildConflictPromptMessage(List<String> names) {
        return buildConflictPromptMessage(joinNames(names));
    }

    public static Component buildDependencyPromptMessage(String list) {
        return Component.translatable("dlc_manager.dependency.prompt", DlcTextFormatter.parse(list));
    }

    public static Component buildDependencyPromptMessage(List<String> names) {
        return buildDependencyPromptMessage(joinNames(names));
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

    public static Component buildPackUpdateIncompatibleConfirmMessage(DlcPackUpdateGuard.CheckResult check) {
        List<String> lines = new ArrayList<>();
        if (check != null) {
            for (DlcPackUpdateGuard.PackStatus status : check.incompatiblePacks) {
                if (status == null) {
                    continue;
                }
                String reason = status.reasons == null || status.reasons.isEmpty()
                    ? ""
                    : " (" + String.join("; ", status.reasons) + ")";
                lines.add(status.identifier() + reason);
            }
        }
        return Component.translatable("dlc_manager.pack.update_incompatible_confirm", String.join("\n", lines));
    }

    public static String describePackBlockingPrecheck(DlcPackInstaller.PrecheckResult precheck) {
        List<String> messages = new ArrayList<>();
        if (!precheck.getIncompatibleVersions().isEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.incompatible_versions", joinLimited(precheck.getIncompatibleVersions())).getString());
        }
        if (!precheck.getMissingDependencies().isEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.missing_dependencies", joinLimited(precheck.getMissingDependencies())).getString());
        }
        if (!precheck.getDlcToEnable().isEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.enable_dlc", joinLimited(precheck.getDlcToEnable())).getString());
        }
        if (!precheck.getPresetsToEnable().isEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.enable_presets", joinLimited(precheck.getPresetsToEnable())).getString());
        }
        if (!precheck.getDlcToDisable().isEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.disable_dlc", joinLimited(precheck.getDlcToDisable())).getString());
        }
        if (!precheck.getPresetsToDisable().isEmpty()) {
            messages.add(Component.translatable("dlc_manager.pack.disable_presets", joinLimited(precheck.getPresetsToDisable())).getString());
        }
        if (!messages.isEmpty()) {
            return String.join("\n", messages);
        }
        return "unknown";
    }

    public static Component confirmYes() {
        return Component.translatable("dlc_manager.confirm_yes");
    }

    public static Component confirmNo() {
        return Component.translatable("dlc_manager.confirm_no");
    }

    public static Component buildUpdateManagerTitle() {
        return Component.translatable("dlc_manager.update.manager_title");
    }

    public static Component buildUpdateConfirmApplyMessage() {
        return Component.translatable("dlc_manager.update.confirm_apply");
    }

    public static Component buildUpdateConfirmRollbackMessage(String targetVersion) {
        return Component.translatable("dlc_manager.update.confirm_rollback", targetVersion);
    }

    public static Component buildUpdateConfirmReapplyMessage(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        String[] compact = compactTransition(baseOld, dlcOld, baseNew, dlcNew);
        return Component.translatable("dlc_manager.update.confirm_reapply", compact[0], compact[1]);
    }

    public static Component buildUpdateConfirmReapplyMessage(String baseOld, String dlcOld, String baseNew, String dlcNew, String changelog) {
        Component base = buildUpdateConfirmReapplyMessage(baseOld, dlcOld, baseNew, dlcNew);
        if (changelog == null || changelog.isBlank()) {
            return base;
        }
        return Component.empty()
            .append(base)
            .append("\n\n")
            .append(Component.translatable("dlc_manager.update.changelog_tooltip_title"))
            .append("\n")
            .append(DlcMarkdownRenderer.renderMarkdownComponent(changelog));
    }

    public static Component buildHeaderConfirmMessage(boolean enable, Component activeFilterLabel) {
        if (activeFilterLabel == null) {
            String key = enable ? "dlc_manager.confirm.enable_all" : "dlc_manager.confirm.disable_all";
            return Component.translatable(key);
        }
        String key = enable ? "dlc_manager.confirm.enable_all_type" : "dlc_manager.confirm.disable_all_type";
        return Component.translatable(key, activeFilterLabel);
    }

    public static String buildBatchAlarmTip(Collection<DlcItem> alarmItems) {
        if (alarmItems == null || alarmItems.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String lang = Minecraft.getInstance().options.languageCode;
        for (DlcItem item : alarmItems) {
            if (item == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(DlcDisplayNameUtil.resolveDisplayName(item));
            String tip = resolveAlarmTip(item, lang);
            if (tip != null && !tip.isBlank()) {
                sb.append("\n").append(tip);
            }
        }
        return sb.toString();
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

    private static String joinNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    private static String joinLimited(List<String> values) {
        int limit = Math.min(values.size(), 6);
        String joined = String.join(", ", values.subList(0, limit));
        if (values.size() > limit) {
            joined += ", ...";
        }
        return joined;
    }

    private static String normalizeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
    }

    private static String[] compactTransition(String oldBase, String oldDlc, String newBase, String newDlc) {
        String sourceBase = normalizeVersion(oldBase);
        String sourceDlc = normalizeVersion(oldDlc);
        String targetBase = normalizeVersion(newBase == null || newBase.isBlank() ? sourceBase : newBase);
        String targetDlc = normalizeVersion(newDlc == null || newDlc.isBlank() ? sourceDlc : newDlc);
        boolean baseChanged = !sourceBase.equals(targetBase);
        boolean dlcChanged = !sourceDlc.equals(targetDlc);
        if (baseChanged && dlcChanged) {
            return new String[] {sourceBase + "+" + sourceDlc, targetBase + "+" + targetDlc};
        }
        if (baseChanged) {
            return new String[] {sourceBase, targetBase};
        }
        if (dlcChanged) {
            return new String[] {sourceDlc, targetDlc};
        }
        return new String[] {sourceBase + "+" + sourceDlc, targetBase + "+" + targetDlc};
    }

    private static String resolveAlarmTip(DlcItem item, String lang) {
        if (item == null || item.getTooltips().isEmpty()) {
            return "";
        }
        if (item.getTooltips().containsKey(lang)) {
            return item.getTooltips().get(lang);
        }
        if (item.getTooltips().containsKey("en_us")) {
            return item.getTooltips().get("en_us");
        }
        return item.getTooltips().values().stream().findFirst().orElse("");
    }
}
