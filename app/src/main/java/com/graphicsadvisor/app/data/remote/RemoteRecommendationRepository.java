package com.graphicsadvisor.app.data.remote;

import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.RecommendationRepository;
import com.graphicsadvisor.app.model.CatalogItem;
import com.graphicsadvisor.app.model.RecommendationResult;
import org.json.JSONArray;
import org.json.JSONObject;

public final class RemoteRecommendationRepository implements RecommendationRepository {

    @Override
    public void recommend(CatalogItem gpu, CatalogItem game, String resolution, String preference, Callback<RecommendationResult> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("gpu_id", Integer.parseInt(gpu.id));
            body.put("game_id", Integer.parseInt(game.id));
            body.put("resolution", apiResolution(resolution));
            body.put("preference", apiPreference(preference));
        } catch (Exception e) {
            cb.onError("This catalog item has no valid API ID.");
            return;
        }
        ApiClient.request("POST", "api/v1/recommendations/", body, Callback.of(json -> cb.onSuccess(parse(json)), cb::onError));
    }

    private static String apiResolution(String value) {
        return "4K".equalsIgnoreCase(value) ? "2160p" : value;
    }

    private static String apiPreference(String value) {
        if ("Image quality".equalsIgnoreCase(value)) return "image_quality";
        if ("Performance".equalsIgnoreCase(value)) return "performance";
        return "balanced";
    }

    private static RecommendationResult parse(JSONObject o) {
        RecommendationResult res = new RecommendationResult();
        res.techKey = o.isNull("recommendation") ? null : o.optString("recommendation");
        res.techName = optStr(o, "recommendation_name");
        res.reason = optStr(o, "reason");
        res.confidence = optStr(o, "confidence");
        res.method = optStr(o, "method");

        JSONArray limits = o.optJSONArray("limitations");
        for (int i = 0; limits != null && i < limits.length(); i++) {
            res.limitations.add(limits.optString(i));
        }

        JSONArray options = o.optJSONArray("options");
        for (int i = 0; options != null && i < options.length(); i++) {
            JSONObject item = options.optJSONObject(i);
            if (item == null) continue;
            JSONObject tech = item.optJSONObject("technology");
            JSONObject compat = item.optJSONObject("compatibility");

            RecommendationResult.Option opt = new RecommendationResult.Option();
            if (tech != null) {
                opt.techKey = optStr(tech, "key");
                opt.techName = optStr(tech, "name");
            }
            opt.score = item.isNull("score") ? -1 : item.optDouble("score", -1);
            opt.evidenceCount = item.optInt("evidence_count", 0);
            if (compat != null) {
                opt.performance = optStr(compat, "performance");
                opt.imageQuality = optStr(compat, "image_quality");
                opt.latency = optStr(compat, "latency");
            }
            JSONArray evidence = item.optJSONArray("evidence");
            for (int k = 0; evidence != null && k < evidence.length(); k++) {
                JSONObject evObj = evidence.optJSONObject(k);
                if (evObj != null) {
                    opt.sources.add(optStr(evObj, "source_title"));
                }
            }
            res.options.add(opt);
            if (res.techKey != null && res.techKey.equals(opt.techKey) && opt.score > 0) {
                res.bestScore = opt.score;
            }
        }
        return res;
    }

    private static String optStr(JSONObject o, String key) {
        return o == null || o.isNull(key) ? "" : o.optString(key, "");
    }
}
