package com.cozooo.dlc_manager.server;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredDependency;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.client.server.DlcServerRemoteItem;
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredProgressItem;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus;
import com.cozooo.dlc_manager.core.DcFormatUtil;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcTooltipLoader;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class DlcServerDownloadService {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private DlcServerDownloadService() {
    }

    public static final class DownloadResult {
        public boolean success;
        public int downloadedCount;
        public int appliedCount;
        public String error;

        public boolean didWork() {
            return downloadedCount > 0 || appliedCount > 0;
        }
    }

    private interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    private static final Object REMOTE_LOCK = new Object();
    private static RemoteDownloadSession remoteSession;
    private static final Object REQUIRED_LOCK = new Object();
    private static RequiredDownloadSession requiredSession;
    private static volatile boolean requiredCancelRequested;

    private static void consoleInfo(String message) {
        System.out.println(message);
    }

    private static void consoleWarn(String message) {
        System.out.println(message);
    }

    private static final class RemoteItemState {
        private final DlcRemoteEntry entry;
        private final String key;
        private final String name;
        private final AtomicLong downloaded = new AtomicLong(0L);
        private volatile long totalBytes = -1L;
        private volatile boolean done;
        private volatile boolean success;
        private volatile String error;

        private RemoteItemState(DlcRemoteEntry entry, String key, String name) {
            this.entry = entry;
            this.key = key;
            this.name = name;
        }
    }

    private static final class RequiredItemState {
        private final DlcRequiredEntry entry;
        private final String id;
        private final String name;
        private final AtomicLong downloaded = new AtomicLong(0L);
        private volatile long totalBytes = -1L;
        private volatile boolean done;
        private volatile boolean success;
        private volatile String error;
        private volatile long lastLogAt;
        private volatile int lastLogPercent = -1;

        private RequiredItemState(DlcRequiredEntry entry, String id, String name) {
            this.entry = entry;
            this.id = id;
            this.name = name;
        }
    }

    private static final class RemoteDownloadSession {
        private final List<RemoteItemState> items;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicBoolean success = new AtomicBoolean(true);
        private final AtomicInteger completed = new AtomicInteger(0);
        private final AtomicLong totalBytes = new AtomicLong(0L);
        private final AtomicLong downloadedBytes = new AtomicLong(0L);
        private final Map<String, RemoteItemState> itemByKey = new ConcurrentHashMap<>();
        private volatile String error;

        private RemoteDownloadSession(List<RemoteItemState> items) {
            this.items = items;
            for (RemoteItemState item : items) {
                itemByKey.put(item.key, item);
            }
        }

        private void start(Path dlcRoot) {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            int maxConcurrent = Math.max(1, DlcPathConfig.getMaxConcurrentDownloads());
            ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
            for (RemoteItemState item : items) {
                executor.submit(() -> {
                    if (!running.get()) {
                        return;
                    }
                    downloadOne(dlcRoot, item);
                });
            }
            executor.shutdown();
            new Thread(() -> {
                while (!executor.isTerminated()) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
                running.set(false);
                finished.set(true);
            }, "dlc-server-remote-finish").start();
        }

        private void downloadOne(Path dlcRoot, RemoteItemState item) {
            if (item == null || item.entry == null) {
                markDone(item, false, "invalid entry");
                return;
            }
            try {
                String fileName = downloadRemoteEntryWithProgress(item.entry, item,
                    (downloaded, total) -> updateProgress(item, downloaded, total));
                if (fileName != null) {
                    updateRemoteFileNameKey(item.entry, fileName);
                }
                markDone(item, true, null);
            } catch (Exception ex) {
                markDone(item, false, ex.getMessage());
                success.set(false);
                error = ex.getMessage();
            }
        }

        private void updateProgress(RemoteItemState item, long current, long total) {
            if (item == null) {
                return;
            }
            long prev = item.downloaded.getAndSet(current);
            long delta = current - prev;
            if (delta != 0) {
                downloadedBytes.addAndGet(delta);
            }
            if (total > 0 && item.totalBytes != total) {
                long oldTotal = item.totalBytes;
                item.totalBytes = total;
                if (oldTotal > 0) {
                    totalBytes.addAndGet(total - oldTotal);
                } else {
                    totalBytes.addAndGet(total);
                }
            }
        }

        private void markDone(RemoteItemState item, boolean ok, String err) {
            if (item == null) {
                return;
            }
            item.done = true;
            item.success = ok;
            item.error = err;
            completed.incrementAndGet();
        }

        private DlcServerRemoteStatus toStatus(String lang) {
            DlcServerRemoteStatus status = new DlcServerRemoteStatus();
            status.running = running.get();
            status.finished = finished.get();
            status.success = success.get();
            status.totalCount = items.size();
            status.completedCount = completed.get();
            status.totalBytes = totalBytes.get();
            status.downloadedBytes = downloadedBytes.get();
            status.error = error;
            for (RemoteItemState item : items) {
                DlcServerRemoteItem view = new DlcServerRemoteItem();
                view.key = item.key;
                view.name = resolveDisplayName(item.entry, lang);
                view.displayNames = resolveDisplayNames(item.entry, view.name);
                view.totalBytes = item.totalBytes;
                view.downloadedBytes = item.downloaded.get();
                view.done = item.done;
                view.success = item.success;
                view.error = item.error;
                status.items.add(view);
            }
            return status;
        }
    }

    private static final class RequiredDownloadSession {
        private final List<RequiredItemState> items;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicBoolean success = new AtomicBoolean(true);
        private final AtomicInteger completed = new AtomicInteger(0);
        private final AtomicLong totalBytes = new AtomicLong(0L);
        private final AtomicLong downloadedBytes = new AtomicLong(0L);
        private final Map<String, RequiredItemState> itemById = new ConcurrentHashMap<>();
        private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        private volatile String error;

        private RequiredDownloadSession(List<RequiredItemState> items) {
            this.items = items;
            for (RequiredItemState item : items) {
                itemById.put(item.id, item);
            }
        }

        private void start(Path dlcRoot) {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            cancelRequested.set(false);
            int maxConcurrent = Math.max(1, DlcPathConfig.getMaxConcurrentDownloads());
            ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
            for (RequiredItemState item : items) {
                executor.submit(() -> {
                    if (!running.get() || cancelRequested.get()) {
                        return;
                    }
                    downloadOne(dlcRoot, item);
                });
            }
            executor.shutdown();
            new Thread(() -> {
                while (!executor.isTerminated()) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
                running.set(false);
                finished.set(true);
            }, "dlc-server-required-finish").start();
        }

        private void downloadOne(Path dlcRoot, RequiredItemState item) {
            if (item == null || item.entry == null) {
                markDone(item, false, "invalid entry");
                return;
            }
            try {
                consoleInfo("[dlc-manager] server required download start: " + item.name + " (" + safeId(item.entry) + ")");
                if (cancelRequested.get()) {
                    markDone(item, false, "canceled");
                    success.set(false);
                    error = "canceled";
                    return;
                }
                String existingName = resolveExistingFileName(item.entry);
                if (existingName != null && !existingName.isBlank() && !isTempFileName(existingName)) {
                    item.entry.setResolvedFileName(existingName);
                    if (entryNeedsApply(item.entry)) {
                        applyRequiredEntry(item.entry);
                        if (entryNeedsApply(item.entry)) {
                            String err = "apply_failed:" + safeId(item.entry);
                            markDone(item, false, err);
                            success.set(false);
                            error = err;
                            consoleWarn("[dlc-manager] server required apply failed: " + safeId(item.entry));
                            return;
                        }
                    }
                    updateProgress(item, 1L, 1L);
                    markDone(item, true, null);
                    return;
                }
                String fileName = downloadRequiredEntryWithProgress(item.entry, (downloaded, total) -> updateProgress(item, downloaded, total));
                if (fileName != null && !fileName.isBlank()) {
                    item.entry.setResolvedFileName(fileName);
                    updateRequiredFileNameKey(item.entry, fileName);
                    updateRequiredUserRemoved(item.entry, false);
                } else {
                    String err = "download_failed:" + safeId(item.entry);
                    markDone(item, false, err);
                    success.set(false);
                    error = err;
                    consoleWarn("[dlc-manager] server required download failed: " + safeId(item.entry));
                    return;
                }
                if (entryNeedsApply(item.entry)) {
                    applyRequiredEntry(item.entry);
                    if (entryNeedsApply(item.entry)) {
                        String err = "apply_failed:" + safeId(item.entry);
                        markDone(item, false, err);
                        success.set(false);
                        error = err;
                        consoleWarn("[dlc-manager] server required apply failed: " + safeId(item.entry));
                        return;
                    }
                }
                markDone(item, true, null);
            } catch (DownloadCanceledException ex) {
                markDone(item, false, "canceled");
                success.set(false);
                error = "canceled";
            } catch (Exception ex) {
                String err = ex.getMessage();
                if (err == null || err.isBlank()) {
                    err = "error:" + safeId(item.entry);
                }
                markDone(item, false, err);
                success.set(false);
                error = err;
                consoleWarn("[dlc-manager] server required download failed: " + safeId(item.entry) + " " + ex.getMessage());
            }
        }

        private void updateProgress(RequiredItemState item, long current, long total) {
            if (item == null) {
                return;
            }
            long prev = item.downloaded.getAndSet(current);
            long delta = current - prev;
            if (delta != 0) {
                downloadedBytes.addAndGet(delta);
            }
            if (total > 0 && item.totalBytes != total) {
                long oldTotal = item.totalBytes;
                item.totalBytes = total;
                if (oldTotal > 0) {
                    totalBytes.addAndGet(total - oldTotal);
                } else {
                    totalBytes.addAndGet(total);
                }
            }
            if (total > 0) {
                int percent = (int) Math.min(100L, (current * 100L) / Math.max(1L, total));
                long now = System.currentTimeMillis();
                if (percent >= 0 && (percent - item.lastLogPercent >= 10 || now - item.lastLogAt >= 5000)) {
                    item.lastLogAt = now;
                    item.lastLogPercent = percent;
                    String fileName = item.entry.getResolvedFileName();
                    if (fileName == null || fileName.isBlank()) {
                        fileName = item.entry.getRequiredFileName();
                    }
                    if (fileName == null || fileName.isBlank()) {
                        fileName = safeId(item.entry);
                    }
                    consoleInfo("[dlc-manager] server required downloading: " + item.name + " (" + fileName + ") " + percent + "%");
                }
            }
        }

        private void markDone(RequiredItemState item, boolean ok, String err) {
            if (item == null) {
                return;
            }
            item.done = true;
            item.success = ok;
            item.error = err;
            completed.incrementAndGet();
            if (ok) {
                consoleInfo("[dlc-manager] server required download done: " + item.name + " (" + safeId(item.entry) + ")");
            } else if (err != null && !err.isBlank()) {
                consoleWarn("[dlc-manager] server required download failed: " + item.name + " (" + safeId(item.entry) + ") " + err);
            } else {
                consoleWarn("[dlc-manager] server required download failed: " + item.name + " (" + safeId(item.entry) + ")");
            }
        }

        private DlcServerRequiredStatus toStatus(String lang) {
            DlcServerRequiredStatus status = new DlcServerRequiredStatus();
            status.running = running.get();
            status.finished = finished.get();
            status.success = success.get();
            status.totalCount = items.size();
            status.completedCount = completed.get();
            status.totalBytes = totalBytes.get();
            status.downloadedBytes = downloadedBytes.get();
            status.error = error;
            for (RequiredItemState item : items) {
                DlcServerRequiredProgressItem view = new DlcServerRequiredProgressItem();
                view.id = item.id;
                view.name = resolveRequiredDisplayName(item.entry, lang);
                view.totalBytes = item.totalBytes;
                view.downloadedBytes = item.downloaded.get();
                view.done = item.done;
                view.success = item.success;
                view.error = item.error;
                status.items.add(view);
            }
            return status;
        }

        private void requestCancel() {
            cancelRequested.set(true);
            success.set(false);
            error = "canceled";
        }
    }

    public static List<DlcServerRemoteItem> collectMissingRemoteItems(Path dlcRoot) {
        return collectMissingRemoteItems(dlcRoot, null);
    }

    public static List<DlcServerRemoteItem> collectMissingRemoteItems(Path dlcRoot, String lang) {
        List<DlcServerRemoteItem> list = new ArrayList<>();
        for (DlcRemoteEntry entry : collectMissingRemote(dlcRoot)) {
            DlcServerRemoteItem item = new DlcServerRemoteItem();
            item.key = selectionKey(entry);
            item.name = resolveDisplayName(entry, lang);
            item.displayNames = resolveDisplayNames(entry, item.name);
            list.add(item);
        }
        return list;
    }

    public static List<DlcServerRequiredItem> collectRequiredItems(Path dlcRoot) {
        return collectRequiredItems(dlcRoot, null);
    }

    public static List<DlcServerRequiredItem> collectRequiredItems(Path dlcRoot, String lang) {
        List<DlcServerRequiredItem> items = new ArrayList<>();
        List<DlcRequiredEntry> entries = loadAllRequiredEntries(dlcRoot);
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList() || entry.isHidden()) {
                continue;
            }
            if (entry.isUserRemoved()) {
                continue;
            }
            String fileName = resolveExistingFileName(entry);
            entry.setResolvedFileName(fileName);
            boolean hasFile = fileName != null && entry.resolveRequiredPath(fileName) != null
                && Files.exists(entry.resolveRequiredPath(fileName)) && !isTempFileName(fileName);
            boolean needsApply = entryNeedsApply(entry);
            DlcServerRequiredItem item = new DlcServerRequiredItem();
            item.id = safeId(entry);
            item.identifier = entry.getIdentifier();
            item.name = resolveRequiredDisplayName(entry, lang);
            item.displayNames.putAll(entry.getDisplayNames());
            item.tips.putAll(entry.getTips());
            for (DlcRequiredDependency dep : entry.getDependencies()) {
                if (dep != null && dep.getIdentifier() != null && !dep.getIdentifier().isBlank()) {
                    item.dependencies.add(dep.getIdentifier());
                }
            }
            item.inList = entry.isInList();
            item.hidden = entry.isHidden();
            item.userRemoved = entry.isUserRemoved();
            item.hasFile = hasFile;
            item.needsApply = needsApply;
            item.applied = hasFile && !needsApply;
            item.fileName = fileName;
            items.add(item);
        }
        return items;
    }

    public static DlcServerRemoteStatus startRemoteDownload(Path dlcRoot, List<String> keys) {
        return startRemoteDownload(dlcRoot, keys, null);
    }

    public static DlcServerRemoteStatus startRemoteDownload(Path dlcRoot, List<String> keys, String lang) {
        synchronized (REMOTE_LOCK) {
            if (remoteSession != null && remoteSession.running.get()) {
                return remoteSession.toStatus(lang);
            }
            List<DlcRemoteEntry> missing;
            if (keys != null && !keys.isEmpty()) {
                missing = collectMissingRemoteByKeys(dlcRoot, keys);
            } else {
                missing = collectMissingRemote(dlcRoot);
            }
            List<RemoteItemState> items = new ArrayList<>();
            for (DlcRemoteEntry entry : missing) {
                String key = selectionKey(entry);
                String name = resolveDisplayName(entry, lang);
                items.add(new RemoteItemState(entry, key, name));
            }
            remoteSession = new RemoteDownloadSession(items);
            remoteSession.start(dlcRoot);
            return remoteSession.toStatus(lang);
        }
    }

    private static List<DlcRemoteEntry> collectMissingRemoteByKeys(Path dlcRoot, List<String> keys) {
        List<DlcRemoteEntry> missing = new ArrayList<>();
        if (dlcRoot == null || keys == null || keys.isEmpty()) {
            return missing;
        }
        List<DlcRemoteEntry> entries = DlcRemoteLoader.load(dlcRoot);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DlcRemoteEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String key = selectionKey(entry);
            if (keys.contains(key)) {
                addMissingRemote(entry, missing, seen, false);
            }
        }
        return missing;
    }

    public static DlcServerRequiredStatus startRequiredDownload(Path dlcRoot, List<String> ids) {
        return startRequiredDownload(dlcRoot, ids, null);
    }

    public static DlcServerRequiredStatus startRequiredDownload(Path dlcRoot, List<String> ids, String lang) {
        synchronized (REQUIRED_LOCK) {
            if (requiredSession != null && requiredSession.running.get()) {
                return requiredSession.toStatus(lang);
            }
            requiredCancelRequested = false;
            List<DlcRequiredEntry> entries = loadAllRequiredEntries(dlcRoot);
            Map<String, DlcRequiredEntry> index = buildRequiredIndex(entries);
            List<DlcRequiredEntry> targets = entries;
            if (ids != null && !ids.isEmpty()) {
                targets = collectRequiredDependencies(ids, index);
            }
            List<RequiredItemState> items = new ArrayList<>();
            for (DlcRequiredEntry entry : targets) {
                if (entry == null || !entry.isInList() || entry.isHidden()) {
                    continue;
                }
                if (entry.isUserRemoved()) {
                    continue;
                }
                String id = safeId(entry);
                String name = resolveRequiredDisplayName(entry, lang);
                items.add(new RequiredItemState(entry, id, name));
            }
            requiredSession = new RequiredDownloadSession(items);
            requiredSession.start(dlcRoot);
            return requiredSession.toStatus(lang);
        }
    }

    public static boolean cancelRequiredDownload() {
        synchronized (REQUIRED_LOCK) {
            if (requiredSession == null || !requiredSession.running.get()) {
                return false;
            }
            requiredCancelRequested = true;
            requiredSession.requestCancel();
            return true;
        }
    }

    public static DlcServerRequiredStatus getRequiredDownloadStatus() {
        return getRequiredDownloadStatus(null);
    }

    public static DlcServerRequiredStatus getRequiredDownloadStatus(String lang) {
        synchronized (REQUIRED_LOCK) {
            if (requiredSession == null) {
                DlcServerRequiredStatus status = new DlcServerRequiredStatus();
                status.running = false;
                status.finished = true;
                status.success = true;
                status.totalCount = 0;
                status.completedCount = 0;
                return status;
            }
            return requiredSession.toStatus(lang);
        }
    }

    public static DlcServerRemoteStatus getRemoteDownloadStatus() {
        return getRemoteDownloadStatus(null);
    }

    public static DlcServerRemoteStatus getRemoteDownloadStatus(String lang) {
        synchronized (REMOTE_LOCK) {
            if (remoteSession == null) {
                DlcServerRemoteStatus status = new DlcServerRemoteStatus();
                status.running = false;
                status.finished = true;
                status.success = true;
                status.totalCount = 0;
                status.completedCount = 0;
                return status;
            }
            return remoteSession.toStatus(lang);
        }
    }

    public static List<DlcRemoteEntry> collectMissingRemote(Path dlcRoot) {
        List<DlcRemoteEntry> missing = new ArrayList<>();
        if (dlcRoot == null) {
            return missing;
        }
        List<DlcRemoteEntry> entries = DlcRemoteLoader.load(dlcRoot);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DlcRemoteEntry entry : entries) {
            addMissingRemote(entry, missing, seen, false);
        }
        return missing;
    }

    private static void addMissingRemote(DlcRemoteEntry entry, List<DlcRemoteEntry> missing, java.util.Set<String> seen, boolean forceInclude) {
        if (entry == null) {
            return;
        }
        String key = selectionKey(entry);
        if (!seen.add(key)) {
            return;
        }
        if (isRemoteMissing(entry, forceInclude)) {
            missing.add(entry);
        }
        List<DlcRemoteEntry> deps = entry.getRemoteDependencies();
        if (deps != null && !deps.isEmpty()) {
            for (DlcRemoteEntry dep : deps) {
                addMissingRemote(dep, missing, seen, true);
            }
        }
    }

    private static boolean isRemoteMissing(DlcRemoteEntry entry, boolean forceInclude) {
        if (entry == null) {
            return false;
        }
        if (!forceInclude && !entry.isInList() && !entry.isHidden()) {
            return false;
        }
        Path target = entry.getTargetPath();
        return target == null || !Files.exists(target);
    }

    private static String selectionKey(DlcRemoteEntry entry) {
        if (entry == null) {
            return "unknown";
        }
        String key = entry.getDlcIdentifier();
        if (key == null || key.isBlank()) {
            Path configPath = entry.getConfigPath();
            if (configPath != null && configPath.getFileName() != null) {
                key = configPath.getFileName().toString();
            }
        }
        if (key == null || key.isBlank()) {
            key = entry.getFileNameHint();
        }
        return key == null || key.isBlank() ? "unknown" : key;
    }

    private static String resolveDisplayName(DlcRemoteEntry entry) {
        return resolveDisplayName(entry, null);
    }

    private static String resolveDisplayName(DlcRemoteEntry entry, String langOverride) {
        if (entry == null) {
            return "";
        }
        String fallback = selectionKey(entry);
        Map<String, String> displayNames = resolveDisplayNames(entry, fallback);
        if (displayNames == null || displayNames.isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        String lang = normalizeLang(langOverride);
        String name = displayNames.get(lang);
        if (name == null || name.isBlank()) {
            name = displayNames.get("en_us");
        }
        if (name == null || name.isBlank()) {
            name = displayNames.values().stream().findFirst().orElse(fallback);
        }
        return name == null ? "" : name;
    }

    private static String resolveRequiredDisplayName(DlcRequiredEntry entry, String langOverride) {
        if (entry == null) {
            return "";
        }
        String fallback = entry.getBaseName();
        if (fallback == null || fallback.isBlank()) {
            fallback = entry.getIdentifier();
        }
        if (fallback == null || fallback.isBlank()) {
            fallback = "required";
        }
        Map<String, String> displayNames = entry.getDisplayNames();
        if (displayNames == null || displayNames.isEmpty()) {
            return fallback;
        }
        String lang = normalizeLang(langOverride);
        String name = displayNames.get(lang);
        if (name == null || name.isBlank()) {
            name = displayNames.get("en_us");
        }
        if (name == null || name.isBlank()) {
            name = displayNames.values().stream().findFirst().orElse(fallback);
        }
        return name == null ? "" : name;
    }

    private static String normalizeLang(String langOverride) {
        String lang = langOverride;
        if (lang == null || lang.isBlank()) {
            lang = CrossPlatformRuntime.languageCode();
        }
        return JsonCaseUtil.normalizeLangKey(lang);
    }


    public static List<DlcRequiredEntry> collectMissingRequired(Path dlcRoot) {
        List<DlcRequiredEntry> missing = new ArrayList<>();
        if (dlcRoot == null) {
            return missing;
        }
        List<DlcRequiredEntry> entries = loadRequiredEntries(dlcRoot);
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList() || entry.isUserRemoved()) {
                continue;
            }
            String fileName = resolveExistingFileName(entry);
            entry.setResolvedFileName(fileName);
            boolean hasFile = fileName != null && entry.resolveRequiredPath(fileName) != null
                && Files.exists(entry.resolveRequiredPath(fileName)) && !isTempFileName(fileName);
            if (!hasFile || entryNeedsApply(entry)) {
                missing.add(entry);
            }
        }
        return missing;
    }

    public static boolean hasRequiredEntries(Path dlcRoot) {
        if (dlcRoot == null) {
            return false;
        }
        return !loadRequiredEntries(dlcRoot).isEmpty();
    }

    public static DownloadResult downloadMissingRemote(Path dlcRoot) {
        DownloadResult result = new DownloadResult();
        List<DlcRemoteEntry> missing = collectMissingRemote(dlcRoot);
        if (missing.isEmpty()) {
            result.success = true;
            return result;
        }
        for (DlcRemoteEntry entry : missing) {
            try {
                String fileName = downloadRemoteEntry(entry);
                if (fileName != null) {
                    result.downloadedCount++;
                    updateRemoteFileNameKey(entry, fileName);
                }
            } catch (Exception ex) {
                result.success = false;
                result.error = ex.getMessage();
                consoleWarn("[dlc-manager] server remote download failed: " + ex.getMessage());
                return result;
            }
        }
        result.success = true;
        return result;
    }

    public static DownloadResult downloadAndApplyRequired(Path dlcRoot) {
        DownloadResult result = new DownloadResult();
        if (dlcRoot == null) {
            result.success = true;
            return result;
        }
        try {
            Files.createDirectories(dlcRoot);
        } catch (IOException ignored) {
        }
        List<DlcRequiredEntry> entries = DlcRequiredLoader.load(dlcRoot);
        if (entries == null || entries.isEmpty()) {
            result.success = true;
            return result;
        }
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList() || entry.isUserRemoved()) {
                continue;
            }
            String fileName = resolveExistingFileName(entry);
            if (fileName == null || fileName.isBlank() || isTempFileName(fileName)) {
                try {
                    String id = safeId(entry);
                    String name = entry.resolveDisplayName();
                    consoleInfo("[dlc-manager] server required download start: " + name + " (" + id + ")");
                    long[] lastLogAt = new long[] {0L};
                    int[] lastPercent = new int[] {-1};
                    fileName = downloadRequiredEntryWithProgress(entry, (downloaded, total) -> {
                        if (total <= 0) {
                            return;
                        }
                        int percent = (int) Math.min(100L, (downloaded * 100L) / Math.max(1L, total));
                        long now = System.currentTimeMillis();
                        if (percent >= 0 && (percent - lastPercent[0] >= 10 || now - lastLogAt[0] >= 5000)) {
                            lastPercent[0] = percent;
                            lastLogAt[0] = now;
                            String label = entry.getRequiredFileName();
                            if (label == null || label.isBlank()) {
                                label = id;
                            }
                            consoleInfo("[dlc-manager] server required downloading: " + name + " (" + label + ") " + percent + "%");
                        }
                    });
                    if (fileName != null && !fileName.isBlank()) {
                        entry.setResolvedFileName(fileName);
                        result.downloadedCount++;
                        updateRequiredFileNameKey(entry, fileName);
                        consoleInfo("[dlc-manager] server required download done: " + name + " (" + id + ")");
                    }
                } catch (Exception ex) {
                    result.success = false;
                    result.error = ex.getMessage();
                    consoleWarn("[dlc-manager] server required download failed: " + ex.getMessage());
                    return result;
                }
            } else {
                entry.setResolvedFileName(fileName);
            }
        }
        int applied = applyRequired(entries);
        result.appliedCount = applied;
        result.success = true;
        return result;
    }

    public static int uninstallRequired(Path dlcRoot, List<String> ids) {
        if (dlcRoot == null || ids == null || ids.isEmpty()) {
            return 0;
        }
        List<DlcRequiredEntry> entries = loadAllRequiredEntries(dlcRoot);
        Map<String, DlcRequiredEntry> index = buildRequiredIndex(entries);
        int removed = 0;
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            DlcRequiredEntry entry = index.get(id);
            if (entry == null) {
                continue;
            }
            removeEntryFiles(entry);
            updateRequiredUserRemoved(entry, true);
            removed++;
        }
        return removed;
    }

    public static int applyRequiredByIds(Path dlcRoot, List<String> ids) {
        if (dlcRoot == null || ids == null || ids.isEmpty()) {
            return 0;
        }
        List<DlcRequiredEntry> entries = loadAllRequiredEntries(dlcRoot);
        Map<String, DlcRequiredEntry> index = buildRequiredIndex(entries);
        int applied = 0;
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            DlcRequiredEntry entry = index.get(id);
            if (entry == null) {
                continue;
            }
            String fileName = resolveExistingFileName(entry);
            if (fileName != null) {
                entry.setResolvedFileName(fileName);
            }
            if (applyRequiredEntry(entry)) {
                updateRequiredUserRemoved(entry, false);
                applied++;
            }
        }
        return applied;
    }

    private static String safeId(DlcRequiredEntry entry) {
        if (entry == null) {
            return "unknown";
        }
        String id = entry.getIdentifier();
        if (id == null || id.isBlank()) {
            id = entry.resolveDisplayName();
        }
        if (id == null || id.isBlank()) {
            id = "required";
        }
        return id;
    }

    private static int applyRequired(List<DlcRequiredEntry> entries) {
        int applied = 0;
        if (entries == null || entries.isEmpty()) {
            return applied;
        }
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String fileName = entry.getResolvedFileName();
            if (fileName == null || fileName.isBlank()) {
                continue;
            }
            Path source = entry.resolveRequiredPath(fileName);
            if (source == null || !Files.exists(source)) {
                continue;
            }
            for (Path targetDir : entry.resolveAppliedTargets()) {
                Path target = resolveTargetPath(targetDir, fileName);
                if (target == null) {
                    continue;
                }
                try {
                    Files.createDirectories(target.getParent());
                    if (!Files.exists(target)) {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        applied++;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return applied;
    }

    private static boolean applyRequiredEntry(DlcRequiredEntry entry) {
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
        for (Path targetDir : entry.resolveAppliedTargets()) {
            Path target = resolveTargetPath(targetDir, fileName);
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
            }
        }
        return applied;
    }

    private static boolean entryNeedsApply(DlcRequiredEntry entry) {
        if (entry == null) {
            return false;
        }
        String fileName = normalizeFileName(entry.getResolvedFileName());
        if (fileName == null || fileName.isBlank()) {
            fileName = normalizeFileName(guessFileName(entry));
            entry.setResolvedFileName(fileName);
        }
        if (isTempFileName(fileName)) {
            entry.setResolvedFileName(null);
            return true;
        }
        if (fileName == null) {
            return true;
        }
        for (Path target : entry.resolveAppliedTargets()) {
            Path targetFile = resolveTargetPath(target, fileName);
            if (targetFile != null && !Files.exists(targetFile)) {
                return true;
            }
        }
        return false;
    }

    private static List<DlcRequiredEntry> loadAllRequiredEntries(Path dlcRoot) {
        List<DlcRequiredEntry> entries = new ArrayList<>(loadRequiredEntries(dlcRoot));
        Map<String, DlcRequiredEntry> index = buildRequiredIndex(entries);
        ArrayDeque<DlcRequiredEntry> queue = new ArrayDeque<>(entries);
        while (!queue.isEmpty()) {
            DlcRequiredEntry entry = queue.poll();
            if (entry == null) {
                continue;
            }
            for (DlcRequiredDependency dep : entry.getDependencies()) {
                if (dep == null) {
                    continue;
                }
                String id = dep.getIdentifier();
                if (id == null || id.isBlank()) {
                    continue;
                }
                if (index.containsKey(id)) {
                    continue;
                }
                DlcRequiredEntry depEntry = dep.getEntry();
                if (depEntry != null && depEntry.isInList()) {
                    depEntry.setIdentifier(id);
                    index.put(id, depEntry);
                    entries.add(depEntry);
                    queue.add(depEntry);
                }
            }
        }
        return entries;
    }

    private static Map<String, DlcRequiredEntry> buildRequiredIndex(List<DlcRequiredEntry> entries) {
        Map<String, DlcRequiredEntry> map = new HashMap<>();
        if (entries == null) {
            return map;
        }
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id == null || id.isBlank()) {
                String safe = safeId(entry);
                if (safe != null && !safe.isBlank()) {
                    map.putIfAbsent(safe, entry);
                }
                continue;
            }
            map.putIfAbsent(id, entry);
            String safe = safeId(entry);
            if (safe != null && !safe.isBlank()) {
                map.putIfAbsent(safe, entry);
            }
        }
        return map;
    }

    private static List<DlcRequiredEntry> collectRequiredDependencies(List<String> ids, Map<String, DlcRequiredEntry> index) {
        List<DlcRequiredEntry> result = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        ArrayDeque<String> queue = new ArrayDeque<>(ids);
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!seen.add(id)) {
                continue;
            }
            DlcRequiredEntry entry = index.get(id);
            if (entry == null) {
                continue;
            }
            result.add(entry);
            for (DlcRequiredDependency dep : entry.getDependencies()) {
                String depId = dep == null ? null : dep.getIdentifier();
                if (depId == null || depId.isBlank()) {
                    continue;
                }
                queue.add(depId);
            }
        }
        return result;
    }

    private static void removeEntryFiles(DlcRequiredEntry entry) {
        if (entry == null) {
            return;
        }
        List<Path> targets = entry.resolveAppliedTargets();
        if (targets.isEmpty()) {
            return;
        }
        String fileName = resolveAppliedFileName(entry, targets);
        for (Path target : targets) {
            if (target == null) {
                continue;
            }
            Path targetFile;
            String name = target.getFileName() == null ? "" : target.getFileName().toString();
            if (name.endsWith(".jar") || name.endsWith(".zip")) {
                targetFile = target;
            } else {
                targetFile = resolveTargetPath(target, fileName);
            }
            if (targetFile != null) {
                try {
                    Files.deleteIfExists(targetFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String resolveAppliedFileName(DlcRequiredEntry entry, List<Path> targets) {
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
                try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(target, base + ".*")) {
                    for (Path path : stream) {
                        String name = path.getFileName() == null ? "" : path.getFileName().toString();
                        if (!DcFormatUtil.isDcName(name)) {
                            return name;
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    private static List<DlcRequiredEntry> loadRequiredEntries(Path dlcRoot) {
        List<DlcRequiredEntry> entries = DlcRequiredLoader.load(dlcRoot);
        if (entries != null && !entries.isEmpty()) {
            return entries;
        }
        List<DlcRequiredEntry> fallback = new ArrayList<>();
        if (dlcRoot == null) {
            return fallback;
        }
        Path requiredDir = dlcRoot.resolve("required");
        if (!Files.isDirectory(requiredDir)) {
            return fallback;
        }
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(requiredDir)) {
            for (Path path : stream) {
                if (path.getFileName() == null) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                if (!DcFormatUtil.isDcName(fileName)) {
                    continue;
                }
                if (isNoticeConfig(fileName)) {
                    continue;
                }
                DlcRequiredEntry entry = DlcRequiredLoader.loadSingle(path, dlcRoot);
                if (entry != null) {
                    fallback.add(entry);
                }
            }
        } catch (IOException ignored) {
        }
        return fallback;
    }

    private static String normalizeFileName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private static String guessFileName(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String directUrl = entry.getDirectUrl();
        if (directUrl != null && !directUrl.isBlank()) {
            String name = DlcDownloadUtil.extractFileNameFromUrl(directUrl);
            if (DlcDownloadUtil.isLikelyFileName(name)) {
                return name;
            }
        }
        return null;
    }

    private static Path resolveTargetPath(Path targetDir, String fileName) {
        if (targetDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = targetDir.getFileName() == null ? "" : targetDir.getFileName().toString();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir.normalize();
        }
        return targetDir.resolve(fileName).normalize();
    }

    private static String resolveExistingFileName(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String fileName = entry.getResolvedFileName();
        if (isConfigFileName(fileName) || isTempFileName(fileName)) {
            fileName = null;
        }
        if (fileName != null && !fileName.isBlank()) {
            Path target = entry.resolveRequiredPath(fileName);
            if (target != null && Files.exists(target) && !isConfigFile(entry, target)) {
                return fileName;
            }
            fileName = null;
        }
        String base = entry.getBaseName();
        if (entry.getRequiredDir() == null || base == null || base.isBlank()) {
            return fileName;
        }
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(entry.getRequiredDir(), base + ".*")) {
            for (Path path : stream) {
                if (isConfigFile(entry, path)) {
                    continue;
                }
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (isTempFileName(name)) {
                    continue;
                }
                return name;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static boolean isConfigFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        return DcFormatUtil.isDcName(fileName);
    }

    private static boolean isTempFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.endsWith(".part");
    }

    private static boolean isConfigFile(DlcRequiredEntry entry, Path file) {
        if (entry == null || file == null) {
            return false;
        }
        Path config = entry.getConfigPath();
        if (config == null) {
            return false;
        }
        try {
            return Files.isSameFile(config, file);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String downloadRequiredEntry(DlcRequiredEntry entry) throws IOException, InterruptedException {
        return downloadRequiredEntryWithProgress(entry, null);
    }

    private static String downloadRequiredEntryWithProgress(DlcRequiredEntry entry, ProgressListener listener)
        throws IOException, InterruptedException {
        if (entry == null) {
            return null;
        }
        checkRequiredCanceled();
        DlcDownloadCandidate.Resolver resolver = DlcDownloadCandidate.resolver(entry);
        IOException lastError = null;
        List<DlcDownloadCandidate> candidates;
        while (!(candidates = resolver.next()).isEmpty()) {
            for (DlcDownloadCandidate candidate : candidates) {
                for (int attempt = 1; attempt <= DlcPathConfig.getMaxRetryCount(); attempt++) {
                    try {
                        checkRequiredCanceled();
                        String fileName = candidate.fileNameOverride;
                        if (fileName == null || fileName.isBlank()) {
                            fileName = entry.getRequiredFileName();
                        }
                        if (candidate.direct && !DlcDownloadUtil.isLikelyFileName(fileName)) {
                            try {
                                String suggested = DlcDownloadService.suggestFileName(candidate.url);
                                if (suggested != null && !suggested.isBlank()) {
                                    fileName = suggested;
                                }
                            } catch (IOException ignored) {
                            }
                        }
                        fileName = finalizeFileName(fileName, entry, candidate.url);
                        Path target = entry.resolveRequiredPath(fileName);
                        if (target == null) {
                            throw new IOException("invalid target path");
                        }
                        Files.createDirectories(target.getParent());
                        if (Files.exists(target)) {
                            return fileName;
                        }
                        Path downloaded = candidate.direct
                            ? DlcDownloadService.download(
                                candidate.url,
                                target.getParent(),
                                fileName,
                                (downloadedBytes, totalBytes) -> {
                                    if (listener != null) {
                                        listener.onProgress(downloadedBytes, totalBytes);
                                    }
                                },
                                () -> requiredCancelRequested || isRequiredCanceled()
                            )
                            : DlcDownloadService.downloadKnown(
                                candidate.url,
                                target.getParent(),
                                fileName,
                                candidate.expectedSize,
                                (downloadedBytes, totalBytes) -> {
                                    if (listener != null) {
                                        listener.onProgress(downloadedBytes, totalBytes);
                                    }
                                },
                                () -> requiredCancelRequested || isRequiredCanceled()
                            );
                        checkRequiredCanceled();
                        verifyDownloadedFile(downloaded, candidate);
                        return downloaded.getFileName() == null ? fileName : downloaded.getFileName().toString();
                    } catch (DlcDownloadService.DownloadCanceledException ex) {
                        throw new DownloadCanceledException("canceled");
                    } catch (DownloadCanceledException ex) {
                        throw ex;
                    } catch (IOException ex) {
                        lastError = ex;
                        if (attempt >= DlcPathConfig.getMaxRetryCount()) {
                            break;
                        }
                    }
                }
            }
        }
        if (lastError == null) {
            lastError = resolver.lastError();
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("missing download url");
    }

    private static String downloadRemoteEntry(DlcRemoteEntry entry) throws IOException, InterruptedException {
        return downloadRemoteEntryWithProgress(entry, null, null);
    }

    private static String downloadRemoteEntryWithProgress(DlcRemoteEntry entry, RemoteItemState state, ProgressListener listener)
        throws IOException, InterruptedException {
        if (entry == null) {
            return null;
        }
        DlcDownloadCandidate.Resolver resolver = DlcDownloadCandidate.resolver(entry);
        IOException lastError = null;
        List<DlcDownloadCandidate> candidates;
        while (!(candidates = resolver.next()).isEmpty()) {
            for (DlcDownloadCandidate candidate : candidates) {
                for (int attempt = 1; attempt <= DlcPathConfig.getMaxRetryCount(); attempt++) {
                    try {
                        String fileName = candidate.fileNameOverride;
                        boolean fileNameExplicit = entry.isFileNameExplicit()
                            && fileName != null && !fileName.isBlank();
                        if (fileName == null || fileName.isBlank()) {
                            fileName = entry.getFileNameHint();
                        }
                        String suggestedName = null;
                        if (candidate.direct || !DlcDownloadUtil.isLikelyFileName(fileName)) {
                            try {
                                suggestedName = DlcDownloadService.suggestFileName(candidate.url);
                            } catch (IOException ignored) {
                            }
                        }
                        if (!fileNameExplicit) {
                            if (candidate.direct && suggestedName != null && !suggestedName.isBlank()) {
                                fileName = suggestedName;
                            }
                            if (!DlcDownloadUtil.isLikelyFileName(fileName) && suggestedName != null && !suggestedName.isBlank()) {
                                fileName = suggestedName;
                            }
                        }
                        fileName = finalizeFileName(fileName, entry, candidate.url);
                        Path target = entry.resolveTargetPath(fileName);
                        if (target == null) {
                            throw new IOException("invalid target path");
                        }
                        Files.createDirectories(target.getParent());
                        if (Files.exists(target)) {
                            return fileName;
                        }
                        Path downloaded = candidate.direct
                            ? DlcDownloadService.download(
                                candidate.url,
                                target.getParent(),
                                fileName,
                                (downloadedBytes, totalBytes) -> {
                                    if (listener != null) {
                                        listener.onProgress(downloadedBytes, totalBytes);
                                    }
                                },
                                null
                            )
                            : DlcDownloadService.downloadKnown(
                                candidate.url,
                                target.getParent(),
                                fileName,
                                candidate.expectedSize,
                                (downloadedBytes, totalBytes) -> {
                                    if (listener != null) {
                                        listener.onProgress(downloadedBytes, totalBytes);
                                    }
                                },
                                null
                            );
                        verifyDownloadedFile(downloaded, candidate);
                        String actualName = downloaded.getFileName() == null ? fileName : downloaded.getFileName().toString();
                        entry.setFileNameHint(actualName);
                        return actualName;
                    } catch (IOException ex) {
                        lastError = ex;
                        if (attempt >= DlcPathConfig.getMaxRetryCount()) {
                            break;
                        }
                    }
                }
            }
        }
        if (lastError == null) {
            lastError = resolver.lastError();
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("missing download url");
    }

    private static void checkRequiredCanceled() throws DownloadCanceledException {
        if (requiredCancelRequested || isRequiredCanceled()) {
            throw new DownloadCanceledException("canceled");
        }
    }

    private static boolean isRequiredCanceled() {
        return requiredSession != null && requiredSession.cancelRequested.get();
    }

    private static void verifyDownloadedFile(Path tempFile, DlcDownloadCandidate candidate) throws IOException {
        String hashType = candidate == null ? null : candidate.hashType;
        String hashValue = candidate == null ? null : candidate.hashValue;
        if (hashType != null && hashValue != null && !hashValue.isBlank()) {
            String algorithm = mapHashAlgorithm(hashType);
            String actual = DlcDownloadUtil.computeHashHex(tempFile, algorithm);
            if (actual == null || !actual.equalsIgnoreCase(DlcDownloadUtil.normalizeHash(hashValue))) {
                Files.deleteIfExists(tempFile);
                throw new IOException("hash mismatch");
            }
            return;
        }
        long expectedSize = candidate == null ? -1L : candidate.expectedSize;
        if (expectedSize > 0) {
            long size = Files.size(tempFile);
            if (size != expectedSize) {
                Files.deleteIfExists(tempFile);
                throw new IOException("size mismatch");
            }
        }
    }

    private static String mapHashAlgorithm(String hashType) {
        if (hashType == null) {
            return null;
        }
        return switch (hashType.toLowerCase(Locale.ROOT)) {
            case "sha1", "sha-1" -> "SHA-1";
            case "sha256", "sha-256" -> "SHA-256";
            case "sha512", "sha-512" -> "SHA-512";
            case "md5" -> "MD5";
            default -> hashType;
        };
    }

    private static final class DownloadCanceledException extends IOException {
        private DownloadCanceledException(String message) {
            super(message);
        }
    }

    private static String finalizeFileName(String fileName, DlcRemoteEntry entry, String url) {
        String cleaned = DlcDownloadUtil.sanitizeFileName(fileName);
        if (cleaned != null && !cleaned.isBlank()) {
            return cleaned;
        }
        String urlName = DlcDownloadUtil.extractFileNameFromUrl(url);
        cleaned = DlcDownloadUtil.sanitizeFileName(urlName);
        if (cleaned != null && !cleaned.isBlank()) {
            return cleaned;
        }
        String fallback = entry == null ? null : entry.getFileNameHint();
        cleaned = DlcDownloadUtil.sanitizeFileName(fallback);
        if (cleaned != null && !cleaned.isBlank()) {
            return cleaned;
        }
        if (entry != null) {
            return switch (entry.getType()) {
                case MOD -> "download.jar";
                case RESOURCE_PACK, DATA_PACK, TACZ_PACK, UNKNOWN -> "download.zip";
            };
        }
        return "download.zip";
    }

    private static String finalizeFileName(String fileName, DlcRequiredEntry entry, String url) {
        String cleaned = DlcDownloadUtil.sanitizeFileName(fileName);
        if (cleaned != null && !cleaned.isBlank()) {
            return cleaned;
        }
        String urlName = DlcDownloadUtil.extractFileNameFromUrl(url);
        cleaned = DlcDownloadUtil.sanitizeFileName(urlName);
        if (cleaned != null && !cleaned.isBlank()) {
            return cleaned;
        }
        if (entry != null) {
            String fallback = entry.getBaseName();
            if (fallback != null && !fallback.isBlank()) {
                return fallback + ".jar";
            }
        }
        return "download.jar";
    }

    private static void updateRemoteFileNameKey(DlcRemoteEntry entry, String fileName) {
        if (entry == null) {
            return;
        }
        String normalized = DlcDownloadUtil.sanitizeFileName(fileName);
        if (normalized == null) {
            return;
        }
        Path config = entry.getConfigPath();
        if (config == null || !Files.exists(config)) {
            return;
        }
        JsonObject obj = DcConfigIO.read(config, DcConfigIO.Layout.DLC_STRUCTURED);
        if (obj == null) {
            return;
        }
        JsonObject reordered = reorderRemoteJson(obj, normalized);
        DcConfigIO.write(config, reordered, DcConfigIO.Layout.DLC_STRUCTURED);
        entry.setFileNameHint(normalized);
    }

    private static JsonObject reorderRemoteJson(JsonObject obj, String fileNameOverride) {
        JsonObject ordered = new JsonObject();
        ordered.addProperty("curseforge_projectid", getString(obj, "curseforge_projectid"));
        ordered.addProperty("curseforge_fileid", getString(obj, "curseforge_fileid"));
        ordered.addProperty("modrinth_projectid", getString(obj, "modrinth_projectid"));
        ordered.addProperty("modrinth_versionid", getString(obj, "modrinth_versionid"));

        JsonElement directUrl = obj.get("directUrl");
        if (directUrl == null || directUrl.isJsonNull()) {
            ordered.addProperty("directUrl", "");
        } else {
            ordered.add("directUrl", directUrl);
        }
        ordered.addProperty("priority", DlcDownloadCandidate.readPriority(obj));
        ordered.addProperty("identifier", getIdentifier(obj));
        ordered.addProperty("type", getString(obj, "type"));
        String fileName = fileNameOverride == null || fileNameOverride.isBlank()
            ? getString(obj, "file_name")
            : fileNameOverride;
        if (fileName != null) {
            ordered.addProperty("file_name", fileName);
        } else {
            ordered.addProperty("file_name", "");
        }
        ordered.addProperty("isInList", getBoolean(obj, "isInList", true));
        ordered.addProperty("isHidden", getBoolean(obj, "isHidden", false));
        ordered.addProperty("AllowEnableByAll", getBoolean(obj, "AllowEnableByAll", true));
        JsonElement deps = JsonCaseUtil.getIgnoreCase(obj, "dependencies");
        if (deps == null || deps.isJsonNull()) {
            ordered.addProperty("dependencies", "");
        } else {
            ordered.add("dependencies", normalizeRelationElement(deps));
        }
        JsonElement conflicts = JsonCaseUtil.getIgnoreCase(obj, "conflicts");
        if (conflicts == null || conflicts.isJsonNull()) {
            ordered.addProperty("conflicts", "");
        } else {
            ordered.add("conflicts", normalizeRelationElement(conflicts));
        }
        JsonObject display = new JsonObject();
        JsonElement displayObj = JsonCaseUtil.getIgnoreCase(obj, "DisplayName");
        if (displayObj != null) {
            if (displayObj.isJsonObject()) {
                for (var e : displayObj.getAsJsonObject().entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        display.addProperty(e.getKey(), e.getValue().getAsString());
                    }
                }
            } else if (displayObj.isJsonPrimitive()) {
                display.addProperty("en_us", displayObj.getAsString());
            }
        }
        ordered.add("DisplayName", display);
        ordered.addProperty("alarm", getBoolean(obj, "alarm", false));
        JsonObject tipObj = new JsonObject();
        JsonElement tip = JsonCaseUtil.getIgnoreCase(obj, "tip");
        if (tip != null) {
            if (tip.isJsonObject()) {
                for (var e : tip.getAsJsonObject().entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        tipObj.addProperty(e.getKey(), e.getValue().getAsString());
                    }
                }
            } else if (tip.isJsonPrimitive()) {
                tipObj.addProperty("en_us", tip.getAsString());
            }
        }
        ordered.add("tip", tipObj);
        return ordered;
    }

    private static void updateRequiredFileNameKey(DlcRequiredEntry entry, String fileName) {
        if (entry == null) {
            return;
        }
        String normalized = DlcDownloadUtil.sanitizeFileName(fileName);
        if (normalized == null) {
            return;
        }
        Path config = entry.getConfigPath();
        if (config == null || !Files.exists(config)) {
            return;
        }
        JsonObject obj = DcConfigIO.read(config, DcConfigIO.Layout.DLC_STRUCTURED);
        if (obj == null) {
            return;
        }
        JsonObject ordered = reorderRequiredJson(obj, normalized);
        DcConfigIO.write(config, ordered, DcConfigIO.Layout.DLC_STRUCTURED);
    }

    private static void updateRequiredUserRemoved(DlcRequiredEntry entry, boolean userRemoved) {
        if (entry == null) {
            return;
        }
        Path config = entry.getConfigPath();
        if (config == null || !Files.exists(config)) {
            return;
        }
        JsonObject obj = DcConfigIO.read(config, DcConfigIO.Layout.DLC_STRUCTURED);
        if (obj == null) {
            return;
        }
        if (userRemoved) {
            obj.addProperty("UserRemoved", true);
        } else {
            obj.remove("UserRemoved");
            obj.remove("userRemoved");
        }
        JsonObject ordered = reorderRequiredJson(obj, entry.getRequiredFileName());
        DcConfigIO.write(config, ordered, DcConfigIO.Layout.DLC_STRUCTURED);
        entry.setUserRemoved(userRemoved);
    }

    private static JsonObject reorderRequiredJson(JsonObject obj, String fileNameOverride) {
        JsonObject ordered = new JsonObject();
        ordered.addProperty("curseforge_projectid", getString(obj, "curseforge_projectid"));
        ordered.addProperty("curseforge_fileid", getString(obj, "curseforge_fileid"));
        ordered.addProperty("modrinth_projectid", getString(obj, "modrinth_projectid"));
        ordered.addProperty("modrinth_versionid", getString(obj, "modrinth_versionid"));
        JsonElement directUrl = obj.get("directUrl");
        if (directUrl == null || directUrl.isJsonNull()) {
            ordered.addProperty("directUrl", "");
        } else {
            ordered.add("directUrl", directUrl);
        }
        ordered.addProperty("priority", DlcDownloadCandidate.readPriority(obj));
        ordered.addProperty("identifier", getString(obj, "identifier"));
        String fileName = fileNameOverride == null || fileNameOverride.isBlank()
            ? getString(obj, "file_name")
            : fileNameOverride;
        if (fileName != null) {
            ordered.addProperty("file_name", fileName);
        } else {
            ordered.addProperty("file_name", "");
        }
        ordered.addProperty("appliedTarget", getString(obj, "appliedTarget"));
        ordered.addProperty("isInList", getBoolean(obj, "isInList", true));
        ordered.addProperty("isHidden", getBoolean(obj, "isHidden", false));
        boolean userRemoved = getBoolean(obj, "UserRemoved", false) || getBoolean(obj, "userRemoved", false);
        if (userRemoved) {
            ordered.addProperty("UserRemoved", true);
        }
        ordered.addProperty("dependencies", getString(obj, "dependencies"));
        JsonObject display = new JsonObject();
        JsonElement displayObj = obj.get("DisplayName");
        if (displayObj != null && displayObj.isJsonObject()) {
            for (var e : displayObj.getAsJsonObject().entrySet()) {
                display.addProperty(e.getKey(), e.getValue().getAsString());
            }
        }
        ordered.add("DisplayName", display);
        JsonObject tipObj = new JsonObject();
        JsonElement tip = obj.get("tip");
        if (tip != null && tip.isJsonObject()) {
            for (var e : tip.getAsJsonObject().entrySet()) {
                tipObj.addProperty(e.getKey(), e.getValue().getAsString());
            }
        }
        ordered.add("tip", tipObj);
        return ordered;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || key == null) {
            return "";
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private static String getIdentifier(JsonObject obj) {
        String identifier = getString(obj, "identifier");
        if (identifier == null || identifier.isBlank()) {
            identifier = getString(obj, "dlc_identifier");
        }
        return identifier;
    }

    private static JsonElement normalizeRelationElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new JsonPrimitive("");
        }
        if (element.isJsonPrimitive()) {
            return element.deepCopy();
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                array.add(normalizeRelationElement(child));
            }
            return array;
        }
        JsonObject obj = element.getAsJsonObject();
        JsonObject normalized = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String targetKey = key;
            if ("dlc_identifier".equalsIgnoreCase(key)) {
                targetKey = "identifier";
            } else if ("dlc_name".equalsIgnoreCase(key)) {
                targetKey = "file_name";
            }
            if (normalized.has(targetKey)) {
                continue;
            }
            normalized.add(targetKey, normalizeRelationElement(entry.getValue()));
        }
        return normalized;
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj == null || key == null) {
            return fallback;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static JsonObject readConfigObject(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try (java.io.Reader reader = Files.newBufferedReader(path)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            return PRETTY_GSON.fromJson(jsonReader, JsonObject.class);
        } catch (IOException | JsonSyntaxException ignored) {
            return null;
        }
    }

    private static Map<String, String> readLocalizedMap(JsonObject obj) {
        Map<String, String> values = new HashMap<>();
        if (obj == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                String key = JsonCaseUtil.normalizeLangKey(entry.getKey());
                if (key != null) {
                    values.put(key, entry.getValue().getAsString());
                }
            }
        }
        return values;
    }

    private static Map<String, String> resolveDisplayNames(DlcRemoteEntry entry, String fallbackName) {
        Map<String, String> values = new HashMap<>();
        if (entry != null && entry.getDisplayNames() != null) {
            values.putAll(entry.getDisplayNames());
        }
        if (!values.isEmpty()) {
            return values;
        }
        JsonObject obj = entry == null ? null : readConfigObject(entry.getConfigPath());
        if (obj != null) {
            JsonObject displayObj = JsonCaseUtil.getObjectIgnoreCase(obj, "DisplayName");
            values.putAll(readLocalizedMap(displayObj));
            if (values.isEmpty()) {
                String display = JsonCaseUtil.getStringIgnoreCase(obj, "DisplayName");
                if (display != null && !display.isBlank()) {
                    values.put("en_us", display);
                }
            }
        }
        if (values.isEmpty() && fallbackName != null && !fallbackName.isBlank()) {
            values.put("en_us", fallbackName);
        }
        return values;
    }

    private static Map<String, String> resolveTips(DlcRemoteEntry entry) {
        Map<String, String> values = new HashMap<>();
        if (entry != null && entry.getTips() != null) {
            values.putAll(entry.getTips());
        }
        if (!values.isEmpty()) {
            return values;
        }
        JsonObject obj = entry == null ? null : readConfigObject(entry.getConfigPath());
        if (obj != null) {
            JsonObject tipObj = JsonCaseUtil.getObjectIgnoreCase(obj, "tip");
            values.putAll(readLocalizedMap(tipObj));
            if (values.isEmpty()) {
                String tip = JsonCaseUtil.getStringIgnoreCase(obj, "tip");
                if (tip != null && !tip.isBlank()) {
                    values.put("en_us", tip);
                }
            }
        }
        return values;
    }

    private static boolean isNoticeConfig(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        return "notice".equalsIgnoreCase(DcFormatUtil.stripConfigExtension(fileName));
    }

}
