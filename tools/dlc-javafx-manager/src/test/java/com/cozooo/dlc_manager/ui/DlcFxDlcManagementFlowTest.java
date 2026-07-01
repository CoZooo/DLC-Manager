package com.cozooo.dlc_manager.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxDlcManagementFlowTest {
    @Test
    void enablePromptsConflictsBeforeDependenciesLikeLocalFlow() {
        Item target = item("target", false);
        Item conflict = item("conflict", true);
        Item dependency = item("dependency", false);
        FakeAdapter adapter = new FakeAdapter(List.of(target, conflict, dependency));
        adapter.conflicts.put("target", List.of(conflictLine(target, conflict)));
        adapter.dependencies.put("target", new DlcFxDlcManagementFlow.DependencyBatch<>(
                List.of(dependencyLine(target, dependency)),
                List.of(dependency)
        ));
        FakePrompts prompts = new FakePrompts();
        DlcFxDlcManagementFlow<Item> flow = new DlcFxDlcManagementFlow<>(adapter, prompts);

        flow.enable(List.of(target));

        assertEquals(List.of("conflict"), prompts.events);
        assertEquals(List.of(), adapter.applied);
    }

    @Test
    void enablingDependenciesChecksConflictsBeforeContinuing() {
        Item target = item("target", false);
        Item dependency = item("dependency", false);
        Item conflict = item("conflict", true);
        FakeAdapter adapter = new FakeAdapter(List.of(target, dependency, conflict));
        adapter.dependencies.put("target", new DlcFxDlcManagementFlow.DependencyBatch<>(
                List.of(dependencyLine(target, dependency)),
                List.of(dependency)
        ));
        adapter.conflicts.put("dependency,target", List.of(conflictLine(dependency, conflict)));
        FakePrompts prompts = new FakePrompts();
        DlcFxDlcManagementFlow<Item> flow = new DlcFxDlcManagementFlow<>(adapter, prompts);

        flow.enable(List.of(target));
        prompts.confirmDependency();

        assertEquals(List.of("dependency", "conflict"), prompts.events);
        assertEquals(List.of(), adapter.applied);
    }

    @Test
    void conflictSkipContinuesWithNonConflictingTargets() {
        Item skipped = item("skipped", false);
        Item kept = item("kept", false);
        Item conflict = item("conflict", true);
        FakeAdapter adapter = new FakeAdapter(List.of(skipped, kept, conflict));
        adapter.conflicts.put("skipped,kept", List.of(conflictLine(skipped, conflict)));
        FakePrompts prompts = new FakePrompts();
        DlcFxDlcManagementFlow<Item> flow = new DlcFxDlcManagementFlow<>(adapter, prompts);

        flow.enable(List.of(skipped, kept));
        prompts.skipConflict();

        assertEquals(List.of("conflict"), prompts.events);
        assertEquals(List.of("enable:kept"), adapter.applied);
    }

    @Test
    void alarmIsConfirmedBeforeApplying() {
        Item target = item("target", false);
        target.alarm = true;
        FakeAdapter adapter = new FakeAdapter(List.of(target));
        FakePrompts prompts = new FakePrompts();
        DlcFxDlcManagementFlow<Item> flow = new DlcFxDlcManagementFlow<>(adapter, prompts);

        flow.enable(List.of(target));

        assertEquals(List.of("alarm"), prompts.events);
        assertEquals(List.of(), adapter.applied);

        prompts.confirmAlarm();

        assertEquals(List.of("enable:target"), adapter.applied);
    }

    @Test
    void disableUsesSharedDisablePlan() {
        Item target = item("target", true);
        Item dependent = item("dependent", true);
        FakeAdapter adapter = new FakeAdapter(List.of(target, dependent));
        adapter.disablePlan = List.of(target, dependent);
        FakePrompts prompts = new FakePrompts();
        DlcFxDlcManagementFlow<Item> flow = new DlcFxDlcManagementFlow<>(adapter, prompts);

        flow.disable(List.of(target));

        assertEquals(List.of("disable:target", "disable:dependent"), adapter.applied);
    }

    private static Item item(String id, boolean enabled) {
        Item item = new Item();
        item.id = id;
        item.identifier = id;
        item.name = id;
        item.enabled = enabled;
        return item;
    }

    private static DlcFxDlcManagementFlow.ConflictLine<Item> conflictLine(Item left, Item right) {
        return new DlcFxDlcManagementFlow.ConflictLine<>(left, right, left.identifier, right.identifier, left.name, right.name);
    }

    private static DlcFxDlcManagementFlow.DependencyLine<Item> dependencyLine(Item item, Item dependency) {
        return new DlcFxDlcManagementFlow.DependencyLine<>(item, dependency, item.name, dependency.name);
    }

    private static final class Item {
        String id;
        String identifier;
        String name;
        boolean enabled;
        boolean alarm;
    }

    private static final class FakeAdapter implements DlcFxDlcManagementFlow.Adapter<Item> {
        private final List<Item> items;
        private final Map<String, List<DlcFxDlcManagementFlow.ConflictLine<Item>>> conflicts = new LinkedHashMap<>();
        private final Map<String, DlcFxDlcManagementFlow.DependencyBatch<Item>> dependencies = new LinkedHashMap<>();
        private final List<String> applied = new ArrayList<>();
        private List<Item> disablePlan = List.of();

        private FakeAdapter(List<Item> items) {
            this.items = items;
        }

        @Override
        public List<Item> allItems() {
            return items;
        }

        @Override
        public String id(Item item) {
            return item == null ? "" : item.id;
        }

        @Override
        public String identifier(Item item) {
            return item == null ? "" : item.identifier;
        }

        @Override
        public String displayName(Item item) {
            return item == null ? "" : item.name;
        }

        @Override
        public boolean alarm(Item item) {
            return item != null && item.alarm;
        }

        @Override
        public DlcFxDlcManagementFlow.ConflictBatch<Item> collectConflicts(List<Item> targets) {
            return new DlcFxDlcManagementFlow.ConflictBatch<>(conflicts.getOrDefault(key(targets), List.of()));
        }

        @Override
        public DlcFxDlcManagementFlow.DependencyBatch<Item> collectMissingDependencies(List<Item> targets) {
            return dependencies.getOrDefault(key(targets), DlcFxDlcManagementFlow.DependencyBatch.empty());
        }

        @Override
        public List<Item> planDisable(List<Item> targets) {
            return disablePlan;
        }

        @Override
        public void applyEnabled(List<Item> items, boolean enabled) {
            for (Item item : items) {
                item.enabled = enabled;
                applied.add((enabled ? "enable:" : "disable:") + item.id);
            }
        }

        private String key(Collection<Item> source) {
            List<String> ids = new ArrayList<>();
            for (Item item : source == null ? List.<Item>of() : source) {
                ids.add(id(item));
            }
            return String.join(",", ids);
        }
    }

    private static final class FakePrompts implements DlcFxDlcManagementFlow.Prompts<Item> {
        private final List<String> events = new ArrayList<>();
        private Runnable conflictConfirm = () -> {};
        private Runnable dependencyConfirm = () -> {};
        private Runnable alarmConfirm = () -> {};

        @Override
        public void showConflict(List<DlcFxDlcManagementFlow.ConflictLine<Item>> lines, Runnable skipConflictingTargets) {
            events.add("conflict");
            conflictConfirm = skipConflictingTargets;
        }

        @Override
        public void showDependencies(List<DlcFxDlcManagementFlow.DependencyLine<Item>> lines, Runnable enableDependencies) {
            events.add("dependency");
            dependencyConfirm = enableDependencies;
        }

        @Override
        public void showAlarm(List<Item> items, Runnable confirm) {
            events.add("alarm");
            alarmConfirm = confirm;
        }

        private void skipConflict() {
            conflictConfirm.run();
        }

        private void confirmDependency() {
            dependencyConfirm.run();
        }

        private void confirmAlarm() {
            alarmConfirm.run();
        }
    }
}
