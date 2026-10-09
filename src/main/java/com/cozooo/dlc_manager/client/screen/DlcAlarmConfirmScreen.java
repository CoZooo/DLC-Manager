package com.cozooo.dlc_manager.client.screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.cozooo.dlc_manager.core.DlcConfig;
public class DlcAlarmConfirmScreen extends DlcPageScreen {
    private final String tip;
    private final Runnable onConfirm;
    private boolean acknowledged;
    private long waitEndMillis;
    private DlcUiButton confirmButton;
    private DlcUiButton acknowledgeButton;
    public DlcAlarmConfirmScreen(Screen parent, String tip, Runnable onConfirm) {
        super(DlcUiPage.ALARM_CONFIRM, parent, Component.translatable("dlc_manager.alarm.title"));
        this.tip = tip == null ? "" : tip; this.onConfirm = onConfirm;
        waitEndMillis = System.currentTimeMillis() + Math.max(0, Math.min(10, DlcConfig.getAlarmWaitingTime())) * 1000L;
    }
    @Override protected void buildPage() {
        var body = node(content, "div", "page-dialog");
        text(body, tip, "page-message warning");
        acknowledgeButton = toggle(body, tr("dlc_manager.alarm.read_ack"), acknowledged, value -> { acknowledged = value; refreshPage(); });
        action(footer, tr("dlc_manager.alarm.cancel"), this::onClose);
        confirmButton = primary(tr("dlc_manager.alarm.confirm"), () -> {
            if (!acknowledged || remaining() > 0) return;
            onClose();
            if (onConfirm != null) Minecraft.getInstance().execute(onConfirm);
        });
        updatePage();
    }
    private int remaining() { return (int) Math.max(0, Math.ceil((waitEndMillis - System.currentTimeMillis()) / 1000.0)); }
    @Override protected void updatePage() {
        if (confirmButton == null) return;
        confirmButton.setEnabled(acknowledged && remaining() == 0);
        acknowledgeButton.setEnabled(remaining() == 0);
        status(remaining() > 0 ? tr("dlc_manager.alarm.remaining", remaining()) : "");
    }
}
