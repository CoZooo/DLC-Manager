package com.cozooo.dlc_manager.ui.packs;

import java.util.Map;

public record DlcFxPackRowState(
    boolean showMoveUp,
    boolean showMoveDown,
    boolean showHomepageHint,
    boolean showIssueAction,
    boolean showSponsorAction,
    boolean canSwitchVersion,
    boolean currentVersion
) {
    public static DlcFxPackRowState of(int rowIndex,
                                       int rowCount,
                                       int versionCount,
                                       Map<String, String> links,
                                       boolean currentVersion) {
        return new DlcFxPackRowState(
            rowIndex > 0,
            rowIndex >= 0 && rowIndex < rowCount - 1,
            hasLink(links, "homepage"),
            hasLink(links, "issue"),
            hasLink(links, "sponsor"),
            versionCount > 1,
            currentVersion
        );
    }

    public static boolean hasLink(Map<String, String> links, String key) {
        if (links == null || key == null || key.isBlank()) {
            return false;
        }
        String value = links.get(key);
        return value != null && !value.isBlank();
    }
}
