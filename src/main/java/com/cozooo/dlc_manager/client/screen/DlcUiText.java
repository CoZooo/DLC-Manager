package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DlcUiText {
    record Run(String text, String css) {
        public String getText() { return text; }
        public String getCss() { return css; }
    }

    private DlcUiText() {}

    static List<Run> runs(String value) {
        List<Run> runs = new ArrayList<>();
        for (var part : DlcTextFormatter.parse(value).getSiblings()) {
            Style style = part.getStyle();
            StringBuilder css = new StringBuilder();
            if (style.getColor() != null) css.append("color:").append(String.format(Locale.ROOT, "#%06x", style.getColor().getValue())).append(';');
            if (style.isBold()) css.append("font-weight:700;");
            if (style.isItalic()) css.append("font-style:italic;");
            if (style.isUnderlined() || style.isStrikethrough()) {
                css.append("text-decoration:");
                if (style.isUnderlined()) css.append("underline ");
                if (style.isStrikethrough()) css.append("line-through");
                css.append(';');
            }
            runs.add(new Run(part.getString(), css.toString()));
        }
        return runs;
    }

    static void set(Element element, String value) {
        List<Run> runs = runs(value);
        if (runs.size() == 1 && runs.get(0).css().isEmpty()) {
            element.setTextContent(runs.get(0).text());
            return;
        }
        element.setTextContent("");
        for (Run run : runs) {
            Element span = element.document.createElement("span");
            span.setAttribute("style", "display:inline;pointer-events:none;" + run.css());
            span.setTextContent(run.text());
            element.appendChild(span);
        }
    }
}
