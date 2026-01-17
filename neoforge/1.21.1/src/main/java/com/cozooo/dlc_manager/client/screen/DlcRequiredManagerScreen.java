package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.required.DlcRequiredDependency;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState;
import com.cozooo.dlc_manager.client.required.DlcRequiredNotice;
import com.cozooo.dlc_manager.client.remote.ModrinthClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.nio.file.StandardCopyOption;

public class DlcRequiredManagerScreen extends Screen {
    private static final ExecutorService INSTALL_EXECUTOR = Executors.newFixedThreadPool(3, r -> {
        Thread thread = new Thread(r, "dlc-required-install");
        thread.setDaemon(true);
        return thread;
    });

    private static final class InstallState {
        private volatile boolean running;
        private volatile boolean done;
        private volatile boolean failed;
        private volatile float progress;
        private volatile String error;
    }

    private final Screen parent;
    private final List<DlcRequiredEntry> allEntries = new ArrayList<>();
    private final List<DlcRequiredEntry> visibleEntries = new ArrayList<>();
    private final Map<String, DlcRequiredEntry> index = new HashMap<>();
    private Map<String, Set<String>> dependents = new HashMap<>();
    private final Map<String, InstallState> installStates = new ConcurrentHashMap<>();
    private volatile boolean batchInstalling;
    private final InstallState batchState = new InstallState();
    private java.util.List<String> noticeLines = java.util.List.of();

    private Button applyAllButton;
    private Button backButton;
    private final Map<String, Button> entryButtons = new HashMap<>();
    private int scrollIndex;

