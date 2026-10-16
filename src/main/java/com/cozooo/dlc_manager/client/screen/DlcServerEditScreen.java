package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.core.DlcConfig;
import java.util.function.Consumer;
public class DlcServerEditScreen extends DlcPageScreen {
    private final DlcServerEntry editing;
    private final Consumer<DlcServerEntry> onSave;
    private String name;
    private String host;
    private String port;
    private String password;
    private DlcUiButton saveButton;
    public DlcServerEditScreen(Screen parent, DlcServerEntry editing, Consumer<DlcServerEntry> onSave) {
        super(DlcUiPage.SERVER_EDIT, parent, DlcTextCompat.translatable("dlc_manager.server.edit_title")); this.editing = editing; this.onSave = onSave;
        name = editing == null || editing.name == null ? "" : editing.name;
        host = editing == null || editing.host == null ? "" : editing.host;
        port = String.valueOf(editing == null || editing.port <= 0 ? DlcConfig.getServerPort() : editing.port);
        password = editing == null || editing.password == null ? "" : editing.password;
    }
    @Override protected void buildPage() {
        var form = node(content, "div", "page-dialog");
        input(form, tr("dlc_manager.server.name"), name, "text", value -> name = value);
        input(form, tr("dlc_manager.server.host"), host, "text", value -> { host = value; updatePage(); });
        input(form, tr("dlc_manager.server.port"), port, "text", value -> { port = value; updatePage(); });
        input(form, tr("dlc_manager.server.password"), password, "password", value -> password = value);
        action(footer, tr("gui.cancel"), this::onClose);
        saveButton = primary(tr("dlc_manager.server.save"), this::save);
        updatePage();
    }
    private int portNumber() { try { return Integer.parseInt(port.trim()); } catch (NumberFormatException ignored) { return -1; } }
    @Override protected void updatePage() {
        if (saveButton == null) return;
        boolean valid = portNumber() > 0 && portNumber() <= 65535;
        saveButton.setEnabled(!host.isBlank() && valid);
        status(valid ? "" : tr("dlc_manager.ui.invalid_port"));
    }
    private void save() {
        if (host.isBlank() || portNumber() <= 0 || portNumber() > 65535) return;
        DlcServerEntry entry = editing == null ? new DlcServerEntry() : editing;
        entry.name = name.trim(); entry.host = host.trim(); entry.port = portNumber(); entry.password = password.trim(); entry.useTcp = true;
        if (onSave != null) onSave.accept(entry);
        onClose();
    }
}
