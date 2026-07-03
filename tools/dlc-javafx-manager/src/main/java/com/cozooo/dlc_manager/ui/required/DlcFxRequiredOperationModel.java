package com.cozooo.dlc_manager.ui.required;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DlcFxRequiredOperationModel {
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final Set<String> installing = ConcurrentHashMap.newKeySet();
    private final Set<String> activeSingles = ConcurrentHashMap.newKeySet();
    private final Set<String> activeBatch = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicBoolean> cancelRequests = new ConcurrentHashMap<>();
    private final Map<String, Float> progressByKey = new ConcurrentHashMap<>();
    private final AtomicBoolean batchCancelRequested = new AtomicBoolean(false);

    public void reset() {
        active.clear();
        installing.clear();
        activeSingles.clear();
        activeBatch.clear();
        cancelRequests.clear();
        progressByKey.clear();
        batchCancelRequested.set(false);
    }

    public void beginBatch(Collection<String> keys) {
        reset();
        for (String key : keys == null ? Set.<String>of() : keys) {
            if (valid(key)) {
                active.add(key);
                activeBatch.add(key);
                cancelRequests.put(key, new AtomicBoolean(false));
                progressByKey.put(key, 0f);
            }
        }
    }

    public void beginSingle(String key) {
        reset();
        if (!valid(key)) {
            return;
        }
        active.add(key);
        activeSingles.add(key);
        installing.remove(key);
        cancelRequests.put(key, new AtomicBoolean(false));
        progressByKey.put(key, 0f);
    }

    public void begin(String key) {
        if (!valid(key)) {
            return;
        }
        active.add(key);
        installing.remove(key);
        cancelRequests.put(key, new AtomicBoolean(false));
        progressByKey.put(key, 0f);
    }

    public void markDownloading(String key, float progress) {
        if (!valid(key)) {
            return;
        }
        active.add(key);
        installing.remove(key);
        setProgress(key, progress);
    }

    public void markInstalling(String key) {
        if (!valid(key)) {
            return;
        }
        active.add(key);
        installing.add(key);
        setProgress(key, 1f);
    }

    public void applyProgress(String key, long downloadedBytes, long totalBytes, boolean done) {
        if (!valid(key)) {
            return;
        }
        if (totalBytes > 0) {
            setProgress(key, downloadedBytes / (float) totalBytes);
        } else if (!progressByKey.containsKey(key)) {
            setProgress(key, done ? 1f : 0f);
        }
        if (done) {
            active.remove(key);
            installing.remove(key);
            cancelRequests.remove(key);
        } else {
            active.add(key);
        }
    }

    public void finish(String key) {
        active.remove(key);
        installing.remove(key);
        activeSingles.remove(key);
        activeBatch.remove(key);
        cancelRequests.remove(key);
        progressByKey.remove(key);
    }

    public void finishAll() {
        for (String key : new ArrayList<>(active)) {
            finish(key);
        }
        active.clear();
        installing.clear();
        activeSingles.clear();
        activeBatch.clear();
        cancelRequests.clear();
        progressByKey.clear();
        batchCancelRequested.set(false);
    }

    public void requestCancel(String key) {
        AtomicBoolean cancel = cancelRequests.get(key);
        if (cancel != null) {
            cancel.set(true);
        }
    }

    public void requestCancelAll() {
        batchCancelRequested.set(true);
        for (AtomicBoolean cancel : cancelRequests.values()) {
            cancel.set(true);
        }
    }

    public void requestBatchCancel() {
        batchCancelRequested.set(true);
        for (String key : activeBatch) {
            requestCancel(key);
        }
    }

    public boolean isBatchCancelRequested() {
        return batchCancelRequested.get();
    }

    public boolean isCancelRequested(String key) {
        AtomicBoolean cancel = cancelRequests.get(key);
        return cancel != null && cancel.get();
    }

    public boolean isActive(String key) {
        return active.contains(key);
    }

    public boolean isInstalling(String key) {
        return installing.contains(key);
    }

    public boolean isSingleActive(String key) {
        return activeSingles.contains(key);
    }

    public boolean isBatchActive(String key) {
        return activeBatch.contains(key);
    }

    public Set<String> activeBatchKeys() {
        return new LinkedHashSet<>(activeBatch);
    }

    public float progress(String key) {
        Float value = progressByKey.get(key);
        return value == null ? Float.NaN : value;
    }

    public String progressText(String key) {
        float progress = progress(key);
        return Float.isNaN(progress) ? "" : Math.max(0, Math.min(100, Math.round(progress * 100f))) + "%";
    }

    public float batchProgress() {
        Collection<String> keys = activeBatch.isEmpty() ? progressByKey.keySet() : activeBatch;
        if (keys.isEmpty()) {
            return 0f;
        }
        float total = 0f;
        int count = 0;
        for (String key : keys) {
            Float value = progressByKey.get(key);
            if (value != null) {
                total += Math.max(0f, Math.min(1f, value));
            }
            count++;
        }
        return count == 0 ? 0f : total / count;
    }

    public boolean hasAnyActive() {
        return !active.isEmpty();
    }

    public void setProgress(String key, float progress) {
        if (valid(key)) {
            progressByKey.put(key, Math.max(0f, Math.min(1f, progress)));
        }
    }

    private static boolean valid(String key) {
        return key != null && !key.isBlank();
    }
}
