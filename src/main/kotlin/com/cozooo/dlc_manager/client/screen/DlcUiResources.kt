package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Document
import com.sighs.apricityui.loader.ClientLoader
import com.sighs.apricityui.loader.Loader
import com.sighs.apricityui.parser.HTML
import com.sighs.apricityui.spi.AuiServices
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.function.Predicate
import java.util.regex.Pattern

/** Resolves a page's resources without sharing its effective template with other pages. */
class DlcUiResources internal constructor(private val page: DlcUiPage, private val resources: Resources) {
    interface Resources {
        fun exists(path: String): Boolean
        fun cachedHtml(path: String): String?
        fun reloadHtml(path: String): Boolean
        fun putHtml(path: String, html: String)
        fun bundledHtml(path: String): String?
        fun resourcePackStyles(): Map<String, String>
        fun warn(message: String)
    }

    private data class Selection(val path: String, val html: String)

    private val candidatePath = ROOT + page.id() + ".html"
    private val fallbackPath = ROOT + page.template() + ".html"
    private val candidateCss = ROOT + page.id() + ".css"
    private val fallbackCss = ROOT + page.template() + ".css"
    private val effectivePath = ROOT + "__dlc_" + page.id() + ".html"
    private var prepared = false
    private var candidateAvailable = false
    private var fallbackAvailable = false
    private var selectedCss = fallbackCss
    private var bundled: String? = null
    private var effectiveHtml: String? = null
    private var packStyles: Map<String, String> = emptyMap()
    private var selected: Selection? = null
    private var rejected: Selection? = null

    constructor(page: DlcUiPage) : this(page, AuiResources())

    /** Probes resources when a screen opens or the resource set is reloaded. */
    fun prepare(): String {
        ensureBundled()
        candidateAvailable = resources.exists(candidatePath)
        fallbackAvailable = if (candidatePath == fallbackPath) candidateAvailable else resources.exists(fallbackPath)
        if (candidateAvailable) ensureCached(candidatePath)
        if (fallbackAvailable && candidatePath != fallbackPath) ensureCached(fallbackPath)
        selectedCss = if (resources.exists(candidateCss)) candidateCss else fallbackCss
        packStyles = resources.resourcePackStyles().toMap()
        prepared = true
        updateTemplate()
        return effectivePath
    }

    /** Checks only in-memory template state; file creation/deletion is handled by prepare(). */
    fun refresh(): Boolean {
        if (!prepared) return false
        if (selected == selectTemplate() && effectiveHtml == resources.cachedHtml(effectivePath)) return false
        return updateTemplate()
    }

    fun isValid(document: Document?): Boolean {
        if (document == null || document.body == null) {
            resources.warn("DLC UI page ${page.id()} has no document body (template $effectivePath)")
            return false
        }
        return hasRequiredIds { id -> document.getElementById(id) != null }
    }

    fun hasRequiredIds(containsId: Predicate<String>): Boolean {
        val missing = (if (page.template() == "manager") MANAGER_IDS else PAGE_IDS).filterNot(containsId::test)
        if (missing.isEmpty()) return true
        resources.warn("DLC UI page ${page.id()} template ${selected?.path ?: candidatePath} is missing required IDs: ${missing.joinToString(", ")}")
        return false
    }

    /** Keeps a rejected override disabled until its source content changes. */
    fun useDefault() {
        if (!prepared) prepare()
        rejected = selected
        resources.warn("DLC UI page ${page.id()} is using bundled fallback $fallbackPath")
        register(materializePackStyles(appendStylesheet(bundled!!, selectedCss)))
    }

    private fun ensureBundled() {
        if (bundled != null) return
        bundled = resources.bundledHtml(fallbackPath)
        if (bundled.isNullOrBlank()) {
            val message = "Missing bundled DLC UI template: $fallbackPath"
            resources.warn(message)
            throw IllegalStateException(message)
        }
    }

    private fun ensureCached(path: String) {
        if (resources.cachedHtml(path) == null && !resources.reloadHtml(path)) {
            resources.warn("Unable to load DLC UI template: $path")
        }
        val html = resources.cachedHtml(path)
        if (html != null && html.isBlank()) resources.warn("Empty DLC UI template: $path")
    }

    private fun selectTemplate(): Selection {
        var html = if (candidateAvailable) resources.cachedHtml(candidatePath) else null
        if (!html.isNullOrBlank()) return Selection(candidatePath, html)
        html = if (fallbackAvailable) resources.cachedHtml(fallbackPath) else null
        if (!html.isNullOrBlank()) return Selection(fallbackPath, html)
        return Selection("classpath:$fallbackPath", bundled!!)
    }

    private fun updateTemplate(): Boolean {
        val next = selectTemplate()
        if (rejected != null && rejected != next) rejected = null
        selected = next
        val html = if (rejected == null) next.html else bundled!!
        return register(materializePackStyles(appendStylesheet(html, selectedCss)))
    }

    private fun materializePackStyles(html: String): String {
        if (packStyles.isEmpty()) return html
        val links = LINKS.matcher(maskNonMarkup(html))
        val result = StringBuilder()
        var copied = 0
        while (links.find()) {
            val tag = html.substring(links.start(), links.end())
            val rel = attribute(tag, "rel") ?: continue
            val href = attribute(tag, "href") ?: continue
            if (!rel.lowercase(Locale.ROOT).trim().split(Regex("\\s+")).contains("stylesheet")) continue
            val css = packStyles[Loader.resolve(effectivePath, href)] ?: continue
            result.append(html, copied, links.start())
            result.append("<style>").append(Pattern.compile("(?i)</style").matcher(css).replaceAll("<\\\\/style")).append("</style>")
            copied = links.end()
        }
        if (copied == 0) return html
        return result.append(html, copied, html.length).toString()
    }

