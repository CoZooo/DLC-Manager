package com.cozooo.dlc_manager.ui;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxRemoteDownloadProgressModelTest {
    @Test
    void appliesPerItemProgressInsteadOfBroadcastingTotalProgress() {
        DlcFxResourceOperationModel model = new DlcFxResourceOperationModel();
        model.beginBatch(List.of("a", "b"));
        JsonObject status = statusWithItems(
            item("a", 25, 100, false),
            item("b", 75, 100, false)
        );

        DlcFxRemoteDownloadProgressModel.applyItemProgress(status, model);

        assertEquals(0.25f, model.progress("a"), 0.0001f);
        assertEquals(0.75f, model.progress("b"), 0.0001f);
    }

    @Test
    void marksDoneItemsAsCompleteWhenTotalBytesAreUnknown() {
        DlcFxResourceOperationModel model = new DlcFxResourceOperationModel();
        model.beginBatch(List.of("a"));
        JsonObject status = statusWithItems(item("a", 0, 0, true));

        DlcFxRemoteDownloadProgressModel.applyItemProgress(status, model);

        assertEquals(1f, model.progress("a"), 0.0001f);
    }

    private static JsonObject statusWithItems(JsonObject... items) {
        JsonObject status = new JsonObject();
        var array = new com.google.gson.JsonArray();
        for (JsonObject item : items) {
            array.add(item);
        }
        status.add("items", array);
        return status;
    }

    private static JsonObject item(String key, long downloadedBytes, long totalBytes, boolean done) {
        JsonObject item = new JsonObject();
        item.addProperty("key", key);
        item.addProperty("downloadedBytes", downloadedBytes);
        item.addProperty("totalBytes", totalBytes);
        item.addProperty("done", done);
        return item;
    }
}
