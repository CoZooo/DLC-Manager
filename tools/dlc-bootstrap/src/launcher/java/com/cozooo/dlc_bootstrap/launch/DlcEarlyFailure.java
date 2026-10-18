package com.cozooo.dlc_bootstrap.launch;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

final class DlcEarlyFailure {
    @FunctionalInterface
    interface Notice {
        void show(DlcEarlyI18n messages, String details) throws Exception;
    }

    private DlcEarlyFailure() {}

    static IllegalStateException stop(Path game, boolean client, Throwable failure) {
        return stop(game, client, failure, DlcEarlyFailure::showDialog);
    }

    static IllegalStateException stop(Path game, boolean client, Throwable failure, Notice notice) {
        System.clearProperty(DlcEarlyProgress.READY_PROPERTY);
        DlcEarlyI18n messages = new DlcEarlyI18n();
        String details = describe(messages, failure);
        Path report = (game == null ? Path.of("") : game).resolve("logs/dlc-bootstrap-error.log").toAbsolutePath().normalize();
        StringWriter stack = new StringWriter();
        failure.printStackTrace(new PrintWriter(stack));
        try {
            Files.createDirectories(report.getParent());
            Files.writeString(report, details + "\n\n" + stack, StandardCharsets.UTF_8);
            details += "\n\n" + messages.text("failure.log", report);
        } catch (Exception ex) {
            details += "\n\n" + messages.text("failure.report_unavailable", ex);
        }
        System.err.println("[dlc-manager-bootstrap] " + details);
        boolean interrupted = Thread.interrupted();
        try {
            if (client) notice.show(messages, details);
        } catch (Exception | LinkageError | java.awt.AWTError ex) {
            if (ex instanceof InterruptedException) interrupted = true;
            System.err.println("[dlc-manager-bootstrap] " + messages.text("failure.dialog_unavailable", ex));
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
            System.clearProperty(DlcEarlyProgress.READY_PROPERTY);
        }
        return new IllegalStateException(details, failure);
    }

    static String describe(DlcEarlyI18n messages, Throwable failure) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var reasons = new LinkedHashSet<String>();
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            String message = cause.getMessage();
            reasons.add(message == null || message.isBlank() ? cause.getClass().getSimpleName() : message);
        }
        return messages.text("failure.summary") + "\n\n" + messages.text("failure.reason") + "\n"
            + String.join("\n", reasons) + "\n\n" + messages.text("failure.retry");
    }

    private static void showDialog(DlcEarlyI18n messages, String details) throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        Runnable show = () -> {
            JTextArea text = new JTextArea(details);
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setCaretPosition(0);
            JScrollPane pane = new JScrollPane(text);
            pane.setPreferredSize(new Dimension(720, 360));
            String close = messages.text("failure.close");
            JOptionPane.showOptionDialog(null, pane, messages.text("title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE, null, new Object[] { close }, close);
        };
        if (EventQueue.isDispatchThread()) show.run(); else EventQueue.invokeAndWait(show);
    }
}
