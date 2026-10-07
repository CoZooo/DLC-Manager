package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxDlcSyncOperationTest {
    @Test
    void canceledOperationDoesNotConnect() {
        DlcFxDlcSyncOperation operation = new DlcFxDlcSyncOperation();
        operation.cancel();

        assertThrows(InterruptedIOException.class, () -> operation.connect(new DlcServerEntry(), 60_000));
    }

    @Test
    void cancellationClosesSocketWhileWaitingForAuthentication() throws Exception {
        verifyBlockedRequestCancellation(true);
    }

    @Test
    void cancellationClosesSocketWhileWaitingForList() throws Exception {
        verifyBlockedRequestCancellation(false);
    }

    private void verifyBlockedRequestCancellation(boolean duringHello) throws Exception {
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (DlcSyncTestServer server = new DlcSyncTestServer(connection -> {
            connection.read();
            if (!duringHello) {
                connection.reply("hello");
                connection.read();
            }
            waiting.countDown();
            assertNull(connection.read());
        }); DlcFxDlcSyncOperation operation = new DlcFxDlcSyncOperation()) {
            Thread worker = new Thread(() -> {
                try (DlcServerSession session = operation.connect(server.entry(), 60_000)) {
                    session.fetchList();
                } catch (Throwable error) {
                    failure.set(error);
                }
            }, "dlc-cancellation-test");
            worker.setDaemon(true);
            operation.setWorker(worker);
            worker.start();
            try {
                assertTrue(waiting.await(5, TimeUnit.SECONDS));
                operation.cancel();
                worker.join(2000);
                assertFalse(worker.isAlive());
                assertTrue(operation.isCanceled());
                assertInstanceOf(IOException.class, failure.get());
                server.awaitCompleted();
            } finally {
                operation.cancel();
                worker.join(2000);
            }
        }
    }
}
