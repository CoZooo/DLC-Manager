package com.cozooo.dlc_manager.server;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredDependency;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredService;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntime;
import com.cozooo.dlc_manager.client.server.DlcServerRemoteItem;
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredProgressItem;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcTooltipLoader;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
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

        default void onStatusKey(String statusKey) {
        }
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
        private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        private volatile String statusKey;
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
            cancelRequested.set(false);
            int maxConcurrent = Math.max(1, DlcConfig.getMaxConcurrentDownloads());
            ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
            for (RemoteItemState item : items) {
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
            }, "dlc-server-remote-finish").start();
        }

        private void downloadOne(Path dlcRoot, RemoteItemState item) {
            if (item == null || item.entry == null) {
                markDone(item, false, "invalid entry");
                return;
            }
            try {
                if (cancelRequested.get()) {
                    markDone(item, false, "canceled");
                    success.set(false);
                    error = "canceled";
                    return;
                }
                String fileName = downloadRemoteEntryWithProgress(item.entry, item, new ProgressListener() {
                    @Override
                    public void onProgress(long downloadedBytes, long totalBytes) {
                        RemoteDownloadSession.this.statusKey = null;
                        updateProgress(item, downloadedBytes, totalBytes);
                    }

                    @Override
                    public void onStatusKey(String statusKey) {
                        RemoteDownloadSession.this.statusKey = statusKey;
                    }
                }, cancelRequested::get);
                if (fileName != null) {
                    updateRemoteFileNameKey(item.entry, fileName);
                }
                markDone(item, true, null);
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                markDone(item, false, "canceled");
                success.set(false);
                error = "canceled";
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
            status.statusKey = statusKey;
            status.error = error;
            for (RemoteItemState item : items) {
                DlcServerRemoteItem view = toRemoteItem(item.entry, lang);
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
            int maxConcurrent = Math.max(1, DlcConfig.getMaxConcurrentDownloads());
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
                    {
                        DlcRequiredService.installOne(item.entry);
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
                } else {
                    String err = "download_failed:" + safeId(item.entry);
                    markDone(item, false, err);
                    success.set(false);
                    error = err;
                    consoleWarn("[dlc-manager] server required download failed: " + safeId(item.entry));
                    return;
                }
                {
                    DlcRequiredService.installOne(item.entry);
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
                view.name = resolveRequiredDisplayName(item.entry);
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
            list.add(toRemoteItem(entry, lang));
        }
        return list;
    }

    public static List<DlcServerRemoteItem> collectRemoteItems(Path dlcRoot) {
        return collectRemoteItems(dlcRoot, null);
    }

    public static List<DlcServerRemoteItem> collectRemoteItems(Path dlcRoot, String lang) {
        List<DlcServerRemoteItem> list = new ArrayList<>();
        if (dlcRoot == null) {
            return list;
        }
        for (DlcRemoteEntry entry : DlcRemoteLoader.load(dlcRoot)) {
            if (entry == null) {
                continue;
            }
            if (entry.isHidden() && entry.getType() != DlcType.DLC_PACK) {
                continue;
            }
            DlcServerRemoteItem item = toRemoteItem(entry, lang);
            boolean missing = isRemoteMissing(entry, false);
            item.done = !missing;
            item.success = !missing;
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
        reconcileRequiredEntries(dlcRoot, entries);
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
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
            item.name = resolveRequiredDisplayName(entry);
            for (DlcRequiredDependency dep : entry.getDependencies()) {
                if (dep != null && dep.getIdentifier() != null && !dep.getIdentifier().isBlank()) {
                    item.dependencies.add(dep.getIdentifier());
                }
            }
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
            if (com.cozooo.dlc_manager.sync.SyncEndpoint.busy(DlcConfig.getDlcRoot().resolve("tmp/sync"))) {
                DlcServerRemoteStatus blocked = new DlcServerRemoteStatus(); blocked.finished = true; blocked.error = "DLC synchronization is in progress"; return blocked;
            }
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
            if (com.cozooo.dlc_manager.sync.SyncEndpoint.busy(DlcConfig.getDlcRoot().resolve("tmp/sync"))) {
                DlcServerRequiredStatus blocked = new DlcServerRequiredStatus(); blocked.finished = true; blocked.error = "DLC synchronization is in progress"; return blocked;
            }
            if (requiredSession != null && requiredSession.running.get()) {
                return requiredSession.toStatus(lang);
            }
            requiredCancelRequested = false;
            List<DlcRequiredEntry> entries = loadAllRequiredEntries(dlcRoot);
            reconcileRequiredEntries(dlcRoot, entries);
            Map<String, DlcRequiredEntry> index = buildRequiredIndex(entries);
            List<DlcRequiredEntry> targets = entries;
            if (ids != null && !ids.isEmpty()) {
                targets = collectRequiredDependencies(ids, index);
            }
            List<RequiredItemState> items = new ArrayList<>();
            for (DlcRequiredEntry entry : targets) {
                if (entry == null) {
                    continue;
                }

                String id = safeId(entry);
                String name = resolveRequiredDisplayName(entry);
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

    public static boolean cancelRemoteDownload() {
        synchronized (REMOTE_LOCK) {
            if (remoteSession == null || !remoteSession.running.get()) {
                return false;
            }
            remoteSession.requestCancel();
            return true;
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

    private static String resolveRemoteType(DlcRemoteEntry entry) {
        if (entry == null || entry.getType() == null) {
            return "";
        }
        return entry.getType().name();
    }

    private static String resolveRemoteFolder(DlcRemoteEntry entry) {
        if (entry == null) {
            return "__root__";
        }
        try {
            Path config = entry.getConfigPath();
            Path remoteRoot = entry.getDlcRoot() == null ? null : entry.getDlcRoot().resolve("remote");
            String relative = null;
            if (config != null && remoteRoot != null && config.startsWith(remoteRoot)) {
                relative = remoteRoot.relativize(config).toString().replace('\\', '/');
            }
            if (relative == null) {
                Path baseDir = entry.getBaseDir();
                if (baseDir != null && remoteRoot != null && baseDir.startsWith(remoteRoot)) {
                    relative = remoteRoot.relativize(baseDir).toString().replace('\\', '/');
                    return relative.isBlank() ? "__root__" : relative;
                }
            }
            if (relative == null || relative.isBlank()) {
                return "__root__";
            }
            int lastSlash = relative.lastIndexOf('/');
            if (lastSlash <= 0) {
                return "__root__";
            }
            return relative.substring(0, lastSlash);
        } catch (Exception ignored) {
            return "__root__";
        }
    }

    private static DlcServerRemoteItem toRemoteItem(DlcRemoteEntry entry, String lang) {
        DlcServerRemoteItem item = new DlcServerRemoteItem();
        item.key = selectionKey(entry);
        item.name = resolveDisplayName(entry, lang);
        item.type = resolveRemoteType(entry);
        item.folder = resolveRemoteFolder(entry);
        item.hidden = entry != null && entry.isHidden();
        item.displayNames = resolveDisplayNames(entry, item.name);
        item.tips = resolveTips(entry);
        return item;
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

    private static String resolveRequiredDisplayName(DlcRequiredEntry entry) {
        return entry == null ? "" : entry.resolveDisplayName();
    }

    private static String normalizeLang(String langOverride) {
        String lang = langOverride;
        if (lang == null || lang.isBlank()) {
            lang = DlcPlatformServices.languageCode();
        }
        return DlcDcConfigIO.normalizeLangKey(lang);
    }

    public static List<DlcRequiredEntry> collectMissingRequired(Path dlcRoot) {
        List<DlcRequiredEntry> missing = new ArrayList<>();
        if (dlcRoot == null) {
            return missing;
        }
        List<DlcRequiredEntry> entries = loadAllRequiredEntries(dlcRoot);
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
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
        List<DlcRequiredEntry> entries = loadAllRequiredEntries(dlcRoot);
        if (entries == null || entries.isEmpty()) {
            result.success = true;
            return result;
        }
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
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
        if (!collectMissingRequired(dlcRoot).isEmpty()) {
            result.success = false;
            result.error = "required installation incomplete";
            return result;
        }
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
        if (entries != null) for (DlcRequiredEntry entry : entries) if (applyRequiredEntry(entry)) applied++;
        return applied;
    }

    private static boolean applyRequiredEntry(DlcRequiredEntry entry) {
        try { return DlcRequiredService.installOne(entry); }
        catch (IOException exception) { consoleWarn("[dlc-manager] required installation failed: " + exception.getMessage()); return false; }
    }

    private static boolean entryNeedsApply(DlcRequiredEntry entry) {
        return DlcRequiredRuntime.entryNeedsApply(entry);
    }

    private static void reconcileRequiredEntries(Path root, List<DlcRequiredEntry> entries) {
        synchronized (REQUIRED_LOCK) {
            if (root == null || requiredSession != null && requiredSession.running.get()) return;
            try {
                if (root.toAbsolutePath().normalize().equals(DlcConfig.getDlcRoot().toAbsolutePath().normalize())) DlcRequiredService.reconcileEntries(entries);
                else DlcRequiredService.reconcileRoot(root, entries);
            } catch (IOException exception) { throw new IllegalStateException("Required resource recovery failed", exception); }
        }
    }

    private static List<DlcRequiredEntry> loadAllRequiredEntries(Path dlcRoot) {
        if (dlcRoot != null && dlcRoot.toAbsolutePath().normalize().equals(DlcConfig.getDlcRoot().toAbsolutePath().normalize())) return DlcRequiredRuntime.loadAllEntries();
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
                if (depEntry != null) {
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
        if (entry == null) return;
        try {
            if (com.cozooo.dlc_manager.core.DlcResourceHistory.restore(entry.getDlcRoot(), "required", entry.getHistoryKey(), true)) return;
            for (Path target : com.cozooo.dlc_manager.client.required.DlcRequiredInstaller.paths(entry)) if (Files.isRegularFile(target)) Files.delete(target);
        } catch (IOException exception) { throw new java.io.UncheckedIOException("Required resource recovery failed", exception); }
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
                if (!DlcDcFormatUtil.isDcName(fileName)) {
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

    private static String resolveExistingFileName(DlcRequiredEntry entry) {
        return DlcRequiredService.resolveExistingFileName(entry);
    }

    private static boolean isConfigFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        return DlcDcFormatUtil.isDcName(fileName);
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
        return DlcRequiredService.downloadOne(entry, listener == null ? null : listener::onProgress, DlcServerDownloadService::isRequiredCanceled);
    }

    private static String downloadRemoteEntry(DlcRemoteEntry entry) throws IOException, InterruptedException {
        return downloadRemoteEntryWithProgress(entry, null, null, null);
    }

    private static String downloadRemoteEntryWithProgress(DlcRemoteEntry entry, RemoteItemState state, ProgressListener listener)
        throws IOException, InterruptedException {
        return downloadRemoteEntryWithProgress(entry, state, listener, null);
    }

    private static String downloadRemoteEntryWithProgress(DlcRemoteEntry entry, RemoteItemState state, ProgressListener listener,
                                                          DlcDownloadService.CancelChecker cancelChecker)
        throws IOException, InterruptedException {
        if (entry == null) {
            return null;
        }
        DlcDownloadCandidate.Resolver resolver = DlcDownloadCandidate.resolver(entry);
        DlcDownloadService.checkCanceled(cancelChecker);
        precheckDownloadSources(entry, listener);
        IOException lastError = null;
        List<DlcDownloadCandidate> candidates;
        while (!(candidates = resolver.next()).isEmpty()) {
            for (DlcDownloadCandidate candidate : candidates) {
                for (int attempt = 1; attempt <= DlcConfig.getMaxRetryCount(); attempt++) {
                    try {
                        DlcDownloadService.checkCanceled(cancelChecker);
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
                        DlcDownloadService.ProgressListener downloadListener = progressListener(listener);
                        Path downloaded = candidate.direct
                            ? DlcDownloadService.download(candidate.url, target.getParent(), fileName, downloadListener, cancelChecker)
                            : DlcDownloadService.downloadKnown(candidate.url, target.getParent(), fileName, candidate.expectedSize, downloadListener, cancelChecker);
                        verifyDownloadedFile(downloaded, candidate);
                        String actualName = downloaded.getFileName() == null ? fileName : downloaded.getFileName().toString();
                        entry.setFileNameHint(actualName);
                        return actualName;
                    } catch (IOException ex) {
                        lastError = ex;
                        if (attempt >= DlcConfig.getMaxRetryCount()) {
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

    private static void precheckDownloadSources(DlcRemoteEntry entry, ProgressListener listener) {
        try {
            List<DlcDownloadCandidate> candidates = DlcDownloadCandidate.resolve(entry);
            if (!hasMissingHostedDownloadCandidate(entry, candidates)) {
                return;
            }
            DlcDownloadService.precheckDownloadSources(candidates, progressListener(listener));
        } catch (Exception ignored) {
        }
    }

    private static void precheckDownloadSources(DlcRequiredEntry entry, ProgressListener listener) {
        try {
            List<DlcDownloadCandidate> candidates = DlcDownloadCandidate.resolve(entry);
            if (!hasMissingHostedDownloadCandidate(entry, candidates)) {
                return;
            }
            DlcDownloadService.precheckDownloadSources(candidates, progressListener(listener));
        } catch (Exception ignored) {
        }
    }

    private static boolean hasMissingHostedDownloadCandidate(DlcRemoteEntry entry, List<DlcDownloadCandidate> candidates) {
        for (DlcDownloadCandidate candidate : candidates) {
            if (candidate == null || candidate.direct) {
                continue;
            }
            String fileName = finalizeFileName(candidate.fileNameOverride, entry, candidate.url);
            Path target = entry.resolveExistingTargetPath(fileName);
            if (target != null && !Files.exists(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMissingHostedDownloadCandidate(DlcRequiredEntry entry, List<DlcDownloadCandidate> candidates) {
        for (DlcDownloadCandidate candidate : candidates) {
            if (candidate == null || candidate.direct) {
                continue;
            }
            String fileName = finalizeFileName(candidate.fileNameOverride, entry, candidate.url);
            Path target = entry.resolveRequiredPath(fileName);
            if (target != null && !Files.exists(target)) {
                return true;
            }
        }
        return false;
    }

    private static DlcDownloadService.ProgressListener progressListener(ProgressListener listener) {
        if (listener == null) {
            return null;
        }
        return new DlcDownloadService.StatusProgressListener() {
            @Override
            public void onProgress(long downloadedBytes, long totalBytes) {
                listener.onProgress(downloadedBytes, totalBytes);
            }

            @Override
            public void onStatusKey(String statusKey) {
                listener.onStatusKey(statusKey);
            }
        };
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
                case RESOURCE_PACK, DATA_PACK, TACZ_PACK, DLC_PACK, UNKNOWN -> "download.zip";
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
        JsonObject obj = DlcDcConfigIO.read(config, DlcDcConfigIO.Layout.DLC_STRUCTURED);
        if (obj == null) {
            return;
        }
        JsonObject reordered = reorderRemoteJson(obj, normalized);
        DlcDcConfigIO.write(config, reordered, DlcDcConfigIO.Layout.DLC_STRUCTURED);
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
        ordered.addProperty("isHidden", getBoolean(obj, "isHidden", false));
        ordered.addProperty("AllowEnableByAll", getBoolean(obj, "AllowEnableByAll", true));
        JsonElement deps = DlcDcConfigIO.getIgnoreCase(obj, "dependencies");
        if (deps == null || deps.isJsonNull()) {
            ordered.addProperty("dependencies", "");
        } else {
            ordered.add("dependencies", normalizeRelationElement(deps));
        }
        JsonElement conflicts = DlcDcConfigIO.getIgnoreCase(obj, "conflicts");
        if (conflicts == null || conflicts.isJsonNull()) {
            ordered.addProperty("conflicts", "");
        } else {
            ordered.add("conflicts", normalizeRelationElement(conflicts));
        }
        JsonObject display = new JsonObject();
        JsonElement displayObj = DlcDcConfigIO.getIgnoreCase(obj, "DisplayName");
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
        JsonElement tip = DlcDcConfigIO.getIgnoreCase(obj, "tip");
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
        JsonObject obj = DlcDcConfigIO.read(config, DlcDcConfigIO.Layout.DLC_STRUCTURED);
        if (obj == null) {
            return;
        }
        JsonObject ordered = reorderRequiredJson(obj, normalized);
        DlcDcConfigIO.write(config, ordered, DlcDcConfigIO.Layout.DLC_STRUCTURED);
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
        JsonElement dependencies = DlcDcConfigIO.getIgnoreCase(obj, "dependencies");
        ordered.add("dependencies", dependencies == null ? new JsonPrimitive("") : dependencies);
        for (var entry : obj.entrySet()) {
            String key = entry.getKey();
            if (!ordered.has(key)) ordered.add(key, entry.getValue());
        }
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
        return getString(obj, "identifier");
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
                String key = DlcDcConfigIO.normalizeLangKey(entry.getKey());
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
            JsonObject displayObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "DisplayName");
            values.putAll(readLocalizedMap(displayObj));
            if (values.isEmpty()) {
                String display = DlcDcConfigIO.getStringIgnoreCase(obj, "DisplayName");
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
            JsonObject tipObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "tip");
            values.putAll(readLocalizedMap(tipObj));
            if (values.isEmpty()) {
                String tip = DlcDcConfigIO.getStringIgnoreCase(obj, "tip");
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
        return "notice".equalsIgnoreCase(DlcDcFormatUtil.stripConfigExtension(fileName));
    }

}
