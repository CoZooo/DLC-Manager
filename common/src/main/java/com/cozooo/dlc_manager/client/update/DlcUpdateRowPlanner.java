package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.server.DlcServerUpdateInfo;
import java.util.ArrayList;
import java.util.List;

public final class DlcUpdateRowPlanner {
    public enum Action {
        NONE,
        ROLLBACK,
        REUPDATE,
        UPDATE,
        MANAGER_ROLLBACK
    }

    public enum Type {
        UPDATE,
        MANAGER
    }

    public static final class UpdateRow {
        public final DlcUpdateMeta meta;
        public final Action action;
        public final Type type;
        public final String oldVersion;
        public final String newVersion;
        public final String rollbackTarget;
        public final String displayOldVersion;
        public final String displayNewVersion;

        public UpdateRow(DlcUpdateMeta meta, Action action, Type type, String oldVersion, String newVersion,
                         String rollbackTarget, String displayOldVersion, String displayNewVersion) {
            this.meta = meta;
            this.action = action;
            this.type = type;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.rollbackTarget = rollbackTarget;
            this.displayOldVersion = displayOldVersion;
            this.displayNewVersion = displayNewVersion;
        }
    }

    public static final class PlanResult {
        public final List<UpdateRow> rows;
        public final boolean hasVisibleUpdates;

        public PlanResult(List<UpdateRow> rows, boolean hasVisibleUpdates) {
            this.rows = rows;
            this.hasVisibleUpdates = hasVisibleUpdates;
        }
    }

    private DlcUpdateRowPlanner() {
    }

    public static PlanResult planClientRows(List<DlcUpdateMeta> history,
                                            DlcSelfUpdateService.SelfUpdateHistory selfHistory,
                                            DlcUpdateService.UpdateCheckResult updateCheck,
                                            boolean rolledBackMode,
                                            String currentBaseVersion,
                                            String currentVersion) {
        List<UpdateRow> rows = new ArrayList<>();
        boolean hasVisibleUpdates = false;

        if (selfHistory != null
            && selfHistory.oldVersion != null && !selfHistory.oldVersion.isBlank()
            && selfHistory.newVersion != null && !selfHistory.newVersion.isBlank()) {
            DlcUpdateMeta managerMeta = new DlcUpdateMeta();
            managerMeta.setDlcOld(selfHistory.oldVersion);
            managerMeta.setDlcNew(selfHistory.newVersion);
            rows.add(new UpdateRow(
                managerMeta,
                Action.MANAGER_ROLLBACK,
                Type.MANAGER,
                selfHistory.oldVersion,
                selfHistory.newVersion,
                selfHistory.oldVersion,
                selfHistory.oldVersion,
                selfHistory.newVersion
            ));
        }

        String currentBase = normalizeVersion(currentBaseVersion);
        String currentDlc = normalizeVersion(currentVersion);
        for (DlcUpdateMeta meta : visibleOnly(history)) {
            String oldVersion = meta == null ? null : meta.getDlcOld();
            String newVersion = meta == null ? null : meta.getDlcNew();
            String oldBase = meta == null ? null : meta.getBaseOld();
            String newBase = meta == null ? null : meta.getBaseNew();
            String targetBase = newBase == null || newBase.isBlank() ? oldBase : newBase;
            String targetDlc = newVersion == null || newVersion.isBlank() ? oldVersion : newVersion;
            String[] display = compactTransition(oldBase, oldVersion, targetBase, targetDlc);
            String rollbackTarget = composePair(oldBase, oldVersion);
            String displayOld = display[0];
            String displayNew = display[1];
            Action action = rolledBackMode && meta != null
                && compareState(
                    targetBase,
                    targetDlc,
                    currentBase,
                    currentDlc
                ) > 0
                ? Action.REUPDATE
                : Action.ROLLBACK;
            rows.add(new UpdateRow(meta, action, Type.UPDATE, oldVersion, newVersion, rollbackTarget, displayOld, displayNew));
        }

        if (!rolledBackMode && updateCheck != null && updateCheck.hasUpdates()) {
            for (DlcUpdatePackage pkg : updateCheck.getUpdates()) {
                if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                    continue;
                }
                if (pkg.getManifest().isHidden()) {
                    continue;
                }
                DlcUpdateMeta meta = new DlcUpdateMeta();
                meta.setDlcOld(pkg.getManifest().getVersion().getDlcOld());
                meta.setDlcNew(pkg.getManifest().getVersion().getDlcNew());
                meta.setBaseOld(pkg.getManifest().getVersion().getBase());
                String targetBase = pkg.getManifest().getVersion().getBaseNew();
                if (targetBase != null && !targetBase.isBlank()) {
                    meta.setBaseNew(targetBase);
                }
                meta.setChangelog(pkg.getManifest().getChangelog());
                String updateBase = meta.getBaseNew() == null || meta.getBaseNew().isBlank() ? meta.getBaseOld() : meta.getBaseNew();
                String updateDlc = meta.getDlcNew() == null || meta.getDlcNew().isBlank() ? meta.getDlcOld() : meta.getDlcNew();
                String[] display = compactTransition(meta.getBaseOld(), meta.getDlcOld(), updateBase, updateDlc);
                String rollbackTarget = composePair(meta.getBaseOld(), meta.getDlcOld());
                String displayOld = display[0];
                String displayNew = display[1];
                rows.add(new UpdateRow(meta, Action.UPDATE, Type.UPDATE, meta.getDlcOld(), meta.getDlcNew(), rollbackTarget, displayOld, displayNew));
                hasVisibleUpdates = true;
            }
        }

