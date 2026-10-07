package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerSession;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxDlcSyncProtocolTest {
    @TempDir
    Path temp;

    @Test
    void requestsServerFileHashes() throws Exception {
        try (DlcSyncTestServer server = new DlcSyncTestServer(connection -> {
            assertEquals("hello", connection.read().get("type").getAsString());
            connection.reply("hello");
            JsonObject request = connection.read();
            assertEquals("file_list", request.get("type").getAsString());
            assertEquals("DLC/mods", request.get("path").getAsString());
            assertTrue(request.get("includeHash").getAsBoolean());
            JsonObject file = new JsonObject();
            file.addProperty("name", "library.jar");
            file.addProperty("size", 3);
            file.addProperty("hash", "lh1-server");
            JsonArray entries = new JsonArray();
            entries.add(file);
            JsonObject response = new JsonObject();
            response.addProperty("ok", true);
            response.add("entries", entries);
            connection.send(response);
        }); DlcServerSession session = DlcServerSession.connect(server.entry(), 5000)) {
            assertEquals("lh1-server", session.listFiles("DLC/mods", true).get(0).hash);
            server.awaitCompleted();
        }
    }

    @Test
    void sendsVerifiedRequiredReplacementAndKeepsPerItemFailures() throws Exception {
        try (DlcSyncTestServer server = new DlcSyncTestServer(connection -> {
            connection.read();
            connection.reply("hello");
            JsonObject request = connection.read();
            assertEquals("required_apply", request.get("type").getAsString());
            assertTrue(request.get("replaceExisting").getAsBoolean());
            assertEquals("library", request.getAsJsonArray("ids").get(0).getAsString());
            JsonObject response = new JsonObject();
            response.addProperty("ok", true);
            response.addProperty("replaceExisting", true);
            response.addProperty("applied", 0);
            response.addProperty("stored", 0);
            JsonArray errors = new JsonArray();
            errors.add("library: locked target");
            response.add("errors", errors);
            connection.send(response);
        }); DlcServerSession session = DlcServerSession.connect(server.entry(), 5000)) {
            JsonObject response = session.requiredApply(List.of("library"), true);
            assertEquals("library: locked target", response.getAsJsonArray("errors").get(0).getAsString());
            server.awaitCompleted();
        }
    }

    @Test
    void uploadsThroughExistingChunkProtocol() throws Exception {
        Path local = Files.writeString(temp.resolve("library.jar"), "new mod bytes");
        try (DlcSyncTestServer server = new DlcSyncTestServer(connection -> {
            connection.read();
            connection.reply("hello");
            JsonObject init = connection.read();
            assertEquals("file_upload_init", init.get("type").getAsString());
            assertEquals("DLC/mods/library.jar", init.get("path").getAsString());
            assertEquals(Files.size(local), init.get("size").getAsLong());
            connection.reply("file_upload_init");
            JsonObject chunk = connection.read();
            assertEquals("file_upload_chunk", chunk.get("type").getAsString());
            assertEquals(init.get("id"), chunk.get("id"));
            assertEquals(Files.readString(local),
                    new String(Base64.getDecoder().decode(chunk.get("data").getAsString()), java.nio.charset.StandardCharsets.UTF_8));
            JsonObject finish = connection.read();
            assertEquals("file_upload_finish", finish.get("type").getAsString());
            connection.reply("file_upload_finish");
        }); DlcServerSession session = DlcServerSession.connect(server.entry(), 5000)) {
            session.uploadFile("DLC/mods/library.jar", local, () -> false);
            server.awaitCompleted();
        }
    }
}
