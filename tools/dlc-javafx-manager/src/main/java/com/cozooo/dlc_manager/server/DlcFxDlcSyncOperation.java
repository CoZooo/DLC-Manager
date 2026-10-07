package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerSession;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class DlcFxDlcSyncOperation implements AutoCloseable {
    private final AtomicBoolean canceled = new AtomicBoolean();
    private final AtomicReference<AutoCloseable> session = new AtomicReference<>();
    private volatile Thread worker;

    void setWorker(Thread worker) {
        this.worker = worker;
        if (canceled.get()) {
            worker.interrupt();
        }
    }

    DlcServerSession connect(DlcServerEntry entry, int timeoutMs) throws IOException {
        checkCanceled();
        DlcServerSession connected = DlcServerSession.connect(entry, timeoutMs, connection -> {
            session.set(connection);
            if (canceled.get()) {
                close();
            }
        });
        if (isCanceled()) {
            close();
            checkCanceled();
        }
        return connected;
    }

    boolean isCanceled() {
        return canceled.get() || Thread.currentThread().isInterrupted();
    }

    void checkCanceled() throws IOException {
        if (isCanceled()) {
            throw new InterruptedIOException("canceled");
        }
    }

    void cancel() {
        canceled.set(true);
        close();
        Thread running = worker;
        if (running != null) {
            running.interrupt();
        }
    }

    @Override
    public void close() {
        AutoCloseable current = session.getAndSet(null);
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
            }
        }
    }
}
