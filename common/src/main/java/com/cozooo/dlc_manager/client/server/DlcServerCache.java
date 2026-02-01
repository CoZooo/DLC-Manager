package com.cozooo.dlc_manager.client.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DlcServerCache {
    private static final Map<String, DlcServerInfo> CACHE = new ConcurrentHashMap<>();

    public static DlcServerInfo get(String key) {
        if (key == null) {
            return null;
        }
        return CACHE.get(key);
    }

    public static void put(String key, DlcServerInfo info) {
        if (key == null || info == null) {
            return;
        }
        CACHE.put(key, info);
    }
}
