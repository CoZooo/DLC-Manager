package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.pack.DlcPackArchive;
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller;
import com.cozooo.dlc_manager.client.pack.DlcPackMeta;
import com.cozooo.dlc_manager.client.pack.DlcPackPackage;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.client.core.DlcFilePicker;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DlcPackManagerScreen extends DlcScreenCompat.MouseScroll {
    private static final int LIST_ROW_HEIGHT = 14;
    private static final int LIST_WIDTH = 320;

    private final Screen parent;
    private Button backButton;
    private Button importButton;
    private Button uninstallButton;
    private List<DlcPackMeta> installedPacks = List.of();
    private int selectedIndex;
    private int scrollIndex;
    private Component status;
    private volatile boolean importingPack;
    private volatile boolean cancelImportRequested;

    public DlcPackManagerScreen(Screen parent) {
        super(Component.translatable("dlc_manager.pack.manager_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshInstalled();
        clearWidgets();
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 28;
        int gap = 10;
        int total = buttonWidth * 3 + gap * 2;
        int startX = this.width / 2 - total / 2;
        Component importLabel = importingPack
            ? Component.translatable("dlc_manager.pack.cancel")
            : Component.translatable("dlc_manager.pack.import");
        importButton = Button.builder(importLabel, btn -> {
                if (importingPack) {
                    requestCancelImport();
                } else {
                    importPack();
                }
            })
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        importButton.active = DlcPathConfig.isPackFuncOn() && (!importingPack || !cancelImportRequested);
        uninstallButton = Button.builder(Component.translatable("dlc_manager.pack.uninstall"), btn -> uninstallSelected())
            .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
            .build();
        uninstallButton.active = !importingPack && !installedPacks.isEmpty();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
            .build();
        backButton.active = !importingPack;
        addRenderableWidget(importButton);
        addRenderableWidget(uninstallButton);
        addRenderableWidget(backButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        if (!DlcPathConfig.isPackFuncOn()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.pack.disabled"), this.width / 2, this.height / 2 - 8, 0xFFCC66);
        } else {
            renderSummary(guiGraphics);
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (importingPack || installedPacks.isEmpty()) {
            return false;
        }
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        int step = deltaY > 0 ? -1 : 1;
        scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!importingPack && button == 0 && isInsidePackList(mouseX, mouseY)) {
            int row = (int) ((mouseY - getPackListTop()) / LIST_ROW_HEIGHT);
            int index = scrollIndex + row;
            if (index >= 0 && index < installedPacks.size() && row < getVisiblePackCount()) {
                selectedIndex = index;
                ensureSelectionVisible();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderSummary(GuiGraphics guiGraphics) {
        Path packRoot = DlcPaths.dlcRoot().resolve("tmp").resolve("pack");
        int count = 0;
        if (Files.isDirectory(packRoot)) {
            try (var stream = Files.list(packRoot)) {
                count = (int) stream.filter(Files::isDirectory).count();
            } catch (Exception ignored) {
            }
        }
        int x = getPackListX();
        int y = 56;
        guiGraphics.drawString(this.font, Component.translatable("dlc_manager.pack.installed_count", count), x, y, 0xE6E6E6, false);
        y += 18;
        guiGraphics.drawString(this.font, DlcTextFormatter.parse(Component.translatable("dlc_manager.pack.first_stage").getString()), x, y, 0xC8C8C8, false);
        y += 22;
        if (status != null) {
            guiGraphics.drawString(this.font, status, x, y, 0xFFCC66, false);
            y += 18;
        }
        if (installedPacks.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable("dlc_manager.pack.none_installed"), x, y, 0x9A9A9A, false);
            return;
        }
        clampListState();
        int visibleCount = Math.min(getVisiblePackCount(), installedPacks.size() - scrollIndex);
        for (int row = 0; row < visibleCount; row++) {
            int i = scrollIndex + row;
            DlcPackMeta pack = installedPacks.get(i);
            int color = i == selectedIndex ? 0x66CCFF : 0xD8D8D8;
            guiGraphics.drawString(
                this.font,
                Component.literal((i == selectedIndex ? "> " : "  ") + pack.identifier + "  " + pack.version),
                x,
                y + row * LIST_ROW_HEIGHT,
                color,
                false
            );
        }
        drawPackScrollBar(guiGraphics);
    }

    private void importPack() {
        if (!DlcPathConfig.isPackFuncOn()) {
            status = Component.translatable("dlc_manager.pack.disabled");
            return;
        }
        Path zip = DlcFilePicker.pickUpdateZip(Component.translatable("dlc_manager.pack.import").getString());
        if (zip == null) {
            return;
        }
        startImportPrecheck(zip);
    }

    private void startImportPrecheck(Path zip) {
        setImportingPack(true, Component.translatable("dlc_manager.pack.importing"));
        new Thread(() -> {
            Path staging = null;
            try {
                DlcPackArchive.ExtractResult extracted = DlcPackArchive.extractToStaging(zip, DlcPaths.dlcRoot());
                staging = extracted.stagingDir;
                checkImportCanceled();
                DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
                DlcPackPackage pack = result.pack;
                if (!result.valid || pack == null) {
                    DlcPackArchive.deleteRecursive(staging);
                    finishImport(Component.translatable("dlc_manager.pack.import_invalid", result.error == null ? "unknown" : result.error));
                    return;
                }
                DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                DlcPackInstaller.PrecheckResult precheck = installer.precheck(pack);
                if (precheck.hasBlockingIssues()) {
                    DlcPackArchive.deleteRecursive(staging);
                    finishImport(Component.translatable("dlc_manager.pack.import_failed", DlcConfirmUtil.describePackBlockingPrecheck(precheck)));
                    return;
                }
                checkImportCanceled();
                Path preparedStaging = staging;
                staging = null;
                Minecraft.getInstance().execute(() -> handlePreparedPack(installer, pack, precheck, preparedStaging));
            } catch (DlcDownloadService.DownloadCanceledException canceled) {
                DlcPackArchive.deleteRecursive(staging);
                finishImport(Component.translatable("dlc_manager.pack.import_cancelled"));
            } catch (Exception ex) {
                DlcPackArchive.deleteRecursive(staging);
                finishImport(Component.translatable("dlc_manager.pack.import_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }, "dlc-pack-precheck").start();
    }

    private void handlePreparedPack(DlcPackInstaller installer, DlcPackPackage pack, DlcPackInstaller.PrecheckResult precheck, Path staging) {
        if (cancelImportRequested) {
            DlcPackArchive.deleteRecursive(staging);
            finishImport(Component.translatable("dlc_manager.pack.import_cancelled"));
            return;
        }
        importingPack = false;
        cancelImportRequested = false;
        DlcPackMeta installed = findInstalledPack(pack.metadata.identifier);
        if (installed != null) {
            confirmReplacePack(installer, pack, precheck, installed, staging);
            return;
        }
        continueImportPack(installer, pack, precheck, false, staging);
    }

    private void requestCancelImport() {
        cancelImportRequested = true;
        status = Component.translatable("dlc_manager.pack.canceling");
        rebuildPackWidgets();
    }

    private void checkImportCanceled() throws DlcDownloadService.DownloadCanceledException {
        if (cancelImportRequested) {
            throw new DlcDownloadService.DownloadCanceledException("canceled");
        }
    }

    private void setImportingPack(boolean importing, Component message) {
        importingPack = importing;
        cancelImportRequested = false;
        status = message;
        rebuildPackWidgets();
    }

    private void finishImport(Component message) {
        Minecraft.getInstance().execute(() -> {
            importingPack = false;
            cancelImportRequested = false;
            status = message;
            refreshInstalled();
            rebuildPackWidgets();
        });
    }

    private DlcPackInstaller.InstallOptions withCancelChecker(DlcPackInstaller.InstallOptions options) {
        DlcPackInstaller.InstallOptions effectiveOptions = options == null ? DlcPackInstaller.InstallOptions.strict() : options;
        return effectiveOptions.withCancelChecker(() -> cancelImportRequested);
    }

    private void startInstallPreparedPack(DlcPackInstaller installer, DlcPackPackage pack, DlcPackInstaller.InstallOptions options, boolean replaceExisting, Path staging) {
        setImportingPack(true, Component.translatable("dlc_manager.pack.importing"));
        new Thread(() -> {
            try {
                DlcPackInstaller.InstallReport report = installer.install(pack, replaceExisting, withCancelChecker(options));
                finishImport(Component.translatable("dlc_manager.pack.import_success", report.identifier, report.filesWritten));
            } catch (DlcDownloadService.DownloadCanceledException canceled) {
                finishImport(Component.translatable("dlc_manager.pack.import_cancelled"));
            } catch (Exception ex) {
                finishImport(Component.translatable("dlc_manager.pack.import_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            } finally {
                DlcPackArchive.deleteRecursive(staging);
            }
        }, "dlc-pack-install").start();
    }

    private void continueImportPack(DlcPackInstaller installer, DlcPackPackage pack, DlcPackInstaller.PrecheckResult precheck, boolean replaceExisting, Path staging) {
        if (precheck.requiresRuntimeConfirmation()) {
            confirmRuntimeChanges(installer, pack, precheck, replaceExisting, staging);
            return;
        }
        startInstallPreparedPack(installer, pack, DlcPackInstaller.InstallOptions.strict(), replaceExisting, staging);
    }

    private void confirmReplacePack(DlcPackInstaller installer, DlcPackPackage pack, DlcPackInstaller.PrecheckResult precheck, DlcPackMeta installed, Path staging) {
        Component message = DlcConfirmUtil.buildPackReplaceConfirmMessage(pack, installed);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (!accepted) {
                status = Component.translatable("dlc_manager.pack.import_cancelled");
                DlcPackArchive.deleteRecursive(staging);
                rebuildPackWidgets();
                return;
            }
            continueImportPack(installer, pack, precheck, true, staging);
        }, this.title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void confirmRuntimeChanges(DlcPackInstaller installer, DlcPackPackage pack, DlcPackInstaller.PrecheckResult precheck, boolean replaceExisting, Path staging) {
        Component message = DlcConfirmUtil.buildPackRuntimeConfirmMessage(pack, precheck);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) {
                startInstallPreparedPack(installer, pack, DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges(), replaceExisting, staging);
            } else {
                status = Component.translatable("dlc_manager.pack.import_cancelled");
                DlcPackArchive.deleteRecursive(staging);
                rebuildPackWidgets();
            }
        }, this.title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void uninstallSelected() {
        if (selectedIndex < 0 || selectedIndex >= installedPacks.size()) {
            return;
        }
        DlcPackMeta selected = installedPacks.get(selectedIndex);
        try {
            DlcPackInstaller.UninstallReport report = new DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot()).uninstall(selected.identifier);
            status = Component.translatable("dlc_manager.pack.uninstall_success", report.identifier, report.filesRestored);
            refreshInstalled();
            if (selectedIndex >= installedPacks.size()) {
                selectedIndex = Math.max(0, installedPacks.size() - 1);
            }
            clampListState();
            rebuildPackWidgets();
        } catch (Exception ex) {
            status = Component.translatable("dlc_manager.pack.uninstall_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private void refreshInstalled() {
        installedPacks = loadInstalledPacks();
        if (selectedIndex >= installedPacks.size()) {
            selectedIndex = Math.max(0, installedPacks.size() - 1);
        }
        clampListState();
    }

    private List<DlcPackMeta> loadInstalledPacks() {
        Path packRoot = DlcPaths.dlcRoot().resolve("tmp").resolve("pack");
        if (!Files.isDirectory(packRoot)) {
            return List.of();
        }
        try (var stream = Files.list(packRoot)) {
            List<DlcPackMeta> out = new ArrayList<>();
            stream.filter(Files::isDirectory).forEach(dir -> {
                DlcPackMeta meta = DlcPackMeta.load(dir.resolve("meta.dc"));
                if (meta != null) {
                    out.add(meta);
                }
            });
            out.sort(Comparator.comparing(meta -> meta.identifier.toLowerCase(java.util.Locale.ROOT)));
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private DlcPackMeta findInstalledPack(String identifier) {
        if (identifier == null) {
            return null;
        }
        for (DlcPackMeta meta : installedPacks) {
            if (meta != null && identifier.equals(meta.identifier)) {
                return meta;
            }
        }
        return null;
    }

    private int getPackListX() {
        return Math.max(20, this.width / 2 - 150);
    }

    private int getPackListTop() {
        int y = 56 + 18 + 22;
        if (status != null) {
            y += 18;
        }
        return y;
    }

    private int getPackListBottom() {
        return Math.max(getPackListTop() + LIST_ROW_HEIGHT, this.height - 42);
    }

    private int getVisiblePackCount() {
        return Math.max(1, (getPackListBottom() - getPackListTop()) / LIST_ROW_HEIGHT);
    }

    private int getMaxScroll() {
        return Math.max(0, installedPacks.size() - getVisiblePackCount());
    }

    private boolean isInsidePackList(double mouseX, double mouseY) {
        int x = getPackListX();
        int top = getPackListTop();
        int bottom = Math.min(getPackListBottom(), top + getVisiblePackCount() * LIST_ROW_HEIGHT);
        return mouseX >= x && mouseX <= x + LIST_WIDTH && mouseY >= top && mouseY < bottom;
    }

    private void clampListState() {
        if (installedPacks.isEmpty()) {
            selectedIndex = 0;
            scrollIndex = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(installedPacks.size() - 1, selectedIndex));
        scrollIndex = Math.max(0, Math.min(getMaxScroll(), scrollIndex));
        ensureSelectionVisible();
    }

    private void ensureSelectionVisible() {
        int visible = getVisiblePackCount();
        if (selectedIndex < scrollIndex) {
            scrollIndex = selectedIndex;
        } else if (selectedIndex >= scrollIndex + visible) {
            scrollIndex = Math.max(0, selectedIndex - visible + 1);
        }
        scrollIndex = Math.max(0, Math.min(getMaxScroll(), scrollIndex));
    }

    private void drawPackScrollBar(GuiGraphics guiGraphics) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int top = getPackListTop();
        int bottom = getPackListBottom();
        int trackHeight = Math.max(1, bottom - top);
        int barHeight = Math.max(12, trackHeight * getVisiblePackCount() / Math.max(1, installedPacks.size()));
        int barY = top + (trackHeight - barHeight) * scrollIndex / Math.max(1, maxScroll);
        int x = getPackListX() + LIST_WIDTH - 6;
        guiGraphics.fill(x, top, x + 4, bottom, 0x66333333);
        guiGraphics.fill(x, barY, x + 4, barY + barHeight, 0xFF66CCFF);
    }

    private void rebuildPackWidgets() {
        init(this.minecraft, this.width, this.height);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !importingPack;
    }

    @Override
    public void onClose() {
        if (!importingPack) {
            super.onClose();
        }
    }
}
