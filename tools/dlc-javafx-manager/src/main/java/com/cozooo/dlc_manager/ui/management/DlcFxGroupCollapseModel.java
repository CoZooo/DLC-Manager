package com.cozooo.dlc_manager.ui.management;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DlcFxGroupCollapseModel {
    private final Set<String> collapsedGroups = new LinkedHashSet<>();
    private final Set<String> animatingGroups = new LinkedHashSet<>();

    public void clear() {
        collapsedGroups.clear();
        animatingGroups.clear();
    }

    public boolean isCollapsed(String groupKey) {
        return groupKey != null && collapsedGroups.contains(groupKey);
    }

    public boolean isAnimating(String groupKey) {
        return groupKey != null && animatingGroups.contains(groupKey);
    }

    public boolean beginCollapse(String groupKey) {
        if (groupKey == null) {
            return false;
        }
        if (animatingGroups.contains(groupKey)) {
            return false;
        }
        animatingGroups.add(groupKey);
        collapsedGroups.add(groupKey);
        return true;
    }

    public boolean beginExpand(String groupKey) {
        if (groupKey == null) {
            return false;
        }
        if (animatingGroups.contains(groupKey)) {
            return false;
        }
        animatingGroups.add(groupKey);
        collapsedGroups.remove(groupKey);
        return true;
    }

    public void finishAnimation(String groupKey) {
        if (groupKey != null) {
            animatingGroups.remove(groupKey);
        }
    }
}
