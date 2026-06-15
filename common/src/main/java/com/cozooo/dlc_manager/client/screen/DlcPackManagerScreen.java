package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.pack.DlcPackArchive;
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller;
import com.cozooo.dlc_manager.client.pack.DlcPackMeta;
import com.cozooo.dlc_manager.client.pack.DlcPackPackage;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.client.core.DlcFilePicker;
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
    private final Screen parent;
    private Button backButton;
    private Button importButton;
    private Button uninstallButton;
    private List<DlcPackMeta> installedPacks = List.of();
    private int selectedIndex;
    private Component status;

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
        importButton = Button.builder(Component.translatable("dlc_manager.pack.import"), btn -> importPack())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        uninstallButton = Button.builder(Component.translatable("dlc_manager.pack.uninstall"), btn -> uninstallSelected())
            .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
            .build();
        uninstallButton.active = !installedPacks.isEmpty();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
            .build();
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
        return false;
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
        int x = Math.max(20, this.width / 2 - 150);
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
        int limit = Math.min(8, installedPacks.size());
        for (int i = 0; i < limit; i++) {
            DlcPackMeta pack = installedPacks.get(i);
            int color = i == selectedIndex ? 0x66CCFF : 0xD8D8D8;
            guiGraphics.drawString(
                this.font,
                Component.literal((i == selectedIndex ? "> " : "  ") + pack.identifier + "  " + pack.version),
                x,
                y,
                color,
                false
            );
            y += 14;
        }
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
        Path staging = null;
        try {
            DlcPackArchive.ExtractResult extracted = DlcPackArchive.extractToStaging(zip, DlcPaths.dlcRoot());
            staging = extracted.stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            DlcPackPackage pack = result.pack;
            if (!result.valid || pack == null) {
                status = Component.translatable("dlc_manager.pack.import_invalid", result.error == null ? "unknown" : result.error);
                return;
            }
            DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot());
            DlcPackInstaller.PrecheckResult precheck = installer.precheck(pack);
            if (precheck.hasBlockingIssues()) {
                status = Component.translatable("dlc_manager.pack.import_failed", describeBlockingPrecheck(precheck));
                return;
            }
            if (precheck.requiresRuntimeConfirmation()) {
                Path confirmStaging = staging;
                staging = null;
                confirmRuntimeChanges(installer, pack, precheck, confirmStaging);
                return;
            }
            installPreparedPack(installer, pack, DlcPackInstaller.InstallOptions.strict());
        } catch (Exception ex) {
            status = Component.translatable("dlc_manager.pack.import_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private void confirmRuntimeChanges(DlcPackInstaller installer, DlcPackPackage pack, DlcPackInstaller.PrecheckResult precheck, Path staging) {
        Component message = Component.translatable("dlc_manager.pack.runtime_confirm", DlcConfirmUtil.buildPackRuntimeChangeMessage(pack, precheck));
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            try {
                if (accepted) {
                    installPreparedPack(installer, pack, DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges());
                } else {
                    status = Component.translatable("dlc_manager.pack.import_cancelled");
                    rebuildPackWidgets();
                }
            } finally {
                DlcPackArchive.deleteRecursive(staging);
            }
        }, this.title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    private void installPreparedPack(DlcPackInstaller installer, DlcPackPackage pack, DlcPackInstaller.InstallOptions options) throws java.io.IOException {
        DlcPackInstaller.InstallReport report = installer.install(pack, options);
        status = Component.translatable("dlc_manager.pack.import_success", report.identifier, report.filesWritten);
        refreshInstalled();
        rebuildPackWidgets();
    }

    private String describeBlockingPrecheck(DlcPackInstaller.PrecheckResult precheck) {
        if (!precheck.getForbiddenPaths().isEmpty()) {
            return Component.translatable("dlc_manager.pack.forbidden_paths", joinLimited(precheck.getForbiddenPaths())).getString();
        }
        if (!precheck.getMissingDependencies().isEmpty()) {
            return Component.translatable("dlc_manager.pack.missing_dependencies", joinLimited(precheck.getMissingDependencies())).getString();
        }
        return "unknown";
    }

    private String joinLimited(List<String> values) {
        int limit = Math.min(values.size(), 6);
        String joined = String.join(", ", values.subList(0, limit));
        if (values.size() > limit) {
            joined += ", ...";
        }
        return joined;
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

    private void rebuildPackWidgets() {
        init(this.minecraft, this.width, this.height);
    }
}
