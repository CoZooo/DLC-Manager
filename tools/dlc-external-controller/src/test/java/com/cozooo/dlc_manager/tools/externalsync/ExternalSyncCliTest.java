package com.cozooo.dlc_manager.tools.externalsync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExternalSyncCliTest {
    @Test
    void parsesRemoteDcFields() throws Exception {
        Path gameDir = Files.createTempDirectory("dlc-ext-dc");
        Path remoteDir = gameDir.resolve("DLC").resolve("remote");
        Files.createDirectories(remoteDir);
        Files.writeString(remoteDir.resolve("alpha.dc"), """
            [basic]
            identifier = alpha
            file_name = alpha.jar
            type = MOD
            [download]
            curseforge_projectid = 123
            curseforge_fileid = 456
            directUrl = ["https://example.test/alpha.jar", "https://mirror.test/alpha.jar"]
            priority = [directUrl, curseforge]
            [dependencies]
            beta
            [conflicts]
            old-alpha
            """);

        List<SyncModels.RemoteEntry> entries = DcRemoteLoader.load(gameDir.resolve("DLC"));

        assertEquals(1, entries.size());
        SyncModels.RemoteEntry entry = entries.get(0);
        assertEquals("alpha", entry.identifier);
        assertEquals("alpha.jar", entry.fileName);
        assertEquals(List.of("directurl", "curseforge"), entry.priority);
        assertEquals(List.of("beta"), entry.dependencies);
        assertEquals(List.of("old-alpha"), entry.conflicts);
    }

    @Test
    void previewsMissingAndExtraLocalMods() throws Exception {
        Path gameDir = Files.createTempDirectory("dlc-ext-preview");
        Files.createDirectories(gameDir.resolve("mods"));
        Files.createDirectories(gameDir.resolve("DLC").resolve("remote"));
        Files.writeString(gameDir.resolve("mods").resolve("extra.jar"), "extra");
        Files.writeString(gameDir.resolve("DLC").resolve("remote").resolve("required.dc"), """
            identifier = required
            file_name = required.jar
            type = MOD
            directUrl = https://example.test/required.jar
            priority = directUrl
            """);
        SyncModels.SyncRequest request = new SyncModels.SyncRequest();
        request.gameDir = gameDir.toString();

        SyncModels.NativeSyncPreview preview = new NativeSyncEngine().preview(request);

        assertTrue(preview.issues.stream().anyMatch(issue -> "missing".equals(issue.kind) && "required.jar".equals(issue.fileName)));
        assertTrue(preview.issues.stream().anyMatch(issue -> "extra_local".equals(issue.kind) && "extra.jar".equals(issue.fileName)));
    }

    @Test
    void speaksDlcJsonLineProtocolForUploadAndApply() throws Exception {
        Path upload = Files.createTempFile("dlc-ext-upload", ".jar");
        Files.writeString(upload, "hello-dlc");
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread server = new Thread(() -> serveProtocol(serverSocket));
            server.start();
            SyncModels.ServerConfig config = new SyncModels.ServerConfig();
            config.host = "127.0.0.1";
            config.port = serverSocket.getLocalPort();
            config.password = "secret";

            try (DlcTcpClient client = DlcTcpClient.connect(config)) {
                SyncModels.ServerInfo info = client.fetchList();
                assertEquals("dlc-test", info.dlcVersion);
                assertEquals(List.of("beta"), info.items.get(0).dependencies);
                assertEquals(List.of("old-alpha"), info.items.get(0).conflicts);
                assertTrue(info.items.get(0).alarm);
                assertFalse(info.items.get(0).allowEnableByAll);
                assertEquals("existing.jar", client.listFiles("mods").get(0).name);
                client.uploadFile("mods/upload.jar", upload);
                assertFalse(client.apply());
            }
            server.join();
        }
    }

    private static void serveProtocol(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept();
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            JsonObject hello = readJson(reader);
            assertEquals("hello", hello.get("type").getAsString());
            assertEquals("secret", hello.get("password").getAsString());
            writeJson(writer, "{\"type\":\"hello\",\"ok\":true}");

            assertEquals("list", readJson(reader).get("type").getAsString());
            writeJson(writer, "{\"type\":\"list\",\"base\":\"base-test\",\"dlc\":\"dlc-test\",\"items\":[{\"id\":\"alpha\",\"dependencies\":[\"beta\"],\"conflicts\":[\"old-alpha\"],\"alarm\":true,\"allowEnableByAll\":false}]}");

            JsonObject fileList = readJson(reader);
            assertEquals("file_list", fileList.get("type").getAsString());
            assertEquals("mods", fileList.get("path").getAsString());
            writeJson(writer, "{\"ok\":true,\"entries\":[{\"name\":\"existing.jar\",\"dir\":false,\"size\":9,\"modified\":1}]}");

            JsonObject init = readJson(reader);
            assertEquals("file_upload_init", init.get("type").getAsString());
            assertEquals("mods/upload.jar", init.get("path").getAsString());
            String uploadId = init.get("id").getAsString();
            writeJson(writer, "{\"ok\":true}");

            JsonObject chunk = readJson(reader);
            assertEquals("file_upload_chunk", chunk.get("type").getAsString());
            assertEquals(uploadId, chunk.get("id").getAsString());
            assertArrayEquals("hello-dlc".getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(chunk.get("data").getAsString()));

            JsonObject finish = readJson(reader);
            assertEquals("file_upload_finish", finish.get("type").getAsString());
            writeJson(writer, "{\"ok\":true}");

            assertEquals("apply", readJson(reader).get("type").getAsString());
            writeJson(writer, "{\"ok\":true,\"requiresRestart\":false}");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static JsonObject readJson(BufferedReader reader) throws IOException {
        return JsonParser.parseString(reader.readLine()).getAsJsonObject();
    }

    private static void writeJson(BufferedWriter writer, String json) throws IOException {
        writer.write(json);
        writer.newLine();
        writer.flush();
    }
}
