package com.cozooo.dlc_manager.client.screen;
import com.cozooo.dlc_manager.client.config.DlcConfigValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.cozooo.dlc_manager.client.config.DlcServerConfigClient;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import java.util.*;
import java.util.concurrent.CompletableFuture;
public class DlcServerConfigScreen extends DlcConfigScreen {
    public enum Tab { SERVER, PATHS, UI, MODPACK, DOWNLOADS }
    private final DlcServerEntry entry;
    private boolean loaded;
    private String error;
    public DlcServerConfigScreen(Screen parent, DlcServerEntry entry) { this(parent, entry, Tab.SERVER); }
    public DlcServerConfigScreen(Screen parent, DlcServerEntry entry, Tab tab) {
        super(parent, Component.translatable("dlc_manager.server.config_title"), categories(), key(tab)); this.entry = entry;
        load();
    }
    private static String key(Tab tab) { return "dlc_manager.config.tab." + (tab == Tab.DOWNLOADS ? "download" : tab == null ? "server" : tab.name().toLowerCase(Locale.ROOT)); }
    private static Map<String, List<DlcConfigValue>> categories() {
        Map<String, List<DlcConfigValue>> result = new LinkedHashMap<>();
        result.put(key(Tab.SERVER), DlcServerConfigClient.getServerConfigs()); result.put(key(Tab.PATHS), DlcServerConfigClient.getPathConfigs());
        result.put(key(Tab.UI), DlcServerConfigClient.getUiConfigs()); result.put(key(Tab.MODPACK), DlcServerConfigClient.getModpackConfigs()); result.put(key(Tab.DOWNLOADS), DlcServerConfigClient.getDownloadConfigs()); return result;
    }
    private void load() {
        busy = true; error = null;
        CompletableFuture.supplyAsync(() -> { try { return DlcServerConnection.fetchServerConfig(entry, 5000); } catch (Exception ignored) { return null; } })
            .whenComplete((json, failure) -> Minecraft.getInstance().execute(() -> {
                busy = false;
                if (json == null) error = tr("dlc_manager.server.connect_failed");
                else { DlcServerConfigClient.loadFromJson(json); captureValues(); loaded = true; }
                refreshPage();
            }));
    }
    @Override protected void buildPage() {
        if (loaded) { super.buildPage(); return; }
        text(content, error == null ? tr("dlc_manager.server.connecting") : error, "empty-state");
        if (!busy) primary(tr("dlc_manager.server.rcon_refresh"), () -> { load(); refreshPage(); });

    }
    @Override protected void updatePage() {
        if (loaded) super.updatePage();
        if (error != null) status(error);
    }
    @Override protected void persist() {
        busy = true; error = null;
        status(tr("dlc_manager.ui.saving"));
        var json = DlcServerConfigClient.toJson();
        CompletableFuture.supplyAsync(() -> { try { return DlcServerConnection.saveServerConfig(entry, 5000, json); } catch (Exception ignored) { return false; } })
            .whenComplete((success, failure) -> Minecraft.getInstance().execute(() -> {
                busy = false;
                if (Boolean.TRUE.equals(success)) Minecraft.getInstance().setScreen(pageParent);
                else { error = tr("dlc_manager.server.config_save_failed"); status(error); }
            }));
    }
}
