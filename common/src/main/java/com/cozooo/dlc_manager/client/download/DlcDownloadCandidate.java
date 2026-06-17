package com.cozooo.dlc_manager.client.download;

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
        return curseForgeCandidate(file == null ? null : file.url, file);
    }

    public static List<DlcDownloadCandidate> curseForgeCandidates(CurseForgeClient.CurseForgeFile file) {
        if (file == null) {
            return List.of();
        }
        List<DlcDownloadCandidate> candidates = new ArrayList<>();
        String primaryUrl = file.url;
        boolean primaryIsWebsiteFallback = isCurseForgeWebsiteDownloadUrl(primaryUrl);
        if (!primaryIsWebsiteFallback && primaryUrl != null && !primaryUrl.isBlank()) {
            candidates.add(curseForgeCandidate(primaryUrl, file));
        }
        for (String url : file.alternateUrls) {
            if (url != null && !url.isBlank() && candidates.stream().noneMatch(candidate -> url.equals(candidate.url))) {
                candidates.add(curseForgeCandidate(url, file));
            }
        }
        if (primaryIsWebsiteFallback && primaryUrl != null && !primaryUrl.isBlank()
            && candidates.stream().noneMatch(candidate -> primaryUrl.equals(candidate.url))) {
            candidates.add(curseForgeCandidate(primaryUrl, file));
        }
        return candidates;
    }

    private static DlcDownloadCandidate curseForgeCandidate(String url, CurseForgeClient.CurseForgeFile file) {
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
        return new DlcDownloadCandidate(url == null ? "" : url, false, file == null ? null : file.fileName, hashType, hashValue, file == null ? -1L : file.size);
    }

    private static boolean isCurseForgeWebsiteDownloadUrl(String url) {
        return url != null
            && !url.isBlank()
            && url.contains("curseforge.com/api/v1/mods/")
            && url.endsWith("/download");
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
        return collect(resolver(entry));
    }

    public static Resolver resolver(DlcRemoteEntry entry) {
        if (entry == null) {
            return Resolver.empty();
        }
        return new Resolver(
            entry.getDownloadPriorityList(),
            entry.getCurseforgeProjectId(),
            entry.getCurseforgeFileId(),
            entry.getModrinthProjectId(),
            entry.getModrinthVersionId(),
            entry.getDirectUrls()
        );
    }

    public static List<DlcDownloadCandidate> resolve(DlcRequiredEntry entry) throws IOException, InterruptedException {
        return collect(resolver(entry));
    }

    public static Resolver resolver(DlcRequiredEntry entry) {
        if (entry == null) {
            return Resolver.empty();
        }
        return new Resolver(
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
        return collect(new Resolver(order, curseProjectId, curseFileId, modrinthProjectId, modrinthVersionId, directUrls));
    }

    private static List<DlcDownloadCandidate> collect(Resolver resolver) throws IOException, InterruptedException {
        List<DlcDownloadCandidate> candidates = new ArrayList<>();
        List<DlcDownloadCandidate> batch;
        while (!(batch = resolver.next()).isEmpty()) {
            candidates.addAll(batch);
        }
        return candidates;
    }

    public static final class Resolver {
        private final List<String> order;
        private final String curseProjectId;
        private final String curseFileId;
        private final String modrinthProjectId;
        private final String modrinthVersionId;
        private final List<String> directUrls;
        private int index;
        private boolean fallbackDirectTried;
        private IOException lastError;

        private Resolver(List<String> order, String curseProjectId, String curseFileId,
                         String modrinthProjectId, String modrinthVersionId, List<String> directUrls) {
            this.order = resolveOrder(order);
            this.curseProjectId = curseProjectId;
            this.curseFileId = curseFileId;
            this.modrinthProjectId = modrinthProjectId;
            this.modrinthVersionId = modrinthVersionId;
            this.directUrls = directUrls == null ? List.of() : directUrls;
        }

        private static Resolver empty() {
            return new Resolver(List.of(), null, null, null, null, List.of());
        }

        public List<DlcDownloadCandidate> next() throws IOException, InterruptedException {
            while (index < order.size()) {
                String source = order.get(index++);
                List<DlcDownloadCandidate> candidates = new ArrayList<>();
                try {
                    if ("directurl".equals(source)) {
                        addDirectCandidates(candidates, directUrls);
                    } else if ("curseforge".equals(source)) {
                        addCurseForgeCandidate(candidates, curseProjectId, curseFileId);
                    } else if ("modrinth".equals(source)) {
                        addModrinthCandidate(candidates, modrinthProjectId, modrinthVersionId);
                    }
                } catch (IOException ex) {
                    lastError = ex;
                    continue;
                }
                if (!candidates.isEmpty()) {
                    return candidates;
                }
            }
            if (!fallbackDirectTried && !order.contains("directurl")) {
                fallbackDirectTried = true;
                List<DlcDownloadCandidate> candidates = new ArrayList<>();
                addDirectCandidates(candidates, directUrls);
                if (!candidates.isEmpty()) {
                    return candidates;
                }
            }
            return List.of();
        }

        public IOException lastError() {
            return lastError;
        }
    }

    public static String readPriority(JsonObject obj) {
        JsonElement element = DcConfigIO.getIgnoreCase(obj, "priority");
        return parsePriority(element);
    }

    public static String parsePriority(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            try {
                return element.getAsString();
            } catch (Exception ignored) {
                return "";
            }
        }
        if (!element.isJsonArray()) {
            return "";
        }
        return parsePriorityArray(element.getAsJsonArray());
    }

    private static String parsePriorityArray(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement item : array) {
            if (item == null || !item.isJsonPrimitive()) {
                continue;
            }
            String value;
            try {
                value = item.getAsString();
            } catch (Exception ignored) {
                continue;
            }
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return String.join(",", values);
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
            candidates.addAll(curseForgeCandidates(file));
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
