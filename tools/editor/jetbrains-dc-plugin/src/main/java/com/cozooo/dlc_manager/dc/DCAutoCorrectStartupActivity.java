package com.cozooo.dlc_manager.dc;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public final class DCAutoCorrectStartupActivity implements StartupActivity.DumbAware {
    private static final Key<Boolean> CORRECTING_KEY = Key.create("dc.correcting");

    private static final Pattern SINGLE_LINE_TRIPLE_RE = Pattern.compile(
        "^(\\s*(?:[A-Za-z_][A-Za-z0-9_.-]*|\"[^\"]+\")\\s*[=:]\\s*)\"\"\"([\\s\\S]*?)\"\"\"(\\s*,?\\s*(?:#.*|//.*)?)$"
    );
    private static final Pattern VALUE_OPEN_QUOTE_RE = Pattern.compile(
        "^(\\s*(?:[A-Za-z_][A-Za-z0-9_.-]*|\"[^\"]+\")\\s*[=:]\\s*)\""
    );

    @Override
    public void runActivity(@NotNull Project project) {
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                Document doc = event.getDocument();
                if (Boolean.TRUE.equals(doc.getUserData(CORRECTING_KEY))) {
                    return;
                }
                if (!isDcDocument(doc)) {
                    return;
                }
                scheduleNormalize(project, doc);
            }
        }, project);

        MessageBusConnection connection = project.getMessageBus().connect(project);
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if (!isDcFile(file)) {
                    return;
                }
                Document doc = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
                if (doc != null) {
                    scheduleNormalize(project, doc);
                }
            }
        });

        VirtualFile[] openFiles = ReadAction.compute(() -> FileEditorManager.getInstance(project).getOpenFiles());
        for (VirtualFile file : openFiles) {
            if (!isDcFile(file)) {
                continue;
            }
            Document doc = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
            if (doc != null) {
                scheduleNormalize(project, doc);
            }
        }
    }

    private static boolean isDcDocument(Document doc) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
        return isDcFile(file);
    }

    private static boolean isDcFile(VirtualFile file) {
        return file != null && "dc".equalsIgnoreCase(file.getExtension());
    }

    private static void scheduleNormalize(Project project, Document doc) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            if (!doc.isWritable()) {
                return;
            }
            applyFixes(project, doc);
        });
    }

    private static void applyFixes(Project project, Document doc) {
        List<Fix> quoteFixes = ReadAction.compute(() -> collectQuoteFixes(doc));
        if (!quoteFixes.isEmpty()) {
            applyFixList(project, doc, quoteFixes);
            return;
        }

        List<Fix> indentFixes = ReadAction.compute(() -> collectIndentationFixes(doc));
        if (indentFixes.isEmpty()) {
            return;
        }
        applyFixList(project, doc, indentFixes);
    }

    private static void applyFixList(Project project, Document doc, List<Fix> fixes) {
        if (fixes.isEmpty()) {
            return;
        }

        fixes.sort(Comparator.comparingInt(Fix::start).reversed());

        WriteCommandAction.runWriteCommandAction(project, () -> {
            doc.putUserData(CORRECTING_KEY, Boolean.TRUE);
            try {
                for (Fix fix : fixes) {
                    int start = fix.start();
                    int end = fix.end();
                    if (start < 0 || end < start || end > doc.getTextLength()) {
                        continue;
                    }
                    doc.replaceString(start, end, fix.replacement());
                }
            } finally {
                doc.putUserData(CORRECTING_KEY, null);
            }
        });
    }

    private static List<Fix> collectQuoteFixes(Document doc) {
        List<Fix> fixes = new ArrayList<>();
        PendingQuote pendingSingleQuoteOpen = null;

        for (int line = 0; line < doc.getLineCount(); line++) {
            int lineStart = doc.getLineStartOffset(line);
            int lineEnd = doc.getLineEndOffset(line);
            String text = doc.getText(new TextRange(lineStart, lineEnd));

            Matcher singleLineTriple = SINGLE_LINE_TRIPLE_RE.matcher(text);
            if (singleLineTriple.matches()) {
                String prefix = singleLineTriple.group(1);
                String content = singleLineTriple.group(2);
                String suffix = singleLineTriple.group(3) == null ? "" : singleLineTriple.group(3);
                String replacement = prefix + "\"" + escapeForDoubleQuoted(content) + "\"" + suffix;
                fixes.add(new Fix(lineStart, lineEnd, replacement));
            }

            if (text.contains("\"\"\"")) {
                pendingSingleQuoteOpen = null;
                continue;
            }

            if (pendingSingleQuoteOpen != null) {
                int closeCol = findUnescapedQuote(text, 0);
                if (closeCol >= 0) {
                    int openOffset = doc.getLineStartOffset(pendingSingleQuoteOpen.line()) + pendingSingleQuoteOpen.column();
                    int closeOffset = lineStart + closeCol;
                    fixes.add(new Fix(openOffset, openOffset + 1, "\"\"\""));
                    fixes.add(new Fix(closeOffset, closeOffset + 1, "\"\"\""));
                    pendingSingleQuoteOpen = null;
                }
                continue;
            }

            int openCol = findValueOpeningQuote(text);
            if (openCol < 0) {
                continue;
            }
            int closeSameLine = findUnescapedQuote(text, openCol + 1);
            if (closeSameLine < 0) {
                pendingSingleQuoteOpen = new PendingQuote(line, openCol);
            }
        }

        if (fixes.isEmpty()) {
            return fixes;
        }

        Set<String> seen = new HashSet<>();
        List<Fix> deduped = new ArrayList<>(fixes.size());
        for (Fix fix : fixes) {
            String key = fix.start() + ":" + fix.end() + ":" + fix.replacement();
            if (seen.add(key)) {
                deduped.add(fix);
            }
        }
        return deduped;
    }

    private static List<Fix> collectIndentationFixes(Document doc) {
        String[] lines = doc.getText().split("\\R", -1);
        List<DcIndentationRules.IndentationIssue> issues = DcIndentationRules.collectIssues(lines);
        if (issues.isEmpty()) {
            return List.of();
        }

        Map<Integer, DcIndentationRules.IndentationIssue> firstByLine = new LinkedHashMap<>();
        for (DcIndentationRules.IndentationIssue issue : issues) {
            firstByLine.putIfAbsent(issue.line, issue);
        }

        List<Fix> fixes = new ArrayList<>();
        for (DcIndentationRules.IndentationIssue issue : firstByLine.values()) {
            if (issue.line < 0 || issue.line >= doc.getLineCount()) {
                continue;
            }
            int lineStart = doc.getLineStartOffset(issue.line);
            int lineEnd = doc.getLineEndOffset(issue.line);
            String lineText = doc.getText(new TextRange(lineStart, lineEnd));
            int leading = leadingWhitespaceLength(lineText);
            if (leading == issue.expectedIndent) {
                continue;
            }
            fixes.add(new Fix(
                lineStart,
                lineStart + leading,
                " ".repeat(Math.max(0, issue.expectedIndent))
            ));
        }
        return fixes;
    }

    private static int findValueOpeningQuote(String line) {
        if (line == null || line.isEmpty() || line.contains("\"\"\"")) {
            return -1;
        }
        Matcher m = VALUE_OPEN_QUOTE_RE.matcher(line);
        if (!m.find()) {
            return -1;
        }
        int quoteIndex = m.group(1).length();
        if (line.startsWith("\"\"\"", quoteIndex)) {
            return -1;
        }
        return quoteIndex;
    }

    private static int findUnescapedQuote(String text, int fromIndex) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        int i = Math.max(0, fromIndex);
        while (i < text.length()) {
            int idx = text.indexOf('"', i);
            if (idx < 0) {
                return -1;
            }
            int backslashes = 0;
            for (int j = idx - 1; j >= 0 && text.charAt(j) == '\\'; j--) {
                backslashes++;
            }
            if ((backslashes & 1) == 0) {
                return idx;
            }
            i = idx + 1;
        }
        return -1;
    }

    private static String escapeForDoubleQuoted(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static int leadingWhitespaceLength(String line) {
        if (line == null || line.isEmpty()) {
            return 0;
        }
        int idx = 0;
        while (idx < line.length() && Character.isWhitespace(line.charAt(idx))) {
            idx++;
        }
        return idx;
    }

    private record PendingQuote(int line, int column) {
    }

    private record Fix(int start, int end, String replacement) {
    }
}
