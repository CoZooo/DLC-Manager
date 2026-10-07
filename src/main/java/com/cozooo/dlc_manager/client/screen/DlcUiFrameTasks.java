package com.cozooo.dlc_manager.client.screen;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.ArrayList;
import java.util.List;

public final class DlcUiFrameTasks implements Executor {
    private final ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();
    private List<Runnable> ready = List.of();

    @Override public void execute(Runnable task) {
        pending.add(task);
    }

    public void runPending() {
        List<Runnable> current = ready;
        ready = List.of();
        for (Runnable task : current) task.run();
        int count = pending.size();
        List<Runnable> next = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Runnable task = pending.poll();
            if (task == null) break;
            next.add(task);
        }
        ready = next;
    }

    public void clear() {
        pending.clear();
        ready = List.of();
    }
}
