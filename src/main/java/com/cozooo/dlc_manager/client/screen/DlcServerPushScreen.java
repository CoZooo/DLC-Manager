package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.*;
import com.cozooo.dlc_manager.client.sync.DlcSyncAdapter;
import com.cozooo.dlc_manager.sync.*;
import com.cozooo.dlc_manager.sync.SyncModel.*;
import com.cozooo.dlc_manager.platform.DlcTextCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DlcServerPushScreen extends DlcFilePageScreen {
    private final DlcServerEntry entry;
    private List<Item> local = List.of(), remote = List.of();
    private List<Row> rows = List.of(), filteredRows = List.of();
    private final Set<String> selected = new HashSet<>();
    private Mode mode = Mode.CONFIG;
    private Category category = Category.ALL;
    private Direction direction = Direction.PUSH;
    private boolean states = true, loaded, settingsSelected;
    private volatile boolean busy;
    private volatile String message = "";
    private volatile double fraction = -1;
    private volatile SyncClient active;
    private volatile AutoCloseable connecting;
    private volatile boolean cancelRequested;
    private volatile DlcServerSession previewSession;
    private volatile SyncClient rollbackClient;
    private volatile String failureReason;
    private boolean syncing;
    private DlcUiButton executeButton;
    private com.sighs.apricityui.init.Element results;
    private Category renderedCategory = Category.ALL;
    private final Map<Category, DlcUiScroll.State> scrolls = new EnumMap<>(Category.class);

    public DlcServerPushScreen(Screen parent, DlcServerEntry entry) {
        super(DlcUiPage.SERVER_PUSH, parent, DlcTextCompat.translatable("dlc_manager.sync.title"));
        this.entry = entry;
    }
    @Override protected void buildPage() {
        footerLeading.setAttribute("style", "display:flex");
        action(footerLeading, tr("dlc_manager.sync.mode_" + mode.name().toLowerCase(Locale.ROOT)), () -> { mode = mode == Mode.FILES ? Mode.CONFIG : Mode.FILES; replan(); }).setEnabled(!busy);
        action(toolbar, tr("dlc_manager.sync." + direction.name().toLowerCase(Locale.ROOT)), () -> { direction = direction == Direction.PUSH ? Direction.PULL : Direction.PUSH; replan(); }).setEnabled(!busy);
        if (SyncModel.hasStateDifferences(local, remote)) toggle(footerLeading, tr("dlc_manager.sync.states"), states, value -> { states = value; replan(); }).setEnabled(!busy);
        if (settingsRow() != null) toggle(footerLeading, tr("dlc_manager.sync.manager_config"), settingsSelected, value -> { settingsSelected = value; refreshPage(); }).setEnabled(!busy);
        action(toolbar, tr("dlc_manager.sync.compare"), this::compare).setEnabled(!busy);
        content.parentElement.setAttribute("class", "page-shell sync-page");
        var layout = node(content, "div", "sync-layout");
        var types = node(layout, "aside", "sync-types");
        for (Category type : Category.values()) {
            if (type == Category.MANAGER_CONFIG) continue;
            var button = action(types, tr("dlc_manager.sync.type_" + type.name().toLowerCase(Locale.ROOT)), () -> { category = type; refilter(); refreshPage(); });
            button.getElement().setAttribute("class", "button filter" + (category == type ? " active" : ""));
            button.setEnabled(!busy);
        }
        if (results != null) scrolls.put(renderedCategory, DlcUiScroll.capture(results));
        results = node(layout, "div", "sync-results");
        renderedCategory = category;
        DlcUiScroll.State savedScroll = scrolls.get(category);
        if (savedScroll != null) DlcUiScroll.restore(results, savedScroll);
        List<Row> shown = visibleRows();
        var list = fileList(loaded && shown.isEmpty() ? tr("dlc_manager.sync.empty") : tr("dlc_manager.sync.differences"));
        results.appendChild(list);
        showWhen(list, () -> !busy || syncing);
        if (!shown.isEmpty()) selectionRow(list, tr("dlc_manager.sync.select_all"), this::selectionState, this::selectVisible);
        for (Row row : shown) {
            String language = com.cozooo.dlc_manager.platform.DlcPlatformServices.languageCode();
            String name = SyncModel.displayName(row.source(), language);
            var line = selectionRow(list, name, () -> Boolean.toString(selected.contains(row.key())), () -> {
                if (!selected.add(row.key())) selected.remove(row.key());
            });
            tip(line, () -> new DlcUiTooltip.Content(name, SyncModel.tooltip(row.source(), language), List.of(), tr("dlc_manager.sync." + SyncModel.status(row, mode))));
            text(line, tr("dlc_manager.sync." + SyncModel.status(row, mode)), "row-description");
        }
        live(results, () -> message, "page-description");
        showWhen(progress(results, () -> fraction), () -> busy);
        if (rollbackClient != null && rollbackClient.canRollback()) {
            var undo = action(footer, tr("dlc_manager.sync.rollback"), this::rollback);
            undo.getElement().setAttribute("class", "button danger"); undo.setEnabled(!busy);
        }
        executeButton = primary(tr(syncing ? "dlc_manager.sync.cancel" : "dlc_manager.sync.execute"), () -> {
            if (syncing) cancel(); else confirm();
        });
        executeButton.getElement().setAttribute("class", syncing ? "button danger" : "button primary");
        updateExecuteButton();
    }
    private com.sighs.apricityui.init.Element selectionRow(com.sighs.apricityui.init.Element list, String name, java.util.function.Supplier<String> checked, Runnable change) {
        var line = fileRowLine(list, name, null, checked, () -> !busy, change, null);
        var checkbox = line.getChildren().get(0);
        var document = pageDocument;
        long generation = document.getRefreshGeneration();
        line.addEventListener("mousedown", event -> {
            if (event instanceof com.sighs.apricityui.event.MouseEvent mouse && mouse.button == 0 && !busy && Minecraft.getInstance().screen == this
                && document == getLinkedDocument() && document.isCurrentGeneration(generation)
                && !(event.target instanceof com.sighs.apricityui.init.Element target && checkbox.contains(target))) {
                event.preventDefault(); change.run(); updatePage();
            }
        });
        return line;
    }
    private Row settingsRow() { return rows.stream().filter(row -> SyncModel.category(row.source()) == Category.MANAGER_CONFIG).findFirst().orElse(null); }
    private void refilter() { filteredRows = rows.stream().filter(row -> SyncModel.category(row.source()) != Category.MANAGER_CONFIG && (category == Category.ALL || SyncModel.category(row.source()) == category)).toList(); }
    private List<Row> visibleRows() { return filteredRows; }
    private List<Row> selectedRows() {
        List<Row> chosen = new ArrayList<>(visibleRows().stream().filter(row -> selected.contains(row.key())).toList());
        if (settingsSelected && settingsRow() != null) chosen.add(settingsRow());
        return chosen;
    }
    private String selectionState() {
        List<Row> shown = visibleRows(); long count = shown.stream().filter(row -> selected.contains(row.key())).count();
        return count == 0 ? "false" : count == shown.size() ? "true" : "mixed";
    }
    private void selectVisible() {
        List<Row> shown = visibleRows(); boolean all = shown.stream().allMatch(row -> selected.contains(row.key()));
        for (Row row : shown) { if (all) selected.remove(row.key()); else selected.add(row.key()); }
    }
    private void updateExecuteButton() {
        if (executeButton != null) executeButton.setEnabled(busy ? syncing && !cancelRequested : settingsSelected && settingsRow() != null || visibleRows().stream().anyMatch(row -> selected.contains(row.key())));
    }
    private SyncModel.Peer peer(DlcServerSession session) {
        return new SyncModel.Peer() {
            public com.google.gson.JsonObject call(com.google.gson.JsonObject request) throws java.io.IOException {
                try { return session.syncRequest(request); } catch (java.io.IOException error) { session.close(); throw error; }
            }
            public void close() throws java.io.IOException { session.close(); }
        };
    }
    private SyncModel.Peer control() {
        return request -> { try (DlcServerSession session = DlcServerSession.connect(entry, 10000)) { return peer(session).call(request); } };
    }
    private void compare() {
        if (busy) return;
        loaded = false; local = List.of(); remote = List.of(); rows = List.of(); filteredRows = List.of(); settingsSelected = false; selected.clear();
        run(() -> {
            List<Item> left, right;
            try (SyncEndpoint endpoint = new SyncEndpoint(new DlcSyncAdapter()); DlcServerSession session = connect(entry)) {
                previewSession = session;
                left = SyncClient.manifest(endpoint, () -> cancelRequested);
                right = SyncClient.manifest(peer(session), () -> cancelRequested);
            } finally { previewSession = null; }
            Minecraft.getInstance().execute(() -> { local = left; remote = right; loaded = true; message = ""; replan(); });
        });
    }
    private void replan() {
        rows = SyncModel.plan(local, remote, direction, states, mode);
        selected.clear(); settingsSelected = false; refilter();
        refreshPage();
    }
    private void confirm() {
        if (busy) return;
        List<Row> chosen = selectedRows();
        if (chosen.isEmpty()) return;
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) execute(chosen);
        }, DlcTextCompat.translatable("dlc_manager.sync.title"), DlcTextCompat.translatable("dlc_manager.sync.confirm", chosen.size()), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }
    private void execute(List<Row> chosen) {
        run(() -> {
            rollbackClient = null;
            try (SyncEndpoint endpoint = new SyncEndpoint(new DlcSyncAdapter()); DlcServerSession session = connect(entry)) {
                SyncClient client = new SyncClient(endpoint, peer(session), control()); active = client; rollbackClient = client;
                if (cancelRequested) client.cancel();
                Result result = client.execute(chosen, mode, states, (phase, resource, done, total) -> {
                    fraction = total > 0 && !phase.equals("restoring") ? done / (double) total : -1;
                    String amount = phase.equals("transferring") || phase.equals("downloading") ? com.cozooo.dlc_manager.core.DlcFileSize.formatProgress(done, total) : done + (total >= 0 ? "/" + total : "");
                    message = DlcTextCompat.translatable("dlc_manager.sync." + phase).getString() + " " + resource.substring(resource.lastIndexOf('/') + 1) + " " + amount;
                });
                message = DlcTextCompat.translatable(result.canceled() ? "dlc_manager.sync.canceled" : "dlc_manager.sync.finished", result.completed(), result.errors().size()).getString()
                    + (result.requiresRestart() ? " " + DlcTextCompat.translatable("dlc_manager.sync.restart").getString() : "") + "\n" + String.join("\n", result.succeeded().stream().map(key -> tr("dlc_manager.sync.succeeded_item", key)).toList()) + "\n" + String.join("\n", result.errors());
                if (chosen.stream().anyMatch(row -> SyncModel.category(row.source()) == Category.UPDATE && result.succeeded().contains(row.key()))) message += "\n" + tr("dlc_manager.sync.updates_ready");
                if (result.canceled()) return;
                if (!result.errors().isEmpty()) failureReason = String.join("\n", result.errors());
                List<Item> left = SyncClient.manifest(endpoint, () -> cancelRequested), right = SyncClient.manifest(peer(session), () -> cancelRequested);
                Minecraft.getInstance().execute(() -> { local = left; remote = right; replan(); });
            } finally { active = null; }
        }, true);
    }
    private void rollback() {
        SyncClient client = rollbackClient;
        if (busy || client == null || !client.canRollback()) return;
        run(() -> {
            message = tr("dlc_manager.sync.restoring");
            try (SyncEndpoint endpoint = new SyncEndpoint(new DlcSyncAdapter()); DlcServerSession session = connect(entry)) {
                client.rollback(endpoint, peer(session));
                List<Item> left = SyncClient.manifest(endpoint), right = SyncClient.manifest(peer(session));
                Minecraft.getInstance().execute(() -> { local = left; remote = right; replan(); });
            }
            message = tr("dlc_manager.sync.restored");
        });
    }
    private void showFailure(String reason) {
        Minecraft.getInstance().setScreen(new DlcSyncFailureScreen(restore -> {
            Minecraft.getInstance().setScreen(this);
            if (restore) rollback();
        }, reason));
    }
    private DlcServerSession connect(DlcServerEntry entry) throws java.io.IOException {
        return DlcServerSession.connect(entry, 120000, socket -> {
            connecting = socket;
            if (cancelRequested) try { socket.close(); } catch (Exception ignored) {}
        });
    }
    private void cancel() {
        if (!busy || cancelRequested) return;
        cancelRequested = true;
        if (active != null) { if (!active.cancel()) { cancelRequested = false; return; } message = tr("dlc_manager.sync.restoring"); fraction = -1; }
        else try { if (connecting != null) connecting.close(); } catch (Exception ignored) {}
        refreshPage();
    }

    @FunctionalInterface private interface Work { void run() throws Exception; }
    private void run(Work work) { run(work, false); }
    private void run(Work work, boolean synchronizing) {
        if (busy) return;
        failureReason = null; cancelRequested = false; syncing = synchronizing; busy = true; fraction = -1;
        message = tr("dlc_manager.sync.working"); refreshPage();
        CompletableFuture.runAsync(() -> {
            try { work.run(); }
            catch (Exception error) { message = cancelRequested && (error instanceof java.net.SocketException || SyncFailure.is(error, "canceled")) ? DlcTextCompat.translatable("dlc_manager.sync.canceled").getString() : error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); if (synchronizing && !cancelRequested) failureReason = failureReason == null ? message : failureReason + "\n" + message; }
            finally { Minecraft.getInstance().execute(() -> { busy = false; syncing = false; connecting = null; refreshPage(); if (failureReason != null) showFailure(failureReason); }); }
        });
    }
    @Override protected void updatePage() { updateExecuteButton(); super.updatePage(); }
    @Override public boolean shouldCloseOnEsc() { return !busy; }
}
