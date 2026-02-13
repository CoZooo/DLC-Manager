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

        public UpdateRow(DlcUpdateMeta meta, Action action, Type type, String oldVersion, String newVersion) {
            this.meta = meta;
            this.action = action;
            this.type = type;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
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
                selfHistory.newVersion
            ));
        }

        for (DlcUpdateMeta meta : visibleOnly(history)) {
            String oldVersion = meta == null ? null : meta.getDlcOld();
            String newVersion = meta == null ? null : meta.getDlcNew();
            Action action = rolledBackMode && meta != null
                && DlcUpdateService.compareVersions(newVersion, currentVersion) > 0
                ? Action.REUPDATE
                : Action.ROLLBACK;
            rows.add(new UpdateRow(meta, action, Type.UPDATE, oldVersion, newVersion));
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
                meta.setChangelog(pkg.getManifest().getChangelog());
                rows.add(new UpdateRow(meta, Action.UPDATE, Type.UPDATE, meta.getDlcOld(), meta.getDlcNew()));
                hasVisibleUpdates = true;
            }
        }

        return new PlanResult(rows, hasVisibleUpdates);
    }

    public static PlanResult planServerRows(List<DlcUpdateMeta> history,
                                            List<DlcServerUpdateInfo> updates,
                                            boolean rolledBackMode,
                                            String currentVersion) {
        List<UpdateRow> rows = new ArrayList<>();
        boolean hasVisibleUpdates = false;

        for (DlcUpdateMeta meta : visibleOnly(history)) {
            String oldVersion = meta == null ? null : meta.getDlcOld();
            String newVersion = meta == null ? null : meta.getDlcNew();
            Action action = rolledBackMode && meta != null
                && DlcUpdateService.compareVersions(newVersion, currentVersion) > 0
                ? Action.REUPDATE
                : Action.ROLLBACK;
            rows.add(new UpdateRow(meta, action, Type.UPDATE, oldVersion, newVersion));
        }

        if (!rolledBackMode && updates != null) {
            for (DlcServerUpdateInfo info : updates) {
                if (info == null || info.hidden) {
                    continue;
                }
                DlcUpdateMeta meta = new DlcUpdateMeta();
                meta.setDlcOld(info.dlcOld);
                meta.setDlcNew(info.dlcNew);
                meta.setChangelog(info.changelog);
                rows.add(new UpdateRow(meta, Action.UPDATE, Type.UPDATE, info.dlcOld, info.dlcNew));
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
}
