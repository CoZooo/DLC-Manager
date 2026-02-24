package com.cozooo.dlc_manager.dc;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class DCCompletionContributor extends CompletionContributor {
    private static final Map<String, List<String>> SECTION_KEYS = createSectionKeys();
    private static final List<String> SECTIONS = new ArrayList<>(SECTION_KEYS.keySet());

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
        String prefix = document.getText(new TextRange(lineStart, offset));

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
}
