package com.graphicsadvisor.app.data.remote;

import android.os.Handler;
import android.os.Looper;
import com.graphicsadvisor.app.BuildConfig;
import com.graphicsadvisor.app.data.Callback;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small asynchronous client for the Django REST API. */
public final class ApiClient {
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile String accessToken;
    private ApiClient() { }
    public static void setAccessToken(String value) { accessToken = value; }
    public static void clearAccessToken() { accessToken = null; }

    public static void request(String method, String path, JSONObject body, Callback<JSONObject> cb) {
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String base = BuildConfig.API_BASE_URL.endsWith("/") ? BuildConfig.API_BASE_URL : BuildConfig.API_BASE_URL + "/";
                connection = (HttpURLConnection) new URL(base + path.replaceFirst("^/", "")).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                }
                String token = accessToken;
                if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
                if (body != null) {
                    try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                StringBuilder text = new StringBuilder();
                if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) text.append(line);
                }
                JSONObject result = text.length() == 0 ? new JSONObject() : new JSONObject(text.toString());
                if (code < 200 || code >= 300) throw new IllegalStateException(errorMessage(result, code));
                MAIN.post(() -> cb.onSuccess(result));
            } catch (Exception e) {
                String message = e instanceof IllegalStateException ? e.getMessage() : "Could not reach the API at " + BuildConfig.API_BASE_URL + ". Check that Django is running and the device can reach it.";
                MAIN.post(() -> cb.onError(message));
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private static String errorMessage(JSONObject data, int code) {
        if (data.has("detail")) return data.optString("detail");
        java.util.Iterator<String> keys = data.keys();
        if (keys.hasNext()) {
            String key = keys.next();
            Object value = data.opt(key);
            if (value instanceof org.json.JSONArray && ((org.json.JSONArray) value).length() > 0) return ((org.json.JSONArray) value).optString(0);
            return String.valueOf(value);
        }
        return "API request failed (HTTP " + code + ").";
    }
}
