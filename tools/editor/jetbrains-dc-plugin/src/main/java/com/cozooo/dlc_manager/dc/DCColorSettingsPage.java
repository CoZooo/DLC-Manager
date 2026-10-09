package com.cozooo.dlc_manager.dc;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DCColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[] {
        new AttributesDescriptor("Section", DCSyntaxHighlighter.SECTION),
        new AttributesDescriptor("Key", DCSyntaxHighlighter.KEY),
        new AttributesDescriptor("String", DCSyntaxHighlighter.STRING),
        new AttributesDescriptor("Number", DCSyntaxHighlighter.NUMBER),
        new AttributesDescriptor("Boolean", DCSyntaxHighlighter.BOOLEAN),
        new AttributesDescriptor("Comment", DCSyntaxHighlighter.COMMENT),
        new AttributesDescriptor("Operator", DCSyntaxHighlighter.OPERATOR),
        new AttributesDescriptor("Punctuation", DCSyntaxHighlighter.PUNCT),
        new AttributesDescriptor("Bad character", DCSyntaxHighlighter.BAD)
    };

    @Override
    public @Nullable Icon getIcon() {
        return DCFileType.INSTANCE.getIcon();
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new DCSyntaxHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        var syntax = DCSyntaxDefinition.get();
        String open = syntax.token("categoryOpen"), close = syntax.token("categoryClose"), assignment = syntax.token("assignment");
        String quote = syntax.strings.stream().filter(rule -> !rule.multiline()).findFirst().orElseThrow().delimiter();
        return syntax.token("comment") + " DC syntax demo\n"
            + open + "example" + close + "\n"
            + "title " + assignment + " " + quote + "A document" + quote + "\n"
            + "enabled " + assignment + " " + syntax.literal("true") + "\n"
            + "retries " + assignment + " 3\n\n"
            + open + "example" + syntax.token("categorySeparator") + "labels" + close + "\n"
            + "en_us " + assignment + " " + quote + "English" + quote + "\n"
            + "zh_cn " + assignment + " " + quote + "中文" + quote + "\n";
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "DC";
    }
}
