package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import java.nio.file.Path;
import java.util.List;

public final class DlcUpdateRuntime {
    private static boolean promptedThisSession = false;

    public static final class ClientUpdateSnapshot {
        public final String baseVersion;
        public final String currentVersion;
        public final String rollbackFrom;
        public final boolean rolledBackMode;
        public final DlcUpdateService.UpdateCheckResult updateCheck;
        public final List<DlcUpdateMeta> history;

        private ClientUpdateSnapshot(String baseVersion,
                                     String currentVersion,
                                     String rollbackFrom,
                                     boolean rolledBackMode,
                                     DlcUpdateService.UpdateCheckResult updateCheck,
                                     List<DlcUpdateMeta> history) {
            this.baseVersion = baseVersion == null ? "" : baseVersion;
            this.currentVersion = currentVersion == null ? "" : currentVersion;
            this.rollbackFrom = rollbackFrom;
            this.rolledBackMode = rolledBackMode;
            this.updateCheck = updateCheck;
            this.history = history == null ? List.of() : history;
        }
    }

    private DlcUpdateRuntime() {
    }

    public static DlcUpdateService.UpdateCheckResult pollTitlePromptCandidate() {
        if (promptedThisSession) {
            return null;
        }
        DlcUpdateService.UpdateCheckResult result = DlcUpdateService.checkForUpdates();
        if (!result.hasUpdates()) {
            return null;
        }
        if (DlcUpdateService.isPromptSkippedForUpdate(result)) {
            return null;
        }
        promptedThisSession = true;
        return result;
    }

    public static boolean isPrompted() {
        return promptedThisSession;
    }

    public static void markPrompted() {
        promptedThisSession = true;
    }

    public static String buildLocalizedChangelog(DlcUpdateService.UpdateCheckResult result) {
        if (result == null) {
            return "";
        }
        return DlcUpdateService.buildChangelogText(result.getUpdates(), CrossPlatformRuntime.languageCode());
    }

    public static boolean isUpdateManagerEnabled() {
        return DlcPathConfig.isUpdateManagerOn();
    }

    public static ClientUpdateSnapshot loadClientSnapshot(Path dlcRoot) {
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        String currentVersion = version == null ? "" : version.getDlc();
        String baseVersion = version == null ? "" : version.getBase();
        String rollbackFrom = version == null ? null : version.getRollbackFrom();
        boolean rolledBackMode = rollbackFrom != null
            && !rollbackFrom.isBlank()
            && !rollbackFrom.equals(currentVersion);
        List<DlcUpdateMeta> baseHistory = DlcUpdateRowPlanner.visibleOnly(DlcUpdateService.listUpdateHistory());
        if (rolledBackMode) {
            List<DlcUpdateMeta> rolledHistory = DlcUpdateRowPlanner.visibleOnly(
                DlcUpdateService.listUpdateHistoryFrom(rollbackFrom)
            );
            if (!rolledHistory.isEmpty()) {
                baseHistory = rolledHistory;
            }
        }
        DlcUpdateService.UpdateCheckResult updateCheck = DlcUpdateService.checkForUpdates();
        return new ClientUpdateSnapshot(
            baseVersion,
            currentVersion,
            rollbackFrom,
            rolledBackMode,
            updateCheck,
            baseHistory
        );
    }
}
