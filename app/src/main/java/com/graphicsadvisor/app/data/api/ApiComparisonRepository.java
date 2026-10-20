package com.graphicsadvisor.app.data.api;

import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.ComparisonRepository;
import com.graphicsadvisor.app.model.Comparison;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONException;
import org.json.JSONObject;

/** Saved comparisons from the /saved-comparisons/ endpoint (needs the backend change listed in the partner guide). */
public class ApiComparisonRepository implements ComparisonRepository {
    private static final String PATH = "saved-comparisons/";

    @Override
    public void list(Callback<List<Comparison>> cb) {
        ApiClient.run(() -> {
            List<Comparison> out = new ArrayList<>();
            for (JSONObject o : ApiClient.getAll(PATH)) out.add(fromJson(o));
            Collections.sort(out, (a, b) -> Long.compare(b.createdAt, a.createdAt));
            return out;
        }, cb);
    }

    @Override
    public void save(Comparison c, Callback<Comparison> cb) {
        ApiClient.run(() -> {
            boolean isNew = c.id == null;
            String path = isNew ? PATH : PATH + c.id + "/";
            ApiClient.Response r = ApiClient.ensureOk(ApiClient.call(isNew ? "POST" : "PUT", path, toJson(c)));
            return fromJson(new JSONObject(r.body));
        }, cb);
    }

    @Override
    public void delete(Comparison c, Callback<Void> cb) {
        ApiClient.run(() -> {
            ApiClient.ensureOk(ApiClient.call("DELETE", PATH + c.id + "/", null));
            return null;
        }, cb);
    }

    private static Comparison fromJson(JSONObject o) throws JSONException {
        Comparison c = new Comparison();
        c.id = String.valueOf(o.getInt("id"));
        c.gpuName = ApiClient.str(o, "gpu_name");
        c.gameName = ApiClient.str(o, "game_name");
        c.resolution = ApiValues.resolutionLabel(ApiClient.str(o, "resolution"));
        c.preset = ApiValues.preferenceLabel(ApiClient.str(o, "preference"));
        c.recommended = ApiClient.str(o, "recommendation");
        c.reason = ApiClient.str(o, "reason");
        c.note = ApiClient.str(o, "note");
        c.createdAt = parseTime(ApiClient.str(o, "created_at"));
        return c;
    }

    private static JSONObject toJson(Comparison c) throws JSONException {
        return new JSONObject()
                .put("gpu_name", c.gpuName)
                .put("game_name", c.gameName)
                .put("resolution", ApiValues.resolutionKey(c.resolution))
                .put("preference", ApiValues.preferenceKey(c.preset))
                .put("recommendation", c.recommended)
                .put("reason", c.reason == null ? "" : c.reason)
                .put("note", c.note == null ? "" : c.note);
    }

    /** Reads "2026-10-19T12:34:56..." (the first 19 characters, treated as UTC). */
    private static long parseTime(String iso) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso.substring(0, 19)).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
