package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateWorldBackup;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.DlcTextCompat;
import com.sighs.apricityui.init.Element;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class DlcWorldBackupScreen extends DlcPageScreen {
    private final Screen parent;
    private final List<DlcUpdatePackage> packages;
    private final Path backupRoot;
    private final Runnable proceed;
    private final Set<String> selected = new LinkedHashSet<>();
    private final DlcUiSaveIcons icons = new DlcUiSaveIcons();
    private List<DlcUpdateWorldBackup.World> worlds = List.of();
    private List<DlcUpdateWorldBackup.Snapshot> snapshots = List.of();
    private Map<String, String> returnTargets = Map.of();
    private boolean started;
    private boolean busy = true;
    private String workingKey;
    private boolean restoreAfter;
    private boolean canRestoreAfter;
    private boolean answered;
    private String error = "";
    private String notice = "";

    public DlcWorldBackupScreen(Screen parent, List<DlcUpdatePackage> packages, Runnable proceed) {
        this(parent, packages, null, proceed);
    }

    public DlcWorldBackupScreen(Screen parent, Path backupRoot) {
        this(parent, null, backupRoot, null);
    }

    private DlcWorldBackupScreen(Screen parent, List<DlcUpdatePackage> packages, Path backupRoot, Runnable proceed) {
        super(DlcUiPage.PAGE, parent, DlcTextCompat.translatable("dlc_manager.update.world_title"));
        this.parent = parent;
        this.packages = packages;
        this.backupRoot = backupRoot;
        this.proceed = proceed;
        this.workingKey = packages != null ? "dlc_manager.update.world_reading_saves" : "dlc_manager.update.world_reading_backups";
    }

    @Override protected boolean showHeaderBackButton() { return packages == null; }

    @Override protected void buildPage() {
        if (!started) { started = true; load(); }
        if (busy) { text(content, tr(workingKey), "page-description"); return; }
        if (!error.isBlank()) text(content, error, "page-warning");
        if (!notice.isBlank()) text(content, notice, "page-description");
        if (packages != null) {
            text(content, tr("dlc_manager.update.world_choose"), "page-description");
            if (worlds.isEmpty()) text(content, tr("dlc_manager.update.world_empty"), "empty-state");
            for (var world : worlds) {
                Element row = toggle(content, "", selected.contains(world.name()), value -> {
                    if (value) selected.add(world.name()); else selected.remove(world.name());
                    refreshPage();
                }).getElement();
                row.setAttribute("class", "button choice save-choice" + (selected.contains(world.name()) ? " selected" : ""));
                row.setAttribute("aria-label", world.name() + " · " + date(world.modified()));
                saveIcon(row, DlcPaths.gameDir().resolve("saves"), Path.of(world.name()));
                Element copy = node(row, "div", "save-choice-copy");
                text(copy, world.name(), "row-title");
                text(copy, date(world.modified()), "row-description");
            }
            if (canRestoreAfter) toggle(content, tr("dlc_manager.update.world_reapply") + " · " + tr(restoreAfter ? "dlc_manager.confirm_yes" : "dlc_manager.confirm_no"), restoreAfter, value -> { restoreAfter = value; refreshPage(); });
            DlcUiButton cancel = action(footer, tr("dlc_manager.update.world_cancel"), this::onClose);
            cancel.getElement().setAttribute("class", "button danger");
            if (error.isBlank()) primary(tr("dlc_manager.update.world_continue"), () -> {
                if (answered) return;
                answered = true;
                for (var pkg : packages) {
                    pkg.setBackupWorlds(new ArrayList<>(selected));
                    pkg.setRestoreWorldsAfterReapply(restoreAfter);
                }
                Minecraft.getInstance().setScreen(parent);
                proceed.run();
            });
        } else {
            if (snapshots.isEmpty()) text(content, tr("dlc_manager.update.world_no_backups"), "empty-state");
            for (var snapshot : snapshots) {
                text(content, date(snapshot.timestamp()) + " · " + tr("dlc_manager.update.world_kind_" + snapshot.kind()), "section-title");
                for (String name : snapshot.worlds()) {
                    var row = node(content, "div", "update-row");
                    saveIcon(row, backupRoot.resolve("worlds/data"), Path.of(snapshot.id(), name));
                    text(row, name, "update-row-copy");
                    var controls = node(row, "div", "row-controls");
                    action(controls, tr("dlc_manager.update.world_delete"), () -> confirmDelete(snapshot.id(), name))
                        .getElement().setAttribute("class", "button danger");
                    action(controls, tr("dlc_manager.update.world_restore"), () -> confirmRestore(snapshot.id(), name));
                }
            }
            if (!snapshots.isEmpty() || !error.isBlank()) action(toolbar, tr("dlc_manager.update.world_delete_all"), () -> confirmDelete(null, null))
                .getElement().setAttribute("class", "button danger");
        }
    }

    private void saveIcon(Element row, Path root, Path relative) {
        Element icon = node(row, "texture", "save-icon");
        icon.setAttribute("aria-hidden", "true");
        icons.bind(icon, root, relative);
    }

    @Override public void removed() {
        icons.clear();
        super.removed();
    }

    private void load() {
        busy = true;
        workingKey = packages != null ? "dlc_manager.update.world_reading_saves" : "dlc_manager.update.world_reading_backups";
        new Thread(() -> {
            try {
                if (packages != null) {
                    var loaded = DlcUpdateWorldBackup.listWorlds(DlcPaths.gameDir());
                    boolean available = false;
                    for (var pkg : packages) {
                        var target = DlcUpdatePackGuard.resolveTargetVersion(List.of(pkg));
                        Path root = DlcUpdateService.resolveMetaRootByPair(DlcPaths.dlcRoot(), target.base(), target.dlc());
                        available |= DlcUpdateWorldBackup.pointer(root, "after") != null;
                    }
                    boolean finalAvailable = available;
                    Minecraft.getInstance().execute(() -> {
                        worlds = loaded;
                        canRestoreAfter = finalAvailable;
                        busy = false;
                        refreshPage();
                    });
                } else {
                    var loaded = DlcUpdateWorldBackup.list(backupRoot);
                    var targets = DlcUpdateWorldBackup.returnTargets(backupRoot);
                    Minecraft.getInstance().execute(() -> { snapshots = loaded; returnTargets = targets; busy = false; refreshPage(); });
                }
            } catch (Exception exception) {
                Minecraft.getInstance().execute(() -> { error = tr("dlc_manager.update.world_error", String.valueOf(exception.getMessage())); busy = false; refreshPage(); });
            }
        }, "dlc-world-backup-list").start();
    }

    private void confirmRestore(String id, String name) {
        if (id.equals(returnTargets.get(name))) { confirmReturn(name); return; }
        confirmOperation(DlcTextCompat.translatable("dlc_manager.update.world_confirm_restore", name),
            "dlc_manager.update.world_restoring",
            () -> DlcUpdateWorldBackup.restore(DlcPaths.gameDir(), backupRoot, id, List.of(name)),
            "dlc_manager.update.world_restored", name);
    }

    private void confirmReturn(String name) {
        confirmOperation(DlcTextCompat.translatable("dlc_manager.update.world_confirm_return", name),
            "dlc_manager.update.world_returning",
            () -> DlcUpdateWorldBackup.returnBeforeRestore(DlcPaths.gameDir(), backupRoot, name),
            "dlc_manager.update.world_returned", name);
    }

    private void confirmDelete(String id, String name) {
        String label = name == null ? "" : name + " · " + snapshots.stream().filter(item -> item.id().equals(id))
            .findFirst().map(item -> date(item.timestamp())).orElse("");
        confirmOperation(DlcTextCompat.translatable(id == null ? "dlc_manager.update.world_confirm_delete_all" : "dlc_manager.update.world_confirm_delete", label),
            id == null ? "dlc_manager.update.world_deleting_all" : "dlc_manager.update.world_deleting",
            () -> { if (id == null) DlcUpdateWorldBackup.deleteAll(backupRoot); else DlcUpdateWorldBackup.delete(backupRoot, id, List.of(name)); },
            id == null ? "dlc_manager.update.world_deleted_all" : "dlc_manager.update.world_deleted", label);
    }

    private void confirmOperation(net.minecraft.network.chat.Component message, String workingKey, BackupAction operation, String successKey, String name) {
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (!accepted) return;
            busy = true;
            this.workingKey = workingKey;
            refreshPage();
            new Thread(() -> {
                String failure = null;
                try { operation.run(); }
                catch (Exception exception) { failure = String.valueOf(exception.getMessage()); }
                String result = failure;
                Minecraft.getInstance().execute(() -> {
                    error = result == null ? "" : tr("dlc_manager.update.world_error", result);
                    notice = result == null ? tr(successKey, name) : "";
                    started = true;
                    load();
                    refreshPage();
                });
            }, "dlc-world-backup-operation").start();
        }, getTitle(), message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    @FunctionalInterface private interface BackupAction { void run() throws Exception; }

    private String date(long time) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time));
    }

    @Override public void onClose() {
        if (!busy) Minecraft.getInstance().setScreen(parent);
    }
}
