package com.cozooo.dlc_manager.client.pack;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DlcPackManifestTest {
    @Test
    void readsProjectAndFileIdsAndIgnoresRequiredFlag() {
        DlcPackManifest manifest = DlcPackManifest.read(JsonParser.parseString("""
            {
              "files": [
                { "projectID": 123, "fileID": 456, "required": false },
                { "projectID": "abc", "fileID": "def", "required": true },
                { "projectID": 1 }
              ]
            }
            """).getAsJsonObject());

        assertEquals(2, manifest.files.size());
        assertEquals("123", manifest.files.get(0).projectId);
        assertEquals("456", manifest.files.get(0).fileId);
        assertEquals("abc", manifest.files.get(1).projectId);
        assertEquals("def", manifest.files.get(1).fileId);
    }
}