        return new PlanResult(rows, hasVisibleUpdates);
    }

    public static PlanResult planServerRows(List<DlcUpdateMeta> history,
                                            List<DlcServerUpdateInfo> updates,
                                            boolean rolledBackMode,
                                            String currentBaseVersion,
                                            String currentVersion) {
        List<UpdateRow> rows = new ArrayList<>();
        boolean hasVisibleUpdates = false;

        String currentBase = normalizeVersion(currentBaseVersion);
        String currentDlc = normalizeVersion(currentVersion);
        for (DlcUpdateMeta meta : visibleOnly(history)) {
            String oldVersion = meta == null ? null : meta.getDlcOld();
            String newVersion = meta == null ? null : meta.getDlcNew();
            String oldBase = meta == null ? null : meta.getBaseOld();
            String newBase = meta == null ? null : meta.getBaseNew();
            String targetBase = newBase == null || newBase.isBlank() ? oldBase : newBase;
            String targetDlc = newVersion == null || newVersion.isBlank() ? oldVersion : newVersion;
            String[] display = compactTransition(oldBase, oldVersion, targetBase, targetDlc);
            String rollbackTarget = composePair(oldBase, oldVersion);
            String displayOld = display[0];
            String displayNew = display[1];
            Action action = rolledBackMode && meta != null
                && compareState(
                    targetBase,
                    targetDlc,
                    currentBase,
                    currentDlc
                ) > 0
                ? Action.REUPDATE
                : Action.ROLLBACK;
            rows.add(new UpdateRow(meta, action, Type.UPDATE, oldVersion, newVersion, rollbackTarget, displayOld, displayNew));
        }

        if (!rolledBackMode && updates != null) {
            for (DlcServerUpdateInfo info : updates) {
                if (info == null || info.hidden) {
                    continue;
                }
                DlcUpdateMeta meta = new DlcUpdateMeta();
                meta.setDlcOld(info.dlcOld);
                meta.setDlcNew(info.dlcNew);
                meta.setBaseOld(info.baseOld);
                if (info.baseNew != null && !info.baseNew.isBlank()) {
                    meta.setBaseNew(info.baseNew);
                }
                meta.setChangelog(info.changelog);
                String updateBase = info.baseNew == null || info.baseNew.isBlank() ? info.baseOld : info.baseNew;
                String updateDlc = info.dlcNew == null || info.dlcNew.isBlank() ? info.dlcOld : info.dlcNew;
                String[] display = compactTransition(info.baseOld, info.dlcOld, updateBase, updateDlc);
                String rollbackTarget = composePair(info.baseOld, info.dlcOld);
                String displayOld = display[0];
                String displayNew = display[1];
                rows.add(new UpdateRow(meta, Action.UPDATE, Type.UPDATE, info.dlcOld, info.dlcNew, rollbackTarget, displayOld, displayNew));
                hasVisibleUpdates = true;
            }
        }

        return new PlanResult(rows, hasVisibleUpdates);
    }

    public static List<DlcUpdateMeta> visibleOnly(List<DlcUpdateMeta> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<DlcUpdateMeta> out = new ArrayList<>();
        for (DlcUpdateMeta meta : input) {
            if (meta != null && !meta.isHidden()) {
                out.add(meta);
            }
        }
        return out;
    }

    private static int compareState(String leftBase, String leftDlc, String rightBase, String rightDlc) {
        int baseCompare = DlcUpdateService.compareVersions(normalizeVersion(leftBase), normalizeVersion(rightBase));
        if (baseCompare != 0) {
            return baseCompare;
        }
        return DlcUpdateService.compareVersions(normalizeVersion(leftDlc), normalizeVersion(rightDlc));
    }

    private static String composePair(String base, String dlc) {
        return normalizeVersion(base) + "+" + normalizeVersion(dlc);
    }

    private static String[] compactTransition(String oldBase, String oldDlc, String newBase, String newDlc) {
        String sourceBase = normalizeVersion(oldBase);
        String sourceDlc = normalizeVersion(oldDlc);
        String targetBase = normalizeVersion(newBase);
        String targetDlc = normalizeVersion(newDlc);
        boolean baseChanged = !sourceBase.equals(targetBase);
        boolean dlcChanged = !sourceDlc.equals(targetDlc);
        if (baseChanged && dlcChanged) {
            return new String[] {composePair(sourceBase, sourceDlc), composePair(targetBase, targetDlc)};
        }
        if (baseChanged) {
            return new String[] {sourceBase, targetBase};
        }
        if (dlcChanged) {
            return new String[] {sourceDlc, targetDlc};
        }
        return new String[] {composePair(sourceBase, sourceDlc), composePair(targetBase, targetDlc)};
    }

    private static String normalizeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
    }
}
