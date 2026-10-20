package com.graphicsadvisor.app.data.api;

import android.os.Handler;
import android.os.Looper;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Session;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Minimal JSON-over-HTTP client for the Django API. Network calls run on a background thread
 * and results are delivered on the main thread through {@link Callback}.
 */
public final class ApiClient {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ApiClient() { }

    /** An error with a message that is safe to show to the user. */
    public static class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }

    /** Raw HTTP result. */
    public static class Response {
        public final int code;
        public final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public boolean ok() {
            return code >= 200 && code < 300;
        }
    }

    /** Work to run off the main thread. */
    public interface Job<T> {
        T run() throws Exception;
    }

    /** Runs the job in the background and reports the result (or a friendly error) on the main thread. */
    public static <T> void run(Job<T> job, Callback<T> cb) {
        POOL.execute(() -> {
            T result = null;
            String error = null;
            try {
                result = job.run();
            } catch (ApiException e) {
                error = e.getMessage();
            } catch (JSONException e) {
                error = "The server sent an unexpected response.";
            } catch (Exception e) {
                error = "Something went wrong. Please try again.";
            }
            final T finalResult = result;
            final String finalError = error;
            MAIN.post(() -> {
                if (finalError != null) cb.onError(finalError);
                else cb.onSuccess(finalResult);
            });
        });
    }

    /** Sends a request with the access token (if signed in). Retries once after refreshing an expired token. */
    public static Response call(String method, String path, JSONObject body) throws ApiException {
        Response r = send(method, path, body, true);
        if (r.code == 401 && Session.refreshToken != null && refreshAccessToken()) {
            r = send(method, path, body, true);
        }
        return r;
    }

    /** Throws an ApiException with the server's message unless the response was successful. */
    public static Response ensureOk(Response r) throws ApiException {
        if (!r.ok()) throw new ApiException(errorMessage(r));
        return r;
    }

    /** Reads every page of a paginated list endpoint. */
    public static List<JSONObject> getAll(String path) throws ApiException, JSONException {
        List<JSONObject> out = new ArrayList<>();
        for (int page = 1; page <= 50; page++) {
            String url = path + (path.contains("?") ? "&" : "?") + "page=" + page;
            JSONObject data = new JSONObject(ensureOk(call("GET", url, null)).body);
            JSONArray results = data.getJSONArray("results");
            for (int i = 0; i < results.length(); i++) out.add(results.getJSONObject(i));
            if (data.isNull("next")) break;
        }
        return out;
    }

    /** String value of a JSON field, "" when missing or null (org.json would return "null"). */
    public static String str(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "");
    }

    private static Response send(String method, String path, JSONObject body, boolean withToken) throws ApiException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(ApiConfig.BASE_URL + path).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            if (withToken && Session.accessToken != null) {
                c.setRequestProperty("Authorization", "Bearer " + Session.accessToken);
            }
            if (body != null) {
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            return new Response(code, read(code >= 400 ? c.getErrorStream() : c.getInputStream()));
        } catch (IOException e) {
            throw new ApiException("Cannot reach the server. Check that it is running and that you are online.");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static synchronized boolean refreshAccessToken() {
        try {
            JSONObject body = new JSONObject().put("refresh", Session.refreshToken);
            Response r = send("POST", "auth/token/refresh/", body, false);
            if (!r.ok()) return false;
            JSONObject data = new JSONObject(r.body);
            Session.accessToken = data.getString("access");
            if (data.has("refresh")) Session.refreshToken = data.getString("refresh");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Turns a Django REST error body into one short message. */
    public static String errorMessage(Response r) {
        try {
            JSONObject o = new JSONObject(r.body);
            if (o.has("detail")) return o.getString("detail");
            Iterator<String> keys = o.keys();
            if (keys.hasNext()) {
                String field = keys.next();
                Object value = o.get(field);
                String msg = value instanceof JSONArray ? ((JSONArray) value).optString(0) : String.valueOf(value);
                if (field.equals("non_field_errors")) return msg;
                String label = field.replace('_', ' ');
                return Character.toUpperCase(label.charAt(0)) + label.substring(1) + ": " + msg;
            }
        } catch (JSONException ignored) {
            // body was not JSON; fall through to the generic messages
        }
        if (r.code == 403) return "You do not have permission to do that.";
        return "Server error (" + r.code + ").";
    }
}
