package com.cozooo.dlc_manager.dc;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public final class DCBasicSyntaxInspection extends LocalInspectionTool {
    private static final Pattern SECTION_LINE = Pattern.compile("^\\[[^\\]]+\\]$");
    private static final Pattern QUOTED_LIST_ITEM = Pattern.compile("^\"[^\"]*\"\\s*,?$");
    private static final Pattern IDENTIFIER_LIST_ITEM = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]*\\s*,?$");

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!(file instanceof DCPsiFile)) {
                    return;
                }
                String text = file.getText();
                if (text == null || text.isEmpty()) {
                    return;
                }
                String[] lines = text.split("\\R", -1);
                int offset = 0;
                boolean tripleOpen = false;
                for (String rawLine : lines) {
                    String line = rawLine == null ? "" : rawLine;
                    String trimmed = line.trim();
                    int lineLength = line.length();

                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                        offset += lineLength + 1;
                        continue;
                    }

                    int tripleCount = countOccurrences(line, "\"\"\"");
                    boolean wasTripleOpen = tripleOpen;
                    if (tripleCount > 0 && (tripleCount % 2 == 1)) {
                        tripleOpen = !tripleOpen;
                    }

                    // Match VSCode behavior: lines inside or containing triple-quote delimiters
                    // are not validated as normal key/value statements.
                    if (wasTripleOpen || tripleCount > 0) {
                        offset += lineLength + 1;
                        continue;
                    }

                    if (trimmed.startsWith("[") && !trimmed.endsWith("]")) {
                        holder.registerProblem(
                            file,
                            "Malformed section header. Expected closing ']'.",
                            ProblemHighlightType.GENERIC_ERROR,
                            TextRange.create(offset, offset + lineLength)
                        );
                        offset += lineLength + 1;
                        continue;
                    }

                    if (SECTION_LINE.matcher(trimmed).matches()) {
                        offset += lineLength + 1;
                        continue;
                    }

                    if (looksLikeListItem(trimmed)) {
                        offset += lineLength + 1;
                        continue;
                    }

                    if (trimmed.contains("=") || trimmed.contains(":")) {
                        offset += lineLength + 1;
                        continue;
                    }

                    holder.registerProblem(
                        file,
                        "Line does not look like a valid key/value statement.",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        TextRange.create(offset, offset + lineLength)
                    );

                    offset += lineLength + 1;
                }
                if (tripleOpen) {
                    int start = Math.max(text.length() - 1, 0);
                    holder.registerProblem(
                        file,
                        "Unclosed triple-quoted string.",
                        ProblemHighlightType.GENERIC_ERROR,
                        TextRange.create(start, text.length())
                    );
                }
            }
        };
    }

    private static boolean looksLikeListItem(String trimmed) {
        if (QUOTED_LIST_ITEM.matcher(trimmed).matches()) {
            return true;
        }
        return IDENTIFIER_LIST_ITEM.matcher(trimmed).matches();
    }

    private static int countOccurrences(String text, String token) {
        if (text == null || text.isEmpty() || token == null || token.isEmpty()) {
            return 0;
        }
        int idx = 0;
        int count = 0;
        while (idx < text.length()) {
            int found = text.indexOf(token, idx);
            if (found < 0) {
                break;
            }
            count++;
            idx = found + token.length();
        }
        return count;
    }
}