    public DlcRequiredManagerScreen(Screen parent) {
        super(Component.translatable("dlc_manager.required.manage_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildEntries();
        noticeLines = splitLines(DlcRequiredNotice.load().resolveManageDesc());
        int buttonWidth = 140;
        int buttonHeight = 20;
        int y = this.height - 40;
        applyAllButton = Button.builder(Component.translatable("dlc_manager.required.apply_all"), button -> applyAll())
            .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("dlc_manager.back"), button -> handleBack())
            .bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(applyAllButton);
        addRenderableWidget(backButton);
        buildEntryButtons();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = 20;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 18;
        for (String line : getManageDescLines()) {
            guiGraphics.drawCenteredString(this.font, line, centerX, y, 0xC8C8C8);
            y += this.font.lineHeight + 2;
        }
        y += 10;

        int listTop = y;
        int listBottom = this.height - 70;
        int lineHeight = 14;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = visibleEntries.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }

        int rowY = listTop;
        for (int i = 0; i < visibleEntries.size(); i++) {
            DlcRequiredEntry entry = visibleEntries.get(i);
            Button button = entryButtons.get(entry.getIdentifier());
            boolean visible = i >= scrollIndex && i < scrollIndex + maxVisible;
            if (!visible) {
                if (button != null) {
                    button.visible = false;
                }
                continue;
            }
            if (button != null) {
                button.visible = true;
                button.setY(rowY - 2);
            }
            Component label = buildEntryLabel(entry);
            guiGraphics.drawString(this.font, label, 20, rowY, 0xE6E6E6, false);
            if (isMouseOverRow(mouseX, mouseY, rowY, lineHeight) && !isMouseOverButton(button, mouseX, mouseY)) {
                renderTooltip(guiGraphics, entry, mouseX, mouseY);
            }
            rowY += lineHeight;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);

        for (var renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        renderInstallOverlays(guiGraphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int listTop = getListTop();
        int listBottom = this.height - 70;
        int lineHeight = 14;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = visibleEntries.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = deltaY > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private int getListTop() {
        int y = 20 + 18;
        y += getManageDescLines().size() * (this.font.lineHeight + 2);
        y += 10;
        return y;
    }

    private List<String> getManageDescLines() {
        return noticeLines == null ? java.util.List.of() : noticeLines;
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        if (text.contains("\n")) {
            String[] parts = text.split("\\n");
            List<String> lines = new ArrayList<>();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    lines.add(part);
                }
            }
            if (!lines.isEmpty()) {
                return lines;
            }
        }
        return new ArrayList<>(List.of(text));
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        clearWidgets();
        entryButtons.clear();
        init();
    }

    private void rebuildEntries() {
        allEntries.clear();
        allEntries.addAll(DlcRequiredManager.loadAllEntries());
        index.clear();
        for (DlcRequiredEntry entry : allEntries) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id != null && !id.isBlank()) {
                index.putIfAbsent(id, entry);
            }
        }
        dependents = DlcRequiredManager.buildDependents(allEntries);
        visibleEntries.clear();
        for (DlcRequiredEntry entry : allEntries) {
            if (entry == null || entry.isHidden() || !entry.isInList()) {
                continue;
            }
            visibleEntries.add(entry);
        }
        autoCleanupHidden();
    }

    private void autoCleanupHidden() {
        for (DlcRequiredEntry entry : allEntries) {
            if (entry == null || !entry.isHidden() || !entry.isInList()) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id != null && dependents.containsKey(id)) {
                continue;
            }
            if (isApplied(entry)) {
                uninstallEntry(entry);
                DlcRequiredRuntimeState.setOverride(id, false);
            }
        }
    }

    private void buildEntryButtons() {
        int buttonWidth = 72;
        int buttonHeight = 16;
        int x = this.width - 20 - buttonWidth;
        for (DlcRequiredEntry entry : visibleEntries) {
            String id = entry.getIdentifier();
            if (id == null || id.isBlank()) {
                continue;
            }
            String label = getEntryButtonLabel(entry);
            Button button = Button.builder(Component.literal(label), btn -> onEntryAction(entry))
                .bounds(x, 0, buttonWidth, buttonHeight)
                .build();
            entryButtons.put(id, button);
            addRenderableWidget(button);
        }
    }

    private void onEntryAction(DlcRequiredEntry entry) {
        if (entry == null) {
            return;
        }
        InstallState state = installStates.get(entry.getIdentifier());
        if (state != null && state.running) {
            return;
        }
        if (getDesiredState(entry)) {
            uninstallEntry(entry);
            DlcRequiredRuntimeState.setOverride(entry.getIdentifier(), false);
            updateEntryButtons();
            return;
        }
        startInstall(entry, false);
    }

    private void applyAll() {
        if (batchInstalling) {
            return;
        }
        batchInstalling = true;
        if (applyAllButton != null) {
            applyAllButton.active = false;
            applyAllButton.setMessage(Component.literal(""));
        }
        batchState.running = true;
        batchState.done = false;
        batchState.failed = false;
        batchState.progress = 0f;
        batchState.error = null;
        List<DlcRequiredEntry> targets = DlcRequiredManager.collectDependencies(visibleEntries);
        INSTALL_EXECUTOR.submit(() -> {
            boolean didApply = false;
            try {
                didApply = installEntries(targets, batchState);
            } catch (Exception ignored) {
                // Best-effort.
            }
            batchInstalling = false;
            batchState.running = false;
            batchState.done = true;
            boolean shouldPrompt = didApply || DlcRequiredRuntimeState.hasPendingChanges();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (applyAllButton != null) {
                    applyAllButton.active = true;
                    applyAllButton.setMessage(Component.translatable("dlc_manager.required.apply_all"));
                }
                updateEntryButtons();
                if (shouldPrompt) {
                    minecraft.setScreen(new ConfirmScreen(accepted -> {
                        if (accepted) {
                            minecraft.stop();
                        } else {
                            minecraft.setScreen(null);
                        }
                    }, Component.translatable("dlc_manager.title"), Component.translatable("dlc_manager.required.restart_prompt"),
                        Component.translatable("dlc_manager.restart_now"),
                        Component.translatable("dlc_manager.restart_later")));
                }
            });
        });
    }

    private void handleBack() {
        Minecraft.getInstance().setScreen(parent);
    }

    private boolean getDesiredState(DlcRequiredEntry entry) {
        boolean applied = isApplied(entry);
        DlcRequiredRuntimeState.getBaseline(entry.getIdentifier(), applied);
        return DlcRequiredRuntimeState.getDesired(entry.getIdentifier(), applied);
    }

    private boolean isApplied(DlcRequiredEntry entry) {
        if (entry == null) {
            return false;
        }
        List<Path> targets = entry.resolveAppliedTargets();
        if (targets.isEmpty()) {
            return true;
        }
        String fileName = resolveAppliedFileName(entry, targets);
        for (Path target : targets) {
            Path targetFile = resolveTarget(target, fileName);
            if (targetFile == null || !Files.exists(targetFile)) {
                return false;
            }
        }
        return true;
    }

    private String resolveExistingFileName(DlcRequiredEntry entry) {
        String fileName = entry.getRequiredFileName();
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        fileName = entry.getResolvedFileName();
        if (fileName != null && !fileName.isBlank()) {
            Path target = entry.resolveRequiredPath(fileName);
            if (target != null && Files.exists(target) && !isConfigFile(entry, target)) {
                return fileName;
            }
        }
        String base = entry.getBaseName();
        if (entry.getRequiredDir() == null || base == null || base.isBlank()) {
            return fileName;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(entry.getRequiredDir(), base + ".*")) {
            for (Path path : stream) {
                if (isConfigFile(entry, path)) {
                    continue;
                }
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (name.endsWith(".json")) {
                    continue;
                }
                return name;
            }
        } catch (IOException ignored) {
            // Best-effort.
        }
        return fileName;
    }

    private void uninstallEntry(DlcRequiredEntry root) {
        Set<String> removeIds = collectUninstallTargets(root);
        for (String id : removeIds) {
            DlcRequiredEntry entry = index.get(id);
            if (entry != null) {
                boolean desired = getDesiredState(entry);
                if (!desired) {
                    continue;
                }
                DlcRequiredRuntimeState.getBaseline(id, isApplied(entry));
                removeEntryFiles(entry);
                DlcRequiredRuntimeState.setOverride(id, false);
            }
        }
    }

    private Set<String> collectUninstallTargets(DlcRequiredEntry root) {
        Set<String> remove = new HashSet<>();
        if (root == null || root.getIdentifier() == null) {
            return remove;
        }
        Map<String, Set<String>> dependentsMap = DlcRequiredManager.buildDependents(allEntries);
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(root.getIdentifier());
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!remove.add(id)) {
                continue;
            }
            DlcRequiredEntry entry = index.get(id);
            if (entry == null) {
                continue;
            }
            for (DlcRequiredDependency dep : entry.getDependencies()) {
                String depId = dep == null ? null : dep.getIdentifier();
                if (depId == null || depId.isBlank()) {
                    continue;
                }
                DlcRequiredEntry depEntry = index.get(depId);
                if (depEntry != null && !getDesiredState(depEntry)) {
                    continue;
                }
                if (hasOtherDependents(depId, remove, dependentsMap)) {
                    continue;
                }
                queue.add(depId);
            }
            Set<String> ups = dependentsMap.get(id);
            if (ups != null) {
                for (String up : ups) {
                    if (!remove.contains(up)) {
                        DlcRequiredEntry upEntry = index.get(up);
                        if (upEntry != null && !getDesiredState(upEntry)) {
                            continue;
                        }
                        queue.add(up);
                    }
                }
            }
        }
        return remove;
    }

    private boolean hasOtherDependents(String id, Set<String> removing, Map<String, Set<String>> dependentsMap) {
        Set<String> deps = dependentsMap.get(id);
        if (deps == null || deps.isEmpty()) {
            return false;
        }
        for (String dep : deps) {
            DlcRequiredEntry entry = index.get(dep);
            if (!removing.contains(dep) && entry != null && getDesiredState(entry)) {
                return true;
            }
        }
        return false;
    }

    private void removeEntryFiles(DlcRequiredEntry entry) {
        if (entry == null) {
            return;
        }
        List<Path> targets = entry.resolveAppliedTargets();
        if (targets.isEmpty()) {
            return;
        }
        String fileName = resolveAppliedFileName(entry, targets);
        for (Path target : entry.resolveAppliedTargets()) {
            if (target == null) {
                continue;
            }
            Path targetFile;
            String name = target.getFileName() == null ? "" : target.getFileName().toString();
            if (name.endsWith(".jar") || name.endsWith(".zip")) {
                targetFile = target;
            } else {
                targetFile = resolveTarget(target, fileName);
            }
            if (targetFile != null) {
                try {
                    Files.deleteIfExists(targetFile);
                } catch (IOException ignored) {
                    // Best-effort.
                }
            }
        }
    }

    private boolean isConfigFile(DlcRequiredEntry entry, Path path) {
        if (entry == null || path == null) {
            return false;
        }
        Path configPath = entry.getConfigPath();
        if (configPath != null && path.equals(configPath)) {
            return true;
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.endsWith(".json");
    }

    private String resolveAppliedFileName(DlcRequiredEntry entry, List<Path> targets) {
        if (entry == null) {
            return null;
        }
        String fileName = entry.getResolvedFileName();
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        if (targets != null) {
            for (Path target : targets) {
                if (target == null) {
                    continue;
                }
                String name = target.getFileName() == null ? "" : target.getFileName().toString();
                if (name.endsWith(".jar") || name.endsWith(".zip")) {
                    return name;
                }
            }
        }
        String base = entry.getBaseName();
        if (base == null || base.isBlank() || targets == null) {
            return null;
        }
        for (Path target : targets) {
            if (target == null) {
                continue;
            }
            if (Files.isDirectory(target)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(target, base + ".*")) {
                    for (Path path : stream) {
                        String name = path.getFileName() == null ? "" : path.getFileName().toString();
                        if (!name.endsWith(".json")) {
                            return name;
                        }
                    }
                } catch (IOException ignored) {
                    // Best-effort.
                }
            }
        }
        return null;
    }

    private Path resolveTarget(Path targetDir, String fileName) {
        if (targetDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = targetDir.getFileName() == null ? "" : targetDir.getFileName().toString();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir;
        }
        return targetDir.resolve(fileName);
    }

    private Component buildEntryLabel(DlcRequiredEntry entry) {
        String name = entry.resolveDisplayName();
        boolean applied = getDesiredState(entry);
        boolean baseline = DlcRequiredRuntimeState.getBaseline(entry.getIdentifier(), applied);
        boolean pending = DlcRequiredRuntimeState.isPending(entry.getIdentifier(), baseline);
        Component label = Component.literal(name);
        if (applied && pending) {
            label = label.copy().withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC);
        } else if (applied) {
            label = label.copy().withStyle(ChatFormatting.GREEN);
        } else if (pending) {
            label = label.copy().withStyle(ChatFormatting.ITALIC);
        }
        if (pending) {
            label = label.copy().append(Component.translatable("dlc_manager.pending.restart"));
        }
        return label;
    }

    private String getEntryButtonLabel(DlcRequiredEntry entry) {
        return getDesiredState(entry)
            ? Component.translatable("dlc_manager.required.uninstall").getString()
            : Component.translatable("dlc_manager.required.download").getString();
    }

    private void updateEntryButtons() {
        for (DlcRequiredEntry entry : visibleEntries) {
            String id = entry.getIdentifier();
            if (id == null) {
                continue;
            }
            Button button = entryButtons.get(id);
            if (button != null) {
                InstallState state = installStates.get(id);
                if (state != null && state.running) {
                    button.active = false;
                    button.setMessage(Component.literal(""));
                } else {
                    button.active = true;
                    button.setMessage(Component.literal(getEntryButtonLabel(entry)));
                }
            }
        }
    }

    private boolean isMouseOverRow(int mouseX, int mouseY, int y, int height) {
        return mouseX >= 16 && mouseX <= this.width - 16 && mouseY >= y && mouseY <= y + height;
    }

    private boolean isMouseOverButton(Button button, int mouseX, int mouseY) {
        if (button == null || !button.visible) {
            return false;
        }
        return button.isMouseOver(mouseX, mouseY);
    }

    private void renderInstallOverlays(GuiGraphics guiGraphics) {
        if (batchState.running && applyAllButton != null && applyAllButton.visible) {
            drawProgressButton(guiGraphics, applyAllButton.getX(), applyAllButton.getY(),
                applyAllButton.getWidth(), applyAllButton.getHeight(), batchState.progress);
        }
        for (DlcRequiredEntry entry : visibleEntries) {
            String id = entry.getIdentifier();
            if (id == null || id.isBlank()) {
                continue;
            }
            InstallState state = installStates.get(id);
            if (state == null || !state.running) {
                continue;
            }
            Button button = entryButtons.get(id);
            if (button == null || !button.visible) {
                continue;
            }
            drawProgressButton(guiGraphics, button.getX(), button.getY(), button.getWidth(), button.getHeight(), state.progress);
        }
    }

    private void drawProgressButton(GuiGraphics guiGraphics, int x, int y, int width, int height, float progress) {
        int bg = 0xFF2B2B2B;
        int fg = 0xFF4CAF50;
        guiGraphics.fill(x, y, x + width, y + height, bg);
        int filled = (int) (width * Math.max(0f, Math.min(1f, progress)));
        guiGraphics.fill(x, y, x + filled, y + height, fg);
        int percent = Math.round(Math.max(0f, Math.min(1f, progress)) * 100f);
        String label = percent + "%";
        int textX = x + (width - this.font.width(label)) / 2;
        int textY = y + (height - this.font.lineHeight) / 2 + 1;
        guiGraphics.drawString(this.font, label, textX, textY, 0xFFFFFF, false);
    }

    private void startInstall(DlcRequiredEntry entry, boolean promptRestart) {
        if (entry == null || entry.getIdentifier() == null) {
            return;
        }
        String id = entry.getIdentifier();
        InstallState state = installStates.computeIfAbsent(id, key -> new InstallState());
        if (state.running) {
            return;
        }
        state.running = true;
        state.done = false;
        state.failed = false;
        state.progress = 0f;
        state.error = null;
        updateEntryButtons();
        List<DlcRequiredEntry> targets = DlcRequiredManager.collectDependencies(List.of(entry));
        INSTALL_EXECUTOR.submit(() -> {
            boolean didApply = false;
            try {
                didApply = installEntries(targets, state);
            } catch (Exception e) {
                state.failed = true;
                state.error = e.getMessage();
            }
            state.running = false;
            state.done = true;
            boolean shouldPrompt = promptRestart && (didApply || DlcRequiredRuntimeState.hasPendingChanges());
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                updateEntryButtons();
                if (shouldPrompt) {
                    minecraft.setScreen(new ConfirmScreen(accepted -> {
                        if (accepted) {
                            minecraft.stop();
                        } else {
                            minecraft.setScreen(this);
                        }
                    }, Component.translatable("dlc_manager.title"), Component.translatable("dlc_manager.required.restart_prompt"),
                        Component.translatable("dlc_manager.restart_now"),
                        Component.translatable("dlc_manager.restart_later")));
                }
            });
        });
    }

    private boolean installEntries(List<DlcRequiredEntry> entries, InstallState state) throws IOException, InterruptedException {
        List<DlcRequiredEntry> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList()) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id == null || id.isBlank() || !seen.add(id)) {
                continue;
            }
            unique.add(entry);
        }
        int total = Math.max(1, unique.size());
        boolean didApply = false;
        int index = 0;
        for (DlcRequiredEntry entry : unique) {
            String id = entry.getIdentifier();
            boolean applied = isApplied(entry);
            DlcRequiredRuntimeState.getBaseline(id, applied);
            updateProgress(state, index, 0f, total);
            String fileName = resolveExistingFileName(entry);
            if (fileName == null || fileName.isBlank()) {
                fileName = resolveFileName(entry);
            }
            if (fileName == null || fileName.isBlank()) {
                downloadEntry(entry, state, index, total);
                fileName = entry.getResolvedFileName();
            } else {
                entry.setResolvedFileName(fileName);
                Path required = entry.resolveRequiredPath(fileName);
                if (required == null || !Files.exists(required)) {
                    downloadEntry(entry, state, index, total);
                }
            }
            if (applyEntry(entry)) {
                didApply = true;
            }
            DlcRequiredRuntimeState.setOverride(id, true);
            index++;
            updateProgress(state, index, 0f, total);
        }
        updateProgress(state, total, 0f, total);
        return didApply;
    }

    private void downloadEntry(DlcRequiredEntry entry, InstallState state, int stepIndex, int totalSteps) throws IOException, InterruptedException {
        if (entry == null) {
            return;
        }
        String url = resolveDownloadUrl(entry);
        if (url == null || url.isBlank()) {
            throw new IOException("missing download url");
        }
        DownloadInfo downloadInfo = openDownload(url);
        String fileName = DlcDownloadUtil.sanitizeFileName(entry.getResolvedFileName());
        if (fileName == null) {
            fileName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName);
        }
        if (fileName == null) {
            fileName = DlcDownloadUtil.stripQuery(entry.getBaseName());
        }
        Path target = entry.resolveRequiredPath(fileName);
        if (target == null) {
            closeQuietly(downloadInfo.connection);
            throw new IOException("invalid target path");
        }
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            closeQuietly(downloadInfo.connection);
            entry.setResolvedFileName(fileName);
            DlcRequiredManager.ensureFileNameKey(entry, fileName);
            return;
        }
        Path tempFile = Files.createTempFile(target.getParent(), "dlc-required", ".tmp");
        downloadToFile(downloadInfo.connection, tempFile, state, stepIndex, totalSteps);
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        entry.setResolvedFileName(fileName);
        DlcRequiredManager.updateFileNameKey(entry, fileName);
    }

    private boolean applyEntry(DlcRequiredEntry entry) {
        if (entry == null) {
            return false;
        }
        String fileName = entry.getResolvedFileName();
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        Path source = entry.resolveRequiredPath(fileName);
        if (source == null || !Files.exists(source)) {
            return false;
        }
        boolean applied = false;
        if (entry.resolveAppliedTargets().isEmpty()) {
            return true;
        }
        for (Path targetDir : entry.resolveAppliedTargets()) {
            Path target = resolveTarget(targetDir, fileName);
            if (target == null) {
                continue;
            }
            try {
                Files.createDirectories(target.getParent());
                if (!Files.exists(target)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    applied = true;
                }
            } catch (IOException ignored) {
                // Best-effort.
            }
        }
        return applied;
    }

    private void updateProgress(InstallState state, int stepIndex, float fraction, int totalSteps) {
        if (state == null) {
            return;
        }
        float progress = (stepIndex + Math.max(0f, Math.min(1f, fraction))) / (float) totalSteps;
        state.progress = Math.max(0f, Math.min(1f, progress));
    }

    private String resolveDownloadUrl(DlcRequiredEntry entry) throws IOException, InterruptedException {
        if (entry == null) {
            return null;
        }
        String directUrl = entry.getDirectUrl();
        if (directUrl != null && !directUrl.isBlank()) {
            return directUrl;
        }
        String versionId = entry.getModrinthVersionId();
        if (versionId != null && !versionId.isBlank()) {
            ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersion(versionId);
            if (version != null) {
                if (entry.getResolvedFileName() == null || entry.getResolvedFileName().isBlank()) {
                    entry.setResolvedFileName(version.fileName);
                }
                return version.url;
            }
        }
        return null;
    }

    private String resolveFileName(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String directUrl = entry.getDirectUrl();
        if (directUrl != null && !directUrl.isBlank()) {
            String name = DlcDownloadUtil.extractFileNameFromUrl(directUrl);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        String versionId = entry.getModrinthVersionId();
        if (versionId != null && !versionId.isBlank()) {
            try {
                ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersion(versionId);
                if (version != null && version.fileName != null && !version.fileName.isBlank()) {
                    return version.fileName;
                }
            } catch (Exception ignored) {
                // Best-effort.
            }
        }
        return null;
    }

    private void downloadToFile(HttpURLConnection connection, Path target, InstallState state, int stepIndex, int totalSteps) throws IOException {
        if (connection == null) {
            throw new IOException("missing download url");
        }
        int total = connection.getContentLength();
        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int len;
            int read = 0;
            while ((len = in.read(buffer)) >= 0) {
                out.write(buffer, 0, len);
                read += len;
                if (total > 0) {
                    updateProgress(state, stepIndex, (float) read / (float) total, totalSteps);
                }
            }
        } finally {
            connection.disconnect();
        }
        updateProgress(state, stepIndex + 1, 0f, totalSteps);
    }

    private DownloadInfo openDownload(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "DLC-Manager");
        connection.connect();
        String fileName = DlcDownloadHeaderUtil.extractFileNameFromHeaders(connection);
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(connection.getURL().toString());
        }
        return new DownloadInfo(connection, fileName);
    }

    private void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
                // Best-effort.
            }
        }
    }

    private static final class DownloadInfo {
        private final HttpURLConnection connection;
        private final String fileName;

        private DownloadInfo(HttpURLConnection connection, String fileName) {
            this.connection = connection;
            this.fileName = fileName;
        }
    }

    private void renderTooltip(GuiGraphics guiGraphics, DlcRequiredEntry entry, int mouseX, int mouseY) {
        if (entry == null) {
            return;
        }
        String tip = resolveTip(entry);
        if (tip == null || tip.isBlank()) {
            return;
        }
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        int maxWidth = Math.min(260, this.width - 40);
        for (String line : tip.split("\\\\n")) {
            lines.addAll(this.font.split(Component.literal(line), maxWidth));
        }
        guiGraphics.renderTooltip(this.font, lines, mouseX, mouseY);
    }

    private String resolveTip(DlcRequiredEntry entry) {
        if (entry == null || entry.getTips().isEmpty()) {
            return "";
        }
        String lang = Minecraft.getInstance().options.languageCode;
        if (entry.getTips().containsKey(lang)) {
            return entry.getTips().get(lang);
        }
        if (entry.getTips().containsKey("en_us")) {
            return entry.getTips().get("en_us");
        }
        return entry.getTips().values().stream().findFirst().orElse("");
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return;
        }
        int barX = this.width - 12;
        int barWidth = 4;
        int barHeight = Math.max(12, (bottom - top) * maxVisible / totalLines);
        int trackHeight = bottom - top;
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int barY = top + (int) ((scrollIndex / (float) maxScroll) * maxTrack);
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF2B2B2B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF6B6B6B);
    }
}
