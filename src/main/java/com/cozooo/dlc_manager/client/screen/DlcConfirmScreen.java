package com.cozooo.dlc_manager.client.screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
public class DlcConfirmScreen extends DlcPageScreen {
    private final BooleanConsumer callback;
    private final Component message;
    private final Component yes;
    private final Component no;
    private boolean answered;
    public DlcConfirmScreen(BooleanConsumer callback, Component title, Component message) {
        this(callback, title, message, Component.translatable("gui.yes"), Component.translatable("gui.no"));
    }
    public DlcConfirmScreen(BooleanConsumer callback, Component title, Component message, Component yes, Component no) {
        super(Minecraft.getInstance().screen, title);
        this.callback = callback; this.message = message; this.yes = yes; this.no = no;
    }
    @Override protected void buildPage() {
        rich(node(content, "div", "page-dialog"), message);
        action(footer, no.getString(), this::onClose);
        primary(yes.getString(), () -> answer(true));
    }
    private void answer(boolean value) {
        if (answered) return;
        answered = true;
        callback.accept(value);
    }
    @Override public void onClose() { answer(false); }
}
