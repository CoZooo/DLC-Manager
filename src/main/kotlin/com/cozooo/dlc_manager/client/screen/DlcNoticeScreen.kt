package com.cozooo.dlc_manager.client.screen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig
import java.util.concurrent.CompletableFuture
class DlcNoticeScreen(parent: Screen?, private val config: DlcNoticeConfig) : DlcPageScreen(parent, Component.translatable("dlc_manager.notice_title")) {
    private var neverShow = config.isNeverShow()
    private var language = ""
    private var resolvedContents = ""
    private var future: CompletableFuture<String>? = null
    override fun showHeaderBackButton(): Boolean = false
    override fun buildPage() {
        val lang = Minecraft.getInstance().options.languageCode
        if (lang != language) resolve(lang)
        val title = config.getTitle().resolve(lang)
        DlcUiText.set(pageDocument.getElementById("page-title"), title.ifBlank { getTitle().string })
        val subhead = if (config.getSubhead().enabled) config.getSubhead().text.resolve(lang) else ""
        val headerSubhead = pageDocument.getElementById("page-subhead")
        DlcUiText.set(headerSubhead, subhead)
        headerSubhead.setAttribute("style", if (subhead.isBlank()) "display:none" else "display:block")
        val body = node(content, "article", "page-prose")
        markdown(body, resolvedContents)
        if (config.getShowFrequency() != DlcNoticeConfig.ShowFrequency.ONCE) toggle(footer, tr("dlc_manager.notice.never_show"), neverShow) { neverShow = it; refreshPage() }
        primary(tr("dlc_manager.notice.read")) { onClose() }
        status(if (future == null) "" else tr("dlc_manager.notice.loading"))
    }
    private fun resolve(lang: String) {
        language = lang
        val fallback = config.getContents().resolve(lang)
        if (config.getContentUrl(lang).isBlank()) { resolvedContents = fallback; future = null }
        else { resolvedContents = tr("dlc_manager.notice.loading"); future = CompletableFuture.supplyAsync { config.resolveContents(lang) } }
    }
    override fun updatePage() {
        if (Minecraft.getInstance().options.languageCode != language) { refreshPage(); return }
        val pending = future
        if (pending != null && pending.isDone) {
            resolvedContents = runCatching { pending.getNow("") }.getOrDefault("").ifBlank { config.getContents().resolve(language) }
            future = null
            refreshPage()
        }
    }
    override fun onClose() {
        if (config.getShowFrequency() != DlcNoticeConfig.ShowFrequency.ONCE) { config.setNeverShow(neverShow); config.save() }
        super.onClose()
    }
}
