package com.cozooo.dlc_manager.client.download;

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class DlcDownloadCandidate {
    public final String url;
    public final boolean direct;
    public final String fileNameOverride;
    public final String hashType;
    public final String hashValue;
    public final long expectedSize;

    private DlcDownloadCandidate(String url, boolean direct, String fileNameOverride, String hashType, String hashValue, long expectedSize) {
        this.url = url;
        this.direct = direct;
        this.fileNameOverride = fileNameOverride;
        this.hashType = hashType;
        this.hashValue = hashValue;
        this.expectedSize = expectedSize;
    }

    public static DlcDownloadCandidate direct(String url) {
        return new DlcDownloadCandidate(url, true, null, null, null, -1L);
    }

    public static DlcDownloadCandidate curseForge(CurseForgeClient.CurseForgeFile file) {
        String hashType = null;
        String hashValue = null;
        if (file != null) {
            if (file.sha1 != null && !file.sha1.isBlank()) {
                hashType = "sha1";
                hashValue = file.sha1;
            } else if (file.md5 != null && !file.md5.isBlank()) {
                hashType = "md5";
                hashValue = file.md5;
            }
        }
        return new DlcDownloadCandidate(file.url, false, file.fileName, hashType, hashValue, file.size);
    }

    public static DlcDownloadCandidate modrinth(ModrinthClient.ModrinthVersion version) {
        String hashType = null;
        String hashValue = null;
        if (version != null) {
            if (version.sha1 != null && !version.sha1.isBlank()) {
                hashType = "sha1";
                hashValue = version.sha1;
            } else if (version.sha512 != null && !version.sha512.isBlank()) {
                hashType = "sha512";
                hashValue = version.sha512;
            }
        }
        return new DlcDownloadCandidate(version.url, false, version.fileName, hashType, hashValue, version.size);
    }

    public static List<DlcDownloadCandidate> resolve(DlcRemoteEntry entry) throws IOException, InterruptedException {
        if (entry == null) {
            return List.of();
        }
        return resolve(
            entry.getDownloadPriorityList(),
            entry.getCurseforgeProjectId(),
            entry.getCurseforgeFileId(),
            entry.getModrinthProjectId(),
            entry.getModrinthVersionId(),
            entry.getDirectUrls()
        );
    }

    public static List<DlcDownloadCandidate> resolve(DlcRequiredEntry entry) throws IOException, InterruptedException {
        if (entry == null) {
            return List.of();
        }
        return resolve(
            entry.getDownloadPriorityList(),
            entry.getCurseforgeProjectId(),
            entry.getCurseforgeFileId(),
            entry.getModrinthProjectId(),
            entry.getModrinthVersionId(),
            entry.getDirectUrls()
        );
    }

    public static List<DlcDownloadCandidate> resolve(List<String> order,
                                                     String curseProjectId,
                                                     String curseFileId,
                                                     String modrinthProjectId,
                                                     String modrinthVersionId,
                                                     List<String> directUrls) throws IOException, InterruptedException {
        List<DlcDownloadCandidate> candidates = new ArrayList<>();
        List<String> resolvedOrder = resolveOrder(order);
        for (String source : resolvedOrder) {
            if ("directurl".equals(source)) {
                addDirectCandidates(candidates, directUrls);
            } else if ("curseforge".equals(source)) {
                addCurseForgeCandidate(candidates, curseProjectId, curseFileId);
            } else if ("modrinth".equals(source)) {
                addModrinthCandidate(candidates, modrinthProjectId, modrinthVersionId);
            }
        }
        if (candidates.isEmpty()) {
            addDirectCandidates(candidates, directUrls);
        }
        return candidates;
    }

    private static List<String> resolveOrder(List<String> order) {
        List<String> resolved = new ArrayList<>();
        if (order != null) {
            for (String key : order) {
                if (key != null && !key.isBlank() && !resolved.contains(key)) {
                    resolved.add(key);
                }
            }
        }
        if (resolved.isEmpty()) {
            resolved.add("curseforge");
            resolved.add("modrinth");
            resolved.add("directurl");
        }
        return resolved;
    }

    private static void addDirectCandidates(List<DlcDownloadCandidate> candidates, List<String> directUrls) {
        if (directUrls == null) {
            return;
        }
        for (String url : directUrls) {
            if (url != null && !url.isBlank()) {
                candidates.add(direct(url));
            }
        }
    }

    private static void addCurseForgeCandidate(List<DlcDownloadCandidate> candidates, String projectId, String fileId)
        throws IOException, InterruptedException {
        if (fileId == null || fileId.isBlank()) {
            return;
        }
        CurseForgeClient.CurseForgeFile file = CurseForgeClient.fetchFileByIdOrVersion(
            projectId,
            fileId,
            CrossPlatformRuntime.mcVersion(),
            CrossPlatformRuntime.loaderName()
        );
        if (file != null && file.url != null && !file.url.isBlank()) {
            candidates.add(curseForge(file));
        }
    }

    private static void addModrinthCandidate(List<DlcDownloadCandidate> candidates, String projectId, String versionId)
        throws IOException, InterruptedException {
        if (versionId == null || versionId.isBlank()) {
            return;
        }
        ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersionByIdOrVersion(
            projectId,
            versionId,
            CrossPlatformRuntime.mcVersion(),
            CrossPlatformRuntime.loaderName()
        );
        if (version != null && version.url != null && !version.url.isBlank()) {
            candidates.add(modrinth(version));
        }
    }
}
