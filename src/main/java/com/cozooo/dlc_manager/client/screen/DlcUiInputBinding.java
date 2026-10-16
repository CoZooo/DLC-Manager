package com.cozooo.dlc_manager.client.screen;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DlcUiInputBinding implements Runnable {
    private final Supplier<String> value;
    private final BooleanSupplier current;
    private final Consumer<String> change;
    private String previous;

    public DlcUiInputBinding(Supplier<String> value, BooleanSupplier current, Consumer<String> change) {
        this.value = value;
        this.current = current;
        this.change = change;
        previous = normalize(value.get());
    }

    @Override public void run() {
        if (!current.getAsBoolean()) return;
        String next = normalize(value.get());
        if (next.equals(previous)) return;
        previous = next;
        change.accept(next);
    }

    private static String normalize(String value) { return value == null ? "" : value; }
}
