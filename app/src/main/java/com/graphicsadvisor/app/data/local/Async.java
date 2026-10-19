package com.graphicsadvisor.app.data.local;

import android.os.Handler;
import android.os.Looper;

/** Runs work on the main thread after a short delay so loading spinners are visible. */
final class Async {
    private Async() { }

    static void later(Runnable r) {
        new Handler(Looper.getMainLooper()).postDelayed(r, 400);
    }
}
