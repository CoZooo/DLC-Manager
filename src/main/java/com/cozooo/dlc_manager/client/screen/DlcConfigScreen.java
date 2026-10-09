package com.cozooo.dlc_manager.client.screen;
import com.cozooo.dlc_manager.client.config.DlcConfigValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.cozooo.dlc_manager.client.config.DlcConfigClient;
import com.cozooo.dlc_manager.core.DlcConfig;
import java.util.*;
public class DlcConfigScreen extends DlcSettingsScreen {
    public enum Tab { SERVER, PATHS, UI, MODPACK, DOWNLOADS }
    public DlcConfigScreen(Screen parent) { this(parent, Tab.PATHS); }
    public DlcConfigScreen(Screen parent, Tab tab) { this(DlcUiPage.LOCAL_CONFIG, parent, Component.translatable("dlc_manager.config.menu.title"), categories(), key(tab)); }
    protected DlcConfigScreen(Screen parent, Component title, Map<String, List<DlcConfigValue>> values, String category) { this(DlcUiPage.LOCAL_CONFIG, parent, title, values, category); }
    protected DlcConfigScreen(DlcUiPage page, Screen parent, Component title, Map<String, List<DlcConfigValue>> values, String category) { super(page, parent, title, values, category); }
    private static String key(Tab tab) { return "dlc_manager.config.tab." + (tab == Tab.DOWNLOADS ? "download" : tab == null ? "paths" : tab.name().toLowerCase(Locale.ROOT)); }
    private static Map<String, List<DlcConfigValue>> categories() {
        DlcConfigClient.syncFromConfig();
        Map<String, List<DlcConfigValue>> result = new LinkedHashMap<>();
        result.put(key(Tab.PATHS), DlcConfigClient.getPathConfigs()); result.put(key(Tab.SERVER), DlcConfigClient.getServerConfigs());
        result.put(key(Tab.UI), DlcConfigClient.getUiConfigs()); result.put(key(Tab.MODPACK), DlcConfigClient.getModpackConfigs()); result.put(key(Tab.DOWNLOADS), DlcConfigClient.getDownloadConfigs());
        return result;
    }
    @Override protected void persist() { DlcConfigClient.applyToConfig(); DlcConfig.save(); Minecraft.getInstance().setScreen(pageParent); }
}
