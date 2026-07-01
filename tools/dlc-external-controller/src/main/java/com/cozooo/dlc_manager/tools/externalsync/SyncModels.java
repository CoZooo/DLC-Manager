package com.cozooo.dlc_manager.tools.externalsync;

import java.util.ArrayList;
import java.util.List;

public final class SyncModels {
    public static final int DEFAULT_SERVER_PORT = 29690;
    public static final int DEFAULT_TIMEOUT_MS = 10_000;

    private SyncModels() {
    }

    public static final class ServerConfig {
        public String host = "127.0.0.1";
        public int port = DEFAULT_SERVER_PORT;
        public String password = "";
        public int timeoutMs = DEFAULT_TIMEOUT_MS;
        public String player = "DLCExternalController";
        public String uuid = "external-controller";
    }

    static final class SyncRequest {
        String gameDir;
        String modsDir;
        String dlcRoot;
        ServerConfig server;
        boolean downloadMissing = true;
        boolean uploadExtra = true;
        boolean applyChanges = true;
        List<String> deleteConfirmed = new ArrayList<>();
        String minecraftVersion;
        String loader;
    }

    static final class RemoteEntry {
        String configPath;
        String identifier;
        String fileName;
        String dlcType = "MOD";
        String curseforgeProjectId;
        String curseforgeFileId;
        String modrinthProjectId;
        String modrinthVersionId;
        List<String> directUrls = new ArrayList<>();
        List<String> priority = new ArrayList<>();
        List<String> dependencies = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
    }

    public static final class ServerItem {
        public String id;
        public String name;
        public String identifier;
        public String path;
        public String type;
        public Boolean enabled;
        public List<String> dependencies = new ArrayList<>();
        public List<String> conflicts = new ArrayList<>();
        public boolean allowEnableByAll = true;
        public boolean alarm;
    }

    public static final class ServerInfo {
        public String baseVersion;
        public String dlcVersion;
        public long remoteMissing;
        public long requiredMissing;
        public List<ServerItem> items = new ArrayList<>();
    }

    public static final class FileEntry {
        public String name;
        public boolean dir;
        public long size;
        public long modified;
    }

    static final class DownloadCandidate {
        String source;
        String url;
        String fileName;
        String sha1;
        String sha256;
        String sha512;
        String md5;
        long size = -1L;
    }

    static final class SyncIssue {
        String kind;
        String severity;
        String identifier;
        String fileName;
        String message;
        String localPath;
        RemoteEntry remote;
        DownloadCandidate candidate;
        FileEntry serverFile;
    }

    static final class NativeSyncPreview {
        String mode = "native";
        String gameDir;
        String modsDir;
        String dlcRoot;
        List<RemoteEntry> remoteEntries = new ArrayList<>();
        ServerInfo serverInfo;
        List<FileEntry> serverFiles = new ArrayList<>();
        List<SyncIssue> issues = new ArrayList<>();
    }

    static final class NativeSyncResult {
        NativeSyncPreview preview;
        List<String> downloaded = new ArrayList<>();
        List<String> uploaded = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        boolean applied;
        boolean requiresRestart;
        List<String> messages = new ArrayList<>();
    }

    static final class TestServerResult {
        boolean ok;
        ServerInfo info;
        String message;
    }
}
