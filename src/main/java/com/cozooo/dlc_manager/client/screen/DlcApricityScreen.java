package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public abstract class DlcApricityScreen extends ApricityScreen {
    private Document boundDocument;
    private long boundGeneration = -1;
    private Selector.Index boundStyleIndex;

    protected DlcApricityScreen(String path) {
        super(path);
        setPauseGame(true);
    }

    @Override
    protected void init() {
        super.init();
        bindCurrentDocument();
    }

    @Override
    public void tick() {
        super.tick();
        bindCurrentDocument();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Document document = getLinkedDocument();
        if (document != null && document.isActive() && document.documentElement != null) {
            Selector.Index index = document.getSelectorIndex();
            if (index != boundStyleIndex) {
                boundStyleIndex = index;
                Document.runWithContext(document, () -> document.documentElement.invalidateStyle());
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void bindCurrentDocument() {
        Document document = getLinkedDocument();
        if (document == null || !document.isActive() || (document == boundDocument && document.getRefreshGeneration() == boundGeneration)) {
            return;
        }
        if (document != boundDocument) {
            document.addEventListener("DOMContentLoaded", event -> Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().screen == this && document == getLinkedDocument()) {
                    boundGeneration = -1;
                }
            }));
        }
        document.setManuallyRendered(false);
        boundDocument = document;
        boundGeneration = document.getRefreshGeneration();
        Document.runWithContext(document, () -> bindDocument(document));
    }

    protected abstract void bindDocument(Document document);

    protected DlcUiButton button(Document document, Element container, String label, Runnable action) {
        long generation = document.getRefreshGeneration();
        return new DlcUiButton(document, container, label, () -> {
            if (Minecraft.getInstance().screen == this && document == getLinkedDocument()
                && document.isCurrentGeneration(generation)) {
                Document.runWithContext(document, action);
            }
        });
    }
}
