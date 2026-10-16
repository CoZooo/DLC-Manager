package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.spi.AuiResourceService;
import com.sighs.apricityui.spi.AuiServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DlcUiResourcePackTest {
    private static final String ROOT = "dlc_manager/ui/";
    private static final String PAGE_HTML = ROOT + "page.html";
    private static final String MANAGER_CSS = ROOT + "manager.css";
    private static final String EFFECTIVE_HTML = ROOT + "__dlc_page.html";

    @TempDir Path instance;

    private AuiClientService previousClient;
    private AuiResourceService previousResources;
    private final Map<String, String> previousHtml = new LinkedHashMap<>();
    private final Map<String, String> packFiles = new LinkedHashMap<>();
    private int openedPackStreams;
    private int closedPackStreams;

    @BeforeEach
    void installResourcePackServices() throws IOException {
        previousClient = AuiServices.client();
        previousResources = AuiServices.resources();
        previousHtml.put(PAGE_HTML, HTML.getTemple(PAGE_HTML));
        previousHtml.put(EFFECTIVE_HTML, HTML.getTemple(EFFECTIVE_HTML));

        AuiServices.setClient((AuiClientService) Proxy.newProxyInstance(
            AuiClientService.class.getClassLoader(), new Class<?>[] { AuiClientService.class },
            (proxy, method, args) -> method.getName().equals("getGameDirectory")
                ? instance : method.invoke(previousClient, args)
        ));
        AuiServices.setResources((AuiResourceService) Proxy.newProxyInstance(
            AuiResourceService.class.getClassLoader(), new Class<?>[] { AuiResourceService.class },
            (proxy, method, args) -> {
                if (method.getName().equals("openResource")) return openPackResource((String) args[0]);
                if (method.getName().equals("listResourcePaths")) {
                    String prefix = (String) args[0];
                    String suffix = (String) args[1];
                    Map<String, String> entries = new LinkedHashMap<>();
                    if ("apricity".equals(prefix)) {
                        for (String path : packFiles.keySet()) {
                            if (suffix == null || suffix.isEmpty() || path.endsWith(suffix)) {
                                entries.put(path, "dlc-ui-test-pack");
                            }
                        }
                    }
                    return entries;
                }
                return method.invoke(previousResources, args);
            }
        ));
        ClientLoader.invalidateStaticResourceCache();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("assets/apricityui/apricity/" + PAGE_HTML)) {
            assertNotNull(stream, "The integration test uses the actual bundled page template");
            HTML.putTemple(PAGE_HTML, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @AfterEach
    void restoreResourcePackServices() {
        try {
            previousHtml.forEach(HTML::putTemple);
        } finally {
            AuiServices.setResources(previousResources);
            AuiServices.setClient(previousClient);
            ClientLoader.invalidateStaticResourceCache();
        }
    }

    @Test
    void resourcePackStylesReplaceTheSameNamedBundledStylesInOriginalOrder() throws IOException {
        String packManager = ".pack-manager-probe { color: #123456; }";
        String packPage = ".pack-page-probe { color: #654321; }";
        packFiles.put(MANAGER_CSS, packManager);
        packFiles.put(ROOT + "page.css", packPage);
        assertEquals(Loader.ResourceLayer.RESOURCE_PACK, layerOf(MANAGER_CSS));

        assertFalse(readResolved(MANAGER_CSS).contains("pack-manager-probe"));
        DlcUiResources resources = new DlcUiResources(DlcUiPage.PAGE);
        String html = HTML.getTemple(resources.prepare());

        assertTrue(html.contains("<style>" + packManager + "</style>"));
        assertTrue(html.contains("<style>" + packPage + "</style>"));
        assertTrue(html.indexOf(packManager) < html.indexOf(packPage));
        assertFalse(html.contains("href=\"manager.css\""));
        assertFalse(html.contains("href=\"page.css\""));
        assertEquals(2, openedPackStreams);
        assertEquals(openedPackStreams, closedPackStreams);
        assertTrue(HTML.getTemple(PAGE_HTML).contains("href=\"manager.css\""));
    }

    @Test
    void instanceStylesTakePrecedenceAndRemainLinkedForHotReload() throws IOException {
        String packCss = ".pack-manager-probe { color: red; }";
        String localCss = ".local-manager-probe { color: green; }";
        packFiles.put(MANAGER_CSS, packCss);
        Path local = instance.resolve("apricity").resolve(MANAGER_CSS);
        Files.createDirectories(local.getParent());
        Files.writeString(local, localCss, StandardCharsets.UTF_8);
        assertEquals(Loader.ResourceLayer.LOCAL_FOLDER, layerOf(MANAGER_CSS));

        DlcUiResources resources = new DlcUiResources(DlcUiPage.PAGE);
        String html = HTML.getTemple(resources.prepare());

        assertEquals(localCss, readResolved(MANAGER_CSS));
        assertTrue(html.contains("href=\"manager.css\""));
        assertTrue(html.contains("href=\"page.css\""));
        assertFalse(html.contains(packCss));
        assertFalse(html.contains(localCss));
        assertEquals(0, openedPackStreams);
        assertEquals(openedPackStreams, closedPackStreams);
    }

    @Test
    void invalidatedResourcePackIndexRefreshesMaterializedStyles() {
        String first = ".pack-version-one { color: red; }";
        String next = ".pack-version-two { color: blue; }";
        packFiles.put(MANAGER_CSS, first);
        DlcUiResources resources = new DlcUiResources(DlcUiPage.PAGE);
        String path = resources.prepare();
        assertTrue(HTML.getTemple(path).contains(first));

        packFiles.put(MANAGER_CSS, next);
        assertFalse(resources.refresh());
        ClientLoader.invalidateStaticResourceCache();
        resources.prepare();

        assertFalse(HTML.getTemple(path).contains(first));
        assertTrue(HTML.getTemple(path).contains(next));
        assertEquals(openedPackStreams, closedPackStreams);
    }

    private Loader.ResourceLayer layerOf(String path) {
        return ClientLoader.listFinalStaticResources().stream()
            .filter(entry -> entry.path().equals(path))
            .findFirst().orElseThrow().layer();
    }

    private String readResolved(String path) throws IOException {
        try (InputStream stream = ClientLoader.getResourceStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Optional<InputStream> openPackResource(String path) {
        if (!path.startsWith("apricity/")) return Optional.empty();
        String content = packFiles.get(path.substring("apricity/".length()));
        if (content == null) return Optional.empty();
        openedPackStreams++;
        return Optional.of(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)) {
            @Override public void close() throws IOException {
                closedPackStreams++;
                super.close();
            }
        });
    }
}
