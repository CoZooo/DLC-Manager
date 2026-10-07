package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.render.Drawer;
import java.util.Objects;

public class DlcUiTextInput extends Input {
    public DlcUiTextInput(Document document) {
        super(document);
    }

    @Override public boolean deleteBackward() {
        if (!canEditText() || !super.deleteBackward()) return false;
        dispatchInputEvent("deleteContentBackward", null);
        return true;
    }

    @Override public boolean deleteForward() {
        if (!canEditText() || !super.deleteForward()) return false;
        dispatchInputEvent("deleteContentForward", null);
        return true;
    }

    @Override public void replaceSelection(String text) {
        String previous = getValue();
        super.replaceSelection(text);
        if ((text == null || text.replace("\r", "").replace("\n", "").isEmpty()) && !Objects.equals(previous, getValue()))
            dispatchInputEvent("deleteContentBackward", null);
    }

    @Override protected void dispatchInputEvent(String inputType, String data) {
        refreshValue();
        super.dispatchInputEvent(inputType, data);
    }

    @Override public void setValue(String value) {
        super.setValue(value);
        refreshValue();
    }

    @Override public void tick() {
        refreshValue();
        super.tick();
    }

    private void refreshValue() {
        if (value != null && !Objects.equals(cachedValue, value)) {
            cachedValue = value;
            getRenderer().text.clear();
            getRenderer().wrappedText.clear();
            if (document != null) document.markDirty(this, Drawer.REPAINT);
        }
    }

    @Override protected String getRenderText() {
        String value = super.getRenderText();
        Text text = Text.of(this);
        text.fontFamily = "unset";
        text.lineHeight = text.renderedFontSize();
        text.content = value;
        return value;
    }

    public static DlcUiTextInput adapt(Element element) {
        if (element instanceof DlcUiTextInput input) return input;
        String value = element.getValue();
        DlcUiTextInput input = new DlcUiTextInput(element.document);
        element.getAttributes().forEach(input::setAttribute);
        element.parentElement.replaceChild(input, element);
        input.setValue(value);
        return input;
    }
}
