package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class DlcServerConfigScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry,
) : Screen(Component.translatable("dlc_manager.server.config_title")) {
    private data class LabelEntry(val label: Component, val y: Int)

    private val labels = ArrayList<LabelEntry>()

    private var serverSupport = false
    private var apiEnabled = false
    private var uiEnableAll = false
    private var uiUpdateManagerOn = false
    private var uiSelfUpdateAutoCheckOn = false
    private var uiPresetFuncOn = false

    private lateinit var serverPortField: EditBox
    private lateinit var serverPasswordField: EditBox
    private lateinit var apiPortField: EditBox
    private lateinit var apiTokenField: EditBox
    private lateinit var dlcRootField: EditBox
    private lateinit var taczRootField: EditBox
    private lateinit var resourcepackRootField: EditBox
    private lateinit var datapackRootField: EditBox
    private lateinit var maxConcurrentField: EditBox
    private lateinit var maxSegmentsField: EditBox
    private lateinit var minSegmentField: EditBox
    private lateinit var maxRetryField: EditBox
    private lateinit var connectTimeoutField: EditBox
    private lateinit var taskTimeoutField: EditBox

    private lateinit var serverSupportButton: Button
    private lateinit var apiEnabledButton: Button
    private lateinit var uiEnableAllButton: Button
    private lateinit var uiUpdateManagerButton: Button
    private lateinit var uiSelfUpdateButton: Button
    private lateinit var uiPresetButton: Button
    private lateinit var saveButton: Button

    private var labelX = 0
    private var fieldX = 0
    private var loading = false

    override fun init() {
        labels.clear()
        val centerX = width / 2
        val labelWidth = 160
        val fieldWidth = 220
        val totalWidth = labelWidth + 6 + fieldWidth
        labelX = centerX - totalWidth / 2
        fieldX = labelX + labelWidth + 6
        var y = 40
        val row = 22

        serverSupport = DlcPathConfig.isServerSupportOnServer()
        apiEnabled = DlcPathConfig.isApiEnabled()
        uiEnableAll = DlcPathConfig.isEnableAllButtonShown()
        uiUpdateManagerOn = DlcPathConfig.isUpdateManagerOn()
        uiSelfUpdateAutoCheckOn = DlcPathConfig.isSelfUpdateAutoCheckOn()
        uiPresetFuncOn = DlcPathConfig.isPresetFuncOn()

        addLabel("dlc_manager.config.ui.serverSupport", y)
        serverSupportButton = Button.builder(toggleLabel("dlc_manager.config.ui.serverSupport", serverSupport)) { btn ->
            serverSupport = !serverSupport
            btn.message = toggleLabel("dlc_manager.config.ui.serverSupport", serverSupport)
        }.bounds(fieldX, y, fieldWidth, 20).build()
        addRenderableWidget(serverSupportButton)
        y += row

        addLabel("dlc_manager.config.ui.serverPort", y)
        serverPortField = createField(y, fieldWidth, "dlc_manager.config.ui.serverPort", 8)
        y += row

        addLabel("dlc_manager.config.ui.serverPassword", y)
        serverPasswordField = createField(y, fieldWidth, "dlc_manager.config.ui.serverPassword", 256)
        y += row + 6

        addLabel("dlc_manager.config.ui.apiEnabled", y)
        apiEnabledButton = Button.builder(toggleLabel("dlc_manager.config.ui.apiEnabled", apiEnabled)) { btn ->
            apiEnabled = !apiEnabled
            btn.message = toggleLabel("dlc_manager.config.ui.apiEnabled", apiEnabled)
        }.bounds(fieldX, y, fieldWidth, 20).build()
        addRenderableWidget(apiEnabledButton)
        y += row

        addLabel("dlc_manager.config.ui.apiPort", y)
        apiPortField = createField(y, fieldWidth, "dlc_manager.config.ui.apiPort", 8)
        y += row

        addLabel("dlc_manager.config.ui.apiToken", y)
        apiTokenField = createField(y, fieldWidth, "dlc_manager.config.ui.apiToken", 256)
        y += row + 6

        addLabel("dlc_manager.config.ui.isEnableAllButtonShown", y)
        uiEnableAllButton = Button.builder(toggleLabel("dlc_manager.config.ui.isEnableAllButtonShown", uiEnableAll)) { btn ->
            uiEnableAll = !uiEnableAll
            btn.message = toggleLabel("dlc_manager.config.ui.isEnableAllButtonShown", uiEnableAll)
        }.bounds(fieldX, y, fieldWidth, 20).build()
        addRenderableWidget(uiEnableAllButton)
        y += row

        addLabel("dlc_manager.config.ui.isUpdateManagerOn", y)
        uiUpdateManagerButton = Button.builder(toggleLabel("dlc_manager.config.ui.isUpdateManagerOn", uiUpdateManagerOn)) { btn ->
            uiUpdateManagerOn = !uiUpdateManagerOn
            btn.message = toggleLabel("dlc_manager.config.ui.isUpdateManagerOn", uiUpdateManagerOn)
        }.bounds(fieldX, y, fieldWidth, 20).build()
        addRenderableWidget(uiUpdateManagerButton)
        y += row

        addLabel("dlc_manager.config.ui.isSelfUpdateAutoCheckOn", y)
        uiSelfUpdateButton = Button.builder(toggleLabel("dlc_manager.config.ui.isSelfUpdateAutoCheckOn", uiSelfUpdateAutoCheckOn)) { btn ->
            uiSelfUpdateAutoCheckOn = !uiSelfUpdateAutoCheckOn
            btn.message = toggleLabel("dlc_manager.config.ui.isSelfUpdateAutoCheckOn", uiSelfUpdateAutoCheckOn)
        }.bounds(fieldX, y, fieldWidth, 20).build()
        addRenderableWidget(uiSelfUpdateButton)
        y += row

        addLabel("dlc_manager.config.ui.isPresetFuncOn", y)
        uiPresetButton = Button.builder(toggleLabel("dlc_manager.config.ui.isPresetFuncOn", uiPresetFuncOn)) { btn ->
            uiPresetFuncOn = !uiPresetFuncOn
            btn.message = toggleLabel("dlc_manager.config.ui.isPresetFuncOn", uiPresetFuncOn)
        }.bounds(fieldX, y, fieldWidth, 20).build()
        addRenderableWidget(uiPresetButton)
        y += row + 6

        addLabel("dlc_manager.config.dlc_root", y)
        dlcRootField = createField(y, fieldWidth, "dlc_manager.config.dlc_root", 256)
        y += row

        addLabel("dlc_manager.config.tacz_root", y)
        taczRootField = createField(y, fieldWidth, "dlc_manager.config.tacz_root", 256)
        y += row

        addLabel("dlc_manager.config.resourcepack_root", y)
        resourcepackRootField = createField(y, fieldWidth, "dlc_manager.config.resourcepack_root", 256)
        y += row

        addLabel("dlc_manager.config.datapack_root", y)
        datapackRootField = createField(y, fieldWidth, "dlc_manager.config.datapack_root", 256)
        y += row + 6

        addLabel("dlc_manager.config.download.max_concurrent", y)
        maxConcurrentField = createField(y, fieldWidth, "dlc_manager.config.download.max_concurrent", 8)
        y += row

        addLabel("dlc_manager.config.download.max_segments", y)
        maxSegmentsField = createField(y, fieldWidth, "dlc_manager.config.download.max_segments", 8)
        y += row

        addLabel("dlc_manager.config.download.min_segment_bytes", y)
        minSegmentField = createField(y, fieldWidth, "dlc_manager.config.download.min_segment_bytes", 8)
        y += row

        addLabel("dlc_manager.config.download.max_retry", y)
        maxRetryField = createField(y, fieldWidth, "dlc_manager.config.download.max_retry", 8)
        y += row

        addLabel("dlc_manager.config.download.connect_timeout", y)
        connectTimeoutField = createField(y, fieldWidth, "dlc_manager.config.download.connect_timeout", 8)
        y += row

        addLabel("dlc_manager.config.download.task_timeout", y)
        taskTimeoutField = createField(y, fieldWidth, "dlc_manager.config.download.task_timeout", 8)

        val buttonHeight = 20
        val bottomY = height - 36
        val gap = 6
        val saveWidth = 100
        val backWidth = 100
        val checkWidth = maxOf(110, font.width(Component.translatable("dlc_manager.self_update.button")) + 12)
        val totalButtonWidth = checkWidth + gap + saveWidth + gap + backWidth
        val startX = centerX - totalButtonWidth / 2
        val selfUpdateCheckButton = Button.builder(Component.translatable("dlc_manager.self_update.button")) {
            Minecraft.getInstance().setScreen(DlcSelfUpdateScreen(this, null))
        }.bounds(startX, bottomY, checkWidth, buttonHeight).build()
        saveButton = Button.builder(Component.translatable("dlc_manager.config.save")) {
            saveConfig()
        }.bounds(startX + checkWidth + gap, bottomY, saveWidth, buttonHeight).build()
        val backButton = Button.builder(Component.translatable("gui.back")) {
            Minecraft.getInstance().setScreen(parent)
        }.bounds(saveButton.x + saveWidth + gap, bottomY, backWidth, buttonHeight).build()
        addRenderableWidget(selfUpdateCheckButton)
        addRenderableWidget(saveButton)
        addRenderableWidget(backButton)

        loadConfig()
        populateFromDefaults()
    }

    private fun createField(y: Int, width: Int, key: String, maxLength: Int): EditBox {
        val field = EditBox(font, fieldX, y, width, 20, Component.translatable(key))
        field.setMaxLength(maxLength)
        addRenderableWidget(field)
        return field
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF)
        for (entry in labels) {
            guiGraphics.drawString(font, entry.label, labelX, entry.y + 6, 0xC8C8C8, false)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun addLabel(key: String, y: Int) {
        labels.add(LabelEntry(Component.translatable(key), y))
    }

    private fun toggleLabel(key: String, value: Boolean): Component {
        return Component.translatable(key)
            .copy()
            .append(Component.literal(": "))
            .append(Component.translatable(if (value) "options.on" else "options.off"))
    }

    private fun populateFromDefaults() {
        serverPortField.value = DlcPathConfig.getServerPort().toString()
        serverPasswordField.value = DlcPathConfig.getServerPassword()
        apiEnabledButton.message = toggleLabel("dlc_manager.config.ui.apiEnabled", apiEnabled)
        apiPortField.value = DlcPathConfig.getApiPort().toString()
        apiTokenField.value = DlcPathConfig.getApiToken()
        dlcRootField.value = DlcPathConfig.getDlcRootRaw()
        taczRootField.value = DlcPathConfig.getTaczRootRaw()
        resourcepackRootField.value = DlcPathConfig.getResourcepackRootRaw()
        datapackRootField.value = DlcPathConfig.getDatapackRootRaw()
        maxConcurrentField.value = DlcPathConfig.getMaxConcurrentDownloads().toString()
        maxSegmentsField.value = DlcPathConfig.getMaxSegmentsPerFile().toString()
        minSegmentField.value = getDefaultMinSegmentMb().toString()
        maxRetryField.value = DlcPathConfig.getMaxRetryCount().toString()
        connectTimeoutField.value = getDefaultConnectTimeoutSeconds().toString()
        taskTimeoutField.value = getDefaultTaskTimeoutSeconds().toString()
    }

    private fun loadConfig() {
        if (loading) {
            return
        }
        loading = true
        CompletableFuture
            .supplyAsync { fetchConfig() }
            .whenComplete { config, _ ->
                Minecraft.getInstance().execute {
                    loading = false
                    if (config != null) {
                        applyConfig(config)
                    }
                }
            }
    }

    private fun fetchConfig(): JsonObject? {
        return try {
            DlcServerConnection.fetchServerConfig(entry, 8000)
        } catch (_: Exception) {
            null
        }
    }

    private fun applyConfig(root: JsonObject) {
        val server = DcConfigIO.getObjectIgnoreCase(root, "server")
        if (server != null) {
            serverSupport = getBoolean(server, "serverSupport", serverSupport)
            serverPortField.value = getInt(server, "serverPort", DlcPathConfig.getServerPort()).toString()
            serverPasswordField.value = getString(server, "serverPassword", "")
            serverSupportButton.message = toggleLabel("dlc_manager.config.ui.serverSupport", serverSupport)
        }
        val api = DcConfigIO.getObjectIgnoreCase(root, "api")
        if (api != null) {
            apiEnabled = getBoolean(api, "apiEnabled", apiEnabled)
            apiPortField.value = getInt(api, "apiPort", DlcPathConfig.getApiPort()).toString()
            apiTokenField.value = getString(api, "apiToken", DlcPathConfig.getApiToken())
            apiEnabledButton.message = toggleLabel("dlc_manager.config.ui.apiEnabled", apiEnabled)
        }
        val ui = DcConfigIO.getObjectIgnoreCase(root, "ui")
        if (ui != null) {
            uiEnableAll = getBoolean(ui, "isEnableAllButtonShown", uiEnableAll)
            uiUpdateManagerOn = getBoolean(ui, "isUpdateManagerOn", uiUpdateManagerOn)
            uiSelfUpdateAutoCheckOn = getBoolean(ui, "isSelfUpdateAutoCheckOn", uiSelfUpdateAutoCheckOn)
            uiPresetFuncOn = getBoolean(ui, "isPresetFuncOn", uiPresetFuncOn)
            uiEnableAllButton.message = toggleLabel("dlc_manager.config.ui.isEnableAllButtonShown", uiEnableAll)
            uiUpdateManagerButton.message = toggleLabel("dlc_manager.config.ui.isUpdateManagerOn", uiUpdateManagerOn)
            uiSelfUpdateButton.message = toggleLabel("dlc_manager.config.ui.isSelfUpdateAutoCheckOn", uiSelfUpdateAutoCheckOn)
            uiPresetButton.message = toggleLabel("dlc_manager.config.ui.isPresetFuncOn", uiPresetFuncOn)
        }
        val paths = DcConfigIO.getObjectIgnoreCase(root, "paths")
        if (paths != null) {
            dlcRootField.value = getString(paths, "dlcRoot", DlcPathConfig.getDlcRootRaw())
            taczRootField.value = getString(paths, "taczRoot", DlcPathConfig.getTaczRootRaw())
            resourcepackRootField.value = getString(paths, "resourcepackRoot", DlcPathConfig.getResourcepackRootRaw())
            datapackRootField.value = getString(paths, "datapackRoot", DlcPathConfig.getDatapackRootRaw())
        }
        val downloads = DcConfigIO.getObjectIgnoreCase(root, "downloads")
        if (downloads != null) {
            maxConcurrentField.value = getInt(downloads, "maxConcurrentDownloads", DlcPathConfig.getMaxConcurrentDownloads()).toString()
            maxSegmentsField.value = getInt(downloads, "maxSegmentsPerFile", DlcPathConfig.getMaxSegmentsPerFile()).toString()
            minSegmentField.value = getInt(downloads, "minSegmentMB", getDefaultMinSegmentMb()).toString()
            maxRetryField.value = getInt(downloads, "maxRetryCount", DlcPathConfig.getMaxRetryCount()).toString()
            connectTimeoutField.value = getInt(downloads, "connectTimeoutSeconds", getDefaultConnectTimeoutSeconds()).toString()
            taskTimeoutField.value = getInt(downloads, "taskTimeoutSeconds", getDefaultTaskTimeoutSeconds()).toString()
        }
    }

    private fun saveConfig() {
        val root = JsonObject()
        val server = JsonObject()
        server.addProperty("serverSupport", serverSupport)
        server.addProperty("serverPort", clampInt(parseInt(serverPortField.value, DlcPathConfig.getServerPort()), 1, 65535))
        server.addProperty("serverPassword", serverPasswordField.value)
        root.add("server", server)

        val api = JsonObject()
        api.addProperty("apiEnabled", apiEnabled)
        api.addProperty("apiPort", clampInt(parseInt(apiPortField.value, DlcPathConfig.getApiPort()), 0, 65535))
        api.addProperty("apiToken", apiTokenField.value)
        root.add("api", api)

        val ui = JsonObject()
        ui.addProperty("isEnableAllButtonShown", uiEnableAll)
        ui.addProperty("isUpdateManagerOn", uiUpdateManagerOn)
        ui.addProperty("isSelfUpdateAutoCheckOn", uiSelfUpdateAutoCheckOn)
        ui.addProperty("isPresetFuncOn", uiPresetFuncOn)
        root.add("ui", ui)

        val paths = JsonObject()
        paths.addProperty("dlcRoot", dlcRootField.value)
        paths.addProperty("taczRoot", taczRootField.value)
        paths.addProperty("resourcepackRoot", resourcepackRootField.value)
        paths.addProperty("datapackRoot", datapackRootField.value)
        root.add("paths", paths)

        val downloads = JsonObject()
        downloads.addProperty("maxConcurrentDownloads", parseInt(maxConcurrentField.value, DlcPathConfig.getMaxConcurrentDownloads()))
        downloads.addProperty("maxSegmentsPerFile", parseInt(maxSegmentsField.value, DlcPathConfig.getMaxSegmentsPerFile()))
        downloads.addProperty("minSegmentMB", parseInt(minSegmentField.value, getDefaultMinSegmentMb()))
        downloads.addProperty("maxRetryCount", parseInt(maxRetryField.value, DlcPathConfig.getMaxRetryCount()))
        downloads.addProperty("connectTimeoutSeconds", parseInt(connectTimeoutField.value, getDefaultConnectTimeoutSeconds()))
        downloads.addProperty("taskTimeoutSeconds", parseInt(taskTimeoutField.value, getDefaultTaskTimeoutSeconds()))
        root.add("downloads", downloads)

        CompletableFuture
            .supplyAsync { submitConfig(root) }
            .whenComplete { ok, _ ->
                Minecraft.getInstance().execute {
                    if (ok == true) {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.config_save_success"),
                            Component.empty(),
                        )
                    } else {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.config_save_failed"),
                            Component.empty(),
                        )
                    }
                }
            }
    }

    private fun submitConfig(root: JsonObject): Boolean {
        return try {
            DlcServerConnection.saveServerConfig(entry, 8000, root)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        @JvmStatic
        private fun parseInt(text: String?, fallback: Int): Int {
            if (text == null) {
                return fallback
            }
            return try {
                text.trim().toInt()
            } catch (_: NumberFormatException) {
                fallback
            }
        }

        @JvmStatic
        private fun clampInt(value: Int, min: Int, max: Int): Int {
            if (value < min) {
                return min
            }
            if (value > max) {
                return max
            }
            return value
        }

        @JvmStatic
        private fun getInt(obj: JsonObject, key: String, fallback: Int): Int {
            val element: JsonElement? = DcConfigIO.getIgnoreCase(obj, key)
            if (element != null && element.isJsonPrimitive) {
                return try {
                    element.asInt
                } catch (_: Exception) {
                    fallback
                }
            }
            return fallback
        }

        @JvmStatic
        private fun getBoolean(obj: JsonObject, key: String, fallback: Boolean): Boolean {
            val element: JsonElement? = DcConfigIO.getIgnoreCase(obj, key)
            if (element != null && element.isJsonPrimitive) {
                return try {
                    element.asBoolean
                } catch (_: Exception) {
                    fallback
                }
            }
            return fallback
        }

        @JvmStatic
        private fun getString(obj: JsonObject, key: String, fallback: String): String {
            val element: JsonElement? = DcConfigIO.getIgnoreCase(obj, key)
            if (element != null && element.isJsonPrimitive) {
                return try {
                    element.asString
                } catch (_: Exception) {
                    fallback
                }
            }
            return fallback
        }

        @JvmStatic
        private fun getDefaultMinSegmentMb(): Int {
            return toMb(DlcPathConfig.getMinSegmentBytes())
        }

        @JvmStatic
        private fun getDefaultConnectTimeoutSeconds(): Int {
            return maxOf(1, DlcPathConfig.getConnectTimeoutMs() / 1000)
        }

        @JvmStatic
        private fun getDefaultTaskTimeoutSeconds(): Int {
            return maxOf(1, DlcPathConfig.getTaskTimeoutMs() / 1000)
        }

        @JvmStatic
        private fun toMb(bytes: Long): Int {
            val mb = bytes / (1024L * 1024L)
            return if (mb > Int.MAX_VALUE) Int.MAX_VALUE else maxOf(1, mb.toInt())
        }
    }
}
