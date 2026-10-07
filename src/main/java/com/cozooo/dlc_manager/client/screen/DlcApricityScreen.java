package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public abstract class DlcApricityScreen extends ApricityScreen {
    private Document boundDocument;
    private long boundGeneration = -1;
    private Selector.Index boundStyleIndex;
    private final java.util.Map<Element, DlcUiInputBinding> inputBindings = new java.util.LinkedHashMap<>();
    private boolean syncingInputs;
    private final java.util.Set<Element> pendingInputs = new java.util.LinkedHashSet<>();
    private static final java.util.concurrent.ExecutorService SEARCH_WORKER = java.util.concurrent.Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "dlc-ui-search"); thread.setDaemon(true); return thread;
    });
    private final DlcUiFrameTasks searchApplications = new DlcUiFrameTasks();
    private final DlcUiAsyncSearch<String> asyncSearch = new DlcUiAsyncSearch<>(SEARCH_WORKER, searchApplications);

    protected DlcApricityScreen(String path) {
        super(path);
        setPauseGame(true);
    }

    @Override
    protected void init() {
        super.init();
        updateViewport();
        bindCurrentDocument();
    }

    @Override
    public void tick() {
        super.tick();
        updateViewport();
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
        syncInputs(document);
        searchApplications.runPending();
    }

    private void updateViewport() {
        Document document = getLinkedDocument();
        if (document != null && document.isActive()) document.setViewportZoom(1120.0 / Math.max(1, Minecraft.getInstance().getWindow().getScreenWidth()));
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        updateViewport();
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
        inputBindings.clear();
        asyncSearch.cancel();
        searchApplications.clear();
        pendingInputs.clear();
        Document.runWithContext(document, () -> bindDocument(document));
    }

    protected abstract void bindDocument(Document document);

    protected final void filterAsync(String query, java.util.List<DlcUiAsyncSearch.Entry<String>> entries, java.util.function.Consumer<java.util.Set<String>> apply) {
        Document document = getLinkedDocument();
        if (document == null) return;
        long generation = document.getRefreshGeneration();
        asyncSearch.request(query, entries, matches -> {
            if (Minecraft.getInstance().screen == this && getLinkedDocument() == document && document.isCurrentGeneration(generation))
                Document.runWithContext(document, () -> apply.accept(matches));
        });
    }

    @Override public void removed() { asyncSearch.cancel(); searchApplications.clear(); super.removed(); }

    protected final DlcUiInputBinding bindInput(Document document, Element input, java.util.function.Consumer<String> change) {
        return bindInput(document, input, change, false);
    }

    protected final DlcUiInputBinding bindInput(Document document, Element input, java.util.function.Consumer<String> change, boolean deferred) {
        long generation = document.getRefreshGeneration();
        java.util.function.BooleanSupplier current = () -> Minecraft.getInstance().screen == this && document == getLinkedDocument()
            && document.isCurrentGeneration(generation) && input.isConnected();
        DlcUiInputBinding binding = new DlcUiInputBinding(input::getValue, current, change);
        Runnable synchronize = () -> {
            if (!current.getAsBoolean()) return;
            Document.runWithContext(document, () -> {
                if (deferred) pendingInputs.add(input);
                else binding.run();
            });
        };
        inputBindings.keySet().removeIf(field -> !field.isConnected());
        inputBindings.put(input, binding);
        input.addEventListener("input", event -> synchronize.run());
        input.addEventListener("change", event -> Document.runWithContext(document, binding));
        if (input instanceof AbstractText text) input.addEventListener("keydown", event -> {
            if (!(event instanceof KeyEvent key) || event.defaultPrevented || Minecraft.getInstance().screen != this
                || document != getLinkedDocument() || !document.isCurrentGeneration(generation)
                || !input.isConnected() || document.getFocusedElement() != input) return;
            Document.runWithContext(document, () -> {
                if (handleInputShortcut(text, key)) {
                    event.preventDefault();
                }
            });
        });
        return binding;
    }

    private boolean handleInputShortcut(AbstractText input, KeyEvent key) {
        if ((!key.controlKey && !key.metaKey) || key.altKey || input.isDisabled()) return false;
        var keyboard = Minecraft.getInstance().keyboardHandler;
        switch (key.keyCode) {
            case GLFW.GLFW_KEY_A -> { if (input.canSelectText()) input.selectAll(); }
            case GLFW.GLFW_KEY_C -> {
                if (input.canSelectText() && input.hasSelection()) keyboard.setClipboard(input.getSelectedText());
            }
            case GLFW.GLFW_KEY_X -> {
                if (input.canEditText() && input.hasSelection()) {
                    keyboard.setClipboard(input.getSelectedText());
                    input.replaceSelection("");
                }
            }
            case GLFW.GLFW_KEY_V -> { if (input.canEditText()) input.insertText(keyboard.getClipboard()); }
            case GLFW.GLFW_KEY_Z -> {
                if (key.shiftKey) return false;
                if (input.canEditText()) input.undo();
            }
            default -> { return false; }
        }
        return true;
    }

    private void syncInputs(Document document) {
        syncInputs(document, false);
    }

    private void syncInputs(Document document, boolean all) {
        if (syncingInputs || document == null || !document.isActive() || Minecraft.getInstance().screen != this) return;
        if (!all && pendingInputs.isEmpty()) return;
        syncingInputs = true;
        try {
            Document.runWithContext(document, () -> {
                if (all) {
                    pendingInputs.clear();
                    inputBindings.keySet().removeIf(input -> !input.isConnected());
                    for (DlcUiInputBinding binding : java.util.List.copyOf(inputBindings.values())) binding.run();
                } else {
                    if (!pendingInputs.isEmpty()) {
                        var pending = java.util.List.copyOf(pendingInputs);
                        pendingInputs.clear();
                        for (Element input : pending) {
                            DlcUiInputBinding binding = inputBindings.get(input);
                            if (binding != null) binding.run();
                        }
                    }
                }
            });
        } finally {
            syncingInputs = false;
        }
    }

    protected DlcUiButton button(Document document, Element container, String label, Runnable action) {
        long generation = document.getRefreshGeneration();
        return new DlcUiButton(document, container, label, () -> {
            if (Minecraft.getInstance().screen == this && document == getLinkedDocument()
                && document.isCurrentGeneration(generation)) {
                Document.runWithContext(document, () -> { syncInputs(document, true); action.run(); });
            }
        });
    }
}
