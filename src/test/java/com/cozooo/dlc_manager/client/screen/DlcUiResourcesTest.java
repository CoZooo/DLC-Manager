package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.parser.CSS;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class DlcUiResourcesTest {
    private static final String ROOT = "dlc_manager/ui/";

    @Test
    void absentOverridesKeepTheSharedTemplateAndItsStyles() {
        FakeResources source = new FakeResources();
        String original = source.cachedHtml(ROOT + "page.html");
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);

        String path = resources.prepare();

        assertEquals(ROOT + "__dlc_local_config.html", path);
        assertEquals(original, source.cachedHtml(path));
        assertEquals(original, source.cachedHtml(ROOT + "page.html"));
        assertTrue(source.cachedHtml(path).contains("href=\"manager.css\""));
        assertTrue(source.cachedHtml(path).contains("href=\"page.css\""));
    }

    @Test
    void htmlOnlyOverrideUsesCachedHtmlAndFallsBackToPageStyles() {
        FakeResources source = new FakeResources();
        String custom = "<html><head><title>Custom</title></head><body>custom body</body></html>";
        source.addHtml("local_config", custom);
        source.raw.put(ROOT + "local_config.html", "raw stream has different resource-pack precedence");
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);

        String html = source.cachedHtml(resources.prepare());

        assertTrue(html.contains("custom body"));
        assertTrue(html.contains("href=\"/" + ROOT + "page.css\""));
        assertFalse(html.contains("raw stream"));
        assertTrue(source.reloads.isEmpty());
    }

    @Test
    void cssOnlyOverridePreservesBaseMarkupAndAddsItsStylesLast() {
        FakeResources source = new FakeResources();
        source.available.add(ROOT + "local_config.css");
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);

        String html = source.cachedHtml(resources.prepare());

        assertTrue(html.contains("id=\"page-content\""));
        assertTrue(html.indexOf("href=\"manager.css\"") < html.indexOf("href=\"page.css\""));
        assertTrue(html.indexOf("href=\"page.css\"") < html.indexOf("href=\"/" + ROOT + "local_config.css\""));
    }

    @Test
    void htmlAndCssOverridesAreSelectedIndependently() {
        FakeResources source = new FakeResources();
        source.addHtml("local_config", source.page().replace("<title>DLC</title>", "<title>Settings</title>"));
        source.available.add(ROOT + "local_config.css");
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);

        String html = source.cachedHtml(resources.prepare());

        assertTrue(html.contains("<title>Settings</title>"));
        assertTrue(html.contains("href=\"/" + ROOT + "local_config.css\""));
        assertTrue(html.contains("href=\"manager.css\""));
        assertTrue(html.contains("href=\"page.css\""));
    }

    @Test
    void resourcePackCanReplaceAnExistingBundledStylesheet() {
        FakeResources source = new FakeResources();
        String packed = ".manager { background: #123456; }";
        source.packCss.put(ROOT + "manager.css", packed);

        String html = source.cachedHtml(new DlcUiResources(DlcUiPage.MANAGER, source).prepare());

        assertTrue(html.contains("<style>" + packed + "</style>"));
        assertFalse(html.contains("href=\"manager.css\""));
        assertEquals(source.bundled.get(ROOT + "manager.html"), source.cachedHtml(ROOT + "manager.html"));
    }

    @Test
    void resourcePackStylesKeepTheirCascadeOrderInTheActualCssExtractor() {
        FakeResources source = new FakeResources();
        String shared = ".shared { color: red; }";
        String family = ".family { color: blue; }";
        String specific = ".specific { color: green; }";
        source.packCss.put(ROOT + "manager.css", shared);
        source.packCss.put(ROOT + "page.css", family);
        source.packCss.put(ROOT + "local_config.css", specific);
        source.available.add(ROOT + "local_config.css");
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);
        String path = resources.prepare();

        CSS.Extractor extractor = new CSS.Extractor(path);
        extractor.handle(source.cachedHtml(path));

        assertEquals(List.of(shared, family, specific), extractor.orderedStylesheetSources().stream()
            .map(CSS.StylesheetSource::value).toList());
        assertTrue(extractor.orderedStylesheetSources().stream().noneMatch(CSS.StylesheetSource::external));
    }

    @Test
    void resourcePackStylesRefreshWhenTheResourceSetIsPreparedAgain() {
        FakeResources source = new FakeResources();
        source.packCss.put(ROOT + "page.css", ".page { color: red; }");
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);
        String path = resources.prepare();
        source.packCss.put(ROOT + "page.css", ".page { color: blue; }");

        assertFalse(resources.refresh());
        assertTrue(source.cachedHtml(path).contains("color: red"));
        resources.prepare();
        assertTrue(source.cachedHtml(path).contains("color: blue"));
        assertFalse(source.cachedHtml(path).contains("color: red"));
    }

    @Test
    void stylesheetTextCannotCloseItsGeneratedStyleElement() {
        FakeResources source = new FakeResources();
        source.packCss.put(ROOT + "page.css", ".label::before { content: \"</style><b>text</b>\"; }");

        String html = source.cachedHtml(new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source).prepare());

        assertTrue(html.contains("<\\/style><b>text</b>"));
        assertFalse(html.contains("</style><b>text</b>"));
    }

    @Test
    void readableTemplateMissingFromAuiCacheIsRegisteredBeforeUse() {
        FakeResources source = new FakeResources();
        source.available.add(ROOT + "local_config.html");
        source.raw.put(ROOT + "local_config.html", source.page().replace("<title>DLC</title>", "<title>New file</title>"));

        String path = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source).prepare();

        assertEquals(List.of(ROOT + "local_config.html"), source.reloads);
        assertTrue(source.cachedHtml(path).contains("<title>New file</title>"));
    }

    @Test
    void missingAndBlankSharedTemplatesUseTheBundledCopy() {
        FakeResources source = new FakeResources();
        source.available.clear();
        source.cache.clear();
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);
        assertEquals(source.page(), source.cachedHtml(resources.prepare()));

        source.addHtml("local_config", "  ");
        source.addHtml("page", "\n");
        assertEquals(source.page(), source.cachedHtml(resources.prepare()));
        assertTrue(source.warnings.stream().anyMatch(value -> value.contains("Empty DLC UI template: " + ROOT + "local_config.html")));
    }

    @Test
    void refreshUsesOnlyExistingMemoryStateAndNotResourceIo() {
        FakeResources source = new FakeResources();
        source.addHtml("local_config", source.page());
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);
        String path = resources.prepare();
        int probes = source.probes;
        int reads = source.bundledReads;
        int reloads = source.reloads.size();
        source.cache.put(ROOT + "local_config.html", source.page().replace("<title>DLC</title>", "<title>Reloaded</title>"));

        assertTrue(resources.refresh());
        assertTrue(source.cachedHtml(path).contains("<title>Reloaded</title>"));
        assertFalse(resources.refresh());
        assertEquals(probes, source.probes);
        assertEquals(reads, source.bundledReads);
        assertEquals(reloads, source.reloads.size());
    }

    @Test
    void prepareDiscoversNewCssAndIgnoresDeletedHtmlStillInTheAuiCache() {
        FakeResources source = new FakeResources();
        source.addHtml("local_config", source.page().replace("<title>DLC</title>", "<title>Override</title>"));
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);
        String path = resources.prepare();
        source.available.remove(ROOT + "local_config.html");
        source.available.add(ROOT + "local_config.css");

        assertFalse(resources.refresh());
        assertTrue(source.cachedHtml(path).contains("<title>Override</title>"));
        resources.prepare();
        assertFalse(source.cachedHtml(path).contains("<title>Override</title>"));
        assertTrue(source.cachedHtml(path).contains("href=\"/" + ROOT + "local_config.css\""));
    }

    @Test
    void refreshRestoresAnEvictedEffectiveTemplateWithoutProbing() {
        FakeResources source = new FakeResources();
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);
        assertFalse(resources.refresh());
        assertEquals(0, source.probes);
        String path = resources.prepare();
        int probes = source.probes;
        source.cache.remove(path);

        assertTrue(resources.refresh());
        assertEquals(source.page(), source.cachedHtml(path));
        assertEquals(probes, source.probes);
    }

    @Test
    void effectiveTemplatesAndCssDoNotLeakAcrossPages() {
        FakeResources source = new FakeResources();
        String shared = source.page();
        source.available.add(ROOT + "local_config.css");
        DlcUiResources local = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);
        DlcUiResources server = new DlcUiResources(DlcUiPage.SERVER_CONFIG, source);

        String localPath = local.prepare();
        String serverPath = server.prepare();

        assertNotEquals(localPath, serverPath);
        assertTrue(source.cachedHtml(localPath).contains("local_config.css"));
        assertFalse(source.cachedHtml(serverPath).contains("local_config.css"));
        assertEquals(shared, source.cachedHtml(ROOT + "page.html"));
        assertEquals(shared, source.cachedHtml(serverPath));
    }

    @Test
    void duplicateCssReferencesAreNormalizedAndMovedAfterTheBaseStyles() {
        FakeResources source = new FakeResources();
        String custom = source.page().replace("<head>", "<head><LINK rel='stylesheet' href='./local_config.css'>")
            .replace("</head>", "<link href='/" + ROOT + "local_config.css' rel='preload stylesheet'></head>");
        source.addHtml("local_config", custom);
        source.available.add(ROOT + "local_config.css");

        String html = source.cachedHtml(new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source).prepare());

        assertEquals(1, occurrences(html, "local_config.css"));
        assertTrue(html.indexOf("href=\"page.css\"") < html.indexOf("local_config.css"));
    }

    @Test
    void commentedOrScriptTextDoesNotSuppressTheActualCssLink() {
        FakeResources source = new FakeResources();
        String custom = source.page().replace("</head>", "<!-- <link rel='stylesheet' href='local_config.css'> -->"
            + "<script>var example = \"<link rel='stylesheet' href='local_config.css'>\";</script></head>");
        source.addHtml("local_config", custom);
        source.available.add(ROOT + "local_config.css");

        String html = source.cachedHtml(new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source).prepare());

        assertTrue(html.contains("<!-- <link rel='stylesheet' href='local_config.css'> -->"));
        assertTrue(html.contains("href=\"/" + ROOT + "local_config.css\""));
    }

    @Test
    void pageStylesheetFollowsInlineStylesEvenWhenTheyAreInTheBody() {
        FakeResources source = new FakeResources();
        source.addHtml("local_config", source.page().replace("</body>", "<style>.custom { color: red; }</style></body>"));
        source.available.add(ROOT + "local_config.css");

        String html = source.cachedHtml(new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source).prepare());

        assertTrue(html.indexOf("</style>") < html.indexOf("href=\"/" + ROOT + "local_config.css\""));
        assertTrue(html.indexOf("local_config.css") < html.indexOf("</body>"));
    }

    @Test
    void basePageIdsDoNotDuplicateTheirOwnStylesheet() {
        for (DlcUiPage page : List.of(DlcUiPage.PAGE, DlcUiPage.MANAGER)) {
            FakeResources source = new FakeResources();
            DlcUiResources resources = new DlcUiResources(page, source);
            assertEquals(source.bundled.get(ROOT + page.template() + ".html"), source.cachedHtml(resources.prepare()));
        }
    }

    @Test
    void invalidOverrideStaysOnTrustedMarkupUntilItsHtmlChanges() {
        FakeResources source = new FakeResources();
        String broken = "<html><body id='wrong'>broken override</body></html>";
        source.addHtml("local_config", broken);
        DlcUiResources resources = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);
        String path = resources.prepare();
        assertFalse(resources.hasRequiredIds(ids(broken)::contains));

        resources.useDefault();

        assertEquals(source.page(), source.cachedHtml(path));
        assertFalse(resources.refresh());
        resources.prepare();
        assertEquals(source.page(), source.cachedHtml(path));
        assertEquals(broken, source.cachedHtml(ROOT + "local_config.html"));
        source.cache.put(ROOT + "local_config.html", source.page().replace("<title>DLC</title>", "<title>Fixed</title>"));
        assertTrue(resources.refresh());
        assertTrue(source.cachedHtml(path).contains("<title>Fixed</title>"));
        assertTrue(resources.hasRequiredIds(ids(source.cachedHtml(path))::contains));
    }

    @Test
    void bindingRequirementsMatchBothBundledTemplatesAndRejectMissingSlots() {
        FakeResources source = new FakeResources();
        DlcUiResources page = new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source);
        DlcUiResources manager = new DlcUiResources(DlcUiPage.SERVER_MANAGER, source);
        Set<String> pageIds = ids(source.page());
        Set<String> managerIds = ids(source.bundled.get(ROOT + "manager.html"));
        assertTrue(page.hasRequiredIds(pageIds::contains));
        assertTrue(manager.hasRequiredIds(managerIds::contains));
        pageIds.remove("page-subhead");
        assertTrue(page.hasRequiredIds(pageIds::contains));
        pageIds.remove("page-content");
        managerIds.remove("manager-search");
        assertFalse(page.hasRequiredIds(pageIds::contains));
        assertFalse(manager.hasRequiredIds(managerIds::contains));
        assertFalse(page.isValid(null));
        assertTrue(source.warnings.stream().anyMatch(value -> value.contains("page-content")));
        assertTrue(source.warnings.stream().anyMatch(value -> value.contains("manager-search")));
    }

    @Test
    void missingBundledTemplateReportsItsExactLogicalPath() {
        FakeResources source = new FakeResources();
        source.bundled.remove(ROOT + "page.html");
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> new DlcUiResources(DlcUiPage.LOCAL_CONFIG, source).prepare());
        assertTrue(error.getMessage().contains(ROOT + "page.html"));
    }

    private static int occurrences(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }

    private static Set<String> ids(String html) {
        Set<String> ids = new HashSet<>();
        Matcher matcher = Pattern.compile("\\bid=[\"']([^\"']+)[\"']").matcher(html);
        while (matcher.find()) ids.add(matcher.group(1));
        return ids;
    }

    private static final class FakeResources implements DlcUiResources.Resources {
        final Set<String> available = new HashSet<>();
        final Map<String, String> raw = new HashMap<>();
        final Map<String, String> cache = new HashMap<>();
        final Map<String, String> bundled = new HashMap<>();
        final Map<String, String> packCss = new HashMap<>();
        final List<String> reloads = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        int probes;
        int bundledReads;

        FakeResources() {
            for (String name : List.of("page", "manager")) {
                String path = ROOT + name + ".html";
                try (InputStream stream = getClass().getClassLoader().getResourceAsStream("assets/apricityui/apricity/" + path)) {
                    assertNotNull(stream, path);
                    String html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    bundled.put(path, html);
                    addHtml(name, html);
                    available.add(ROOT + name + ".css");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }
        }

        String page() { return bundled.get(ROOT + "page.html"); }
        void addHtml(String name, String html) {
            String path = ROOT + name + ".html";
            available.add(path);
            raw.put(path, html);
            cache.put(path, html);
        }
        @Override public boolean exists(String path) { probes++; return available.contains(path); }
        @Override public String cachedHtml(String path) { return cache.get(path); }
        @Override public boolean reloadHtml(String path) {
            reloads.add(path);
            String html = raw.get(path);
            if (html == null) return false;
            cache.put(path, html);
            return true;
        }
        @Override public void putHtml(String path, String html) { cache.put(path, html); }
        @Override public String bundledHtml(String path) { bundledReads++; return bundled.get(path); }
        @Override public Map<String, String> resourcePackStyles() { return packCss; }
        @Override public void warn(String message) { warnings.add(message); }
    }
}
