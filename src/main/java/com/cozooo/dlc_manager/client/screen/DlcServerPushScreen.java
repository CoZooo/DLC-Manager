package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.*;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.sync.DlcSyncAdapter;
import com.cozooo.dlc_manager.sync.*;
import com.cozooo.dlc_manager.sync.SyncModel.*;
import com.cozooo.dlc_manager.platform.DlcTextCompat;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.MutableComponent;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DlcServerPushScreen extends DlcFilePageScreen {
    private final DlcServerEntry entry;
    private List<Item> local = List.of(), remote = List.of();
    private List<Row> rows = List.of(), filteredRows = List.of();
    private Comparison comparison = new Comparison(List.of(), Map.of());
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
    private boolean progressScrollLocked;
    private boolean progressMode;
    private DlcServerPushScreen progressParent;
    private List<Row> progressChosen = List.of();
    private boolean progressStarted;
    private Result lastResult;
    private boolean completionMode;
    private boolean completionRunning;
    private List<DlcRemoteEntry> completionDlc = List.of();
    private List<DlcRequiredEntry> completionRequired = List.of();
    private boolean completeDlcSelected = true;
    private boolean installRequiredSelected = true;

    public DlcServerPushScreen(Screen parent, DlcServerEntry entry) {
        super(DlcUiPage.SERVER_PUSH, parent, DlcTextCompat.translatable("dlc_manager.sync.title"));
        this.entry = entry;
    }

    private DlcServerPushScreen(DlcServerPushScreen parent, DlcServerEntry entry, List<Row> chosen, Mode mode, boolean states, Direction direction) {
        this(parent, entry);
        progressMode = true;
        progressParent = parent;
        progressChosen = List.copyOf(chosen);
        this.mode = mode;
        this.states = states;
        this.direction = direction;
    }

    @Override protected void init() {
        super.init();
        if (progressMode && !progressStarted) {
            progressStarted = true;
            execute(progressChosen);
        }
    }

    @Override protected void buildPage() {
        if (progressMode) {
            buildProgressPage();
            return;
        }
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
        String language = com.cozooo.dlc_manager.platform.DlcPlatformServices.languageCode();
        for (Row row : shown) {
            String name = SyncModel.displayName(row.source(), language);
            String status = tr("dlc_manager.sync." + comparison.status(row));
            var tooltip = new DlcUiTooltip.Content(name, SyncModel.tooltip(row.source(), language), List.of(), status);
            var line = selectionRow(list, name, () -> Boolean.toString(selected.contains(row.key())), () -> {
                if (!selected.add(row.key())) selected.remove(row.key());
            });
            tip(line, () -> tooltip);
            text(line, status, "row-description");
        }
        live(results, () -> message, "page-description");
        showWhen(progress(results, () -> fraction), () -> busy);
        if (rollbackClient != null && rollbackClient.canRollback()) {
            var undo = action(footer, tr("dlc_manager.sync.rollback"), this::rollback);
            undo.getElement().setAttribute("class", "button danger"); undo.setEnabled(!busy);
        }
        executeButton = primary(tr("dlc_manager.sync.execute"), this::confirm);
        executeButton.getElement().setAttribute("class", "button primary");
        updateExecuteButton();
    }

    private void buildProgressPage() {
        content.parentElement.setAttribute("class", "page-shell sync-page sync-progress-page");
        if (!progressScrollLocked) {
            progressScrollLocked = true;
            content.addEventListener("wheel", event -> {
                if (progressMode) {
                    event.preventDefault();
                    content.setScrollTop(0);
                }
            });
        }
        content.setAttribute("style", "overflow-y:hidden;overflow-x:hidden");
        content.setScrollTop(0);
        Element body = node(content, "div", "page-dialog");
        if (completionMode) {
            text(body, tr("dlc_manager.sync.complete_prompt"), "section-title");
            if (completionRunning) {
                live(body, () -> message, "page-description");
                progress(body, () -> fraction);
                action(footer, tr("dlc_manager.sync.cancel"), this::cancelCompletion).getElement().setAttribute("class", "button danger");
            } else {
                if (!message.isBlank()) live(body, () -> message, "page-description");
                Element options = node(body, "div", "completion-options");
                if (!completionDlc.isEmpty()) {
                    Element option = node(options, "div", "completion-option");
                    text(option, tr("dlc_manager.sync.complete_dlc_info", completionDlc.size()), "completion-info");
                    DlcUiButton button = toggle(option, tr("dlc_manager.sync.complete_dlc"), completeDlcSelected, value -> { completeDlcSelected = value; refreshPage(); });
                    button.getElement().setAttribute("class", "button choice completion-choice" + (completeDlcSelected ? " selected" : ""));
                }
                if (!completionRequired.isEmpty()) {
                    Element option = node(options, "div", "completion-option");
                    text(option, tr("dlc_manager.sync.install_required_info", completionRequired.size()), "completion-info");
                    DlcUiButton button = toggle(option, tr("dlc_manager.sync.install_required"), installRequiredSelected, value -> { installRequiredSelected = value; refreshPage(); });
                    button.getElement().setAttribute("class", "button choice completion-choice" + (installRequiredSelected ? " selected" : ""));
                }
                DlcUiButton complete = completionAction(tr("dlc_manager.sync.done"), this::startCompletion);
                complete.getElement().setAttribute("class", "button primary");
            }
            return;
        }
        text(body, tr("dlc_manager.sync.progress_title"), "section-title");
        live(body, () -> message, "page-description");
        progress(body, () -> fraction);
        if (busy) action(footer, tr("dlc_manager.sync.cancel"), this::cancel).getElement().setAttribute("class", "button danger");
    }
    private com.sighs.apricityui.init.Element selectionRow(com.sighs.apricityui.init.Element list, String name, java.util.function.Supplier<String> checked, Runnable change) {
        var line = managerRowLine(list, name, checked, () -> !busy, change);
        line.setAttribute("class", "dlc-row sync-row");
        var checkbox = line.getChildren().get(0);
        var document = pageDocument;
        long generation = document.getRefreshGeneration();
        line.addEventListener("mousedown", event -> {
            if (event instanceof com.sighs.apricityui.event.MouseEvent mouse && mouse.button == 0 && !busy && Minecraft.getInstance().screen == this
                && document == getLinkedDocument() && document.isCurrentGeneration(generation)
                && !(event.target instanceof com.sighs.apricityui.init.Element target && checkbox.contains(target))) {
                event.preventDefault(); change.run(); syncSelections(); updatePage();
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
        if (executeButton != null) executeButton.setEnabled(!busy && (settingsSelected && settingsRow() != null || !selected.isEmpty() && visibleRows().stream().anyMatch(row -> selected.contains(row.key()))));
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
        comparison = SyncModel.compare(local, remote, direction, states, mode);
        rows = comparison.rows();
        selected.clear(); settingsSelected = false; refilter();
        refreshPage();
    }
    private void confirm() {
        if (busy) return;
        List<Row> chosen = selectedRows();
        if (chosen.isEmpty()) return;
        String language = com.cozooo.dlc_manager.platform.DlcPlatformServices.languageCode();
        MutableComponent message = DlcTextCompat.translatable("dlc_manager.sync.confirm", chosen.size());
        for (Row row : chosen) {
            message.append(DlcTextCompat.literal("\n"));
            message.append(DlcTextFormatter.parse(SyncModel.displayName(row.source(), language)));
        }
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) Minecraft.getInstance().setScreen(new DlcServerPushScreen(this, entry, chosen, mode, states, direction));
        }, DlcTextCompat.translatable("dlc_manager.sync.title"), message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
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
                lastResult = result;
                message = DlcTextCompat.translatable(result.canceled() ? "dlc_manager.sync.canceled" : "dlc_manager.sync.finished", result.completed(), result.errors().size()).getString()
                    + (result.requiresRestart() ? " " + DlcTextCompat.translatable("dlc_manager.sync.restart").getString() : "") + "\n" + String.join("\n", result.succeeded().stream().map(key -> tr("dlc_manager.sync.succeeded_item", key)).toList()) + "\n" + String.join("\n", result.errors());
                if (chosen.stream().anyMatch(row -> SyncModel.category(row.source()) == Category.UPDATE && result.succeeded().contains(row.key()))) message += "\n" + tr("dlc_manager.sync.updates_ready");
                if (result.canceled()) return;
                if (!result.errors().isEmpty()) failureReason = String.join("\n", result.errors());
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
            finally { Minecraft.getInstance().execute(() -> {
                busy = false; syncing = false; connecting = null;
                if (progressMode) finishProgress();
                else { refreshPage(); if (failureReason != null) showFailure(failureReason); }
            }); }
        });
    }

    private void finishProgress() {
        if (progressParent == null) return;
        progressParent.applyResult(lastResult, rollbackClient);
        if (failureReason != null) {
            Minecraft.getInstance().setScreen(new DlcSyncFailureScreen(restore -> {
                Minecraft.getInstance().setScreen(progressParent);
                if (restore) progressParent.rollback();
            }, failureReason));
        } else {
            Set<String> succeeded = lastResult == null ? Set.of() : new HashSet<>(lastResult.succeeded());
            completionDlc = synchronizedDlcEntries(succeeded);
            completionRequired = synchronizedRequiredEntries(succeeded);
            if (!completionDlc.isEmpty() || !completionRequired.isEmpty()) {
                completionMode = true;
                completionRunning = false;
                completeDlcSelected = !completionDlc.isEmpty();
                installRequiredSelected = !completionRequired.isEmpty();
                message = "";
                fraction = -1;
                refreshPage();
            } else Minecraft.getInstance().setScreen(progressParent);
        }
    }

    private void startCompletion() {
        boolean completeDlc = completeDlcSelected;
        boolean installRequired = installRequiredSelected;
        if (completionRunning) return;
        if (!completeDlc && !installRequired) {
            completionMode = false;
            if (progressParent != null) Minecraft.getInstance().setScreen(progressParent);
            return;
        }
        completionRunning = true;
        cancelRequested = false;
        fraction = 0;
        message = tr("dlc_manager.sync.completing");
        refreshPage();
        CompletableFuture.runAsync(() -> {
            int errors = 0;
            int total = (completeDlc ? completionDlc.size() : 0) + (installRequired ? completionRequired.size() : 0);
            int[] completed = {0};
            try {
                if (completeDlc) {
                    int success;
                    int failures;
                    if (mode == Mode.CONFIG) {
                        int[] result = completeRemoteOnServer(completionDlc, total, completed[0]);
                        success = result[0]; failures = result[1];
                    } else {
                        var result = com.cozooo.dlc_manager.client.remote.DlcRemoteService.downloadAll(completionDlc, new com.cozooo.dlc_manager.client.remote.DlcRemoteService.BatchProgressListener() {
                            @Override public void onEntryStatus(DlcRemoteEntry item, String statusKey, int done, int ignored) {
                                completed[0] = done; fraction = total == 0 ? 1 : completed[0] / (double) total; message = tr("dlc_manager.sync.completing");
                            }
                        }, () -> cancelRequested);
                        completionDlc = com.cozooo.dlc_manager.client.remote.DlcRemoteService.collectMissing(completionDlc);
                        success = result.success; failures = result.errors.size();
                    }
                    errors += failures; completed[0] += success;
                    if (failures == 0) completionDlc = List.of();
                }
                if (installRequired) {
                    int success;
                    int failures;
                    if (mode == Mode.CONFIG) {
                        int[] result = completeRequiredOnServer(completionRequired, total, completed[0]);
                        success = result[0]; failures = result[1];
                    } else {
                        var result = com.cozooo.dlc_manager.client.required.DlcRequiredService.downloadAll(completionRequired, new com.cozooo.dlc_manager.client.required.DlcRequiredService.BatchProgressListener() {
                            @Override public void onEntryStatus(DlcRequiredEntry item, String statusKey, int done, int ignored) {
                                fraction = total == 0 ? 1 : (completed[0] + done) / (double) total; message = tr("dlc_manager.sync.completing");
                            }
                        }, () -> cancelRequested);
                        success = result.success; failures = result.errors.size();
                        completed[0] += success;
                        for (DlcRequiredEntry item : completionRequired) {
                            if (cancelRequested) break;
                            try {
                                com.cozooo.dlc_manager.client.required.DlcRequiredService.installOne(item);
                            } catch (Exception ignored) {
                                failures++;
                            }
                        }
                        completionRequired = com.cozooo.dlc_manager.client.required.DlcRequiredService.collectMissingOrApply(completionRequired);
                    }
                    errors += failures; completed[0] += mode == Mode.CONFIG ? success : 0;
                    if (failures == 0) completionRequired = List.of();
                }
                if (!cancelRequested) fraction = 1;
            } catch (Exception ignored) { if (!cancelRequested) errors++; }
            int failures = errors;
            Minecraft.getInstance().execute(() -> {
                completionRunning = false;
                if (cancelRequested) message = tr("dlc_manager.sync.canceled");
                else if (completionDlc.isEmpty() && completionRequired.isEmpty()) { completionMode = false; if (progressParent != null) Minecraft.getInstance().setScreen(progressParent); }
                else if (failures > 0) message = tr("dlc_manager.sync.completion_failed", failures);
                else message = "";
                refreshPage();
            });
        });
    }

    private void cancelCompletion() { if (completionRunning) cancelRequested = true; }

    private int[] completeRemoteOnServer(List<DlcRemoteEntry> entries, int total, int completed) throws Exception {
        List<String> ids = new ArrayList<>();
        for (DlcRemoteEntry item : entries) {
            if (item.getDlcIdentifier() != null && !item.getDlcIdentifier().isBlank() && !ids.contains(item.getDlcIdentifier())) ids.add(item.getDlcIdentifier());
        }
        if (ids.isEmpty()) return new int[] {0, 0};
        DlcServerRemoteStatus started = DlcServerConnection.startRemoteDownload(entry, 8000, ids);
        if (started == null) throw new java.io.IOException("server remote download unavailable");
        return awaitRemoteServerDownload(started, total, completed);
    }

    private int[] awaitRemoteServerDownload(DlcServerRemoteStatus initial, int total, int completed) throws Exception {
        DlcServerRemoteStatus status = initial;
        boolean cancelSent = false;
        while (!status.finished) {
            if (cancelRequested && !cancelSent) {
                DlcServerConnection.cancelRemoteDownload(entry, 3000);
                cancelSent = true;
            }
            status = DlcServerConnection.fetchRemoteDownloadStatus(entry, 3000);
            if (status == null) throw new java.io.IOException("server remote download status unavailable");
            fraction = total == 0 ? 1 : (completed + status.completedCount) / (double) total;
            message = tr("dlc_manager.sync.completing");
            if (!status.finished) Thread.sleep(200L);
        }
        if (cancelRequested) return new int[] {0, 0};
        if (!status.success) throw new java.io.IOException(status.error == null ? "server remote download failed" : status.error);
        return new int[] {status.completedCount, 0};
    }

    private int[] completeRequiredOnServer(List<DlcRequiredEntry> entries, int total, int completed) throws Exception {
        List<String> ids = new ArrayList<>();
        for (DlcRequiredEntry item : entries) {
            if (item.getIdentifier() != null && !item.getIdentifier().isBlank() && !ids.contains(item.getIdentifier())) ids.add(item.getIdentifier());
        }
        if (ids.isEmpty()) return new int[] {0, 0};
        DlcServerRequiredStatus status = DlcServerConnection.startRequiredDownload(entry, 8000, ids);
        if (status == null) throw new java.io.IOException("server required download unavailable");
        boolean cancelSent = false;
        while (!status.finished) {
            if (cancelRequested && !cancelSent) {
                DlcServerConnection.cancelRequiredDownload(entry, 3000);
                cancelSent = true;
            }
            status = DlcServerConnection.fetchRequiredDownloadStatus(entry, 3000);
            if (status == null) throw new java.io.IOException("server required download status unavailable");
            fraction = total == 0 ? 1 : (completed + status.completedCount) / (double) total;
            message = tr("dlc_manager.sync.completing");
            if (!status.finished) Thread.sleep(200L);
        }
        if (cancelRequested) return new int[] {0, 0};
        if (!status.success) throw new java.io.IOException(status.error == null ? "server required download failed" : status.error);
        return new int[] {status.completedCount, 0};
    }

    private DlcUiButton completionAction(String label, Runnable run) {
        DlcUiButton button = new DlcUiButton(pageDocument, footer, label, run);
        button.getElement().addEventListener("click", event -> run.run());
        return button;
    }

    private Set<java.nio.file.Path> synchronizedPaths(Set<String> keys) {
        Set<java.nio.file.Path> paths = new HashSet<>();
        List<java.nio.file.Path> roots = DlcConfig.getDlcRoots();
        for (String key : keys) {
            int separator = key.indexOf(':');
            if (separator <= 0) continue;
            try {
                int index = Integer.parseInt(key.substring(0, separator));
                if (index >= 0 && index < roots.size()) paths.add(roots.get(index).resolve(key.substring(separator + 1)).normalize());
            } catch (NumberFormatException ignored) { }
        }
        return paths;
    }

    private List<DlcRemoteEntry> synchronizedDlcEntries(Set<String> keys) {
        if (mode != Mode.CONFIG || keys.isEmpty()) return List.of();
        Set<String> selectedKeys = new HashSet<>();
        for (Row row : progressChosen) if (keys.contains(row.key()) && SyncModel.category(row.source()) == Category.DLC) selectedKeys.add(row.key());
        Set<java.nio.file.Path> paths = synchronizedPaths(selectedKeys);
        Map<java.nio.file.Path, Row> selected = new HashMap<>();
        for (Row row : progressChosen) if (selectedKeys.contains(row.key())) selected.put(synchronizedPaths(Set.of(row.key())).stream().findFirst().orElse(null), row);
        List<DlcRemoteEntry> entries = new ArrayList<>();
        for (java.nio.file.Path root : DlcConfig.getDlcRoots()) for (DlcRemoteEntry entry : com.cozooo.dlc_manager.client.remote.DlcRemoteLoader.load(root)) {
            java.nio.file.Path path = entry.getConfigPath() == null ? null : entry.getConfigPath().toAbsolutePath().normalize();
            if (paths.contains(path) && selected.get(path) != null && needsResourceCompletion(selected.get(path))) entries.add(entry);
        }
        return entries;
    }

    private List<DlcRequiredEntry> synchronizedRequiredEntries(Set<String> keys) {
        if (mode != Mode.CONFIG || keys.isEmpty()) return List.of();
        Set<String> selectedKeys = new HashSet<>();
        for (Row row : progressChosen) if (keys.contains(row.key()) && SyncModel.category(row.source()) == Category.REQUIRED) selectedKeys.add(row.key());
        Set<java.nio.file.Path> paths = synchronizedPaths(selectedKeys);
        Map<java.nio.file.Path, Row> selected = new HashMap<>();
        for (Row row : progressChosen) if (selectedKeys.contains(row.key())) selected.put(synchronizedPaths(Set.of(row.key())).stream().findFirst().orElse(null), row);
        List<DlcRequiredEntry> entries = new ArrayList<>();
        for (java.nio.file.Path root : DlcConfig.getDlcRoots()) for (DlcRequiredEntry entry : com.cozooo.dlc_manager.client.required.DlcRequiredLoader.load(root)) {
            java.nio.file.Path path = entry.getConfigPath() == null ? null : entry.getConfigPath().toAbsolutePath().normalize();
            if (paths.contains(path) && selected.get(path) != null && needsResourceCompletion(selected.get(path))) entries.add(entry);
        }
        return entries;
    }

    private boolean needsResourceCompletion(Row row) {
        Item target = row.target();
        if (target == null) return true;
        return target.resources().isEmpty() || target.resources().stream().anyMatch(file -> file.size() < 0 || file.hash() == null);
    }

    private void applyResult(Result result, SyncClient client) {
        rollbackClient = client;
        if (result != null) {
            Set<String> succeeded = new HashSet<>(result.succeeded());
            rows = rows.stream().filter(row -> !succeeded.contains(row.key())).toList();
            selected.removeAll(succeeded);
            settingsSelected = false;
            refilter();
        }
        refreshPage();
    }
    @Override protected void updatePage() { updateExecuteButton(); }
    @Override public boolean shouldCloseOnEsc() { return !busy; }
}
