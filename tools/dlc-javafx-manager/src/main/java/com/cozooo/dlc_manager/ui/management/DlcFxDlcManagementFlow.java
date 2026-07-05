package com.cozooo.dlc_manager.ui.management;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DlcFxDlcManagementFlow<T> {
    public interface Adapter<T> {
        List<T> allItems();
        String id(T item);
        String identifier(T item);
        String displayName(T item);
        boolean alarm(T item);
        ConflictBatch<T> collectConflicts(List<T> targets);
        DependencyBatch<T> collectMissingDependencies(List<T> targets);
        List<T> planDisable(List<T> targets);
        void applyEnabled(List<T> items, boolean enabled);
        default String conflictFilterKey(T item) {
            String identifier = identifier(item);
            return identifier == null || identifier.isBlank() ? id(item) : identifier;
        }
        default void clearSelection() {
        }
        default void refreshRows() {
        }
    }

    public interface Prompts<T> {
        void showConflict(List<ConflictLine<T>> lines, Runnable skipConflictingTargets);
        void showDependencies(List<DependencyLine<T>> lines, Runnable enableDependencies);
        void showAlarm(List<T> items, Runnable confirm);
    }

    public record ConflictBatch<T>(List<ConflictLine<T>> lines) {
        public ConflictBatch {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        public static <T> ConflictBatch<T> empty() {
            return new ConflictBatch<>(List.of());
        }
    }

    public record DependencyBatch<T>(List<DependencyLine<T>> lines, List<T> itemsToEnable) {
        public DependencyBatch {
            lines = lines == null ? List.of() : List.copyOf(lines);
            itemsToEnable = itemsToEnable == null ? List.of() : List.copyOf(itemsToEnable);
        }

        public static <T> DependencyBatch<T> empty() {
            return new DependencyBatch<>(List.of(), List.of());
        }
    }

    public record ConflictLine<T>(T left, T right, String leftKey, String rightKey, String leftDisplayName, String rightDisplayName) {
    }

    public record DependencyLine<T>(T item, T dependency, String itemDisplayName, String dependencyDisplayName) {
    }

    private final Adapter<T> adapter;
    private final Prompts<T> prompts;

    public DlcFxDlcManagementFlow(Adapter<T> adapter, Prompts<T> prompts) {
        this.adapter = adapter;
        this.prompts = prompts;
    }

    public void enable(Collection<T> targets) {
        List<T> active = compact(targets);
        if (active.isEmpty()) {
            return;
        }
        ConflictBatch<T> conflicts = adapter.collectConflicts(active);
        if (!conflicts.lines().isEmpty()) {
            prompts.showConflict(conflicts.lines(), () -> {
                List<T> filtered = filterConflictTargets(active, conflicts.lines());
                if (!filtered.isEmpty()) {
                    continueEnable(filtered);
                }
            });
            return;
        }
        DependencyBatch<T> dependencies = adapter.collectMissingDependencies(active);
        if (!dependencies.lines().isEmpty()) {
            showDependencyPrompt(dependencies, active);
            return;
        }
        if (active.stream().anyMatch(adapter::alarm)) {
            prompts.showAlarm(active, () -> apply(active, true));
            return;
        }
        apply(active, true);
    }

    public void disable(Collection<T> targets) {
        List<T> active = compact(targets);
        if (active.isEmpty()) {
            return;
        }
        apply(adapter.planDisable(active), false);
    }

    private void continueEnable(List<T> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        DependencyBatch<T> dependencies = adapter.collectMissingDependencies(targets);
        if (!dependencies.lines().isEmpty()) {
            showDependencyPrompt(dependencies, targets);
            return;
        }
        List<T> alarmItems = targets.stream().filter(adapter::alarm).toList();
        if (!alarmItems.isEmpty()) {
            prompts.showAlarm(alarmItems, () -> apply(targets, true));
            return;
        }
        apply(targets, true);
    }

    private void showDependencyPrompt(DependencyBatch<T> dependencies, List<T> targets) {
        prompts.showDependencies(dependencies.lines(), () -> {
            List<T> planned = new ArrayList<>();
            planned.addAll(dependencies.itemsToEnable());
            planned.addAll(targets);
            ConflictBatch<T> plannedConflicts = adapter.collectConflicts(planned);
            if (!plannedConflicts.lines().isEmpty()) {
                prompts.showConflict(plannedConflicts.lines(), () -> {
                    List<T> filtered = filterConflictTargets(planned, plannedConflicts.lines());
                    if (!filtered.isEmpty()) {
                        continueEnable(filtered);
                    }
                });
                return;
            }
            continueEnable(planned);
        });
    }

    private void apply(List<T> items, boolean enabled) {
        List<T> active = compact(items);
        if (active.isEmpty()) {
            return;
        }
        adapter.applyEnabled(active, enabled);
        adapter.clearSelection();
        adapter.refreshRows();
    }

    private List<T> filterConflictTargets(List<T> targets, List<ConflictLine<T>> lines) {
        if (targets == null || targets.isEmpty() || lines == null || lines.isEmpty()) {
            return targets == null ? List.of() : targets;
        }
        java.util.Set<String> targetKeys = new java.util.HashSet<>();
        for (T item : targets) {
            String key = adapter.conflictFilterKey(item);
            if (key != null && !key.isBlank()) {
                targetKeys.add(key);
            }
        }
        java.util.Set<String> skipped = new java.util.HashSet<>();
        for (ConflictLine<T> line : lines) {
            if (line == null) {
                continue;
            }
            if (line.leftKey() != null && targetKeys.contains(line.leftKey())) {
                skipped.add(line.leftKey());
            }
            if (line.rightKey() != null && targetKeys.contains(line.rightKey())) {
                skipped.add(line.rightKey());
            }
        }
        if (skipped.isEmpty()) {
            return targets;
        }
        List<T> filtered = new ArrayList<>();
        for (T item : targets) {
            String key = adapter.conflictFilterKey(item);
            if (key == null || !skipped.contains(key)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private List<T> compact(Collection<T> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<T> out = new ArrayList<>();
        for (T item : source) {
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }
}
