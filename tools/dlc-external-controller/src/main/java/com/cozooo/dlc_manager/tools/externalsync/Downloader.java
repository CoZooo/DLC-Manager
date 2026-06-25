package com.cozooo.dlc_manager.tools.externalsync;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

final class Downloader {
    private final HttpClient client;
    private final int timeoutMs;

    Downloader(int timeoutMs) {
        this.timeoutMs = timeoutMs <= 0 ? SyncModels.DEFAULT_TIMEOUT_MS : timeoutMs;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.timeoutMs))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    Path download(SyncModels.DownloadCandidate candidate, Path outputDir, String preferredFileName) throws ExternalSyncException {
        if (candidate == null || candidate.url == null || candidate.url.isBlank()) {
            throw new ExternalSyncException("empty download candidate");
        }
        String fileName = firstNonBlank(preferredFileName, candidate.fileName, DcRemoteLoader.extractFileName(candidate.url), "download.bin");
        fileName = DcRemoteLoader.sanitizeFileName(fileName);
        if (fileName == null || fileName.isBlank()) {
            fileName = "download.bin";
        }
        Path target = outputDir.resolve(fileName);
        Path temp = outputDir.resolve(fileName + ".part");
        try {
            Files.createDirectories(outputDir);
            Files.deleteIfExists(temp);
            HttpRequest request = HttpRequest.newBuilder(URI.create(candidate.url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", "DLC-Manager-External-Controller")
                .GET()
                .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalSyncException("http " + response.statusCode() + " downloading " + candidate.url);
            }
            try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(temp)) {
                input.transferTo(output);
            }
            if (!HashUtil.matches(temp, candidate)) {
                throw new ExternalSyncException("download verification failed for " + fileName);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException ex) {
            deleteQuietly(temp);
            throw new ExternalSyncException("download failed: " + candidate.url, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            deleteQuietly(temp);
            throw new ExternalSyncException("download interrupted: " + candidate.url, ex);
        } catch (ExternalSyncException ex) {
            deleteQuietly(temp);
            throw ex;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
