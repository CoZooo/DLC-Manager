package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcFilePicker;
import com.cozooo.dlc_manager.client.pack.DlcPackArchive;
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller;
import com.cozooo.dlc_manager.client.pack.DlcPackMeta;
import com.cozooo.dlc_manager.client.pack.DlcPackMetadata;
import com.cozooo.dlc_manager.client.pack.DlcPackPackage;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DlcPackManagerScreen extends DlcScreenCompat.MouseScroll {
    private static final int ROW_HEIGHT = 58;
    private static final int ICON_SIZE = 32;
    private static final int BUTTON_WIDTH = 46;
    private static final int BUTTON_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 6;

    private final Screen parent;
    private Button importButton;
    private Button urlButton;
    private Button applyButton;
    private Button backButton;
    private final Map<String, PackArchiveEntry> pendingInstalls = new LinkedHashMap<>();
    private final Set<String> pendingUninstalls = new HashSet<>();
    private List<PackArchiveEntry> archiveEntries = List.of();
    private Map<String, DlcPackMeta> installedByIdentifier = Map.of();
    private int selectedIndex = -1;
    private int scrollIndex;
    private boolean draggingScrollbar;
    private String lastClickKey;
    private long lastClickAt;
    private Component status;
    private volatile boolean busy;
    private int iconTextureSerial;

    public DlcPackManagerScreen(Screen parent) {
        super(Component.translatable("dlc_manager.pack.manager_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshEntries();
        clearWidgets();
        int buttonWidth = 104;
        int buttonHeight = 20;
        int y = this.height - 28;
        int gap = 8;
        int total = buttonWidth * 4 + gap * 3;
        int startX = this.width / 2 - total / 2;
        importButton = Button.builder(Component.translatable("dlc_manager.pack.import"), btn -> importPackArchive())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        importButton.active = DlcPathConfig.isPackFuncOn() && !busy;
        urlButton = Button.builder(Component.translatable("dlc_manager.pack.import_url"), btn -> Minecraft.getInstance().setScreen(new DlcPackUrlScreen(this)))
            .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
            .build();
        urlButton.active = DlcPathConfig.isPackFuncOn() && !busy;
        applyButton = Button.builder(Component.translatable("dlc_manager.pack.apply"), btn -> applyPendingChanges())
            .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
            .build();
        applyButton.active = DlcPathConfig.isPackFuncOn() && hasPendingChanges() && !busy;
        Component backLabel = hasPendingChanges() ? Component.translatable("dlc_manager.pack.cancel_changes") : Component.translatable("dlc_manager.pack.back");
        backButton = Button.builder(backLabel, btn -> onBackOrCancel())
            .bounds(startX + (buttonWidth + gap) * 3, y, buttonWidth, buttonHeight)
            .build();
        backButton.active = !busy;
        addRenderableWidget(importButton);
        addRenderableWidget(urlButton);
        addRenderableWidget(applyButton);
        addRenderableWidget(backButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        if (!DlcPathConfig.isPackFuncOn()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.pack.disabled"), this.width / 2, this.height / 2 - 8, 0xFFCC66);
        } else {
            renderPackList(guiGraphics, mouseX, mouseY);
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderPackList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = getListLeft();
        int right = getListRight();
        int top = getListTop();
        int bottom = getListBottom();
        guiGraphics.drawString(this.font, Component.translatable("dlc_manager.pack.archive_count", archiveEntries.size()), left, 38, 0xE6E6E6, false);
        if (status != null) {
            guiGraphics.drawString(this.font, status, left + 150, 38, 0xFFCC66, false);
        }
        if (archiveEntries.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.pack.none_archived"), this.width / 2, top + 36, 0x9A9A9A);
            return;
        }
        clampScroll();
        int visible = Math.min(getVisibleCount(), archiveEntries.size() - scrollIndex);
        for (int row = 0; row < visible; row++) {
            int index = scrollIndex + row;
            PackArchiveEntry entry = archiveEntries.get(index);
            int rowTop = top + row * ROW_HEIGHT;
            renderEntry(guiGraphics, entry, index, left, right - SCROLLBAR_WIDTH - 4, rowTop, mouseX, mouseY);
        }
        drawScrollBar(guiGraphics);
    }

    private void renderEntry(GuiGraphics guiGraphics, PackArchiveEntry entry, int index, int left, int right, int y, int mouseX, int mouseY) {
        boolean selected = index == selectedIndex;
        int background = selected ? 0x66335566 : 0x55202020;
        guiGraphics.fill(left, y, right, y + ROW_HEIGHT - 4, background);
        int arrowX = left + 4;
        guiGraphics.drawString(this.font, Component.literal("^"), arrowX, y + 8, canMoveUp(index) ? 0xFFFFFF : 0x666666, false);
        guiGraphics.drawString(this.font, Component.literal("v"), arrowX, y + 30, canMoveDown(index) ? 0xFFFFFF : 0x666666, false);
        int iconX = left + 24;
        int iconY = y + 11;
        if (entry.iconTexture != null) {
            guiGraphics.blit(entry.iconTexture, iconX, iconY, ICON_SIZE, ICON_SIZE, 0.0F, 0.0F, entry.iconWidth, entry.iconHeight, entry.iconWidth, entry.iconHeight);
        } else {
            guiGraphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xFF3B3B3B);
            String initial = entry.displayName.isBlank() ? "P" : entry.displayName.substring(0, 1).toUpperCase(Locale.ROOT);
            guiGraphics.drawCenteredString(this.font, Component.literal(initial), iconX + ICON_SIZE / 2, iconY + 12, 0xFFFFFF);
        }

        int textX = iconX + ICON_SIZE + 8;
        int textRight = right - (BUTTON_WIDTH + 5) * 4 - 14;
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(entry.displayName, textRight - textX)), textX, y + 8, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(entry.authorText, textRight - textX)), textX, y + 23, 0xC8C8C8, false);
        Component version = Component.translatable("dlc_manager.pack.version_line", entry.metadata.version);
        guiGraphics.drawString(this.font, version, textX, y + 38, 0xAFC8FF, false);

        int buttonX = right - (BUTTON_WIDTH + 5) * 4;
        drawRowButton(guiGraphics, buttonX, y + 8, installButtonLabel(entry), mouseX, mouseY);
        drawRowButton(guiGraphics, buttonX + BUTTON_WIDTH + 5, y + 8, Component.translatable("dlc_manager.pack.delete"), mouseX, mouseY);
        drawRowButton(guiGraphics, buttonX + (BUTTON_WIDTH + 5) * 2, y + 8, Component.translatable("dlc_manager.pack.feedback"), mouseX, mouseY);
        drawRowButton(guiGraphics, buttonX + (BUTTON_WIDTH + 5) * 3, y + 8, Component.translatable("dlc_manager.pack.support_author"), mouseX, mouseY);
    }

    private void drawRowButton(GuiGraphics guiGraphics, int x, int y, Component label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        guiGraphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, hover ? 0xFF5A5A5A : 0xFF404040);
        guiGraphics.drawCenteredString(this.font, label, x + BUTTON_WIDTH / 2, y + 4, 0xFFFFFF);
    }

    private Component installButtonLabel(PackArchiveEntry entry) {
        if (pendingInstalls.containsKey(entry.metadata.identifier) && pendingInstalls.get(entry.metadata.identifier) == entry) {
            return pendingInstallButtonLabel(entry);
        }
        if (pendingUninstalls.contains(entry.metadata.identifier)) {
            return Component.translatable("dlc_manager.pack.pending_uninstall");
        }
        DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
        if (installed != null && entry.metadata.version.equals(installed.version)) {
            return Component.translatable("dlc_manager.pack.uninstall");
        }
        if (installed != null) {
            int compare = DlcPackMetadata.compareVersionStrings(entry.metadata.version, installed.version);
            if (compare > 0) {
                return Component.translatable("dlc_manager.pack.upgrade");
            }
            if (compare < 0) {
                return Component.translatable("dlc_manager.pack.downgrade");
            }
            return Component.translatable("dlc_manager.pack.replace");
        }
        return Component.translatable("dlc_manager.pack.install");
    }

    private Component pendingInstallButtonLabel(PackArchiveEntry entry) {
        DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
        if (installed == null) {
            return Component.translatable("dlc_manager.pack.pending_install");
        }
        int compare = DlcPackMetadata.compareVersionStrings(entry.metadata.version, installed.version);
        if (compare > 0) {
            return Component.translatable("dlc_manager.pack.pending_upgrade");
        }
        if (compare < 0) {
            return Component.translatable("dlc_manager.pack.pending_downgrade");
        }
        return Component.translatable("dlc_manager.pack.pending_replace");
    }

    @Override
    public boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (busy || archiveEntries.isEmpty() || mouseY < getListTop() || mouseY > getListBottom()) {
            return false;
        }
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + (deltaY > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!busy && button == 0) {
            if (isInsideScrollbar(mouseX, mouseY)) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY);
                return true;
            }
            int row = rowAt(mouseY);
            if (row >= 0) {
                int index = scrollIndex + row;
                if (index >= 0 && index < archiveEntries.size()) {
                    return handleEntryClick(archiveEntries.get(index), index, mouseX, mouseY);
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleEntryClick(PackArchiveEntry entry, int index, double mouseX, double mouseY) {
        selectedIndex = index;
        int rowTop = getListTop() + (index - scrollIndex) * ROW_HEIGHT;
        int left = getListLeft();
        int right = getListRight() - SCROLLBAR_WIDTH - 4;
        int arrowX = left + 4;
        if (mouseX >= arrowX && mouseX < arrowX + 12) {
            if (mouseY >= rowTop + 4 && mouseY < rowTop + 24) {
                moveEntry(index, -1);
                return true;
            }
            if (mouseY >= rowTop + 26 && mouseY < rowTop + 48) {
                moveEntry(index, 1);
                return true;
            }
        }
        int buttonX = right - (BUTTON_WIDTH + 5) * 4;
        if (mouseY >= rowTop + 8 && mouseY < rowTop + 8 + BUTTON_HEIGHT) {
            int action = (int) ((mouseX - buttonX) / (BUTTON_WIDTH + 5));
            if (mouseX >= buttonX && action >= 0 && action < 4) {
                if (action == 0) {
                    toggleInstall(entry);
                } else if (action == 1) {
                    deleteArchive(entry);
                } else if (action == 2) {
                    openLink(entry, "issue");
                } else {
                    openLink(entry, "sponsor");
                }
                return true;
            }
        }
        long now = Util.getMillis();
        if (entry.key.equals(lastClickKey) && now - lastClickAt < 350L) {
            openLink(entry, "homepage");
        }
        lastClickKey = entry.key;
        lastClickAt = now;
        rebuildPackWidgets();
        return true;
    }

    private void toggleInstall(PackArchiveEntry entry) {
        String identifier = entry.metadata.identifier;
        if (pendingInstalls.containsKey(identifier) && pendingInstalls.get(identifier) == entry) {
            pendingInstalls.remove(identifier);
        } else if (pendingUninstalls.remove(identifier)) {
            pendingInstalls.put(identifier, entry);
        } else {
            DlcPackMeta installed = installedByIdentifier.get(identifier);
            if (installed != null && entry.metadata.version.equals(installed.version)) {
                pendingUninstalls.add(identifier);
                pendingInstalls.remove(identifier);
            } else {
                pendingInstalls.put(identifier, entry);
            }
        }
        rebuildPackWidgets();
    }

    private void deleteArchive(PackArchiveEntry entry) {
        DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
        if (installed != null && entry.metadata.version.equals(installed.version)) {
            status = Component.translatable("dlc_manager.pack.delete_installed_blocked");
            rebuildPackWidgets();
            return;
        }
        try {
            Files.deleteIfExists(entry.archivePath);
            pendingInstalls.remove(entry.metadata.identifier, entry);
            status = Component.translatable("dlc_manager.pack.delete_success", entry.displayName);
            refreshEntries();
            rebuildPackWidgets();
        } catch (Exception ex) {
            status = Component.translatable("dlc_manager.pack.delete_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private void openLink(PackArchiveEntry entry, String key) {
        String url = entry.metadata.links.get(key);
        if (url == null || url.isBlank()) {
            status = Component.translatable("dlc_manager.pack.link_missing");
            rebuildPackWidgets();
            return;
        }
        Util.getPlatform().openUri(url);
    }

    private void moveEntry(int index, int direction) {
        int target = index + direction;
        if (target < 0 || target >= archiveEntries.size()) {
            return;
        }
        List<PackArchiveEntry> mutable = new ArrayList<>(archiveEntries);
        PackArchiveEntry entry = mutable.remove(index);
        mutable.add(target, entry);
        archiveEntries = mutable;
        selectedIndex = target;
        ensureSelectionVisible();
    }

    private void importPackArchive() {
        if (!DlcPathConfig.isPackFuncOn()) {
            status = Component.translatable("dlc_manager.pack.disabled");
            return;
        }
        Path zip = DlcFilePicker.pickUpdateZip(Component.translatable("dlc_manager.pack.import").getString());
        if (zip == null) {
            return;
        }
        setBusy(true, Component.translatable("dlc_manager.pack.importing"));
        new Thread(() -> {
            try {
                Path copied = copyArchiveToPackRoot(zip);
                finishBusy(Component.translatable("dlc_manager.pack.import_staged", copied.getFileName().toString()));
            } catch (Exception ex) {
                finishBusy(Component.translatable("dlc_manager.pack.import_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }, "dlc-pack-import-copy").start();
    }

    public void importDownloadedPack(Path zip) {
        if (zip != null) {
            status = Component.translatable("dlc_manager.pack.import_staged", zip.getFileName().toString());
            refreshEntries();
            rebuildPackWidgets();
        }
    }

    private Path copyArchiveToPackRoot(Path source) throws IOException {
        Path packRoot = DlcPaths.dlcRoot().resolve("pack");
        Files.createDirectories(packRoot);
        String fileName = source.getFileName() == null ? "pack.zip" : source.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileName = fileName + ".zip";
        }
        Path target = uniquePath(packRoot, fileName);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private Path uniquePath(Path dir, String fileName) {
        Path target = dir.resolve(fileName);
        if (!Files.exists(target)) {
            return target;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            Path candidate = dir.resolve(base + "-" + i + ext);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return dir.resolve(base + "-" + System.nanoTime() + ext);
    }

    private void applyPendingChanges() {
        if (!hasPendingChanges() || busy) {
            return;
        }
        String confirmText = buildReplacementConfirmText();
        if (confirmText != null) {
            Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(accepted -> {
                Minecraft.getInstance().setScreen(this);
                if (accepted) {
                    startApplyPendingChanges();
                }
            }, Component.translatable("dlc_manager.pack.apply_confirm_title"), Component.literal(confirmText), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
            return;
        }
        startApplyPendingChanges();
    }

    private void startApplyPendingChanges() {
        setBusy(true, Component.translatable("dlc_manager.pack.applying"));
        new Thread(() -> {
            try {
                DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                for (String identifier : new ArrayList<>(pendingUninstalls)) {
                    installer.uninstall(identifier);
                }
                for (PackArchiveEntry entry : archiveEntries) {
                    if (pendingInstalls.get(entry.metadata.identifier) == entry) {
                        installArchive(installer, entry);
                    }
                }
                pendingUninstalls.clear();
                pendingInstalls.clear();
                finishBusy(Component.translatable("dlc_manager.pack.apply_success"));
            } catch (Exception ex) {
                finishBusy(Component.translatable("dlc_manager.pack.apply_failed", describeFailure(ex)));
            }
        }, "dlc-pack-apply").start();
    }

    private String buildReplacementConfirmText() {
        List<String> lines = new ArrayList<>();
        for (PackArchiveEntry entry : archiveEntries) {
            if (pendingInstalls.get(entry.metadata.identifier) != entry) {
                continue;
            }
            DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
            if (installed == null) {
                continue;
            }
            int compare = DlcPackMetadata.compareVersionStrings(entry.metadata.version, installed.version);
            if (compare > 0) {
                lines.add(Component.translatable("dlc_manager.pack.apply_confirm_upgrade", entry.displayName, installed.version, entry.metadata.version).getString());
            } else if (compare < 0) {
                lines.add(Component.translatable("dlc_manager.pack.apply_confirm_downgrade", entry.displayName, installed.version, entry.metadata.version).getString());
            } else {
                lines.add(Component.translatable("dlc_manager.pack.apply_confirm_replace", entry.displayName, installed.version).getString());
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private void installArchive(DlcPackInstaller installer, PackArchiveEntry entry) throws IOException {
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(entry.archivePath, DlcPaths.dlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (!result.valid || result.pack == null) {
                throw new IOException(result.error == null ? "invalid DLC Pack" : result.error);
            }
            DlcPackInstaller.PrecheckResult precheck = installer.precheck(result.pack);
            if (precheck.hasBlockingIssues() || precheck.requiresRuntimeConfirmation()) {
                throw new IOException(DlcConfirmUtil.describePackBlockingPrecheck(precheck));
            }
            boolean replace = installedByIdentifier.containsKey(result.pack.metadata.identifier);
            installer.install(result.pack, replace, DlcPackInstaller.InstallOptions.strict());
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private String describeFailure(Exception ex) {
        if (ex instanceof IOException io) {
            String message = io.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        String message = ex.getMessage();
        return (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
    }

    private void setBusy(boolean busy, Component message) {
        this.busy = busy;
        this.status = message;
        rebuildPackWidgets();
    }

    private void finishBusy(Component message) {
        Minecraft.getInstance().execute(() -> {
            busy = false;
            status = message;
            refreshEntries();
            rebuildPackWidgets();
        });
    }

    private void onBackOrCancel() {
        if (hasPendingChanges()) {
            pendingInstalls.clear();
            pendingUninstalls.clear();
            status = Component.translatable("dlc_manager.pack.changes_cancelled");
            rebuildPackWidgets();
            return;
        }
        releaseIconTextures();
        Minecraft.getInstance().setScreen(parent);
    }

    private void refreshEntries() {
        Map<String, Integer> oldOrder = new HashMap<>();
        for (int i = 0; i < archiveEntries.size(); i++) {
            oldOrder.put(archiveEntries.get(i).key, i);
        }
        Map<String, String> pendingInstallKeys = new HashMap<>();
        for (Map.Entry<String, PackArchiveEntry> entry : pendingInstalls.entrySet()) {
            pendingInstallKeys.put(entry.getKey(), entry.getValue().key);
        }
        releaseIconTextures();
        uninstallIncompatibleInstalledPacks();
        installedByIdentifier = loadInstalledPacks();
        List<PackArchiveEntry> scanned = scanPackArchives();
        scanned.sort(Comparator.comparingInt(entry -> oldOrder.getOrDefault(entry.key, Integer.MAX_VALUE)));
        archiveEntries = scanned;
        pendingInstalls.clear();
        for (PackArchiveEntry entry : archiveEntries) {
            if (entry.key.equals(pendingInstallKeys.get(entry.metadata.identifier))) {
                pendingInstalls.put(entry.metadata.identifier, entry);
            }
        }
        pendingUninstalls.removeIf(identifier -> !installedByIdentifier.containsKey(identifier));
        if (selectedIndex >= archiveEntries.size()) {
            selectedIndex = archiveEntries.isEmpty() ? -1 : archiveEntries.size() - 1;
        }
        clampScroll();
    }

    private List<PackArchiveEntry> scanPackArchives() {
        Path packRoot = DlcPaths.dlcRoot().resolve("pack");
        try {
            Files.createDirectories(packRoot);
        } catch (IOException ignored) {
        }
        if (!Files.isDirectory(packRoot)) {
            return List.of();
        }
        List<PackArchiveEntry> out = new ArrayList<>();
        try (var stream = Files.list(packRoot)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .forEach(path -> {
                    PackArchiveEntry entry = inspectArchive(path);
                    if (entry != null) {
                        out.add(entry);
                    }
                });
        } catch (Exception ignored) {
        }
        return out;
    }

    private PackArchiveEntry inspectArchive(Path archive) {
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(archive, DlcPaths.dlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (!result.valid || result.pack == null) {
                return null;
            }
            DlcPackPackage pack = result.pack;
            String displayName = pack.metadata.resolveDisplayName(Minecraft.getInstance().options.languageCode);
            String authorText = pack.metadata.authors.isEmpty() ? Component.translatable("dlc_manager.pack.unknown_author").getString() : String.join(", ", pack.metadata.authors);
            IconTexture icon = loadIconTexture(pack.iconFile);
            return new PackArchiveEntry(archive.toAbsolutePath().normalize(), pack.metadata, displayName, authorText, icon == null ? null : icon.location, icon == null ? 0 : icon.width, icon == null ? 0 : icon.height);
        } catch (Exception ignored) {
            return null;
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private IconTexture loadIconTexture(Path iconFile) {
        if (iconFile == null || !Files.isRegularFile(iconFile)) {
            return null;
        }
        NativeImage image = null;
        try (InputStream input = Files.newInputStream(iconFile)) {
            image = NativeImage.read(input);
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            DynamicTexture texture = new DynamicTexture(image);
            image = null;
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register("dlc_pack_icon/" + (++iconTextureSerial), texture);
            return new IconTexture(location, imageWidth, imageHeight);
        } catch (Exception ignored) {
            if (image != null) {
                image.close();
            }
            return null;
        }
    }

    private void releaseIconTextures() {
        for (PackArchiveEntry entry : archiveEntries) {
            if (entry.iconTexture != null) {
                Minecraft.getInstance().getTextureManager().release(entry.iconTexture);
            }
        }
    }

    private Map<String, DlcPackMeta> loadInstalledPacks() {
        Path packRoot = DlcPaths.dlcRoot().resolve("tmp").resolve("pack");
        Map<String, DlcPackMeta> out = new HashMap<>();
        if (!Files.isDirectory(packRoot)) {
            return out;
        }
        try (var stream = Files.list(packRoot)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                DlcPackMeta meta = DlcPackMeta.load(dir.resolve("meta.dc"));
                if (meta != null) {
                    out.put(meta.identifier, meta);
                }
            });
        } catch (Exception ignored) {
        }
        return out;
    }

    private void uninstallIncompatibleInstalledPacks() {
        try {
            List<DlcPackInstaller.UninstallReport> reports =
                new DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot()).uninstallIncompatibleForCurrentVersion();
            if (!reports.isEmpty()) {
                status = Component.translatable("dlc_manager.pack.auto_uninstalled_incompatible", reports.size());
            }
        } catch (Exception ex) {
            status = Component.translatable("dlc_manager.pack.auto_uninstall_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private boolean hasPendingChanges() {
        return !pendingInstalls.isEmpty() || !pendingUninstalls.isEmpty();
    }

    private boolean canMoveUp(int index) {
        return index > 0;
    }

    private boolean canMoveDown(int index) {
        return index >= 0 && index < archiveEntries.size() - 1;
    }

    private int getListLeft() {
        return Math.max(14, this.width / 2 - 235);
    }

    private int getListRight() {
        return Math.min(this.width - 14, this.width / 2 + 235);
    }

    private int getListTop() {
        return 54;
    }

    private int getListBottom() {
        return Math.max(getListTop() + ROW_HEIGHT, this.height - 42);
    }

    private int getVisibleCount() {
        return Math.max(1, (getListBottom() - getListTop()) / ROW_HEIGHT);
    }

    private int getMaxScroll() {
        return Math.max(0, archiveEntries.size() - getVisibleCount());
    }

    private int rowAt(double mouseY) {
        int top = getListTop();
        int bottom = getListBottom();
        if (mouseY < top || mouseY >= bottom) {
            return -1;
        }
        int row = (int) ((mouseY - top) / ROW_HEIGHT);
        return row < getVisibleCount() ? row : -1;
    }

    private void clampScroll() {
        scrollIndex = Math.max(0, Math.min(getMaxScroll(), scrollIndex));
    }

    private void ensureSelectionVisible() {
        if (selectedIndex < 0) {
            return;
        }
        int visible = getVisibleCount();
        if (selectedIndex < scrollIndex) {
            scrollIndex = selectedIndex;
        } else if (selectedIndex >= scrollIndex + visible) {
            scrollIndex = Math.max(0, selectedIndex - visible + 1);
        }
        clampScroll();
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        int x = getListRight() - SCROLLBAR_WIDTH;
        return mouseX >= x && mouseX <= x + SCROLLBAR_WIDTH && mouseY >= getListTop() && mouseY <= getListBottom();
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int top = getListTop();
        int track = Math.max(1, getListBottom() - top);
        double ratio = Math.max(0.0, Math.min(1.0, (mouseY - top) / track));
        scrollIndex = Math.max(0, Math.min(maxScroll, (int) Math.round(ratio * maxScroll)));
    }

    private void drawScrollBar(GuiGraphics guiGraphics) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int top = getListTop();
        int bottom = getListBottom();
        int trackHeight = Math.max(1, bottom - top);
        int barHeight = Math.max(18, trackHeight * getVisibleCount() / Math.max(1, archiveEntries.size()));
        int barY = top + (trackHeight - barHeight) * scrollIndex / Math.max(1, maxScroll);
        int x = getListRight() - SCROLLBAR_WIDTH;
        guiGraphics.fill(x, top, x + SCROLLBAR_WIDTH, bottom, 0x66333333);
        guiGraphics.fill(x, barY, x + SCROLLBAR_WIDTH, barY + barHeight, 0xFF66CCFF);
    }

    private String trimToWidth(String text, int width) {
        if (text == null) {
            return "";
        }
        if (this.font.width(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        String value = text;
        while (!value.isEmpty() && this.font.width(value + ellipsis) > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }

    private void rebuildPackWidgets() {
        init(this.minecraft, this.width, this.height);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !busy;
    }

    @Override
    public void onClose() {
        if (!busy) {
            onBackOrCancel();
        }
    }

    private static final class IconTexture {
        private final ResourceLocation location;
        private final int width;
        private final int height;

        private IconTexture(ResourceLocation location, int width, int height) {
            this.location = location;
            this.width = width;
            this.height = height;
        }
    }

    private static final class PackArchiveEntry {
        private final Path archivePath;
        private final DlcPackMetadata metadata;
        private final String displayName;
        private final String authorText;
        private final ResourceLocation iconTexture;
        private final int iconWidth;
        private final int iconHeight;
        private final String key;

        private PackArchiveEntry(Path archivePath, DlcPackMetadata metadata, String displayName, String authorText, ResourceLocation iconTexture, int iconWidth, int iconHeight) {
            this.archivePath = archivePath;
            this.metadata = metadata;
            this.displayName = displayName == null ? metadata.identifier : displayName;
            this.authorText = authorText == null ? "" : authorText;
            this.iconTexture = iconTexture;
            this.iconWidth = iconWidth;
            this.iconHeight = iconHeight;
            this.key = archivePath.toString() + "#" + metadata.identifier + "@" + metadata.version;
        }
    }
}
