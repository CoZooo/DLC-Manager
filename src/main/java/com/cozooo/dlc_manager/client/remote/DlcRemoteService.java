package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.download.DlcSharedService;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class DlcRemoteService {
    public interface ProgressListener extends DlcSharedService.ProgressListener {
    }

    public interface CancelChecker extends DlcSharedService.CancelChecker {
    }

    public interface SourceSwitchChecker extends DlcSharedService.SourceSwitchChecker<DlcRemoteEntry> {
    }

    public interface BatchProgressListener extends DlcSharedService.BatchProgressListener<DlcRemoteEntry> {
    }

    public static final class EntryError {
        public final DlcRemoteEntry entry;
        public final Throwable error;

        public EntryError(DlcRemoteEntry entry, Throwable error) {
            this.entry = entry;
            this.error = error;
        }
    }

    public static final class BatchResult {
        public final int total;
        public final int success;
        public final List<EntryError> errors;

        public BatchResult(int total, int success, List<EntryError> errors) {
            this.total = total;
            this.success = success;
            this.errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    private DlcRemoteService() {
    }

    public static boolean isMissing(DlcRemoteEntry entry) {
        if (entry == null) {
            return false;
        }
        Path target = entry.getTargetPath();
        return target == null || !Files.exists(target);
    }

    public static int countMissing(List<DlcRemoteEntry> entries) {
        int count = 0;
        if (entries != null) {
            for (DlcRemoteEntry entry : entries) {
                if (isMissing(entry)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static List<DlcRemoteEntry> collectMissing(List<DlcRemoteEntry> entries) {
        List<DlcRemoteEntry> missing = new ArrayList<>();
        if (entries != null) {
            for (DlcRemoteEntry entry : entries) {
                if (isMissing(entry)) {
                    missing.add(entry);
                }
            }
        }
        return missing;
    }

    public static List<DlcRemoteEntry> expandMissingWithDependencies(List<DlcRemoteEntry> roots) {
        List<DlcRemoteEntry> expanded = expandWithDependencies(roots);
        List<DlcRemoteEntry> result = new ArrayList<>();
        for (DlcRemoteEntry entry : expanded) {
            if (isMissing(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static List<DlcRemoteEntry> expandWithDependencies(List<DlcRemoteEntry> roots) {
        List<DlcRemoteEntry> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayDeque<DlcRemoteEntry> queue = new ArrayDeque<>();
        if (roots != null) {
            queue.addAll(roots);
        }
        while (!queue.isEmpty()) {
            DlcRemoteEntry entry = queue.poll();
            if (entry == null) {
                continue;
            }
            String key = entry.getUniqueKey();
            if (key == null || key.isBlank() || !seen.add(key)) {
                continue;
            }
            result.add(entry);
            for (DlcRemoteEntry dep : entry.getRemoteDependencies()) {
                if (dep != null) {
                    queue.add(dep);
                }
            }
        }
        return result;
    }

    public static String downloadOne(DlcRemoteEntry entry, ProgressListener listener, CancelChecker cancelChecker)
        throws IOException, InterruptedException {
        return downloadOne(entry, listener, cancelChecker, null);
    }

    public static String downloadOne(DlcRemoteEntry entry, ProgressListener listener, CancelChecker cancelChecker,
                                     SourceSwitchChecker switchChecker) throws IOException, InterruptedException {
        return downloadOneShared(entry, listener, cancelChecker, switchChecker);
    }

    private static String downloadOneShared(DlcRemoteEntry entry,
                                            DlcSharedService.ProgressListener listener,
                                            DlcSharedService.CancelChecker cancelChecker,
                                            DlcSharedService.SourceSwitchChecker<DlcRemoteEntry> switchChecker)
        throws IOException, InterruptedException {
        if (entry == null) {
            throw new IOException("missing remote entry");
        }
        DlcSharedService.checkCanceled(cancelChecker);
        DlcDownloadCandidate.Resolver resolver = DlcDownloadCandidate.resolver(entry);
        precheckDownloadSources(entry, listener);
        IOException lastError = null;
        int attemptId = 0;
        boolean canceled = false;
        List<DlcDownloadCandidate> candidates;
        while (!(candidates = resolver.next()).isEmpty()) {
            for (DlcDownloadCandidate candidate : candidates) {
                int maxRetry = Math.max(1, DlcConfig.getMaxRetryCount());
                for (int attempt = 1; attempt <= maxRetry; attempt++) {
                    try {
                        DlcSharedService.checkCanceled(cancelChecker);
                        attemptId++;
                        if (listener instanceof DlcSharedService.AttemptProgressListener attemptListener) {
                            attemptListener.onAttemptStart(attemptId);
                        }
                        DlcSharedService.DownloadInfo downloadInfo = null;
                        String headerName = null;
                        boolean shouldProbe = candidate.direct || !DlcDownloadUtil.isLikelyFileName(candidate.fileNameOverride);
                        if (shouldProbe) {
                            downloadInfo = DlcSharedService.openDownload(candidate.url, 0);
                            headerName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName);
                            DlcSharedService.closeQuietly(downloadInfo.connection);
                        }
                        String fileName = candidate.fileNameOverride;
                        boolean fileNameExplicit = entry.isFileNameExplicit() && fileName != null && !fileName.isBlank();
                        if (fileName == null || fileName.isBlank()) {
                            fileName = entry.getFileNameHint();
                        }
                        if (!fileNameExplicit) {
                            if (candidate.direct && headerName != null) {
                                fileName = headerName;
                            }
                            if (!DlcDownloadUtil.isLikelyFileName(fileName) && headerName != null) {
                                fileName = headerName;
                            }
                            if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
                                fileName = DlcDownloadService.suggestFileName(candidate.url);
                            }
                        }
                        fileName = finalizeFileName(fileName, entry, candidate.url);
                        Path target = entry.resolveTargetPath(fileName);
                        if (target == null) {
                            throw new IOException("invalid target path");
                        }
                        Files.createDirectories(target.getParent());
                        if (Files.exists(target)) {
                            entry.setFileNameHint(fileName);
                            DlcRemoteRuntime.updateFileNameKey(entry, fileName);
                            return fileName;
                        }
                        Path resolved = DlcSharedService.downloadCandidate(candidate, target, fileName, shouldProbe, listener,
                            () -> DlcSharedService.isCanceled(cancelChecker) || DlcSharedService.isSwitchRequested(switchChecker, entry));
                        DlcSharedService.verifyDownloadedFile(resolved, candidate, downloadInfo, cancelChecker);
                        entry.setFileNameHint(fileName);
                        DlcRemoteRuntime.updateFileNameKey(entry, fileName);
                        return fileName;
                    } catch (DlcDownloadService.DownloadCanceledException ex) {
                        if (DlcSharedService.isSwitchRequested(switchChecker, entry)) {
                            lastError = new DlcSharedService.DownloadSwitchException("switch");
                            DlcSharedService.clearSwitchRequest(switchChecker, entry);
                            DlcSharedService.notifyProgress(listener, 0L, 1L);
                            break;
                        }
                        canceled = true;
                        throw new DlcSharedService.DownloadCanceledException("canceled");
                    } catch (DlcSharedService.DownloadCanceledException ex) {
                        canceled = true;
                        throw ex;
                    } catch (IOException ex) {
                        if (DlcSharedService.isSwitchException(ex)) {
                            lastError = ex;
                            DlcSharedService.clearSwitchRequest(switchChecker, entry);
                            DlcSharedService.notifyProgress(listener, 0L, 1L);
                            break;
                        }
                        if (ex.getCause() instanceof DlcSharedService.DownloadCanceledException canceledCause) {
                            canceled = true;
                            throw canceledCause;
                        }
                        lastError = ex;
                        if (ex instanceof DlcDownloadService.SegmentDownloadException) {
                            DlcSharedService.notifyProgress(listener, 0L, 1L);
                            break;
                        }
                        if (attempt >= maxRetry) {
                            DlcSharedService.notifyProgress(listener, 0L, 1L);
                        }
                    }
                }
            }
        }
        if (DlcSharedService.isCanceled(cancelChecker) || canceled) {
            throw new DlcSharedService.DownloadCanceledException("canceled");
        }
        if (attemptId > 0) {
            DlcSharedService.notifyProgress(listener, 0L, 1L);
        }
        if (lastError == null) {
            lastError = resolver.lastError();
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("missing download url");
    }

    public static BatchResult downloadAll(List<DlcRemoteEntry> entries,
                                          BatchProgressListener listener,
                                          CancelChecker cancelChecker) throws InterruptedException {
        return downloadAll(entries, listener, cancelChecker, null);
    }

    public static BatchResult downloadAll(List<DlcRemoteEntry> entries,
                                          BatchProgressListener listener,
                                          CancelChecker cancelChecker,
                                          SourceSwitchChecker switchChecker) throws InterruptedException {
        DlcSharedService.BatchResult<DlcRemoteEntry> result = DlcSharedService.downloadAll(
            entries, listener, cancelChecker, switchChecker, DlcRemoteService::downloadOneShared);
        return toBatchResult(result);
    }

    private static void precheckDownloadSources(DlcRemoteEntry entry, DlcSharedService.ProgressListener listener) {
        try {
            List<DlcDownloadCandidate> candidates = DlcDownloadCandidate.resolve(entry);
            if (!hasMissingHostedDownloadCandidate(entry, candidates)) {
                return;
            }
            DlcDownloadService.precheckDownloadSources(candidates, listener == null ? null : DlcSharedService.toDownloadProgress(listener));
        } catch (Exception ignored) {
        }
    }

    private static boolean hasMissingHostedDownloadCandidate(DlcRemoteEntry entry, List<DlcDownloadCandidate> candidates) {
        if (candidates == null) {
            return false;
        }
        for (DlcDownloadCandidate candidate : candidates) {
            if (candidate == null || candidate.direct) {
                continue;
            }
            String fileName = finalizeFileName(candidate.fileNameOverride, entry, candidate.url);
            Path target = entry.resolveTargetPath(fileName);
            if (target != null && !Files.exists(target)) {
                return true;
            }
        }
        return false;
    }

    private static String finalizeFileName(String fileName, DlcRemoteEntry entry, String url) {
        String name = fileName;
        if (name == null || name.isBlank()) {
            String fallback = entry.getFileNameHint();
            if (fallback == null || fallback.isBlank()) {
                fallback = entry.getDlcIdentifier();
            }
            if (fallback == null || fallback.isBlank()) {
                fallback = "dlc-download";
            }
            name = fallback;
        }
        if (!name.contains(".")) {
            String extension = guessExtension(entry);
            if (extension != null) {
                name += extension;
            }
        }
        return name;
    }

    private static String guessExtension(DlcRemoteEntry entry) {
        if (entry == null || entry.getType() == null) {
            return null;
        }
        return switch (entry.getType()) {
            case MOD -> ".jar";
            case RESOURCE_PACK, DATA_PACK, TACZ_PACK, DLC_PACK, UNKNOWN -> ".zip";
        };
    }

    private static BatchResult toBatchResult(DlcSharedService.BatchResult<DlcRemoteEntry> result) {
        List<EntryError> errors = new ArrayList<>();
        for (DlcSharedService.EntryError<DlcRemoteEntry> error : result.errors) {
            errors.add(new EntryError(error.entry, error.error));
        }
        return new BatchResult(result.total, result.success, errors);
    }
}
