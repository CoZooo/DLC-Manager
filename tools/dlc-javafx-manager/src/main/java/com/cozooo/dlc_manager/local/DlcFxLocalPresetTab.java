package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.app.DlcFxManagerApp;
import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.preset.DlcFxPresetFlow;
import com.cozooo.dlc_manager.ui.preset.DlcFxPresetShell;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class DlcFxLocalPresetTab {
    private final LocalPresetAdapter adapter = new LocalPresetAdapter();
    private final DlcFxPresetShell<DlcPresetEntry> shell = new DlcFxPresetShell<>(adapter);
    private boolean gameDirSelected;
    private Runnable capturePresetSnapshot = () -> {};
    private Supplier<Map<String, Boolean>> snapshotDlcState = Map::of;
    private Function<Map<String, Boolean>, Boolean> syncPresetDlcChanges =
            ignored -> syncIndexedDlcOverridesAndHasPendingChanges();

    public DlcFxLocalPresetTab(DlcFxPlatformProvider provider) {
    }

    public void setPresetSnapshotActions(Runnable capturePresetSnapshot) {
        this.capturePresetSnapshot = capturePresetSnapshot == null ? () -> {} : capturePresetSnapshot;
    }

    public void setPresetStateActions(Runnable capturePresetSnapshot,
                                      Supplier<Map<String, Boolean>> snapshotDlcState,
                                      Function<Map<String, Boolean>, Boolean> syncPresetDlcChanges) {
        this.capturePresetSnapshot = capturePresetSnapshot == null ? () -> {} : capturePresetSnapshot;
        this.snapshotDlcState = snapshotDlcState == null ? Map::of : snapshotDlcState;
        this.syncPresetDlcChanges = syncPresetDlcChanges == null
                ? ignored -> syncIndexedDlcOverridesAndHasPendingChanges()
                : syncPresetDlcChanges;
    }

    public Tab build() {
        return shell.build();
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        shell.onContextChanged(DlcFxI18n.tr("dlc_manager.local.game_dir_changed"));
        shell.refreshAsync();
    }

    public void onTabSelected() {
        shell.onTabSelected();
    }

    public boolean hasPendingChanges() {
        return shell.hasPendingChanges();
    }

    public void applyPendingChanges() {
        shell.applyPendingChanges();
    }

    public void cancelPendingChanges() {
        shell.cancelPendingChanges();
    }

    static boolean syncIndexedDlcOverridesAndHasPendingChanges() {
        boolean pending = false;
        for (DlcItem item : DlcRuntimeState.getIndexedItems()) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            if (override != null) {
                item.setEnabled(override);
            }
            if (DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                pending = true;
            }
        }
        return pending;
    }

    private DlcPresetStatus displayStatus(DlcPresetEntry item, DlcFxPresetFlow<DlcPresetEntry> flow) {
        if (item == null) {
            return DlcPresetStatus.OFF;
        }
        if (!flow.isPending(item)) {
            if (!item.getStatus().isEnabled()) {
                return DlcPresetStatus.OFF;
            }
            return statusFromCurrentDlcState(item);
        }
        if (!flow.effectiveEnabled(item)) {
            return DlcPresetStatus.OFF;
        }
        return statusFromPendingEnable(item);
    }

    private DlcPresetStatus statusFromCurrentDlcState(DlcPresetEntry item) {
        Set<String> identifiers = DlcPresetRuntimeState.resolveEffectiveEnableIdentifiers(item.getId());
        if (identifiers.isEmpty()) {
            return DlcPresetStatus.ON;
        }
        boolean allEnabled = true;
        for (String identifier : identifiers) {
            DlcItem dlc = DlcRuntimeState.getItemByIdentifier(identifier);
            boolean enabled = dlc != null && effectiveDlcEnabled(dlc);
            allEnabled &= enabled;
        }
        return allEnabled ? DlcPresetStatus.ON : DlcPresetStatus.PART;
    }

    private DlcPresetStatus statusFromPendingEnable(DlcPresetEntry item) {
        Set<String> identifiers = DlcPresetRuntimeState.resolveEffectiveEnableIdentifiers(item.getId());
        if (identifiers.isEmpty()) {
            return DlcPresetStatus.ON;
        }
        for (String identifier : identifiers) {
            if (DlcRuntimeState.getItemByIdentifier(identifier) == null) {
                return DlcPresetStatus.PART;
            }
        }
        return DlcPresetStatus.ON;
    }

    private boolean effectiveDlcEnabled(DlcItem item) {
        Boolean override = DlcRuntimeState.getOverride(item.getId());
        return override != null ? override : item.isEnabled();
    }

    private List<String> pendingBlockerNames(DlcPresetEntry target,
                                             List<DlcPresetEntry> entries,
                                             DlcFxPresetFlow<DlcPresetEntry> flow) {
        if (target == null) {
            return List.of();
        }
        Set<String> targetEnable = DlcPresetRuntimeState.resolveEffectiveEnableIdentifiers(target.getId());
        if (targetEnable.isEmpty()) {
            return List.of();
        }
        Set<String> targetConflicts = DlcPresetRuntimeState.resolveEffectiveConflictIdentifiers(target.getId());
        List<String> blockers = new ArrayList<>();
        String lang = currentLang();
        for (DlcPresetEntry entry : entries == null ? List.<DlcPresetEntry>of() : entries) {
            if (entry == null || entry == target || !flow.effectiveEnabled(entry)) {
                continue;
            }
            Set<String> enabled = DlcPresetRuntimeState.resolveEffectiveEnableIdentifiers(entry.getId());
            Set<String> conflicts = DlcPresetRuntimeState.resolveEffectiveConflictIdentifiers(entry.getId());
            if (!intersects(targetEnable, conflicts) && !intersects(targetConflicts, enabled)) {
                continue;
            }
            blockers.add(entry.resolveDisplayName(lang));
        }
        if (blockers.isEmpty() && DlcPresetRuntimeState.isPresetBlocked(target.getId())) {
            blockers.addAll(DlcPresetRuntimeState.getPresetBlockerNames(target.getId(), lang));
        }
        return blockers;
    }

    private static boolean intersects(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String currentLang() {
        String lang = CrossPlatformRuntime.languageCode();
        return lang == null || lang.isBlank() ? "en_us" : lang;
    }

    private void showGoToDlcManagementPrompt(javafx.scene.Scene scene) {
        DlcFxModalOverlay.show(
                scene,
                DlcFxI18n.tr("dlc_manager.preset.title"),
                DlcFxI18n.tr("dlc_manager.preset.apply_hint"),
                () -> {
                    DlcFxManagerApp app = DlcFxManagerApp.instance();
                    if (app != null) {
                        app.showLocalDlcManagementTab();
                    }
                },
                DlcFxI18n.tr("dlc_manager.preset.goto_dlc"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.preset.goto_later")
        );
    }

    private final class LocalPresetAdapter implements DlcFxPresetShell.Adapter<DlcPresetEntry> {
        @Override
        public boolean available() {
            return gameDirSelected && DlcConfig.getDlcRoot() != null;
        }

        @Override
        public String unavailableStatusText() {
            return DlcFxI18n.tr("dlc_manager.status.select_game_dir");
        }

        @Override
        public List<DlcPresetEntry> load() {
            List<DlcPresetEntry> loaded = DlcPresetRuntimeState.getPresets();
            if (loaded.isEmpty()) {
                DlcPresetRuntimeState.reload();
                loaded = DlcPresetRuntimeState.getPresets();
            }
            return loaded;
        }

        @Override
        public DlcFxPresetShell.ApplyResult<DlcPresetEntry> apply(Map<String, Boolean> pending) {
            capturePresetSnapshot.run();
            Map<String, Boolean> beforeDlcState = snapshotDlcState.get();
            for (Map.Entry<String, Boolean> entry : pending.entrySet()) {
                DlcPresetRuntimeState.setPresetEnabled(entry.getKey(), entry.getValue());
            }
            DlcPresetRuntimeState.applyOverridesToRuntime();
            DlcPresetRuntimeState.syncStatusFromRuntime();
            boolean dlcChanged = syncPresetDlcChanges.apply(beforeDlcState);
            return new DlcFxPresetShell.ApplyResult<>(
                    DlcPresetRuntimeState.getPresets(),
                    dlcChanged,
                    DlcFxI18n.tr("dlc_manager.status.apply_done")
            );
        }

        @Override
        public String id(DlcPresetEntry entry) {
            return entry == null ? "" : entry.getId();
        }

        @Override
        public boolean enabled(DlcPresetEntry entry) {
            return entry != null && entry.getStatus().isEnabled();
        }

        @Override
        public boolean alarm(DlcPresetEntry entry) {
            return entry != null && entry.isAlarm();
        }

        @Override
        public String displayName(DlcPresetEntry entry, String lang) {
            return entry == null ? "" : entry.resolveDisplayName(lang);
        }

        @Override
        public String tip(DlcPresetEntry entry, String lang) {
            return entry == null ? "" : entry.resolveTip(lang);
        }

        @Override
        public DlcPresetStatus displayStatus(DlcPresetEntry entry, DlcFxPresetFlow<DlcPresetEntry> flow) {
            return DlcFxLocalPresetTab.this.displayStatus(entry, flow);
        }

        @Override
        public List<String> blockerNames(DlcPresetEntry entry,
                                         List<DlcPresetEntry> entries,
                                         DlcFxPresetFlow<DlcPresetEntry> flow,
                                         String lang) {
            return pendingBlockerNames(entry, entries, flow);
        }

        @Override
        public void afterApplied(DlcFxPresetShell.ApplyResult<DlcPresetEntry> result, javafx.scene.Scene scene) {
            if (result != null && result.dlcChanged()) {
                showGoToDlcManagementPrompt(scene);
            }
        }
    }
}
