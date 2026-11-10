package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.*
import com.cozooo.dlc_manager.client.sync.DlcSyncAdapter
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.sync.*
import com.cozooo.dlc_manager.sync.DlcSyncModel.*
import com.cozooo.dlc_manager.platform.DlcTextCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import java.util.Locale
import java.util.concurrent.CompletableFuture

class DlcServerPushScreen(parent: Screen, private val entry: DlcServerEntry?) :
    DlcFilePageScreen(DlcUiPage.SERVER_PUSH, parent, DlcTextCompat.translatable("dlc_manager.sync.title")) {
    private var progressMode = false
    private var progressParent: DlcServerPushScreen? = null
    private var progressChosen: List<Row> = emptyList()
    private var progressStarted = false
    private var lastResult: Result? = null
    private var completionMode = false
    private var completionRunning = false
    private var completionDlc = emptyList<com.cozooo.dlc_manager.client.remote.DlcRemoteEntry>()
    private var completionRequired = emptyList<com.cozooo.dlc_manager.client.required.DlcRequiredEntry>()
    private var completeDlcSelected = true
    private var installRequiredSelected = true
    private var local = emptyList<Item>()
    private var remote = emptyList<Item>()
    private var rows = emptyList<Row>()
    private var filteredRows = emptyList<Row>()
    private var comparison = Comparison(emptyList(), emptyMap())
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
    @Volatile private var active: DlcSyncClient? = null
    @Volatile private var previewSession: DlcServerSession? = null
    @Volatile private var rollbackClient: DlcSyncClient? = null
    @Volatile private var failureReason: String? = null
    private var syncing = false
    private var results: com.sighs.apricityui.init.Element? = null
    private var renderedCategory = Category.ALL
    private val scrolls = java.util.EnumMap<Category, DlcUiScroll.State>(Category::class.java)
    private var executeButton: DlcUiButton? = null
    private var progressScrollLocked = false

    private constructor(parent: DlcServerPushScreen, entry: DlcServerEntry?, chosen: List<Row>, mode: Mode, states: Boolean, direction: Direction) : this(parent, entry) {
        progressMode = true
        progressParent = parent
        progressChosen = chosen.toList()
        this.mode = mode
        this.states = states
        this.direction = direction
    }

    override fun init() {
        super.init()
        if (progressMode && !progressStarted) {
            progressStarted = true
            execute(progressChosen)
        }
    }

    override fun buildPage() {
        if (progressMode) {
            buildProgressPage()
            return
        }
        footerLeading.setAttribute("style", "display:flex")
        action(footerLeading, tr("dlc_manager.sync.mode_" + mode.name.lowercase(Locale.ROOT))) { mode = if (mode == Mode.FILES) Mode.CONFIG else Mode.FILES; replan() }.setEnabled(!busy)
        action(toolbar, tr("dlc_manager.sync." + direction.name.lowercase(Locale.ROOT))) { direction = if (direction == Direction.PUSH) Direction.PULL else Direction.PUSH; replan() }.setEnabled(!busy)
        if (DlcSyncModel.hasStateDifferences(local, remote)) toggle(footerLeading, tr("dlc_manager.sync.states"), states) { states = it; replan() }.setEnabled(!busy)
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
        val language = com.cozooo.dlc_manager.platform.DlcPlatformServices.languageCode()
        for (row in shown) {
            val name = DlcSyncModel.displayName(row.source, language)
            val status = tr("dlc_manager.sync." + comparison.status(row))
            val tooltip = DlcUiTooltip.Content(name, DlcSyncModel.tooltip(row.source, language), emptyList(), status)
            val line = selectionRow(list, name, { selected.contains(row.key).toString() }) { if (!selected.add(row.key)) selected.remove(row.key) }
            tip(line) { tooltip }
            text(line, status, "row-description")
        }
        live(results, { message }, "page-description")
        showWhen(progress(results) { fraction }) { busy }
        if (rollbackClient?.canRollback() == true) {
            val undo = action(footer, tr("dlc_manager.sync.rollback"), ::rollback)
            undo.getElement().setAttribute("class", "button danger"); undo.setEnabled(!busy)
        }
        executeButton = primary(tr("dlc_manager.sync.execute")) { confirm() }
        executeButton?.getElement()?.setAttribute("class", "button primary")
        updateExecuteButton()
    }

    private fun buildProgressPage() {
        content.parentElement.setAttribute("class", "page-shell sync-page sync-progress-page")
        if (!progressScrollLocked) {
            progressScrollLocked = true
            content.addEventListener("wheel") { event ->
                if (progressMode) {
                    event.preventDefault()
                    content.setScrollTop(0.0)
                }
            }
        }
        content.setAttribute("style", "overflow-y:hidden;overflow-x:hidden")
        content.setScrollTop(0.0)
        val body = node(content, "div", "page-dialog")
        if (completionMode) {
            text(body, tr("dlc_manager.sync.complete_prompt"), "section-title")
            if (completionRunning) {
                live(body, { message }, "page-description")
                progress(body) { fraction }
                action(footer, tr("dlc_manager.sync.cancel")) { cancelCompletion() }.getElement().setAttribute("class", "button danger")
            } else {
                if (message.isNotBlank()) live(body, { message }, "page-description")
                val options = node(body, "div", "completion-options")
                if (completionDlc.isNotEmpty()) {
                    val option = node(options, "div", "completion-option")
                    text(option, tr("dlc_manager.sync.complete_dlc_info", completionDlc.size), "completion-info")
                    toggle(option, tr("dlc_manager.sync.complete_dlc"), completeDlcSelected) { completeDlcSelected = it; refreshPage() }
                        .also { it.getElement().setAttribute("class", "button choice completion-choice" + if (completeDlcSelected) " selected" else "") }
                }
                if (completionRequired.isNotEmpty()) {
                    val option = node(options, "div", "completion-option")
                    text(option, tr("dlc_manager.sync.install_required_info", completionRequired.size), "completion-info")
                    toggle(option, tr("dlc_manager.sync.install_required"), installRequiredSelected) { installRequiredSelected = it; refreshPage() }
                        .also { it.getElement().setAttribute("class", "button choice completion-choice" + if (installRequiredSelected) " selected" else "") }
                }
                completionAction(tr("dlc_manager.sync.done")) { startCompletion() }
                    .also { it.getElement().setAttribute("class", "button primary") }
            }
            return
        }
        text(body, tr("dlc_manager.sync.progress_title"), "section-title")
        live(body, { message }, "page-description")
        progress(body) { fraction }
        if (busy) {
            action(footer, tr("dlc_manager.sync.cancel")) { cancel() }.getElement().setAttribute("class", "button danger")
        }
    }
    private fun selectionRow(list: com.sighs.apricityui.init.Element, name: String, checked: java.util.function.Supplier<String>, change: Runnable): com.sighs.apricityui.init.Element {
        val line = managerRowLine(list, name, checked, { !busy }, change)
        line.setAttribute("class", "dlc-row sync-row")
        val checkbox = line.getChildren()[0]
        val document = pageDocument
        val generation = document.getRefreshGeneration()
        line.addEventListener("mousedown") { event ->
            val target = event.target
            if (event is com.sighs.apricityui.event.MouseEvent && event.button == 0 && !busy && Minecraft.getInstance().screen === this && document === getLinkedDocument() && document.isCurrentGeneration(generation) && !(target is com.sighs.apricityui.init.Element && checkbox.contains(target))) {
                event.preventDefault(); change.run(); syncSelections(); updatePage()
            }
        }
        return line
    }
    private fun settingsRow() = rows.firstOrNull { DlcSyncModel.category(it.source) == Category.MANAGER_CONFIG }
    private fun refilter() { filteredRows = rows.filter { DlcSyncModel.category(it.source) != Category.MANAGER_CONFIG && (category == Category.ALL || DlcSyncModel.category(it.source) == category) } }
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
        executeButton?.setEnabled(!busy && (settingsSelected && settingsRow() != null || selected.isNotEmpty() && visibleRows().any { it.key in selected }))
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
                DlcSyncEndpoint(DlcSyncAdapter()).use { endpoint -> connect().use { session ->
                    previewSession = session
                    val left = DlcSyncClient.manifest(endpoint) { cancelRequested }
                    val right = DlcSyncClient.manifest(peer(session)) { cancelRequested }
                    left to right
                } }
            } finally { previewSession = null }
            Minecraft.getInstance().execute { local = manifests.first; remote = manifests.second; loaded = true; message = ""; replan() }
        }
    }
    private fun replan() {
        comparison = DlcSyncModel.compare(local, remote, direction, states, mode)
        rows = comparison.rows
        selected.clear(); settingsSelected = false; refilter()
        refreshPage()
    }
    private fun confirm() {
        if (busy) return
        val chosen = selectedRows()
        if (chosen.isEmpty()) return
        val language = com.cozooo.dlc_manager.platform.DlcPlatformServices.languageCode()
        val message = DlcTextCompat.translatable("dlc_manager.sync.confirm", chosen.size)
        chosen.forEach { row ->
            message.append(DlcTextCompat.literal("\n"))
            message.append(DlcTextFormatter.parse(DlcSyncModel.displayName(row.source, language)))
        }
        Minecraft.getInstance().setScreen(DlcConfirmScreen({ accepted ->
            Minecraft.getInstance().setScreen(this)
            if (accepted) Minecraft.getInstance().setScreen(DlcServerPushScreen(this, entry, chosen, mode, states, direction))
        }, DlcTextCompat.translatable("dlc_manager.sync.title"), message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()))
    }
    private fun execute(chosen: List<Row>) = run(synchronizing = true) {
        rollbackClient = null
        try {
            DlcSyncEndpoint(DlcSyncAdapter()).use { endpoint -> connect().use { session ->
                val client = DlcSyncClient(endpoint, peer(session), control()); active = client; rollbackClient = client
                if (cancelRequested) client.cancel()
                val result = client.execute(chosen, mode, states) { phase, resource, done, total ->
                    fraction = if (total > 0 && phase != "restoring") done.toDouble() / total else -1.0
                    val amount = if (phase == "transferring" || phase == "downloading") com.cozooo.dlc_manager.core.DlcFileSize.formatProgress(done, total) else "$done" + if (total >= 0) "/$total" else ""
                    message = DlcTextCompat.translatable("dlc_manager.sync.$phase").string + " ${resource.substringAfterLast('/')} $amount"
                }
                lastResult = result
                message = DlcTextCompat.translatable(if (result.canceled) "dlc_manager.sync.canceled" else "dlc_manager.sync.finished", result.completed, result.errors.size).string +
                    (if (result.requiresRestart) " " + DlcTextCompat.translatable("dlc_manager.sync.restart").string else "") + "\n" + result.succeeded.joinToString("\n") { tr("dlc_manager.sync.succeeded_item", it) } + "\n" + result.errors.joinToString("\n")
                if (chosen.any { DlcSyncModel.category(it.source) == Category.UPDATE && it.key in result.succeeded }) message += "\n" + tr("dlc_manager.sync.updates_ready")
                if (result.canceled) return@run
                if (result.errors.isNotEmpty()) failureReason = result.errors.joinToString("\n")
            } }
        } finally { active = null }
    }
    private fun rollback() {
        val client = rollbackClient ?: return
        if (busy || !client.canRollback()) return
        run {
            message = tr("dlc_manager.sync.restoring")
            DlcSyncEndpoint(DlcSyncAdapter()).use { endpoint -> connect().use { session ->
                client.rollback(endpoint, peer(session))
                val left = DlcSyncClient.manifest(endpoint)
                val right = DlcSyncClient.manifest(peer(session))
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
            try { work() } catch (error: Exception) { message = if (cancelRequested && (error is java.net.SocketException || DlcSyncFailure.`is`(error, "canceled"))) DlcTextCompat.translatable("dlc_manager.sync.canceled").string else error.message ?: error.javaClass.simpleName; if (synchronizing && !cancelRequested) failureReason = failureReason?.let { "$it\n$message" } ?: message }
            finally { Minecraft.getInstance().execute {
                busy = false; syncing = false; connecting = null
                if (progressMode) finishProgress()
                else { refreshPage(); failureReason?.let(::showFailure) }
            } }
        }
    }

    private fun finishProgress() {
        val target = progressParent ?: return
        target.applyResult(lastResult, rollbackClient)
        val failure = failureReason
        if (failure != null) {
            Minecraft.getInstance().setScreen(DlcSyncFailureScreen({ restore ->
                Minecraft.getInstance().setScreen(target)
                if (restore) target.rollback()
            }, failure))
        } else {
            val succeeded = lastResult?.succeeded?.toSet().orEmpty()
            completionDlc = synchronizedDlcEntries(succeeded)
            completionRequired = synchronizedRequiredEntries(succeeded)
            if (completionDlc.isNotEmpty() || completionRequired.isNotEmpty()) {
                completionMode = true
                completionRunning = false
                completeDlcSelected = completionDlc.isNotEmpty()
                installRequiredSelected = completionRequired.isNotEmpty()
                message = ""
                fraction = -1.0
                refreshPage()
            } else Minecraft.getInstance().setScreen(target)
        }
    }

    private fun startCompletion() {
        val completeDlc = completeDlcSelected
        val installRequired = installRequiredSelected
        if (completionRunning) return
        if (!completeDlc && !installRequired) {
            completionMode = false
            progressParent?.let { Minecraft.getInstance().setScreen(it) }
            return
        }
        completionRunning = true
        cancelRequested = false
        fraction = 0.0
        message = tr("dlc_manager.sync.completing")
        refreshPage()
        CompletableFuture.runAsync {
            var errors = 0
            val overallTotal = (if (completeDlc) completionDlc.size else 0) + (if (installRequired) completionRequired.size else 0)
            var completedCount = 0
            try {
                if (completeDlc) {
                    val result = if (mode == Mode.CONFIG) completeRemoteOnServer(completionDlc, overallTotal, completedCount) else kotlin.run {
                        val localResult = com.cozooo.dlc_manager.client.remote.DlcRemoteService.downloadAll(completionDlc, object : com.cozooo.dlc_manager.client.remote.DlcRemoteService.BatchProgressListener {
                            override fun onEntryStatus(entry: com.cozooo.dlc_manager.client.remote.DlcRemoteEntry?, statusKey: String?, completed: Int, total: Int) {
                                completedCount = completed
                                fraction = if (overallTotal == 0) 1.0 else completedCount.toDouble() / overallTotal
                                message = tr("dlc_manager.sync.completing")
                            }
                        }, { cancelRequested })
                        completionDlc = com.cozooo.dlc_manager.client.remote.DlcRemoteService.collectMissing(completionDlc)
                        localResult.success to localResult.errors.size
                    }
                    errors += result.second
                    completedCount += result.first
                    if (result.second == 0) completionDlc = emptyList()
                }
                if (installRequired) {
                    val result = if (mode == Mode.CONFIG) completeRequiredOnServer(completionRequired, overallTotal, completedCount) else kotlin.run {
                        val localResult = com.cozooo.dlc_manager.client.required.DlcRequiredService.downloadAll(completionRequired, object : com.cozooo.dlc_manager.client.required.DlcRequiredService.BatchProgressListener {
                        override fun onEntryStatus(entry: com.cozooo.dlc_manager.client.required.DlcRequiredEntry?, statusKey: String?, completed: Int, total: Int) {
                            fraction = if (overallTotal == 0) 1.0 else (completedCount + completed).toDouble() / overallTotal
                            message = tr("dlc_manager.sync.completing")
                        }
                        }, { cancelRequested })
                        var localErrors = localResult.errors.size
                        completedCount += localResult.success
                        for (entry in completionRequired) {
                            if (cancelRequested) break
                            try {
                                com.cozooo.dlc_manager.client.required.DlcRequiredService.installOne(entry)
                            } catch (_: Exception) {
                                localErrors++
                            }
                        }
                        completionRequired = com.cozooo.dlc_manager.client.required.DlcRequiredService.collectMissingOrApply(completionRequired)
                        localResult.success to localErrors
                    }
                    errors += result.second
                    if (result.second == 0) completionRequired = emptyList()
                }
                if (!cancelRequested) fraction = 1.0
            } catch (error: Exception) {
                if (!cancelRequested) errors++
            }
            Minecraft.getInstance().execute {
                completionRunning = false
                if (cancelRequested) message = tr("dlc_manager.sync.canceled")
                else if (completionDlc.isEmpty() && completionRequired.isEmpty()) {
                    completionMode = false
                    progressParent?.let { Minecraft.getInstance().setScreen(it) }
                } else if (errors > 0) message = tr("dlc_manager.sync.completion_failed", errors)
                else message = ""
                refreshPage()
            }
        }
    }

    private fun cancelCompletion() {
        if (completionRunning) cancelRequested = true
    }

    private fun completeRemoteOnServer(entries: List<com.cozooo.dlc_manager.client.remote.DlcRemoteEntry>, total: Int, completed: Int): Pair<Int, Int> {
        val ids = entries.mapNotNull { it.getDlcIdentifier()?.takeIf(String::isNotBlank) }.distinct()
        if (ids.isEmpty()) return 0 to 0
        val started = com.cozooo.dlc_manager.client.server.DlcServerConnection.startRemoteDownload(entry, 8000, ids)
            ?: throw java.io.IOException("server remote download unavailable")
        return awaitRemoteServerDownload(started, total, completed)
    }

    private fun awaitRemoteServerDownload(initial: com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus, total: Int, completed: Int): Pair<Int, Int> {
        var status = initial
        var cancelSent = false
        while (!status.finished) {
            if (cancelRequested && !cancelSent) {
                com.cozooo.dlc_manager.client.server.DlcServerConnection.cancelRemoteDownload(entry, 3000)
                cancelSent = true
            }
            val next = com.cozooo.dlc_manager.client.server.DlcServerConnection.fetchRemoteDownloadStatus(entry, 3000)
                ?: throw java.io.IOException("server remote download status unavailable")
            status = next
            val done = status.completedCount
            fraction = if (total == 0) 1.0 else (completed + done).toDouble() / total
            message = tr("dlc_manager.sync.completing")
            if (!status.finished) Thread.sleep(200)
        }
        if (cancelRequested) return 0 to 0
        if (!status.success) throw java.io.IOException(status.error ?: "server remote download failed")
        return status.completedCount to 0
    }

    private fun completeRequiredOnServer(entries: List<com.cozooo.dlc_manager.client.required.DlcRequiredEntry>, total: Int, completed: Int): Pair<Int, Int> {
        val ids = entries.mapNotNull { it.getIdentifier().takeIf(String::isNotBlank) }.distinct()
        if (ids.isEmpty()) return 0 to 0
        val started = com.cozooo.dlc_manager.client.server.DlcServerConnection.startRequiredDownload(entry, 8000, ids)
            ?: throw java.io.IOException("server required download unavailable")
        var status = started
        var cancelSent = false
        while (!status.finished) {
            if (cancelRequested && !cancelSent) {
                com.cozooo.dlc_manager.client.server.DlcServerConnection.cancelRequiredDownload(entry, 3000)
                cancelSent = true
            }
            val next = com.cozooo.dlc_manager.client.server.DlcServerConnection.fetchRequiredDownloadStatus(entry, 3000)
                ?: throw java.io.IOException("server required download status unavailable")
            status = next
            fraction = if (total == 0) 1.0 else (completed + status.completedCount).toDouble() / total
            message = tr("dlc_manager.sync.completing")
            if (!status.finished) Thread.sleep(200)
        }
        if (cancelRequested) return 0 to 0
        if (!status.success) throw java.io.IOException(status.error ?: "server required download failed")
        return status.completedCount to 0
    }

    private fun completionAction(label: String, run: () -> Unit): DlcUiButton {
        val button = DlcUiButton(pageDocument, footer, label, run)
        button.getElement().addEventListener("click") { run() }
        return button
    }

    private fun synchronizedPaths(keys: Set<String>): Set<java.nio.file.Path> = keys.mapNotNull { key ->
        val separator = key.indexOf(':')
        val index = key.substring(0, separator).toIntOrNull()
        if (separator <= 0 || index == null || index !in DlcConfig.getDlcRoots().indices) null
        else DlcConfig.getDlcRoots()[index].resolve(key.substring(separator + 1)).normalize()
    }.toSet()

    private fun synchronizedDlcEntries(keys: Set<String>): List<com.cozooo.dlc_manager.client.remote.DlcRemoteEntry> {
        if (mode != Mode.CONFIG || keys.isEmpty()) return emptyList()
        val selectedKeys = progressChosen.filter { it.key in keys && DlcSyncModel.category(it.source) == Category.DLC }.map { it.key }.toSet()
        val paths = synchronizedPaths(selectedKeys)
        val selected = progressChosen.filter { it.key in selectedKeys }
            .associateBy { synchronizedPaths(setOf(it.key)).firstOrNull() }
        val entries = DlcConfig.getDlcRoots().flatMap { com.cozooo.dlc_manager.client.remote.DlcRemoteLoader.load(it) }
            .filter { entry ->
                val path = entry.getConfigPath()?.toAbsolutePath()?.normalize()
                path in paths && selected[path]?.let(::needsResourceCompletion) == true
            }
        return entries
    }

    private fun synchronizedRequiredEntries(keys: Set<String>): List<com.cozooo.dlc_manager.client.required.DlcRequiredEntry> {
        if (mode != Mode.CONFIG || keys.isEmpty()) return emptyList()
        val selectedKeys = progressChosen.filter { it.key in keys && DlcSyncModel.category(it.source) == Category.REQUIRED }.map { it.key }.toSet()
        val paths = synchronizedPaths(selectedKeys)
        val selected = progressChosen.filter { it.key in selectedKeys }
            .associateBy { synchronizedPaths(setOf(it.key)).firstOrNull() }
        val entries = DlcConfig.getDlcRoots().flatMap { com.cozooo.dlc_manager.client.required.DlcRequiredLoader.load(it) }
            .filter { entry ->
                val path = entry.getConfigPath()?.toAbsolutePath()?.normalize()
                path in paths && selected[path]?.let(::needsResourceCompletion) == true
            }
        return entries
    }

    private fun needsResourceCompletion(row: Row): Boolean {
        val target = row.target ?: return true
        return target.resources.isEmpty() || target.resources.any { it.size < 0 || it.hash == null }
    }

    private fun applyResult(result: Result?, client: DlcSyncClient?) {
        rollbackClient = client
        result?.succeeded?.toSet()?.let { succeeded ->
            rows = rows.filterNot { it.key in succeeded }
            selected.removeAll(succeeded)
            settingsSelected = false
            refilter()
        }
        refreshPage()
    }
    override fun updatePage() { updateExecuteButton() }
    override fun shouldCloseOnEsc() = !busy
}
