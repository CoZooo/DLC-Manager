package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DlcUiResources {
    private static final String ROOT = "dlc_manager/ui/";
    private static final String CLASSPATH_ROOT = "assets/apricityui/apricity/";
    private static final Logger LOGGER = LoggerFactory.getLogger(DlcUiResources.class);
    private static final Pattern LINKS = Pattern.compile("(?is)<link\\b[^>]*>");
    private static final Pattern NON_MARKUP = Pattern.compile("(?is)<!--.*?-->|<(script|style)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern CLOSE_HEAD = Pattern.compile("(?i)</head\\s*>");
    private static final Pattern CLOSE_BODY = Pattern.compile("(?i)</body\\s*>");
    private static final List<String> PAGE_IDS = List.of(
        "page-back", "page-title", "page-toolbar", "page-content",
        "page-leading-actions", "page-status", "page-actions"
    );
    private static final List<String> MANAGER_IDS = List.of(
        "manager", "manager-sidebar", "sidebar-content", "manager-title", "category-label",
        "manager-filters", "tools-label", "manager-tools", "manager-header", "manager-connection",
        "sidebar-toggle-slot", "manager-search", "search-actions", "manager-summary", "batch-actions",
        "manager-context", "manager-list", "manager-mode", "pending-status", "manager-actions"
    );

    interface Resources {
        boolean exists(String path);
        String cachedHtml(String path);
        boolean reloadHtml(String path);
        void putHtml(String path, String html);
        String bundledHtml(String path);
        Map<String, String> resourcePackStyles();
        void warn(String message);
    }

    private record Selection(String path, String html) {}

    private final DlcUiPage page;
    private final Resources resources;
    private final String candidatePath;
    private final String fallbackPath;
    private final String candidateCss;
    private final String fallbackCss;
    private final String effectivePath;
    private boolean prepared;
    private boolean candidateAvailable;
    private boolean fallbackAvailable;
    private String selectedCss;
    private String bundled;
    private String effectiveHtml;
    private Map<String, String> packStyles = Map.of();
    private Selection selected;
    private Selection rejected;

    public DlcUiResources(DlcUiPage page) {
        this(page, new AuiResources());
    }

    DlcUiResources(DlcUiPage page, Resources resources) {
        this.page = Objects.requireNonNull(page);
        this.resources = Objects.requireNonNull(resources);
        candidatePath = ROOT + page.id() + ".html";
        fallbackPath = ROOT + page.template() + ".html";
        candidateCss = ROOT + page.id() + ".css";
        fallbackCss = ROOT + page.template() + ".css";
        effectivePath = ROOT + "__dlc_" + page.id() + ".html";
    }

    public String prepare() {
        ensureBundled();
        candidateAvailable = resources.exists(candidatePath);
        fallbackAvailable = candidatePath.equals(fallbackPath)
            ? candidateAvailable : resources.exists(fallbackPath);
        if (candidateAvailable) ensureCached(candidatePath);
        if (fallbackAvailable && !candidatePath.equals(fallbackPath)) ensureCached(fallbackPath);
        selectedCss = resources.exists(candidateCss) ? candidateCss : fallbackCss;
        packStyles = Map.copyOf(resources.resourcePackStyles());
        prepared = true;
        updateTemplate();
        return effectivePath;
    }

    public boolean refresh() {
        if (!prepared) return false;
        if (Objects.equals(selected, selectTemplate()) && Objects.equals(effectiveHtml, resources.cachedHtml(effectivePath))) return false;
        return updateTemplate();
    }

    public boolean isValid(Document document) {
        if (document == null || document.body == null) {
            resources.warn("DLC UI page " + page.id() + " has no document body (template " + effectivePath + ")");
            return false;
        }
        return hasRequiredIds(id -> document.getElementById(id) != null);
    }

    boolean hasRequiredIds(Predicate<String> containsId) {
        List<String> missing = new ArrayList<>();
        for (String id : "manager".equals(page.template()) ? MANAGER_IDS : PAGE_IDS) {
            if (!containsId.test(id)) missing.add(id);
        }
        if (missing.isEmpty()) return true;
        resources.warn("DLC UI page " + page.id() + " template "
            + (selected == null ? candidatePath : selected.path()) + " is missing required IDs: " + String.join(", ", missing));
        return false;
    }

    public void useDefault() {
        if (!prepared) prepare();
        rejected = selected;
        resources.warn("DLC UI page " + page.id() + " is using bundled fallback " + fallbackPath);
        register(materializePackStyles(appendStylesheet(bundled, selectedCss)));
    }

    private void ensureBundled() {
        if (bundled != null) return;
        bundled = resources.bundledHtml(fallbackPath);
        if (bundled == null || bundled.isBlank()) {
            String message = "Missing bundled DLC UI template: " + fallbackPath;
            resources.warn(message);
            throw new IllegalStateException(message);
        }
    }

    private void ensureCached(String path) {
        if (resources.cachedHtml(path) == null && !resources.reloadHtml(path)) {
            resources.warn("Unable to load DLC UI template: " + path);
        }
        String html = resources.cachedHtml(path);
        if (html != null && html.isBlank()) resources.warn("Empty DLC UI template: " + path);
    }

    private Selection selectTemplate() {
        String html = candidateAvailable ? resources.cachedHtml(candidatePath) : null;
        if (html != null && !html.isBlank()) return new Selection(candidatePath, html);
        html = fallbackAvailable ? resources.cachedHtml(fallbackPath) : null;
        if (html != null && !html.isBlank()) return new Selection(fallbackPath, html);
        return new Selection("classpath:" + fallbackPath, bundled);
    }

    private boolean updateTemplate() {
        Selection next = selectTemplate();
        if (rejected != null && !rejected.equals(next)) rejected = null;
        selected = next;
        String html = rejected == null ? next.html() : bundled;
        return register(materializePackStyles(appendStylesheet(html, selectedCss)));
    }

    private String materializePackStyles(String html) {
        if (packStyles.isEmpty()) return html;
        Matcher links = LINKS.matcher(maskNonMarkup(html));
        StringBuilder result = new StringBuilder();
        int copied = 0;
        while (links.find()) {
            String tag = html.substring(links.start(), links.end());
            String rel = attribute(tag, "rel");
            String href = attribute(tag, "href");
            if (rel == null || href == null
                || !List.of(rel.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+")).contains("stylesheet")) continue;
            String css = packStyles.get(Loader.resolve(effectivePath, href));
            if (css == null) continue;
            result.append(html, copied, links.start());
            result.append("<style>").append(css.replaceAll("(?i)</style", "<\\\\/style")).append("</style>");
            copied = links.end();
        }
        if (copied == 0) return html;
        return result.append(html, copied, html.length()).toString();
    }

    private boolean register(String html) {
        if (Objects.equals(effectiveHtml, html) && Objects.equals(resources.cachedHtml(effectivePath), html)) return false;
        effectiveHtml = html;
        resources.putHtml(effectivePath, html);
        return true;
    }

    private String appendStylesheet(String html, String cssPath) {
        String masked = maskNonMarkup(html);
        Matcher links = LINKS.matcher(masked);
        List<int[]> matches = new ArrayList<>();
        while (links.find()) {
            String tag = html.substring(links.start(), links.end());
            String rel = attribute(tag, "rel");
            String href = attribute(tag, "href");
            if (rel != null && href != null && List.of(rel.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+")).contains("stylesheet")
                && cssPath.equals(Loader.resolve(effectivePath, href))) {
                matches.add(new int[] { links.start(), links.end() });
            }
        }
        if (!matches.isEmpty() && cssPath.equals(fallbackCss)) return html;
        StringBuilder without = new StringBuilder(html);
        for (int i = matches.size() - 1; i >= 0; i--) without.delete(matches.get(i)[0], matches.get(i)[1]);
        String source = without.toString();
        String link = "\n    <link rel=\"stylesheet\" href=\"/" + cssPath + "\">\n";
        Matcher close = CLOSE_BODY.matcher(maskNonMarkup(source));
        if (!close.find()) {
            close = CLOSE_HEAD.matcher(maskNonMarkup(source));
            if (!close.find()) return source + link;
        }
        return source.substring(0, close.start()) + link + source.substring(close.start());
    }

    private static String attribute(String tag, String name) {
        Matcher matcher = Pattern.compile("(?is)(?<![\\w-])" + name
            + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))").matcher(tag);
        if (!matcher.find()) return null;
        for (int i = 1; i <= 3; i++) if (matcher.group(i) != null) return matcher.group(i);
        return null;
    }

    private static String maskNonMarkup(String html) {
        Matcher matcher = NON_MARKUP.matcher(html);
        StringBuilder masked = new StringBuilder(html);
        while (matcher.find()) {
            for (int i = matcher.start(); i < matcher.end(); i++) masked.setCharAt(i, ' ');
        }
        return masked.toString();
    }

    private static final class AuiResources implements Resources {
        private static List<Loader.StaticResourceEntry> styleIndex;
        private static Map<String, String> indexedStyles = Map.of();

        @Override public boolean exists(String path) {
            try (InputStream stream = ClientLoader.getResourceStream(path)) {
                return stream != null;
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Unable to inspect DLC UI resource {}", path, exception);
                return false;
            }
        }

        @Override public String cachedHtml(String path) { return HTML.getTemple(path); }
        @Override public boolean reloadHtml(String path) { return HTML.reload(path); }
        @Override public void putHtml(String path, String html) { HTML.putTemple(path, html); }

        @Override public Map<String, String> resourcePackStyles() {
            List<Loader.StaticResourceEntry> index = ClientLoader.listFinalStaticResources();
            if (styleIndex == index) return indexedStyles;
            Map<String, String> styles = new LinkedHashMap<>();
            for (Loader.StaticResourceEntry entry : index) {
                String path = entry.path();
                if (entry.layer() != Loader.ResourceLayer.RESOURCE_PACK || !path.endsWith(".css")
                    || !path.startsWith(ROOT) || path.substring(ROOT.length()).contains("/")) continue;
                try (InputStream stream = AuiServices.resources().openResource("apricity/" + path).orElse(null)) {
                    if (stream != null) styles.put(path, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException | RuntimeException exception) {
                    LOGGER.warn("Unable to read DLC UI resource-pack stylesheet {}", path, exception);
                }
            }
            indexedStyles = Map.copyOf(styles);
            styleIndex = index;
            return indexedStyles;
        }

        @Override public String bundledHtml(String path) {
            try (InputStream stream = DlcUiResources.class.getClassLoader().getResourceAsStream(CLASSPATH_ROOT + path)) {
                return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                LOGGER.warn("Unable to read bundled DLC UI template {}", path, exception);
                return null;
            }
        }

        @Override public void warn(String message) { LOGGER.warn(message); }
    }
}
