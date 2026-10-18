package com.cozooo.dlc_manager.sync;

import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SyncFailureTest {
    @TempDir Path game;
    private final AtomicReference<String> language = new AtomicReference<>("en_us");
    @BeforeEach void setup() {
        var delegate = new TestPlatformProvider(game, game.resolve("config"));
        PlatformServices.setProvider((DlcPlatformServices.Provider) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DlcPlatformServices.Provider.class},
            (proxy, method, arguments) -> method.getName().equals("languageCode") ? language.get() : method.invoke(delegate, arguments)));
    }
    @AfterEach void reset() { PlatformServices.setProvider(null); }
    @Test void messagesUseTheCurrentLanguageAndKeepResourceNamesAsArguments() {
        SyncFailure failure = new SyncFailure("source_changed", "remote/example.dc");
        assertEquals("Source changed after comparison: remote/example.dc", failure.getMessage());
        language.set("zh_CN");
        assertEquals("比较后源文件已发生变化：remote/example.dc", failure.getMessage());
        language.set("fr_fr");
        assertEquals("Source changed after comparison: remote/example.dc", failure.getMessage());
    }
    @Test void receivedErrorsAreTranslatedInTheClientsLanguageInsteadOfTheServers() {
        JsonObject response = new JsonObject(); response.addProperty("ok", false);
        SyncFailure source = new SyncFailure("resource_missing", "resource.zip");
        response.addProperty("reason", source.getMessage()); source.write(response);
        language.set("zh_cn");
        IOException received = assertThrows(IOException.class, () -> SyncClient.checked(request -> response, SyncClient.request("inspect")));
        assertEquals("DLC 资源缺失：resource.zip", received.getMessage());
        assertTrue(SyncFailure.is(received, "resource_missing"));
    }
    @Test void cancellationAndBusyDetectionDoNotDependOnTranslatedMessages() {
        language.set("zh_cn");
        SyncFailure failure = new SyncFailure("active").causedBy(new IOException("details"));
        assertTrue(SyncFailure.is(failure, "active")); assertFalse(SyncFailure.is(failure, "canceled"));
        assertEquals("details", failure.getCause().getMessage());
    }
    @Test void unknownSystemErrorsKeepTheirOriginalDetailsAndInvalidRepliesAreLocalized() {
        JsonObject response = new JsonObject(); response.addProperty("ok", false); response.addProperty("reason", "permission denied: example.jar");
        IOException error = assertThrows(IOException.class, () -> SyncClient.checked(request -> response, SyncClient.request("inspect")));
        assertEquals("permission denied: example.jar", error.getMessage());
        response.addProperty("reasonKey", "dlc_manager.sync.error.resource_missing"); response.addProperty("reasonArgs", "invalid"); language.set("zh_cn");
        error = assertThrows(IOException.class, () -> SyncClient.checked(request -> response, SyncClient.request("inspect")));
        assertEquals("收到的同步响应无效。", error.getMessage());
    }
}
