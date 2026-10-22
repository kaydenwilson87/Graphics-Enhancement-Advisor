package com.graphicsadvisor.app.data.api;

import com.graphicsadvisor.app.BuildConfig;

/** Where the backend lives. Must end with a slash. */
public final class ApiConfig {
    /**
     * The Android emulator reaches the computer it runs on through 10.0.2.2.
     * Injected at build time with -PapiBaseUrl. Netlify serves the frontend;
     * this URL must point to the Django API and end with /api/v1/.
     */
    public static final String BASE_URL = BuildConfig.API_BASE_URL;

    private ApiConfig() { }
}