    private fun register(html: String): Boolean {
        if (effectiveHtml == html && resources.cachedHtml(effectivePath) == html) return false
        effectiveHtml = html
        resources.putHtml(effectivePath, html)
        return true
    }

    private fun appendStylesheet(html: String, cssPath: String): String {
        val links = LINKS.matcher(maskNonMarkup(html))
        val matches = ArrayList<IntArray>()
        while (links.find()) {
            val tag = html.substring(links.start(), links.end())
            val rel = attribute(tag, "rel")
            val href = attribute(tag, "href")
            if (rel != null && href != null && rel.lowercase(Locale.ROOT).trim().split(Regex("\\s+")).contains("stylesheet")
                && cssPath == Loader.resolve(effectivePath, href)) {
                matches.add(intArrayOf(links.start(), links.end()))
            }
        }
        if (matches.isNotEmpty() && cssPath == fallbackCss) return html
        val without = StringBuilder(html)
        for (match in matches.asReversed()) without.delete(match[0], match[1])
        val source = without.toString()
        val link = "\n    <link rel=\"stylesheet\" href=\"/$cssPath\">\n"
        var close = CLOSE_BODY.matcher(maskNonMarkup(source))
        if (!close.find()) {
            close = CLOSE_HEAD.matcher(maskNonMarkup(source))
            if (!close.find()) return source + link
        }
        return source.substring(0, close.start()) + link + source.substring(close.start())
    }

    private class AuiResources : Resources {
        override fun exists(path: String): Boolean = try {
            ClientLoader.getResourceStream(path).use { it != null }
        } catch (exception: IOException) {
            LOGGER.warn("Unable to inspect DLC UI resource {}", path, exception)
            false
        } catch (exception: RuntimeException) {
            LOGGER.warn("Unable to inspect DLC UI resource {}", path, exception)
            false
        }

        override fun cachedHtml(path: String): String? = HTML.getTemple(path)
        override fun reloadHtml(path: String): Boolean = HTML.reload(path)
        override fun putHtml(path: String, html: String) = HTML.putTemple(path, html)

        override fun resourcePackStyles(): Map<String, String> {
            val index = ClientLoader.listFinalStaticResources()
            if (styleIndex === index) return indexedStyles
            val styles = linkedMapOf<String, String>()
            for (entry in index) {
                val path = entry.path()
                if (entry.layer() != Loader.ResourceLayer.RESOURCE_PACK || !path.endsWith(".css")
                    || !path.startsWith(ROOT) || path.substring(ROOT.length).contains('/')) continue
                try {
                    AuiServices.resources().openResource("apricity/$path").orElse(null).use { stream ->
                        if (stream != null) styles[path] = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    }
                } catch (exception: IOException) {
                    LOGGER.warn("Unable to read DLC UI resource-pack stylesheet {}", path, exception)
                } catch (exception: RuntimeException) {
                    LOGGER.warn("Unable to read DLC UI resource-pack stylesheet {}", path, exception)
                }
            }
            indexedStyles = styles.toMap()
            styleIndex = index
            return indexedStyles
        }

        override fun bundledHtml(path: String): String? = try {
            DlcUiResources::class.java.classLoader.getResourceAsStream(CLASSPATH_ROOT + path).use { stream ->
                stream?.let { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            }
        } catch (exception: IOException) {
            LOGGER.warn("Unable to read bundled DLC UI template {}", path, exception)
            null
        }

        override fun warn(message: String) = LOGGER.warn(message)

        companion object {
            private var styleIndex: List<Loader.StaticResourceEntry>? = null
            private var indexedStyles: Map<String, String> = emptyMap()
        }
    }

    companion object {
        private const val ROOT = "dlc_manager/ui/"
        private const val CLASSPATH_ROOT = "assets/apricityui/apricity/"
        private val LOGGER = LoggerFactory.getLogger(DlcUiResources::class.java)
        private val LINKS = Pattern.compile("(?is)<link\\b[^>]*>")
        private val NON_MARKUP = Pattern.compile("(?is)<!--.*?-->|<(script|style)\\b[^>]*>.*?</\\1\\s*>")
        private val CLOSE_HEAD = Pattern.compile("(?i)</head\\s*>")
        private val CLOSE_BODY = Pattern.compile("(?i)</body\\s*>")
        private val PAGE_IDS = listOf(
            "page-back", "page-title", "page-toolbar", "page-content",
            "page-leading-actions", "page-status", "page-actions"
        )
        private val MANAGER_IDS = listOf(
            "manager", "manager-sidebar", "sidebar-content", "manager-title", "category-label",
            "manager-filters", "tools-label", "manager-tools", "manager-header", "manager-connection",
            "sidebar-toggle-slot", "manager-search", "search-actions", "manager-summary", "batch-actions",
            "manager-context", "manager-list", "manager-mode", "pending-status", "manager-actions"
        )

        private fun attribute(tag: String, name: String): String? {
            val matcher = Pattern.compile("(?is)(?<![\\w-])$name" +
                "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))").matcher(tag)
            if (!matcher.find()) return null
            for (i in 1..3) if (matcher.group(i) != null) return matcher.group(i)
            return null
        }

        private fun maskNonMarkup(html: String): String {
            val matcher = NON_MARKUP.matcher(html)
            val masked = StringBuilder(html)
            while (matcher.find()) {
                for (i in matcher.start() until matcher.end()) masked.setCharAt(i, ' ')
            }
            return masked.toString()
        }
    }
}
