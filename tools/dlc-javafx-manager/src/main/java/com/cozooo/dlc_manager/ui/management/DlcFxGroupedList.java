package com.cozooo.dlc_manager.ui.management;
import com.cozooo.dlc_manager.ui.common.DlcFxSelectableList;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DlcFxGroupedList<T> {
    private final DlcFxSelectableList<T> selectableList;
    private final Set<String> collapsedGroups = new LinkedHashSet<>();
    private final Set<String> animatingGroups = new LinkedHashSet<>();

    public DlcFxGroupedList() {
        this.selectableList = null;
    }

    public DlcFxGroupedList(DlcFxSelectableList<T> selectableList) {
        this.selectableList = selectableList;
    }

    public DlcFxSelectableList<T> selectableList() {
        return selectableList;
    }

    public boolean isCollapsed(String key) {
        return key != null && collapsedGroups.contains(key);
    }

    public boolean isAnimating(String key) {
        return key != null && animatingGroups.contains(key);
    }

    public void clear() {
        collapsedGroups.clear();
        animatingGroups.clear();
    }

    public boolean toggle(String key) {
        if (key == null || animatingGroups.contains(key)) {
            return false;
        }
        if (collapsedGroups.contains(key)) {
            collapsedGroups.remove(key);
        } else {
            collapsedGroups.add(key);
        }
        return true;
    }

    public void playRowReveal(Node node) {
        if (node == null) {
            return;
        }
        node.setOpacity(0);
        node.setScaleY(0.96);
        FadeTransition fade = new FadeTransition(Duration.millis(150), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition scale = new ScaleTransition(Duration.millis(150), node);
        scale.setFromY(0.96);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
        scale.play();
    }

    public Set<String> collapsedGroups() {
        return collapsedGroups;
    }

    public List<T> filterVisibleRows(Iterable<T> rows,
                                     Predicate<T> isGroupRow,
                                     Function<T, String> groupKeyResolver) {
        List<T> visible = new ArrayList<>();
        String currentGroup = null;
        boolean currentCollapsed = false;
        if (rows == null) {
            return visible;
        }
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            if (isGroupRow != null && isGroupRow.test(row)) {
                currentGroup = groupKeyResolver == null ? null : groupKeyResolver.apply(row);
                currentCollapsed = isCollapsed(currentGroup);
                visible.add(row);
            } else if (!currentCollapsed) {
                visible.add(row);
            }
        }
        return visible;
    }
}
