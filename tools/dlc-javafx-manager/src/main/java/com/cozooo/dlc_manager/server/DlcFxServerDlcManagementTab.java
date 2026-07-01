package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.tools.externalsync.ApplyOutcome;
import com.cozooo.dlc_manager.tools.externalsync.SyncModels;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcManagementFlow;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcManagementListModel;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcManagementShell;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcTogglePlanner;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxPageActions;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcFxServerDlcManagementTab {

    private final DlcFxServerConnection conn;
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button refreshBtn = DlcFxPageActions.primary(DlcFxI18n.tr("dlc_manager.local.scan_dlc"), null);
    private final RemoteDlcAdapter adapter = new RemoteDlcAdapter();
    private DlcFxDlcManagementShell<SyncModels.ServerItem> shell;
    private DlcFxDlcManagementFlow<SyncModels.ServerItem> flow;
    private boolean loadedForCurrentConnection;

    public DlcFxServerDlcManagementTab(DlcFxServerConnection conn) {
        this.conn = conn;
        conn.connectedProperty().addListener((obs, oldValue, newValue) -> loadedForCurrentConnection = false);
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage(DlcFxI18n.tr("dlc_manager.status.disconnected"));

        flow = new DlcFxDlcManagementFlow<>(new RemoteDlcFlowAdapter(), new RemoteDlcPrompts());
        shell = new DlcFxDlcManagementShell<>(
                adapter,
                statusLabel,
                progressBar,
                this::applyChanges,
                this::cancelChanges,
                items -> flow.enable(items),
                items -> flow.disable(new ArrayList<>(items))
        );
        shell.setAvailable(conn.connectedProperty().get());
        conn.connectedProperty().addListener((obs, oldValue, connected) -> shell.setAvailable(connected));

        refreshBtn.disableProperty().bind(conn.connectedProperty().not());
        refreshBtn.setOnAction(e -> refreshList());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBox = new HBox(10, refreshBtn, statusLabel, spacer, shell.selectionActions());
        topBox.setAlignment(Pos.CENTER_LEFT);
        page.setTop(DlcFxToolbar.vertical(topBox));

        VBox.setVgrow(shell.listFrame(), Priority.ALWAYS);

        VBox center = new VBox(10, shell.filterBar(), shell.listFrame());
        page.setCenter(page.section(center));
        page.setBottom(shell.bottomPanel());
        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.remote_dlc"), page.root());
    }

    public void onTabSelected() {
        if (!loadedForCurrentConnection && conn.connectedProperty().get()) {
            refreshList();
        }
    }

    private void refreshList() {
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
        shell.setBusy(true);
        conn.runAsync(f -> f.fetchList(), info -> {
            shell.setBusy(false);
            loadedForCurrentConnection = true;
            adapter.resetBaseline(info.items);
            shell.setItems(info.items);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.common.count_items", info.items.size()));
        }, err -> {
            shell.setBusy(false);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", DlcFxServerConnection.safeMessage(err)));
        });
    }

    private void applyChanges() {
        List<SyncModels.ServerItem> changed = new ArrayList<>(shell.changedItems());
        if (changed.isEmpty()) {
            return;
        }
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.applying"));
        shell.setBusy(true);
        conn.runAsync(f -> {
            for (SyncModels.ServerItem item : changed) {
                f.setEnabled(item.id, item.enabled != null && item.enabled);
            }
            return f.applyWithMessages();
        }, outcome -> {
            shell.setBusy(false);
            adapter.commitBaseline(changed);
            shell.commitCurrentState();
            statusLabel.setText(outcome.requiresRestart()
                    ? DlcFxI18n.tr("dlc_manager.remote.applied_restart")
                    : DlcFxI18n.tr("dlc_manager.remote.applied"));
        }, err -> {
            shell.setBusy(false);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_failed", DlcFxServerConnection.safeMessage(err)));
        });
    }

    private void cancelChanges() {
        shell.resetChanges();
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.cancel_done"));
    }

    private String buildConflictMessage(List<DlcFxDlcManagementFlow.ConflictLine<SyncModels.ServerItem>> conflicts) {
        List<String> lines = new ArrayList<>();
        for (DlcFxDlcManagementFlow.ConflictLine<SyncModels.ServerItem> conflict : conflicts) {
            if (conflict != null) {
                lines.add(conflict.leftDisplayName() + " <-> " + conflict.rightDisplayName());
            }
        }
        return String.join("\n", lines);
    }

    private String buildDependencyMessage(List<DlcFxDlcManagementFlow.DependencyLine<SyncModels.ServerItem>> dependencies) {
        List<String> lines = new ArrayList<>();
        for (DlcFxDlcManagementFlow.DependencyLine<SyncModels.ServerItem> dependency : dependencies) {
            if (dependency != null) {
                lines.add(dependency.itemDisplayName() + " -> " + dependency.dependencyDisplayName());
            }
        }
        return String.join("\n", lines);
    }

    private javafx.scene.Scene overlayScene() {
        return shell == null || shell.listFrame() == null ? null : shell.listFrame().getScene();
    }

    private List<DlcFxDlcTogglePlanner.Item> plannerItems(List<SyncModels.ServerItem> source) {
        List<DlcFxDlcTogglePlanner.Item> out = new ArrayList<>();
        for (SyncModels.ServerItem item : source == null ? List.<SyncModels.ServerItem>of() : source) {
            if (item == null) {
                continue;
            }
            out.add(new DlcFxDlcTogglePlanner.Item(
                    item.id,
                    item.identifier,
                    item.enabled != null && item.enabled,
                    item.dependencies,
                    item.conflicts,
                    item.allowEnableByAll,
                    item.alarm,
                    false));
        }
        return out;
    }

    private List<String> itemIds(Iterable<SyncModels.ServerItem> items) {
        List<String> ids = new ArrayList<>();
        if (items != null) {
            for (SyncModels.ServerItem item : items) {
                if (item != null && item.id != null && !item.id.isBlank()) {
                    ids.add(item.id);
                }
            }
        }
        return ids;
    }

    private List<SyncModels.ServerItem> itemsByIds(List<String> ids) {
        Map<String, SyncModels.ServerItem> byId = new LinkedHashMap<>();
        for (SyncModels.ServerItem item : shell.items()) {
            if (item != null && item.id != null) {
                byId.put(item.id, item);
            }
        }
        List<SyncModels.ServerItem> out = new ArrayList<>();
        for (String id : ids == null ? List.<String>of() : ids) {
            SyncModels.ServerItem item = byId.get(id);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    private String displayName(String id) {
        for (SyncModels.ServerItem item : shell.items()) {
            if (item != null && id != null && id.equals(item.id)) {
                return item.name == null || item.name.isBlank() ? id : item.name;
            }
        }
        return id == null ? "" : id;
    }

    private SyncModels.ServerItem itemById(String id) {
        if (id == null || shell == null) {
            return null;
        }
        for (SyncModels.ServerItem item : shell.items()) {
            if (item != null && id.equals(item.id)) {
                return item;
            }
        }
        return null;
    }

    private String buildAlarmTip(List<SyncModels.ServerItem> items) {
        List<String> lines = new ArrayList<>();
        for (SyncModels.ServerItem item : items == null ? List.<SyncModels.ServerItem>of() : items) {
            if (item != null) {
                lines.add(adapter.displayName(item));
            }
        }
        return String.join("\n\n", lines);
    }

    private static final class RemoteDlcAdapter implements DlcFxDlcManagementShell.Adapter<SyncModels.ServerItem> {
        private final Map<String, Boolean> baseline = new LinkedHashMap<>();

        void resetBaseline(List<SyncModels.ServerItem> items) {
            baseline.clear();
            if (items == null) {
                return;
            }
            for (SyncModels.ServerItem item : items) {
                if (item != null) {
                    baseline.put(id(item), item.enabled != null && item.enabled);
                }
            }
        }

        void commitBaseline(List<SyncModels.ServerItem> items) {
            if (items == null) {
                return;
            }
            for (SyncModels.ServerItem item : items) {
                if (item != null) {
                    baseline.put(id(item), item.enabled != null && item.enabled);
                }
            }
        }

        @Override
        public String id(SyncModels.ServerItem item) {
            return item == null || item.id == null ? "" : item.id;
        }

        @Override
        public String displayName(SyncModels.ServerItem item) {
            if (item == null) {
                return "";
            }
            return item.name == null || item.name.isBlank() ? item.id : item.name;
        }

        @Override
        public String typeText(SyncModels.ServerItem item) {
            return item == null || item.type == null || item.type.isBlank() ? "?" : item.type;
        }

        @Override
        public DlcType type(SyncModels.ServerItem item) {
            if (item == null || item.type == null || item.type.isBlank()) {
                return null;
            }
            try {
                return DlcType.valueOf(item.type.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        @Override
        public String folder(SyncModels.ServerItem item) {
            if (item == null || item.path == null || item.path.isBlank()) {
                return DlcFxDlcManagementListModel.ROOT_GROUP;
            }
            String normalized = item.path.replace('\\', '/');
            int slash = normalized.lastIndexOf('/');
            if (slash <= 0) {
                return DlcFxDlcManagementListModel.ROOT_GROUP;
            }
            return normalized.substring(0, slash);
        }

        @Override
        public boolean baselineEnabled(SyncModels.ServerItem item) {
            Boolean value = item == null ? null : baseline.get(id(item));
            return value != null && value;
        }

        @Override
        public boolean effectiveEnabled(SyncModels.ServerItem item) {
            return item != null && item.enabled != null && item.enabled;
        }

        @Override
        public void setEffectiveEnabled(SyncModels.ServerItem item, boolean enabled) {
            if (item != null) {
                item.enabled = enabled;
            }
        }

        @Override
        public String tooltip(SyncModels.ServerItem item) {
            if (item == null) {
                return "";
            }
            StringBuilder tip = new StringBuilder();
            tip.append(DlcFxI18n.tr("dlc_manager.tooltip.id")).append(item.id);
            if (item.identifier != null) {
                tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.identifier")).append(item.identifier);
            }
            if (item.path != null) {
                tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.path")).append(item.path);
            }
            return tip.toString();
        }
    }

    private final class RemoteDlcFlowAdapter implements DlcFxDlcManagementFlow.Adapter<SyncModels.ServerItem> {
        @Override
        public List<SyncModels.ServerItem> allItems() {
            return shell == null ? List.of() : List.copyOf(shell.items());
        }

        @Override
        public String id(SyncModels.ServerItem item) {
            return adapter.id(item);
        }

        @Override
        public String identifier(SyncModels.ServerItem item) {
            return item == null || item.identifier == null ? "" : item.identifier;
        }

        @Override
        public String displayName(SyncModels.ServerItem item) {
            return adapter.displayName(item);
        }

        @Override
        public boolean alarm(SyncModels.ServerItem item) {
            return item != null && item.alarm;
        }

        @Override
        public DlcFxDlcManagementFlow.ConflictBatch<SyncModels.ServerItem> collectConflicts(List<SyncModels.ServerItem> targets) {
            List<DlcFxDlcTogglePlanner.Conflict> conflicts = DlcFxDlcTogglePlanner.planEnable(
                    plannerItems(allItems()),
                    itemIds(targets)
            ).conflicts();
            List<DlcFxDlcManagementFlow.ConflictLine<SyncModels.ServerItem>> lines = new ArrayList<>();
            for (DlcFxDlcTogglePlanner.Conflict conflict : conflicts) {
                if (conflict == null) {
                    continue;
                }
                SyncModels.ServerItem left = itemById(conflict.leftId());
                SyncModels.ServerItem right = itemById(conflict.rightId());
                lines.add(new DlcFxDlcManagementFlow.ConflictLine<>(
                        left,
                        right,
                        conflict.leftId(),
                        conflict.rightId(),
                        DlcFxServerDlcManagementTab.this.displayName(conflict.leftId()),
                        DlcFxServerDlcManagementTab.this.displayName(conflict.rightId())
                ));
            }
            return new DlcFxDlcManagementFlow.ConflictBatch<>(lines);
        }

        @Override
        public DlcFxDlcManagementFlow.DependencyBatch<SyncModels.ServerItem> collectMissingDependencies(List<SyncModels.ServerItem> targets) {
            DlcFxDlcTogglePlanner.EnablePlan plan = DlcFxDlcTogglePlanner.planEnable(
                    plannerItems(allItems()),
                    itemIds(targets)
            );
            List<DlcFxDlcManagementFlow.DependencyLine<SyncModels.ServerItem>> lines = new ArrayList<>();
            Set<String> dependencyIds = new LinkedHashSet<>();
            for (DlcFxDlcTogglePlanner.Dependency dependency : plan.dependencies()) {
                if (dependency == null) {
                    continue;
                }
                dependencyIds.add(dependency.dependencyId());
                lines.add(new DlcFxDlcManagementFlow.DependencyLine<>(
                        itemById(dependency.itemId()),
                        itemById(dependency.dependencyId()),
                        DlcFxServerDlcManagementTab.this.displayName(dependency.itemId()),
                        DlcFxServerDlcManagementTab.this.displayName(dependency.dependencyId())
                ));
            }
            return new DlcFxDlcManagementFlow.DependencyBatch<>(lines, itemsByIds(new ArrayList<>(dependencyIds)));
        }

        @Override
        public List<SyncModels.ServerItem> planDisable(List<SyncModels.ServerItem> targets) {
            return itemsByIds(DlcFxDlcTogglePlanner.planDisable(plannerItems(allItems()), itemIds(targets)));
        }

        @Override
        public void applyEnabled(List<SyncModels.ServerItem> items, boolean enabled) {
            for (SyncModels.ServerItem item : items == null ? List.<SyncModels.ServerItem>of() : items) {
                adapter.setEffectiveEnabled(item, enabled);
            }
        }

        @Override
        public String conflictFilterKey(SyncModels.ServerItem item) {
            return id(item);
        }

        @Override
        public void clearSelection() {
            if (shell != null) {
                shell.model().clearSelection();
            }
        }

        @Override
        public void refreshRows() {
            if (shell != null) {
                shell.refreshRows();
            }
        }
    }

    private final class RemoteDlcPrompts implements DlcFxDlcManagementFlow.Prompts<SyncModels.ServerItem> {
        @Override
        public void showConflict(List<DlcFxDlcManagementFlow.ConflictLine<SyncModels.ServerItem>> lines, Runnable skipConflictingTargets) {
            DlcFxModalOverlay.show(
                    overlayScene(),
                    DlcFxI18n.tr("dlc_manager.batch.conflicts_title"),
                    DlcFxRichTextRenderer.formatted(buildConflictMessage(lines)),
                    skipConflictingTargets,
                    DlcFxI18n.tr("dlc_manager.batch.conflicts_skip_enable"),
                    () -> {},
                    DlcFxI18n.tr("dlc_manager.common.cancel")
            );
        }

        @Override
        public void showDependencies(List<DlcFxDlcManagementFlow.DependencyLine<SyncModels.ServerItem>> lines, Runnable enableDependencies) {
            DlcFxModalOverlay.show(
                    overlayScene(),
                    DlcFxI18n.tr("dlc_manager.batch.dependencies_title"),
                    DlcFxRichTextRenderer.formatted(buildDependencyMessage(lines)),
                    enableDependencies,
                    DlcFxI18n.tr("dlc_manager.batch.enable_deps", DlcFxI18n.tr("dlc_manager.scope.group")),
                    () -> {},
                    DlcFxI18n.tr("dlc_manager.common.cancel")
            );
        }

        @Override
        public void showAlarm(List<SyncModels.ServerItem> items, Runnable confirm) {
            DlcFxModalOverlay.show(
                    overlayScene(),
                    DlcFxI18n.tr("dlc_manager.alarm.title"),
                    DlcFxRichTextRenderer.formatted(buildAlarmTip(items)),
                    confirm,
                    DlcFxI18n.tr("dlc_manager.confirm_yes"),
                    () -> {},
                    DlcFxI18n.tr("dlc_manager.confirm_no")
            );
        }
    }
}
