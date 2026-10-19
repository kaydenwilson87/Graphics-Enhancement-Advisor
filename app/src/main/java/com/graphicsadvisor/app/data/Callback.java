package com.graphicsadvisor.app.data;

import java.util.function.Consumer;

/** Async result handler. Firebase returns data asynchronously, so the whole app uses this shape. */
public interface Callback<T> {
    void onSuccess(T result);

    void onError(String message);

    /** Lambda-friendly factory: Callback.of(result -> ..., error -> ...). */
    static <T> Callback<T> of(Consumer<T> ok, Consumer<String> err) {
        return new Callback<T>() {
            @Override public void onSuccess(T result) { ok.accept(result); }
            @Override public void onError(String message) { err.accept(message); }
        };
    }
}
