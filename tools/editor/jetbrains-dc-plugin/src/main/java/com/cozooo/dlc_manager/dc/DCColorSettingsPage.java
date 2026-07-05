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
        return """
            # DC demo
            [download]
            modrinth_projectid = "XxWD5pD3"
            modrinth_versionid = "7KVs6HMQ"
            priority = "modrinth"

            [basic]
            identifier = "AE2"
            file_name = "appliedenergistics2-forge-15.4.10.jar"
            type = "mod"
            isHidden = false
            alarm = false
            retries = 3

            [DisplayName]
            en_us = "Applied Energistics 2"
            zh_cn = "应用能源2"
            """;
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
