package com.cozooo.dlc_manager.dc;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ui.ColorChooser;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public final class DCCompletionContributor extends CompletionContributor {
    private static final Map<String, List<String>> SECTION_KEYS = createSectionKeys();
    private static final List<String> SECTIONS = new ArrayList<>(SECTION_KEYS.keySet());
    private static final Pattern COLOR_CONTEXT_PATTERN = Pattern.compile("(&|&#[0-9a-fA-F]{0,6})$");
    private static final List<ColorPreset> COLOR_PRESETS = List.of(
        new ColorPreset("White", "FFFFFF"),
        new ColorPreset("Black", "000000"),
        new ColorPreset("Red", "FF5555"),
        new ColorPreset("Orange", "FFAA00"),
        new ColorPreset("Yellow", "FFFF55"),
        new ColorPreset("Green", "55FF55"),
        new ColorPreset("Aqua", "55FFFF"),
        new ColorPreset("Blue", "5555FF"),
        new ColorPreset("Purple", "AA00AA"),
        new ColorPreset("Gray", "AAAAAA")
    );

    public DCCompletionContributor() {
        extend(
            com.intellij.codeInsight.completion.CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(DCLanguage.INSTANCE),
            new CompletionProvider<>() {
                @Override
                protected void addCompletions(
                    @NotNull CompletionParameters parameters,
                    @NotNull ProcessingContext context,
                    @NotNull CompletionResultSet result
                ) {
                    provide(parameters, result);
                }
            }
        );
    }

    private void provide(CompletionParameters parameters, CompletionResultSet result) {
        if (parameters.getEditor().getProject() == null) {
            return;
        }
        var document = parameters.getEditor().getDocument();
        int offset = parameters.getOffset();
        int line = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(line);
        int lineEnd = document.getLineEndOffset(line);
        String prefix = document.getText(new TextRange(lineStart, offset));
        String lineText = document.getText(new TextRange(lineStart, lineEnd));
        ColorContext colorContext = detectColorContext(prefix, lineStart, offset, lineText);

        if (colorContext != null) {
            provideColorCompletions(parameters, result, colorContext);
            return;
        }

        if (prefix.trim().isEmpty() || prefix.trim().startsWith("[")) {
            int bracketInLine = prefix.lastIndexOf('[');
            for (String section : SECTIONS) {
                if (prefix.trim().startsWith("[") && bracketInLine >= 0) {
                    final int replaceStart = lineStart + bracketInLine;
                    result.addElement(
                        LookupElementBuilder.create(section + "]")
                            .withInsertHandler((context, item) -> {
                                context.getDocument().replaceString(
                                    replaceStart,
                                    context.getTailOffset(),
                                    "[" + section + "]"
                                );
                                context.getEditor().getCaretModel().moveToOffset(replaceStart + section.length() + 2);
                            })
                    );
                } else {
                    result.addElement(LookupElementBuilder.create("[" + section + "]"));
                }
            }
            return;
        }

        String section = currentSection(document.getText(), line);
        List<String> keys = SECTION_KEYS.getOrDefault(section, List.of());
        if (!prefix.contains("=") && !prefix.contains(":")) {
            for (String key : keys) {
                result.addElement(LookupElementBuilder.create(key + " = "));
            }
        }
    }

    private void provideColorCompletions(
        CompletionParameters parameters,
        CompletionResultSet result,
        ColorContext colorContext
    ) {
        CompletionResultSet colorResult = result.withPrefixMatcher(colorContext.tokenText);
        LookupElement picker = createColorPickerElement(colorContext);
        colorResult.addElement(PrioritizedLookupElement.withPriority(picker, 1000));

        for (ColorPreset preset : COLOR_PRESETS) {
            if (!colorContext.typedHex.isEmpty() && !preset.hex.startsWith(colorContext.typedHex)) {
                continue;
            }
            String token = "&#" + preset.hex;
            LookupElement presetItem = createPresetColorElement(colorContext, preset.name, token);
            colorResult.addElement(PrioritizedLookupElement.withPriority(presetItem, 100));
        }
    }

    private LookupElement createColorPickerElement(ColorContext colorContext) {
        return new LookupElement() {
            @Override
            public @NotNull String getLookupString() {
                return colorContext.tokenText;
            }

            @Override
            public void renderElement(@NotNull LookupElementPresentation presentation) {
                presentation.setItemText("Pick custom color...");
                presentation.setTypeText("Color Picker");
            }

            @Override
            public void handleInsert(@NotNull InsertionContext context) {
                context.setAddCompletionChar(false);
                // IntelliJ may perform a temporary replacement before showing the picker.
                // Restore the original token immediately to avoid visible "text eaten" flicker.
                replaceColorToken(context, colorContext, colorContext.tokenText);
                Color initial = colorFromToken(colorContext.tokenText);
                Color chosen = ColorChooser.chooseColor(
                    context.getEditor().getComponent(),
                    "Pick Color",
                    initial,
                    false
                );
                String token = chosen == null ? colorContext.tokenText : toHexToken(chosen);
                replaceColorToken(context, colorContext, token);
            }
        };
    }

    private LookupElement createPresetColorElement(ColorContext colorContext, String name, String token) {
        return new LookupElement() {
            @Override
            public @NotNull String getLookupString() {
                return token;
            }

            @Override
            public void renderElement(@NotNull LookupElementPresentation presentation) {
                presentation.setItemText(name + " (" + token + ")");
                presentation.setTypeText("DC hex color");
            }

            @Override
            public void handleInsert(@NotNull InsertionContext context) {
                context.setAddCompletionChar(false);
                replaceColorToken(context, colorContext, token);
            }
        };
    }

    private void replaceColorToken(InsertionContext context, ColorContext colorContext, String replacement) {
        int docLength = context.getDocument().getTextLength();
        if (docLength <= 0) {
            return;
        }

        int anchor = Math.max(0, Math.min(colorContext.lineStartOffset, docLength - 1));
        int line = context.getDocument().getLineNumber(anchor);
        int currentLineStart = context.getDocument().getLineStartOffset(line);
        int currentLineEnd = context.getDocument().getLineEndOffset(line);

        if (currentLineStart == colorContext.lineStartOffset) {
            String rewrittenLine = colorContext.lineTextBeforeInsert.substring(0, colorContext.tokenStartInLine)
                + replacement
                + colorContext.lineTextBeforeInsert.substring(colorContext.tokenEndInLine);
            context.getDocument().replaceString(currentLineStart, currentLineEnd, rewrittenLine);
            context.getEditor().getCaretModel().moveToOffset(currentLineStart + colorContext.tokenStartInLine + replacement.length());
            return;
        }

        int start = Math.max(0, Math.min(colorContext.tokenStartOffset, docLength));
        int end = Math.max(start, Math.min(colorContext.tokenEndOffset, docLength));
        context.getDocument().replaceString(start, end, replacement);
        context.getEditor().getCaretModel().moveToOffset(start + replacement.length());
    }

    private ColorContext detectColorContext(String prefix, int lineStart, int offset, String lineText) {
        Matcher matcher = COLOR_CONTEXT_PATTERN.matcher(prefix);
        if (!matcher.find()) {
            return null;
        }
        String token = matcher.group(1);
        int tokenStart = offset - token.length();
        if (tokenStart < lineStart) {
            return null;
        }
        String typedHex = token.startsWith("&#") ? token.substring(2).toUpperCase() : "";
        int tokenStartInLine = tokenStart - lineStart;
        int tokenEndInLine = offset - lineStart;
        if (tokenStartInLine < 0 || tokenEndInLine < tokenStartInLine || tokenEndInLine > lineText.length()) {
            return null;
        }
        return new ColorContext(
            tokenStart,
            offset,
            token,
            typedHex,
            lineStart,
            lineText,
            tokenStartInLine,
            tokenEndInLine
        );
    }

    private Color colorFromToken(String token) {
        if (token == null || token.length() <= 2 || !token.startsWith("&#")) {
            return Color.WHITE;
        }
        String hex = token.substring(2);
        if (hex.length() < 6) {
            hex = (hex + "FFFFFF").substring(0, 6);
        }
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            return Color.WHITE;
        }
        return new Color(Integer.parseInt(hex, 16));
    }

    private String toHexToken(Color color) {
        return String.format("&#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static final class ColorPreset {
        private final String name;
        private final String hex;

        private ColorPreset(String name, String hex) {
            this.name = name;
            this.hex = hex;
        }
    }

    private static final class ColorContext {
        private final int tokenStartOffset;
        private final int tokenEndOffset;
        private final String tokenText;
        private final String typedHex;
        private final int lineStartOffset;
        private final String lineTextBeforeInsert;
        private final int tokenStartInLine;
        private final int tokenEndInLine;

        private ColorContext(
            int tokenStartOffset,
            int tokenEndOffset,
            String tokenText,
            String typedHex,
            int lineStartOffset,
            String lineTextBeforeInsert,
            int tokenStartInLine,
            int tokenEndInLine
        ) {
            this.tokenStartOffset = tokenStartOffset;
            this.tokenEndOffset = tokenEndOffset;
            this.tokenText = tokenText;
            this.typedHex = typedHex;
            this.lineStartOffset = lineStartOffset;
            this.lineTextBeforeInsert = lineTextBeforeInsert;
            this.tokenStartInLine = tokenStartInLine;
            this.tokenEndInLine = tokenEndInLine;
        }
    }

    private String currentSection(String fullText, int lineNumber) {
        String[] lines = fullText.split("\\R", -1);
        int idx = Math.min(lineNumber, lines.length - 1);
        for (int i = idx; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("[") && line.endsWith("]") && line.length() > 2) {
                return line.substring(1, line.length() - 1);
            }
        }
        return "basic";
    }

    private static Map<String, List<String>> createSectionKeys() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("download", List.of(
            "modrinth_projectid", "modrinth_versionid", "curseforge_projectid",
            "curseforge_fileid", "directUrl", "priority"
        ));
        map.put("basic", List.of(
            "identifier", "file_name", "type", "appliedTarget", "isInList",
            "isHidden", "AllowEnableByAll", "dependencies", "conflicts", "alarm",
            "available", "ShowFrequency", "neverShow", "isNoticeEnabled", "enabled",
            "platform", "base", "base_new", "dlc", "dlc_new"
        ));
        map.put("dependencies", List.of());
        map.put("DisplayName", List.of());
        map.put("tip", List.of());
        map.put("notice", List.of());
        map.put("version", List.of("platform", "base", "base_new", "dlc", "dlc_new"));
        map.put("changelog", List.of());
        map.put("file", List.of());
        map.put("add", List.of());
        map.put("del", List.of());
        map.put("move", List.of());
        map.put("modify", List.of());
        map.put("update", List.of());
        map.put("remote", List.of());
        map.put("required", List.of());
        return map;
    }

    static Map<String, List<String>> sectionKeys() {
        return SECTION_KEYS;
    }
}
