package com.cozooo.dlc_manager.ui.remote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DlcFxResourceOperationModel {
    private final Set<String> activeSingles = ConcurrentHashMap.newKeySet();
    private final Set<String> activeBatch = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicBoolean> singleCancelRequests = new ConcurrentHashMap<>();
    private final Map<String, Float> progressByKey = new ConcurrentHashMap<>();
    private final AtomicBoolean batchCancelRequested = new AtomicBoolean(false);

    public void reset() {
        activeSingles.clear();
        activeBatch.clear();
        singleCancelRequests.clear();
        progressByKey.clear();
        batchCancelRequested.set(false);
    }

    public void beginBatch(Collection<String> keys) {
        activeBatch.clear();
        batchCancelRequested.set(false);
        for (String key : keys == null ? Set.<String>of() : keys) {
            if (valid(key)) {
                activeBatch.add(key);
                progressByKey.put(key, 0f);
            }
        }
    }

    public void finishBatch() {
        for (String key : new ArrayList<>(activeBatch)) {
            if (!activeSingles.contains(key)) {
                progressByKey.remove(key);
            }
        }
        activeBatch.clear();
        batchCancelRequested.set(false);
    }

    public void requestBatchCancel() {
        batchCancelRequested.set(true);
    }

    public boolean isBatchCancelRequested() {
        return batchCancelRequested.get();
    }

    public void beginSingle(String key) {
        if (!valid(key)) {
            return;
        }
        singleCancelRequests.put(key, new AtomicBoolean(false));
        activeSingles.add(key);
        progressByKey.put(key, 0f);
    }

    public void finishSingle(String key) {
        activeSingles.remove(key);
        singleCancelRequests.remove(key);
        if (!activeBatch.contains(key)) {
            progressByKey.remove(key);
        }
    }

    public void requestSingleCancel(String key) {
        AtomicBoolean cancel = singleCancelRequests.get(key);
        if (cancel != null) {
            cancel.set(true);
        }
    }

    public boolean isSingleCancelRequested(String key) {
        AtomicBoolean cancel = singleCancelRequests.get(key);
        return cancel != null && cancel.get();
    }

    public void setProgress(String key, float progress) {
        if (valid(key)) {
            progressByKey.put(key, Math.max(0f, Math.min(1f, progress)));
        }
    }

    public float progress(String key) {
        Float value = progressByKey.get(key);
        return value == null ? Float.NaN : value;
    }

    public float batchProgress() {
        if (activeBatch.isEmpty()) {
            return 0f;
        }
        float total = 0f;
        int count = 0;
        for (String key : activeBatch) {
            Float value = progressByKey.get(key);
            if (value != null) {
                total += value;
            }
            count++;
        }
        return count == 0 ? 0f : total / count;
    }

    public boolean isSingleActive(String key) {
        return activeSingles.contains(key);
    }

    public boolean isBatchActive(String key) {
        return activeBatch.contains(key);
    }

    public boolean hasAnyActive() {
        return !activeSingles.isEmpty() || !activeBatch.isEmpty();
    }

    public Set<String> activeBatchKeys() {
        return new LinkedHashSet<>(activeBatch);
    }

    private static boolean valid(String key) {
        return key != null && !key.isBlank();
    }
}
