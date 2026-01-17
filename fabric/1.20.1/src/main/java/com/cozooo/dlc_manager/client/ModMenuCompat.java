package com.cozooo.dlc_manager.client;

import com.cozooo.dlc_manager.client.screen.DlcPathConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DlcPathConfigScreen::new;
    }
}
