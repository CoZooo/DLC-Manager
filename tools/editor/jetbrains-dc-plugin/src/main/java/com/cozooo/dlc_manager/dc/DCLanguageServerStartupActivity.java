package com.cozooo.dlc_manager.dc;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public final class DCLanguageServerStartupActivity implements StartupActivity.DumbAware {
    @Override public void runActivity(@NotNull Project project) {
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override public void documentChanged(@NotNull DocumentEvent event) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
                if (isDc(file) && FileEditorManager.getInstance(project).isFileOpen(file))
                    DCLanguageServer.get(project).sync(DCLanguageServer.snapshot(file, event.getDocument()));
            }
        }, project);
        project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) { sync(project, file); }
            @Override public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if (isDc(file)) DCLanguageServer.get(project).close(Path.of(file.getPath()).toUri().toString());
            }
        });
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) sync(project, file);
    }
    private static boolean isDc(VirtualFile file) { return file != null && "dc".equalsIgnoreCase(file.getExtension()); }
    private static void sync(Project project, VirtualFile file) {
        if (!isDc(file)) return;
        ReadAction.run(() -> {
            var document = FileDocumentManager.getInstance().getDocument(file);
            if (document != null) DCLanguageServer.get(project).sync(DCLanguageServer.snapshot(file, document));
        });
    }
}
