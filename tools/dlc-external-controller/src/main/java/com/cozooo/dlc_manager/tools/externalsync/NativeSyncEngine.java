package com.cozooo.dlc_manager.tools.externalsync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class NativeSyncEngine {
    SyncModels.NativeSyncPreview preview(SyncModels.SyncRequest request) throws ExternalSyncException {
        DlcPaths paths = DlcPaths.from(request);
        List<SyncModels.RemoteEntry> remoteEntries;
        try {
            remoteEntries = DcRemoteLoader.load(paths.dlcRoot).stream().filter(this::isModEntry).toList();
        } catch (IOException ex) {
            throw new ExternalSyncException("failed to load DLC remote dc files", ex);
        }
        Map<String, Path> localFiles = scanLocalMods(paths.modsDir);
        SyncModels.ServerInfo serverInfo = null;
        List<SyncModels.FileEntry> serverFiles = new ArrayList<>();
        boolean serverFilesKnown = false;
        SyncModels.SyncIssue serverIssue = null;
        if (request.server != null) {
            try (DlcTcpClient client = DlcTcpClient.connect(request.server)) {
                serverInfo = client.fetchList();
                serverFiles = client.listFiles("mods");
                serverFilesKnown = true;
            } catch (Exception ex) {
                serverIssue = issue("server_error", "warning", null, null, ex.getMessage());
            }
        }
        SyncModels.NativeSyncPreview preview = buildPreview(request, paths, remoteEntries, localFiles, serverInfo, serverFiles, serverFilesKnown);
        if (serverIssue != null) {
            preview.issues.add(serverIssue);
        }
        return preview;
    }

    SyncModels.NativeSyncResult apply(SyncModels.SyncRequest request) throws ExternalSyncException {
        SyncModels.NativeSyncPreview preview = preview(request);
        DlcPaths paths = DlcPaths.from(request);
        DownloadResolver resolver = new DownloadResolver(timeout(request));
        Downloader downloader = new Downloader(timeout(request));
        SyncModels.NativeSyncResult result = new SyncModels.NativeSyncResult();
        result.preview = preview;
        Map<String, Path> uploadCandidates = new LinkedHashMap<>();
        if (request.downloadMissing) {
            for (SyncModels.SyncIssue issue : preview.issues) {
                if (!"missing".equals(issue.kind) || issue.remote == null) {
                    continue;
                }
                Path downloaded = downloadFirst(downloader, resolver.resolve(issue.remote, request.minecraftVersion, request.loader), paths.modsDir, issue.remote.fileName, result.messages);
                if (downloaded != null) {
                    result.downloaded.add(downloaded.toString());
                    uploadCandidates.put(remoteModPath(downloaded.getFileName().toString()), downloaded);
                }
            }
        }
        if (request.uploadExtra) {
            for (SyncModels.SyncIssue issue : preview.issues) {
                if (("extra_local".equals(issue.kind) || "server_mismatch".equals(issue.kind)) && issue.localPath != null && issue.fileName != null) {
                    uploadCandidates.put(remoteModPath(issue.fileName), Path.of(issue.localPath));
                }
            }
        }
        if (request.server != null) {
            try (DlcTcpClient client = DlcTcpClient.connect(request.server)) {
                for (Map.Entry<String, Path> upload : uploadCandidates.entrySet()) {
                    try {
                        client.uploadFile(upload.getKey(), upload.getValue());
                        result.uploaded.add(upload.getKey());
                    } catch (Exception ex) {
                        result.messages.add("upload " + upload.getKey() + " failed: " + ex.getMessage());
                    }
                }
                for (String remotePath : request.deleteConfirmed) {
                    if (remotePath == null || remotePath.isBlank()) {
                        continue;
                    }
                    try {
                        client.deleteFile(remotePath.trim());
                        result.deleted.add(remotePath.trim());
                    } catch (Exception ex) {
                        result.messages.add("delete " + remotePath + " failed: " + ex.getMessage());
                    }
                }
                if (request.applyChanges) {
                    if (preview.serverInfo != null) {
                        for (SyncModels.ServerItem item : preview.serverInfo.items) {
                            if (item.id != null && item.enabled != null) {
                                try {
                                    client.setEnabled(item.id, item.enabled);
                                } catch (Exception ex) {
                                    result.messages.add("set " + item.id + " failed: " + ex.getMessage());
                                }
                            }
                        }
                    }
                    result.requiresRestart = client.apply();
                    result.applied = true;
                }
            } catch (IOException ex) {
                result.messages.add("server close failed: " + ex.getMessage());
            }
        }
        return result;
    }

    SyncModels.TestServerResult testServer(SyncModels.ServerConfig server) {
        SyncModels.TestServerResult result = new SyncModels.TestServerResult();
        try (DlcTcpClient client = DlcTcpClient.connect(server)) {
            result.info = client.fetchList();
            result.ok = true;
            result.message = "ok";
        } catch (Exception ex) {
            result.ok = false;
            result.message = ex.getMessage();
        }
        return result;
    }

    private SyncModels.NativeSyncPreview buildPreview(SyncModels.SyncRequest request, DlcPaths paths, List<SyncModels.RemoteEntry> remoteEntries,
                                                       Map<String, Path> localFiles, SyncModels.ServerInfo serverInfo,
                                                       List<SyncModels.FileEntry> serverFiles, boolean serverFilesKnown) {
        SyncModels.NativeSyncPreview preview = new SyncModels.NativeSyncPreview();
        preview.gameDir = paths.gameDir.toString();
        preview.modsDir = paths.modsDir.toString();
        preview.dlcRoot = paths.dlcRoot.toString();
        preview.remoteEntries.addAll(remoteEntries);
        preview.serverInfo = serverInfo;
        preview.serverFiles.addAll(serverFiles);
        Set<String> remoteNames = new LinkedHashSet<>();
        for (SyncModels.RemoteEntry remote : remoteEntries) {
            remoteNames.add(lower(remote.fileName));
        }
        Map<String, SyncModels.FileEntry> serverByName = new LinkedHashMap<>();
        for (SyncModels.FileEntry file : serverFiles) {
            if (!file.dir) {
                serverByName.put(lower(file.name), file);
            }
        }
        DownloadResolver resolver = new DownloadResolver(timeout(request));
        for (SyncModels.RemoteEntry remote : remoteEntries) {
            if (!localFiles.containsKey(lower(remote.fileName))) {
                SyncModels.SyncIssue syncIssue = issue("missing", "warning", remote.identifier, remote.fileName, "local file " + remote.fileName + " is missing");
                syncIssue.remote = remote;
                List<SyncModels.DownloadCandidate> candidates = resolver.resolve(remote, request.minecraftVersion, request.loader);
                syncIssue.candidate = candidates.isEmpty() ? null : candidates.get(0);
                preview.issues.add(syncIssue);
            }
        }
        for (Map.Entry<String, Path> local : localFiles.entrySet()) {
            Path path = local.getValue();
            String fileName = path.getFileName().toString();
            SyncModels.FileEntry serverFile = serverByName.get(local.getKey());
            if (!remoteNames.contains(local.getKey())) {
                SyncModels.SyncIssue syncIssue = issue("extra_local", "info", null, fileName, "local file " + fileName + " is not declared by DLC remote entries");
                syncIssue.localPath = path.toString();
                syncIssue.serverFile = serverFile;
                preview.issues.add(syncIssue);
                continue;
            }
            if (!serverFilesKnown) {
                continue;
            }
            if (serverFile == null) {
                SyncModels.SyncIssue syncIssue = issue("server_mismatch", "warning", null, fileName, "server file is missing for " + fileName);
                syncIssue.localPath = path.toString();
                preview.issues.add(syncIssue);
            } else {
                try {
                    long localSize = Files.size(path);
                    if (serverFile.size > 0 && serverFile.size != localSize) {
                        SyncModels.SyncIssue syncIssue = issue("server_mismatch", "warning", null, fileName, "server file size differs for " + fileName);
                        syncIssue.localPath = path.toString();
                        syncIssue.serverFile = serverFile;
                        preview.issues.add(syncIssue);
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return preview;
    }

    private Map<String, Path> scanLocalMods(Path modsDir) throws ExternalSyncException {
        Map<String, Path> files = new LinkedHashMap<>();
        if (!Files.isDirectory(modsDir)) {
            return files;
        }
        try (Stream<Path> stream = Files.list(modsDir)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> isModFile(path.getFileName().toString()))
                .sorted(Comparator.comparing(path -> lower(path.getFileName().toString())))
                .forEach(path -> files.put(lower(path.getFileName().toString()), path.toAbsolutePath().normalize()));
        } catch (IOException ex) {
            throw new ExternalSyncException("failed to scan mods dir", ex);
        }
        return files;
    }

    private Path downloadFirst(Downloader downloader, List<SyncModels.DownloadCandidate> candidates, Path modsDir, String preferredFileName, List<String> messages) {
        String lastError = "no candidate";
        for (SyncModels.DownloadCandidate candidate : candidates) {
            try {
                return downloader.download(candidate, modsDir, preferredFileName);
            } catch (Exception ex) {
                lastError = ex.getMessage();
            }
        }
        messages.add("download " + preferredFileName + " failed: " + lastError);
        return null;
    }

    private boolean isModEntry(SyncModels.RemoteEntry entry) {
        String type = entry.dlcType == null ? "" : entry.dlcType.trim().toUpperCase(Locale.ROOT);
        return entry.fileName != null && !entry.fileName.isBlank() && (type.isBlank() || type.equals("MOD") || type.equals("MOD_PACK") || type.equals("MODPACK"));
    }

    private boolean isModFile(String fileName) {
        String lowerName = lower(fileName);
        return lowerName.endsWith(".jar") || lowerName.endsWith(".zip");
    }

    private static SyncModels.SyncIssue issue(String kind, String severity, String identifier, String fileName, String message) {
        SyncModels.SyncIssue issue = new SyncModels.SyncIssue();
        issue.kind = kind;
        issue.severity = severity;
        issue.identifier = identifier;
        issue.fileName = fileName;
        issue.message = message;
        return issue;
    }

    private static String remoteModPath(String fileName) {
        return "mods/" + fileName.replace('\\', '/').replace("/", "");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static int timeout(SyncModels.SyncRequest request) {
        return request.server == null ? SyncModels.DEFAULT_TIMEOUT_MS : request.server.timeoutMs;
    }
}
