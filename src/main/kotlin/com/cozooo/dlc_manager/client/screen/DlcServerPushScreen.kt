package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.*
import com.cozooo.dlc_manager.client.sync.DlcSyncAdapter
import com.cozooo.dlc_manager.sync.*
import com.cozooo.dlc_manager.sync.SyncModel.*
import com.cozooo.dlc_manager.platform.DlcTextCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import java.util.Locale
import java.util.concurrent.CompletableFuture

class DlcServerPushScreen(parent: Screen, private val entry: DlcServerEntry?) :
    DlcFilePageScreen(DlcUiPage.SERVER_PUSH, parent, DlcTextCompat.translatable("dlc_manager.sync.title")) {
    private var local = emptyList<Item>()
    private var remote = emptyList<Item>()
    private var rows = emptyList<Row>()
    private var filteredRows = emptyList<Row>()
    private val selected = mutableSetOf<String>()
    private var mode = Mode.CONFIG
    private var category = Category.ALL
    private var direction = Direction.PUSH
    private var settingsSelected = false
    private var states = true
    private var loaded = false
    @Volatile private var busy = false
    @Volatile private var message = ""
    @Volatile private var fraction = -1.0
    @Volatile private var connecting: AutoCloseable? = null
    @Volatile private var cancelRequested = false
    @Volatile private var active: SyncClient? = null
    @Volatile private var previewSession: DlcServerSession? = null
    @Volatile private var rollbackClient: SyncClient? = null
    @Volatile private var failureReason: String? = null
    private var syncing = false
    private var results: com.sighs.apricityui.init.Element? = null
    private var renderedCategory = Category.ALL
    private val scrolls = java.util.EnumMap<Category, DlcUiScroll.State>(Category::class.java)
    private var executeButton: DlcUiButton? = null

    override fun buildPage() {
        footerLeading.setAttribute("style", "display:flex")
        action(footerLeading, tr("dlc_manager.sync.mode_" + mode.name.lowercase(Locale.ROOT))) { mode = if (mode == Mode.FILES) Mode.CONFIG else Mode.FILES; replan() }.setEnabled(!busy)
        action(toolbar, tr("dlc_manager.sync." + direction.name.lowercase(Locale.ROOT))) { direction = if (direction == Direction.PUSH) Direction.PULL else Direction.PUSH; replan() }.setEnabled(!busy)
        if (SyncModel.hasStateDifferences(local, remote)) toggle(footerLeading, tr("dlc_manager.sync.states"), states) { states = it; replan() }.setEnabled(!busy)
        if (settingsRow() != null) toggle(footerLeading, tr("dlc_manager.sync.manager_config"), settingsSelected) { settingsSelected = it; refreshPage() }.setEnabled(!busy)
        action(toolbar, tr("dlc_manager.sync.compare"), ::compare).setEnabled(!busy)
        content.parentElement.setAttribute("class", "page-shell sync-page")
        val layout = node(content, "div", "sync-layout")
        val types = node(layout, "aside", "sync-types")
        for (type in Category.entries) {
            if (type == Category.MANAGER_CONFIG) continue
            val button = action(types, tr("dlc_manager.sync.type_" + type.name.lowercase(Locale.ROOT))) { category = type; refilter(); refreshPage() }
            button.getElement().setAttribute("class", "button filter" + if (category == type) " active" else "")
            button.setEnabled(!busy)
        }
        results?.let { scrolls[renderedCategory] = DlcUiScroll.capture(it) }
        val results = node(layout, "div", "sync-results")
        this.results = results
        renderedCategory = category
        scrolls[category]?.let { DlcUiScroll.restore(results, it) }
        val shown = visibleRows()
        val list = fileList(if (loaded && shown.isEmpty()) tr("dlc_manager.sync.empty") else tr("dlc_manager.sync.differences"))
        results.appendChild(list)
        showWhen(list) { !busy || syncing }
        if (shown.isNotEmpty()) selectionRow(list, tr("dlc_manager.sync.select_all"), ::selectionState, ::selectVisible)
        for (row in shown) {
            val language = com.cozooo.dlc_manager.platform.DlcPlatformServices.languageCode()
            val name = SyncModel.displayName(row.source, language)
            val line = selectionRow(list, name, { selected.contains(row.key).toString() }) { if (!selected.add(row.key)) selected.remove(row.key) }
            tip(line) { DlcUiTooltip.Content(name, SyncModel.tooltip(row.source, language), emptyList(), tr("dlc_manager.sync." + SyncModel.status(row, mode))) }
            text(line, tr("dlc_manager.sync." + SyncModel.status(row, mode)), "row-description")
        }
        live(results, { message }, "page-description")
        showWhen(progress(results) { fraction }) { busy }
        if (rollbackClient?.canRollback() == true) {
            val undo = action(footer, tr("dlc_manager.sync.rollback"), ::rollback)
            undo.getElement().setAttribute("class", "button danger"); undo.setEnabled(!busy)
        }
        executeButton = primary(tr(if (syncing) "dlc_manager.sync.cancel" else "dlc_manager.sync.execute")) {
            if (syncing) cancel() else confirm()
        }
        executeButton?.getElement()?.setAttribute("class", if (syncing) "button danger" else "button primary")
        updateExecuteButton()
    }
    private fun selectionRow(list: com.sighs.apricityui.init.Element, name: String, checked: java.util.function.Supplier<String>, change: Runnable): com.sighs.apricityui.init.Element {
        val line = fileRowLine(list, name, null, checked, { !busy }, change, null)
        val checkbox = line.getChildren()[0]
        val document = pageDocument
        val generation = document.getRefreshGeneration()
        line.addEventListener("mousedown") { event ->
            val target = event.target
            if (event is com.sighs.apricityui.event.MouseEvent && event.button == 0 && !busy && Minecraft.getInstance().screen === this && document === getLinkedDocument() && document.isCurrentGeneration(generation) && !(target is com.sighs.apricityui.init.Element && checkbox.contains(target))) {
                event.preventDefault(); change.run(); updatePage()
            }
        }
        return line
    }
    private fun settingsRow() = rows.firstOrNull { SyncModel.category(it.source) == Category.MANAGER_CONFIG }
    private fun refilter() { filteredRows = rows.filter { SyncModel.category(it.source) != Category.MANAGER_CONFIG && (category == Category.ALL || SyncModel.category(it.source) == category) } }
    private fun visibleRows(): List<Row> = filteredRows
    private fun selectedRows(): List<Row> = visibleRows().filter { it.key in selected } + if (settingsSelected) listOfNotNull(settingsRow()) else emptyList()
    private fun selectionState(): String {
        val shown = visibleRows()
        val count = shown.count { selected.contains(it.key) }
        return if (count == 0) "false" else if (count == shown.size) "true" else "mixed"
    }
    private fun selectVisible() {
        val shown = visibleRows()
        val all = shown.all { selected.contains(it.key) }
        shown.forEach { if (all) selected.remove(it.key) else selected.add(it.key) }
    }
    private fun updateExecuteButton() {
        executeButton?.setEnabled(if (busy) syncing && !cancelRequested else settingsSelected && settingsRow() != null || visibleRows().any { it.key in selected })
    }
    private fun peer(session: DlcServerSession) = object : Peer {
        override fun call(request: com.google.gson.JsonObject): com.google.gson.JsonObject = try { session.syncRequest(request) }
            catch (error: java.io.IOException) { session.close(); throw error }
        override fun close() = session.close()
    }
    private fun control() = Peer { request -> DlcServerSession.connect(entry, 10000).use { peer(it).call(request) } }
    private fun compare() {
        if (busy) return
        loaded = false; local = emptyList(); remote = emptyList(); rows = emptyList(); filteredRows = emptyList(); settingsSelected = false; selected.clear()
        run {
            val manifests = try {
                SyncEndpoint(DlcSyncAdapter()).use { endpoint -> connect().use { session ->
                    previewSession = session
                    val left = SyncClient.manifest(endpoint) { cancelRequested }
                    val right = SyncClient.manifest(peer(session)) { cancelRequested }
                    left to right
                } }
            } finally { previewSession = null }
            Minecraft.getInstance().execute { local = manifests.first; remote = manifests.second; loaded = true; message = ""; replan() }
        }
    }
    private fun replan() {
        rows = SyncModel.plan(local, remote, direction, states, mode)
        selected.clear(); settingsSelected = false; refilter()
        refreshPage()
    }
    private fun confirm() {
        if (busy) return
        val chosen = selectedRows()
        if (chosen.isEmpty()) return
        Minecraft.getInstance().setScreen(DlcConfirmScreen({ accepted ->
            Minecraft.getInstance().setScreen(this)
            if (accepted) execute(chosen)
        }, DlcTextCompat.translatable("dlc_manager.sync.title"), DlcTextCompat.translatable("dlc_manager.sync.confirm", chosen.size), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()))
    }
    private fun execute(chosen: List<Row>) = run(synchronizing = true) {
        rollbackClient = null
        try {
            SyncEndpoint(DlcSyncAdapter()).use { endpoint -> connect().use { session ->
                val client = SyncClient(endpoint, peer(session), control()); active = client; rollbackClient = client
                if (cancelRequested) client.cancel()
                val result = client.execute(chosen, mode, states) { phase, resource, done, total ->
                    fraction = if (total > 0 && phase != "restoring") done.toDouble() / total else -1.0
                    val amount = if (phase == "transferring" || phase == "downloading") com.cozooo.dlc_manager.core.DlcFileSize.formatProgress(done, total) else "$done" + if (total >= 0) "/$total" else ""
                    message = DlcTextCompat.translatable("dlc_manager.sync.$phase").string + " ${resource.substringAfterLast('/')} $amount"
                }
                message = DlcTextCompat.translatable(if (result.canceled) "dlc_manager.sync.canceled" else "dlc_manager.sync.finished", result.completed, result.errors.size).string +
                    (if (result.requiresRestart) " " + DlcTextCompat.translatable("dlc_manager.sync.restart").string else "") + "\n" + result.succeeded.joinToString("\n") { tr("dlc_manager.sync.succeeded_item", it) } + "\n" + result.errors.joinToString("\n")
                if (chosen.any { SyncModel.category(it.source) == Category.UPDATE && it.key in result.succeeded }) message += "\n" + tr("dlc_manager.sync.updates_ready")
                if (result.canceled) return@run
                if (result.errors.isNotEmpty()) failureReason = result.errors.joinToString("\n")
                val left = SyncClient.manifest(endpoint) { cancelRequested }
                val right = SyncClient.manifest(peer(session)) { cancelRequested }
                Minecraft.getInstance().execute { local = left; remote = right; replan() }
            } }
        } finally { active = null }
    }
    private fun rollback() {
        val client = rollbackClient ?: return
        if (busy || !client.canRollback()) return
        run {
            message = tr("dlc_manager.sync.restoring")
            SyncEndpoint(DlcSyncAdapter()).use { endpoint -> connect().use { session ->
                client.rollback(endpoint, peer(session))
                val left = SyncClient.manifest(endpoint)
                val right = SyncClient.manifest(peer(session))
                Minecraft.getInstance().execute { local = left; remote = right; replan() }
            } }
            message = tr("dlc_manager.sync.restored")
        }
    }
    private fun showFailure(reason: String) {
        Minecraft.getInstance().setScreen(DlcSyncFailureScreen({ restore ->
            Minecraft.getInstance().setScreen(this)
            if (restore) rollback()
        }, reason))
    }
    private fun connect(): DlcServerSession = DlcServerSession.connect(entry, 120000) { socket ->
        connecting = socket
        if (cancelRequested) try { socket.close() } catch (_: Exception) {}
    }
    private fun cancel() {
        if (!busy || cancelRequested) return
        cancelRequested = true
        if (active != null) { if (active?.cancel() != true) { cancelRequested = false; return }; message = tr("dlc_manager.sync.restoring"); fraction = -1.0 }
        else try { connecting?.close() } catch (_: Exception) {}
        refreshPage()
    }
    private fun run(synchronizing: Boolean = false, work: () -> Unit) {
        if (busy) return
        failureReason = null; cancelRequested = false; syncing = synchronizing; busy = true; fraction = -1.0
        message = tr("dlc_manager.sync.working"); refreshPage()
        CompletableFuture.runAsync {
            try { work() } catch (error: Exception) { message = if (cancelRequested && (error is java.net.SocketException || SyncFailure.`is`(error, "canceled"))) DlcTextCompat.translatable("dlc_manager.sync.canceled").string else error.message ?: error.javaClass.simpleName; if (synchronizing && !cancelRequested) failureReason = failureReason?.let { "$it\n$message" } ?: message }
            finally { Minecraft.getInstance().execute { busy = false; syncing = false; connecting = null; refreshPage(); failureReason?.let(::showFailure) } }
        }
    }
    override fun updatePage() { updateExecuteButton(); super.updatePage() }
    override fun shouldCloseOnEsc() = !busy
}
