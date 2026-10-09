package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcFilePicker;
import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.client.pack.DlcPackMetadata;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerPackInfo;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DlcServerPackManagerScreen extends DlcPackPageScreen {
    private static final int TIMEOUT_MS = 8000;





    private static final int BUTTON_HEIGHT = 16;













    private final Screen parent;
    private final DlcServerEntry entry;
    private Button importButton;
    private Button urlButton;
    private Button applyButton;
    private Button backButton;
    private Button refreshButton;
    private final Map<String, PackEntry> pendingInstalls = new LinkedHashMap<>();
    private final Set<String> pendingUninstalls = new LinkedHashSet<>();
    private List<PackGroup> archiveGroups = List.of();
    private List<String> entryOrder = List.of();
    private List<String> currentOrder = List.of();
    private final Map<String, String> selectedVersionByIdentifier = new LinkedHashMap<>();
    private int scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarDragOffset;
    private String versionDropdownIdentifier;
    private Component status;
    private String lastClickKey;
    private long lastClickAt;
    private volatile boolean loading;
    private volatile boolean working;
    private int iconTextureSerial;

    public DlcServerPackManagerScreen(Screen parent, DlcServerEntry entry) {
        super(DlcUiPage.SERVER_PACKS, parent, Component.translatable("dlc_manager.server.pack.manager_title"));
        this.parent = parent;
        this.entry = entry;
    }

    private void initControls() {
        if (!working) {
            refresh();
        }
        rebuildButtons();
    }

    public void refresh() {
        refresh(null);
    }

    public void refresh(Component finalStatus) {
        loading = true;
        status = Component.translatable("dlc_manager.server.files_loading");
        new Thread(() -> {
            DlcServerConnection.PackListResponse result;
            try {
                result = DlcServerConnection.fetchPackList(entry, TIMEOUT_MS);
            } catch (Exception ex) {
                result = null;
            }
            DlcServerConnection.PackListResponse finalResult = result;
            Minecraft.getInstance().execute(() -> {
                loading = false;
                if (finalResult == null) {
                    releaseIconTextures();
                    archiveGroups = List.of();
                    entryOrder = List.of();
                    currentOrder = List.of();
                    status = Component.translatable("dlc_manager.server.pack.load_failed");
                } else {
                    rebuildEntries(finalResult.packs, finalResult.order);
                    int count = 0;
                    for (PackGroup group : archiveGroups) {
                        count += group.versions.size();
                    }
                    status = finalStatus == null ? Component.translatable("dlc_manager.pack.archive_count", count) : finalStatus;
                }
                rebuildButtons();
            });
        }, "dlc-server-pack-refresh").start();
    }

    private void rebuildButtons() {
        clearWidgets();
        int buttonWidth = 104;
        int buttonHeight = 20;
        int gap = 8;
        int total = buttonWidth * 4 + gap * 3;
        int startX = this.width / 2 - total / 2;
        int y = this.height - 28;
        importButton = Button.builder(Component.translatable("dlc_manager.pack.import"), btn -> importPack())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        urlButton = Button.builder(Component.translatable("dlc_manager.pack.import_url"), btn -> Minecraft.getInstance().setScreen(new DlcServerPackUrlScreen(this, entry)))
            .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
            .build();
        applyButton = Button.builder(Component.translatable("dlc_manager.pack.apply"), btn -> applyPendingChanges())
            .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
            .build();
        Component backLabel = hasPendingChanges() ? Component.translatable("dlc_manager.pack.cancel_changes") : Component.translatable("dlc_manager.pack.back");
        backButton = Button.builder(backLabel, btn -> onBackOrCancel())
            .bounds(startX + (buttonWidth + gap) * 3, y, buttonWidth, buttonHeight)
            .build();
        refreshButton = Button.builder(Component.translatable("dlc_manager.server.files_refresh"), btn -> refresh())
            .bounds(this.width - 24 - 70, 8, 70, 20)
            .build();
        addRenderableWidget(importButton);
        addRenderableWidget(urlButton);
        addRenderableWidget(applyButton);
        addRenderableWidget(backButton);
        addRenderableWidget(refreshButton);
        updateButtonState();
    }

    private void updateButtonState() {
        boolean active = !loading && !working;
        if (importButton != null) importButton.active = active;
        if (urlButton != null) urlButton.active = active;
        if (applyButton != null) applyButton.active = active && hasPendingChanges();
        if (backButton != null) backButton.active = !working;
        if (refreshButton != null) refreshButton.active = active && !hasPendingChanges();
    }

    private void drawSizedButton(GuiGraphics guiGraphics, int x, int y, int width, Component label, int mouseX, int mouseY) {
        drawSizedButton(guiGraphics, x, y, width, label, mouseX, mouseY, 0xFFFFFF);
    }

    private void drawSizedButton(GuiGraphics guiGraphics, int x, int y, int width, Component label, int mouseX, int mouseY, int textColor) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        guiGraphics.fill(x, y, x + width, y + BUTTON_HEIGHT, hover ? 0xFF5A5A5A : 0xFF404040);
        guiGraphics.drawCenteredString(this.font, label, x + width / 2, y + 4, textColor);
    }

    private void importPack() {
        Path zip = DlcFilePicker.pickAnyFile(Component.translatable("dlc_manager.pack.import").getString());
        if (zip == null) return;
        startWork(Component.translatable("dlc_manager.pack.importing"), Component.translatable("dlc_manager.pack.import_staged", zip.getFileName().toString()), false, () -> {
            try {
                boolean ok = DlcServerConnection.importPackPackage(entry, TIMEOUT_MS, zip);
                return ok ? null : Component.translatable("dlc_manager.pack.import_failed", "upload failed");
            } catch (Exception ex) {
                return Component.translatable("dlc_manager.pack.import_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        });
    }

    private void applyPendingChanges() {
        if (!hasPendingChanges()) return;
        if (pendingInstalls.isEmpty() && pendingUninstalls.isEmpty()) {
            startWork(Component.translatable("dlc_manager.pack.applying"), Component.translatable("dlc_manager.pack.order_applied"), false, () -> {
                try {
                    DlcServerConnection.UpdateResponse orderResponse = DlcServerConnection.savePackOrder(entry, TIMEOUT_MS, currentOrder);
                    if (orderResponse == null || !orderResponse.ok) {
                        return Component.translatable("dlc_manager.pack.apply_failed", orderResponse == null ? "save order failed" : orderResponse.message);
                    }
                    entryOrder = new ArrayList<>(currentOrder);
                    return null;
                } catch (Exception ex) {
                    return Component.translatable("dlc_manager.pack.apply_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                }
            });
            return;
        }
        working = true;
        updateButtonState();
        Minecraft.getInstance().setScreen(new DlcServerPackApplyScreen(
            this,
            entry,
            new ArrayList<>(currentOrder),
            new ArrayList<>(pendingUninstalls),
            pendingInstalls.values().stream().map(pack -> pack.archivePath).toList()
        ));
    }

    public void finishServerPackApply(boolean success, String message) {
        working = false;
        if (success) {
            pendingInstalls.clear();
            pendingUninstalls.clear();
            refresh(Component.translatable("dlc_manager.pack.apply_success"));
        } else {
            status = Component.translatable("dlc_manager.pack.apply_failed", message == null ? "failed" : message);
            rebuildButtons();
        }
    }

    public void showServerRestartPrompt() {
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(restart -> {
            if (restart) {
                new Thread(() -> {
                    try {
                        DlcServerConnection.restartServer(entry, TIMEOUT_MS);
                    } catch (Exception ignored) {
                    }
                }, "dlc-server-pack-restart").start();
            }
            Minecraft.getInstance().setScreen(this);
        }, Component.translatable("dlc_manager.server.restart_title"), Component.translatable("dlc_manager.server.restart_prompt"),
            Component.translatable("dlc_manager.restart_now"), Component.translatable("dlc_manager.restart_later")));
    }

    private void startWork(Component workingText, WorkAction action) {
        startWork(workingText, Component.translatable("dlc_manager.pack.apply_success"), false, action);
    }

    private void startWork(Component workingText, Component successText, boolean clearPendingOnSuccess, WorkAction action) {
        if (working) return;
        working = true;
        status = workingText;
        updateButtonState();
        new Thread(() -> {
            Component error = action.run();
            Minecraft.getInstance().execute(() -> {
                working = false;
                if (error == null) {
                    if (clearPendingOnSuccess) {
                        pendingInstalls.clear();
                        pendingUninstalls.clear();
                    }
                    refresh(successText);
                } else {
                    status = error;
                    rebuildButtons();
                }
            });
        }, "dlc-server-pack-work").start();
    }

    private void toggleInstall(PackGroup group) {
        PackEntry pack = group.displayEntry();
        if (pendingInstalls.get(pack.identifier) == pack) {
            pendingInstalls.remove(pack.identifier);
            rebuildButtons();
            return;
        }
        if (pendingUninstalls.remove(pack.identifier)) {
            rebuildButtons();
            return;
        }
        if (isEntryCurrentlyInstalled(pack)) {
            pendingUninstalls.add(pack.identifier);
        } else {
            pendingInstalls.put(pack.identifier, pack);
            pendingUninstalls.remove(pack.identifier);
        }
        rebuildButtons();
    }

    private void confirmDelete(PackEntry pack) {
        Component prompt = isIdentifierInstalled(pack.identifier)
            ? Component.translatable("dlc_manager.pack.delete_confirm_uninstall", pack.displayNameText)
            : Component.translatable("dlc_manager.pack.delete_confirm", pack.displayNameText);
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) {
                Component successText = isIdentifierInstalled(pack.identifier)
                    ? Component.translatable("dlc_manager.pack.delete_uninstalled_success", pack.displayNameText)
                    : Component.translatable("dlc_manager.pack.delete_success", pack.displayNameText);
                startWork(Component.translatable("dlc_manager.pack.delete"), successText, false, () -> {
                    try {
                        DlcServerConnection.UpdateResponse response = DlcServerConnection.deletePack(entry, TIMEOUT_MS, pack.archivePath);
                        return response == null || !response.ok
                            ? Component.translatable("dlc_manager.pack.delete_failed", response == null ? pack.displayNameText : response.message)
                            : null;
                    } catch (Exception ex) {
                        return Component.translatable("dlc_manager.pack.delete_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                    }
                });
            }
        }, Component.translatable("dlc_manager.pack.delete_confirm_title"), prompt, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void confirmOpenLink(PackEntry pack, String key) {
        String url = pack.links.get(key);
        if (url == null || url.isBlank()) return;
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) {
                Util.getPlatform().openUri(url);
            }
        }, Component.translatable("dlc_manager.pack.open_link_title"), Component.translatable("dlc_manager.pack.open_link_prompt", url), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private boolean moveGroup(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= archiveGroups.size()) return true;
        List<PackGroup> groups = new ArrayList<>(archiveGroups);
        PackGroup moved = groups.remove(index);
        groups.add(target, moved);
        archiveGroups = groups;
        currentOrder = groups.stream().map(group -> group.orderKey).toList();
        saveOrderLive(currentOrder);

        rebuildButtons();
        return true;
    }

    private void saveOrderLive(List<String> order) {
        new Thread(() -> {
            try {
                DlcServerConnection.savePackOrder(entry, TIMEOUT_MS, order);
            } catch (Exception ignored) {
            }
        }, "dlc-server-pack-order").start();
    }

    private void onBackOrCancel() {
        if (hasPendingChanges()) {
            pendingInstalls.clear();
            pendingUninstalls.clear();
            currentOrder = List.copyOf(entryOrder);
            saveOrderLive(entryOrder);
            sortGroupsByCurrentOrder();
            status = Component.translatable("dlc_manager.pack.changes_cancelled");
            rebuildButtons();
            return;
        }
        returnToParent();
    }

    private void returnToParent() {
        releaseIconTextures();
        Minecraft.getInstance().setScreen(parent);
    }

    private void rebuildEntries(List<DlcServerPackInfo> packs, List<String> order) {
        Map<String, String> pendingInstallKeys = new HashMap<>();
        for (Map.Entry<String, PackEntry> entry : pendingInstalls.entrySet()) {
            pendingInstallKeys.put(entry.getKey(), entry.getValue().key);
        }
        releaseIconTextures();
        List<PackEntry> entries = new ArrayList<>();
        if (packs != null) {
            for (DlcServerPackInfo info : packs) {
                PackEntry entry = toEntry(info);
                if (!entry.identifier.isBlank() && !entry.version.isBlank() && !entry.archivePath.isBlank()) {
                    entries.add(entry);
                }
            }
        }
        List<PackGroup> groups = groupEntries(entries);
        entryOrder = normalizeGroupOrder(order, groups);
        currentOrder = List.copyOf(entryOrder);
        archiveGroups = groups;
        normalizeSelections();
        sortGroupsByCurrentOrder();
        pendingInstalls.clear();
        for (PackGroup group : archiveGroups) {
            PackEntry pack = group.displayEntry();
            if (!isEntryCurrentlyInstalled(pack) && pack.key.equals(pendingInstallKeys.get(pack.identifier))) {
                pendingInstalls.put(pack.identifier, pack);
            }
        }
        pendingInstalls.keySet().removeIf(identifier -> archiveGroups.stream().noneMatch(group -> group.identifier.equals(identifier)));
        pendingUninstalls.removeIf(identifier -> archiveGroups.stream().noneMatch(group -> group.identifier.equals(identifier)));

    }

    private PackEntry toEntry(DlcServerPackInfo info) {
        String identifier = info.identifier == null ? "" : info.identifier;
        String version = info.version == null ? "" : info.version;
        String archivePath = info.archivePath == null ? "" : info.archivePath;
        String orderKey = info.orderKey != null && !info.orderKey.isBlank() ? info.orderKey : archivePath + "#" + identifier + "@" + version;
        Component displayName = DlcTextFormatter.parse(info.displayName != null && !info.displayName.isBlank() ? info.displayName : identifier);
        Component description = DlcMarkdownRenderer.renderMarkdownComponent(info.description);
        List<String> authors = info.authors == null ? List.of() : info.authors.stream().filter(value -> value != null && !value.isBlank()).toList();
        if (authors.isEmpty()) {
            authors = List.of(Component.translatable("dlc_manager.pack.unknown_author").getString());
        }
        return new PackEntry(archivePath, orderKey, identifier, version, displayName, description, authors, info.links == null ? Map.of() : info.links,
            info.installed, info.installedVersion == null ? "" : info.installedVersion, loadIconTexture(info.iconBase64));
    }

    private List<PackGroup> groupEntries(List<PackEntry> entries) {
        Map<String, List<PackEntry>> byIdentifier = new LinkedHashMap<>();
        Map<String, List<PackEntry>> mutable = new LinkedHashMap<>();
        for (PackEntry entry : entries) {
            mutable.computeIfAbsent(entry.identifier, key -> new ArrayList<>()).add(entry);
        }
        byIdentifier.putAll(mutable);
        List<PackGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<PackEntry>> mapEntry : byIdentifier.entrySet()) {
            List<PackEntry> versions = new ArrayList<>(mapEntry.getValue());
            versions.sort((a, b) -> {
                int compare = DlcPackMetadata.compareVersionStrings(b.version, a.version);
                return compare != 0 ? compare : a.archivePath.compareToIgnoreCase(b.archivePath);
            });
            PackEntry installed = versions.stream().filter(pack -> pack.installed).findFirst().orElse(null);
            PackEntry orderEntry = installed == null ? versions.get(0) : installed;
            String selected = selectedVersionByIdentifier.get(mapEntry.getKey());
            PackEntry display = versions.stream().filter(pack -> pack.key.equals(selected) || pack.version.equals(selected)).findFirst().orElse(orderEntry);
            groups.add(new PackGroup(mapEntry.getKey(), versions, orderEntry.key, display.key));
        }
        return groups;
    }

    private void normalizeSelections() {
        Set<String> valid = new HashSet<>();
        for (PackGroup group : archiveGroups) {
            valid.add(group.identifier);
        }
        selectedVersionByIdentifier.keySet().removeIf(key -> !valid.contains(key));
        List<PackGroup> out = new ArrayList<>();
        for (PackGroup group : archiveGroups) {
            String selected = selectedVersionByIdentifier.get(group.identifier);
            PackEntry display = group.versions.stream().filter(pack -> pack.key.equals(selected) || pack.version.equals(selected)).findFirst().orElse(group.displayEntry());
            selectedVersionByIdentifier.put(group.identifier, display.key);
            out.add(new PackGroup(group.identifier, group.versions, group.orderKey, display.key));
        }
        archiveGroups = out;
    }

    private List<String> normalizeGroupOrder(List<String> savedOrder, List<PackGroup> groups) {
        List<String> out = new ArrayList<>();
        if (savedOrder != null) {
            for (String key : savedOrder) {
                String identifier = identifierFromOrderKey(key);
                if (identifier == null) continue;
                PackGroup group = groups.stream().filter(candidate -> candidate.identifier.equals(identifier)).findFirst().orElse(null);
                if (group != null && !out.contains(group.orderKey)) {
                    out.add(group.orderKey);
                }
            }
        }
        for (PackGroup group : groups) {
            if (!out.contains(group.orderKey)) {
                out.add(group.orderKey);
            }
        }
        return out;
    }

    private void sortGroupsByCurrentOrder() {
        archiveGroups = archiveGroups.stream()
            .sorted(Comparator.comparingInt(group -> {
                int index = currentOrder.indexOf(group.orderKey);
                return index < 0 ? Integer.MAX_VALUE : index;
            }))
            .toList();
    }

    private String identifierFromOrderKey(String key) {
        if (key == null || key.isBlank()) return null;
        int hash = key.lastIndexOf('#');
        int at = key.lastIndexOf('@');
        if (hash < 0 || at <= hash + 1) return null;
        return key.substring(hash + 1, at);
    }

    private IconTexture loadIconTexture(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        NativeImage image = null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
                image = NativeImage.read(input);
            }
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            DynamicTexture texture = new DynamicTexture(image);
            image = null;
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register("dlc_server_pack_icon/" + (++iconTextureSerial), texture);
            return new IconTexture(location, imageWidth, imageHeight);
        } catch (Exception ignored) {
            if (image != null) image.close();
            return null;
        }
    }

    private void releaseIconTextures() {
        for (PackGroup group : archiveGroups) {
            for (PackEntry pack : group.versions) {
                if (pack.iconTexture != null) {
                    Minecraft.getInstance().getTextureManager().release(pack.iconTexture.location);
                }
            }
        }
    }

    private boolean hasPendingChanges() {
        return !pendingInstalls.isEmpty() || !pendingUninstalls.isEmpty() || !currentOrder.equals(entryOrder);
    }

    private boolean isEntryCurrentlyInstalled(PackEntry entry) {
        return entry.installed && entry.installedVersion.equals(entry.version);
    }

    private boolean isIdentifierInstalled(String identifier) {
        return archiveGroups.stream().anyMatch(group -> group.identifier.equals(identifier) && group.versions.stream().anyMatch(pack -> pack.installed));
    }

    private Component installButtonLabel(PackGroup group) {
        PackEntry pack = group.displayEntry();
        PackEntry installed = group.versions.stream().filter(candidate -> candidate.installed).findFirst().orElse(null);
        int compare = installed == null ? 0 : DlcPackMetadata.compareVersionStrings(pack.version, installed.installedVersion.isBlank() ? installed.version : installed.installedVersion);
        if (pendingInstalls.get(pack.identifier) == pack) {
            if (installed == null) return Component.translatable("dlc_manager.pack.pending_install");
            if (compare > 0) return Component.translatable("dlc_manager.pack.pending_upgrade");
            if (compare < 0) return Component.translatable("dlc_manager.pack.pending_downgrade");
            return Component.translatable("dlc_manager.pack.pending_install");
        }
        if (pendingUninstalls.contains(pack.identifier)) return Component.translatable("dlc_manager.pack.pending_uninstall");
        if (installed != null && compare == 0) return Component.translatable("dlc_manager.pack.uninstall");
        if (installed != null) return compare > 0 ? Component.translatable("dlc_manager.pack.upgrade") : Component.translatable("dlc_manager.pack.downgrade");
        return Component.translatable("dlc_manager.pack.install");
    }

    private List<RowAction> actionButtons(PackGroup group) {
        PackEntry pack = group.displayEntry();
        List<RowAction> out = new ArrayList<>();
        out.add(RowAction.INSTALL);
        out.add(RowAction.DELETE);
        String issue = pack.links.get("issue");
        if (issue != null && !issue.isBlank()) out.add(RowAction.FEEDBACK);
        String sponsor = pack.links.get("sponsor");
        if (sponsor != null && !sponsor.isBlank()) out.add(RowAction.SUPPORT);
        return out;
    }

    private Component actionLabel(PackGroup group, RowAction action) {
        return switch (action) {
            case INSTALL -> installButtonLabel(group);
            case DELETE -> Component.translatable("dlc_manager.pack.delete");
            case FEEDBACK -> Component.translatable("dlc_manager.pack.feedback");
            case SUPPORT -> Component.translatable("dlc_manager.pack.support_author");
            case MOVE_UP -> Component.literal("▲");
            case MOVE_DOWN -> Component.literal("▼");
        };
    }

    private Component versionOptionLabel(PackEntry entry) {
        return entry.installed ? Component.translatable("dlc_manager.pack.version_installed", entry.version) : Component.literal(entry.version);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !working;
    }

    @Override
    public void onClose() {
        if (!working) onBackOrCancel();
    }

    private interface WorkAction {
        Component run();
    }

    private record IconTexture(ResourceLocation location, int width, int height) {
    }

    private static final class PackEntry {
        private final String archivePath;
        private final String key;
        private final String identifier;
        private final String version;
        private final Component displayName;
        private final Component description;
        private final List<String> authorNames;
        private final Map<String, String> links;
        private final boolean installed;
        private final String installedVersion;
        private final IconTexture iconTexture;
        private final String displayNameText;

        private PackEntry(String archivePath, String key, String identifier, String version, Component displayName, Component description,
                          List<String> authorNames, Map<String, String> links, boolean installed, String installedVersion, IconTexture iconTexture) {
            this.archivePath = archivePath == null ? "" : archivePath;
            this.key = key == null ? "" : key;
            this.identifier = identifier == null ? "" : identifier;
            this.version = version == null ? "" : version;
            this.displayName = displayName == null ? Component.literal(this.identifier) : displayName;
            this.description = description == null ? Component.empty() : description;
            this.authorNames = authorNames == null || authorNames.isEmpty() ? List.of() : List.copyOf(authorNames);
            this.links = links == null ? Map.of() : Map.copyOf(links);
            this.installed = installed;
            this.installedVersion = installedVersion == null ? "" : installedVersion;
            this.iconTexture = iconTexture;
            String rawDisplayName = this.displayName.getString();
            this.displayNameText = rawDisplayName.isBlank() ? this.identifier : rawDisplayName;
        }
    }

    private static final class PackGroup {
        private final String identifier;
        private final List<PackEntry> versions;
        private final String orderKey;
        private final String selectedVersionKey;

        private PackGroup(String identifier, List<PackEntry> versions, String orderKey, String selectedVersionKey) {
            this.identifier = identifier;
            this.versions = List.copyOf(versions);
            this.orderKey = orderKey;
            this.selectedVersionKey = selectedVersionKey;
        }

        private PackEntry displayEntry() {
            for (PackEntry entry : versions) {
                if (entry.key.equals(selectedVersionKey)) {
                    return entry;
                }
            }
            return versions.get(0);
        }
    }

    private enum RowAction {
        INSTALL,
        DELETE,
        FEEDBACK,
        SUPPORT,
        MOVE_UP,
        MOVE_DOWN
    }
    private final PackAdapter<PackGroup> packAdapter = new PackAdapter<>() {
        @Override public PackDetails details(PackGroup group) {
            var pack = group.displayEntry();
            return new PackDetails(pack.displayName, pack.description, pack.authorNames, pack.version, pack.iconTexture == null ? null : pack.iconTexture.location().toString(), group.selectedVersionKey);
        }
        @Override public List<PackVersion> versions(PackGroup group) { return group.versions.stream().map(version -> new PackVersion(version.key, versionOptionLabel(version).getString())).toList(); }
        @Override public List<PackAction> actions(PackGroup group) {
            var pack = group.displayEntry();
            List<PackAction> choices = new ArrayList<>();
            for (var choice : actionButtons(group)) choices.add(new PackAction(actionLabel(group, choice).getString(), choice == RowAction.DELETE, () -> {
                switch (choice) {
                    case INSTALL -> toggleInstall(group);
                    case DELETE -> confirmDelete(pack);
                    case FEEDBACK -> confirmOpenLink(pack, "issue");
                    case SUPPORT -> confirmOpenLink(pack, "sponsor");
                    default -> {}
                }
            }));
            String homepage = pack.links.get("homepage");
            if (homepage != null && !homepage.isBlank()) choices.add(new PackAction(tr("dlc_manager.pack.homepage"), false, () -> confirmOpenLink(pack, "homepage")));
            return choices;
        }
        @Override public void selectVersion(PackGroup group, String key) { selectServerVersion(group, key); }
        @Override public void move(int index, int direction) { moveGroup(index, direction); }
    };
    @Override protected boolean canEditPacks() { return !loading && !working; }

    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        pageRevision = java.util.Objects.hash(archiveGroups, pendingInstalls, pendingUninstalls, loading, working, applyButton);

        if (!controlsReady) { controlsReady = true; initControls(); }
        var list = section(null, null);
        live(content, () -> status == null ? "" : status.getString(), "page-description");

        if (archiveGroups.isEmpty() && !loading) text(list, tr("dlc_manager.ui.empty_packs"), "page-description");
        renderPackRows(list, archiveGroups, packAdapter);
        renderPackFooter(importButton, urlButton, backButton, applyButton, hasPendingChanges());
        mount(toolbar, refreshButton);
    }
    @Override protected void updatePage() { updateButtonState(); if (pageRevision != java.util.Objects.hash(archiveGroups, pendingInstalls, pendingUninstalls, loading, working, applyButton)) refreshPage(); }
    private void selectServerVersion(PackGroup group, String key) { var selected = group.versions.stream().filter(version -> version.key.equals(key)).findFirst().orElse(null); if (selected == null) return; selectedVersionByIdentifier.put(group.identifier, key); archiveGroups = archiveGroups.stream().map(item -> item.identifier.equals(group.identifier) ? new PackGroup(item.identifier, item.versions, item.orderKey, key) : item).toList(); if (pendingInstalls.containsKey(group.identifier) && isEntryCurrentlyInstalled(selected)) pendingInstalls.remove(group.identifier); rebuildButtons(); refreshPage(); }

}
