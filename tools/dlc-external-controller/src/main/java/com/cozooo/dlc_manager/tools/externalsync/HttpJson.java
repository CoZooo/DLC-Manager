package com.cozooo.dlc_manager.tools.externalsync;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

final class HttpJson {
    private static final Gson GSON = new Gson();

    private final HttpClient client;
    private final int timeoutMs;

    HttpJson(int timeoutMs) {
        this.timeoutMs = timeoutMs <= 0 ? SyncModels.DEFAULT_TIMEOUT_MS : timeoutMs;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.timeoutMs))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    JsonObject getObject(String url, Map<String, String> headers) throws ExternalSyncException {
        JsonElement element = GSON.fromJson(getString(url, headers), JsonElement.class);
        if (element == null || !element.isJsonObject()) {
            throw new ExternalSyncException("invalid json from " + url);
        }
        return element.getAsJsonObject();
    }

    String getString(String url, Map<String, String> headers) throws ExternalSyncException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(timeoutMs))
            .GET()
            .header("User-Agent", "DLC-Manager-External-Controller");
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && value != null) {
                    builder.header(key, value);
                }
            });
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalSyncException("http " + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (IOException ex) {
            throw new ExternalSyncException("request failed: " + url, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ExternalSyncException("request interrupted: " + url, ex);
        }
    }
}
