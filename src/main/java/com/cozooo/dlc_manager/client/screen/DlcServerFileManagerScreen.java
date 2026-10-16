package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerFileEntry;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.core.DlcFilePicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerFileManagerScreen extends DlcFilePageScreen {


    private final Screen parent;
    private final DlcServerEntry entry;
    private EditBox pathField;
    private List<DlcServerFileEntry> entries = new ArrayList<>();
    private int scrollIndex;
    private int selectedIndex = -1;
    private int lastClickIndex = -1;
    private long lastClickTime;
    private Button refreshButton;
    private Button upButton;
    private Button uploadButton;
    private Button saveButton;
    private Button deleteButton;
    private Button backButton;
    private boolean loading;
    private boolean draggingScroll;
    private int scrollDragOffset;

    public DlcServerFileManagerScreen(Screen parent, DlcServerEntry entry) {
        super(DlcUiPage.SERVER_FILES, parent, DlcTextCompat.translatable("dlc_manager.server.files_title"));
        this.parent = parent;
        this.entry = entry;
    }

    private void initControls() {
        int centerX = this.width / 2;
        int fieldWidth = this.width - 120;
        if (fieldWidth < 160) {
            fieldWidth = 160;
        }
        pathField = new EditBox(this.font, centerX - fieldWidth / 2, 36, fieldWidth, 20, DlcTextCompat.translatable("dlc_manager.server.files_path"));
        pathField.setMaxLength(512);
        addRenderableWidget(pathField);

        int panelLeft = 24;
        int panelRight = this.width - 24;
        int buttonWidth = 90;
        int buttonHeight = 20;
        int topButtonsY = 62;
        int gap = (panelRight - panelLeft - buttonWidth * 4) / 5;
        if (gap < 6) {
            gap = 6;
        }
        int startX = panelLeft + gap;
        refreshButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.files_refresh"), btn -> refresh())
            .bounds(startX, topButtonsY, buttonWidth, buttonHeight)
            .build();
        deleteButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.files_delete"), btn -> confirmDelete())
            .bounds(startX + buttonWidth + gap, topButtonsY, buttonWidth, buttonHeight)
            .build();
        saveButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.files_save"), btn -> saveSelected())
            .bounds(startX + (buttonWidth + gap) * 2, topButtonsY, buttonWidth, buttonHeight)
            .build();
        uploadButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.files_upload"), btn -> uploadFile())
            .bounds(startX + (buttonWidth + gap) * 3, topButtonsY, buttonWidth, buttonHeight)
            .build();

        int bottomY = this.height - 28;
        upButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.files_up"), btn -> goUp())
            .bounds(centerX - buttonWidth - 6, bottomY, buttonWidth, buttonHeight)
            .build();
        backButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(centerX + 6, bottomY, buttonWidth, buttonHeight)
            .build();

        addRenderableWidget(refreshButton);
        addRenderableWidget(upButton);
        addRenderableWidget(uploadButton);
        addRenderableWidget(saveButton);
        addRenderableWidget(deleteButton);
        addRenderableWidget(backButton);

        updateSelectionState();
        refresh();
    }

    private void refresh() {
        if (loading) {
            return;
        }
        loading = true;
        String path = pathField.getValue().trim();
        CompletableFuture
            .supplyAsync(() -> loadEntries(path))
            .whenComplete((list, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (list != null) {
                    entries = list;
                    selectedIndex = -1;
                    updateSelectionState();
                }
            }));
    }

    private List<DlcServerFileEntry> loadEntries(String path) {
        try {
            return DlcServerConnection.listFiles(entry, 10000, path);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void goUp() {
        String path = pathField.getValue().trim();
        if (path.isEmpty()) {
            return;
        }
        String normalized = path.replace("\\", "/");
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int idx = normalized.lastIndexOf('/');
        if (idx <= 0) {
            pathField.setValue("");
        } else {
            pathField.setValue(normalized.substring(0, idx));
        }
        refresh();
    }

    private void openSelected() {
        DlcServerFileEntry entry = getSelectedEntry();
        if (entry == null || !entry.dir) {
            return;
        }
        String path = pathField.getValue().trim();
        String next = path.isEmpty() ? entry.name : path + "/" + entry.name;
        pathField.setValue(next);
        refresh();
    }

    private void uploadFile() {
        Path file = DlcFilePicker.pickAnyFile(DlcTextCompat.translatable("dlc_manager.server.files_upload").getString());
        if (file == null) {
            return;
        }
        String path = pathField.getValue().trim();
        String target = path.isEmpty() ? file.getFileName().toString() : path + "/" + file.getFileName().toString();
        loading = true;
        CompletableFuture
            .supplyAsync(() -> uploadPath(target, file))
            .whenComplete((ok, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (ok != null && ok) {
                    refresh();
                } else {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                        DlcTextCompat.translatable("dlc_manager.server.files_upload_failed"), DlcTextCompat.empty());
                }
            }));
    }

    private boolean upload(String target, Path file) {
        try {
            return DlcServerConnection.uploadFile(entry, 20000, target, file);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean uploadPath(String target, Path source) {
        try {
            if (source == null || !Files.exists(source)) {
                return false;
            }
            if (!Files.isDirectory(source)) {
                return upload(target, source);
            }
            Path base = source;
            final String baseTarget = target;
            return Files.walk(base)
                .filter(Files::isRegularFile)
                .allMatch(file -> {
                    Path relative = base.relativize(file);
                    String rel = relative.toString().replace("\\", "/");
                    String childTarget = baseTarget.isEmpty() ? rel : baseTarget + "/" + rel;
                    try {
                        return upload(childTarget, file);
                    } catch (Exception ignored) {
                        return false;
                    }
                });
        } catch (Exception ignored) {
            return false;
        }
    }

    private void saveSelected() {
        DlcServerFileEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        String basePath = pathField.getValue().trim();
        String remotePath = basePath.isEmpty() ? entry.name : basePath + "/" + entry.name;
        String fileName = entry.dir ? (entry.name + ".zip") : entry.name;
        Path target = DlcFilePicker.pickSaveFile(DlcTextCompat.translatable("dlc_manager.server.files_save").getString(), fileName);
        if (target == null) {
            return;
        }
        if (Files.isDirectory(target)) {
            target = target.resolve(fileName);
        } else if (entry.dir && !target.toString().endsWith(".zip")) {
            target = target.resolveSibling(target.getFileName().toString() + ".zip");
        }
        Path finalTarget = target;
        loading = true;
        CompletableFuture
            .supplyAsync(() -> download(remotePath, finalTarget))
            .whenComplete((ok, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (ok != null && ok) {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                        DlcTextCompat.translatable("dlc_manager.server.files_save_success"), DlcTextCompat.empty());
                } else {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                        DlcTextCompat.translatable("dlc_manager.server.files_save_failed"), DlcTextCompat.empty());
                }
            }));
    }

    private boolean download(String remotePath, Path target) {
        try {
            return DlcServerConnection.downloadFile(entry, 20000, remotePath, target);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void updateSelectionState() {
        if (saveButton != null) {
            saveButton.active = !loading && getSelectedEntry() != null;
        }
        if (deleteButton != null) deleteButton.active = !loading && getSelectedEntry() != null;
        if (refreshButton != null) refreshButton.active = !loading;
        if (uploadButton != null) uploadButton.active = !loading;
        if (upButton != null) upButton.active = !loading;
    }

    private void confirmDelete() {
        DlcServerFileEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        Component title = DlcTextCompat.translatable("dlc_manager.server.files_delete");
        Component message = DlcTextCompat.translatable("dlc_manager.server.files_delete_confirm", entry.name);
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            if (accepted) {
                confirmDeleteAgain(entry);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void confirmDeleteAgain(DlcServerFileEntry entry) {
        Component title = DlcTextCompat.translatable("dlc_manager.server.files_delete");
        Component message = DlcTextCompat.translatable("dlc_manager.server.files_delete_confirm_again", entry.name);
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            if (accepted) {
                deleteSelected(entry);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void deleteSelected(DlcServerFileEntry entry) {
        String path = pathField.getValue().trim();
        String target = path.isEmpty() ? entry.name : path + "/" + entry.name;
        loading = true;
        CompletableFuture
            .supplyAsync(() -> delete(target))
            .whenComplete((ok, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (ok != null && ok) {
                    refresh();
                } else {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                        DlcTextCompat.translatable("dlc_manager.server.files_delete_failed"), DlcTextCompat.empty());
                }
            }));
    }

    private boolean delete(String path) {
        try {
            return DlcServerConnection.deleteFile(entry, 15000, path);
        } catch (Exception ignored) {
            return false;
        }
    }

    private DlcServerFileEntry getSelectedEntry() {
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            return entries.get(selectedIndex);
        }
        return null;
    }

    private String formatSize(long size) {
        if (size <= 0) {
            return "";
        }
        if (size > 1024 * 1024) {
            return String.format("%.1f MB", size / 1024.0 / 1024.0);
        }
        if (size > 1024) {
            return String.format("%.1f KB", size / 1024.0);
        }
        return size + " B";
    }

    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        if (!controlsReady) { controlsReady = true; initControls(); }
        pageRevision = java.util.Objects.hash(entries, loading);

        mount(toolbar, upButton); mount(toolbar, refreshButton); mount(toolbar, uploadButton);
        mount(content, pathField, tr("dlc_manager.server.files_path"), "text");
        if (entries.isEmpty() && !loading) text(content, tr("dlc_manager.ui.empty_files"), "page-description");
        var list = fileList(null);
        for (int i = 0; i < entries.size(); i++) {
            var file = entries.get(i);
            final int index = i;
            fileRow(list, file.name + (file.dir ? "/" : ""), file.dir ? null : formatSize(file.size),
                () -> Boolean.toString(index == selectedIndex), () -> !loading,
                () -> { selectedIndex = selectedIndex == index ? -1 : index; updateSelectionState(); },
                file.dir ? () -> { selectedIndex = index; openSelected(); } : null);
        }
        mount(footer, deleteButton).getElement().setAttribute("class", "button danger"); mount(footer, saveButton);
        status(loading ? tr("dlc_manager.server.files_loading") : "");

    }
    @Override protected void updatePage() {
        updateSelectionState(); pathField.active = !loading;
        if (pageRevision != java.util.Objects.hash(entries, loading)) refreshPage();
        super.updatePage();
    }

    @Override public boolean shouldCloseOnEsc() { return !loading; }
}
