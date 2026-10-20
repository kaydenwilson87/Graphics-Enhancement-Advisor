package com.graphicsadvisor.app.data.api;

/** Where the backend lives. Must end with a slash. */
public final class ApiConfig {
    /**
     * The Android emulator reaches the computer it runs on through 10.0.2.2.
     * When the backend is hosted online, replace this with its https address,
     * e.g. "https://your-api.example.com/api/v1/".
     */
    public static final String BASE_URL = "http://10.0.2.2:8000/api/v1/";

    private ApiConfig() { }
}
