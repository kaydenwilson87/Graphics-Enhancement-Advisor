package com.graphicsadvisor.app.data.api;

import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.RecommendationRepository;
import com.graphicsadvisor.app.model.CatalogItem;
import com.graphicsadvisor.app.model.RecommendationResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Asks POST /recommendations/ and converts the answer into a {@link RecommendationResult}. */
public class ApiRecommendationRepository implements RecommendationRepository {

    @Override
    public void recommend(CatalogItem gpu, CatalogItem game, String resolution, String preference,
                          Callback<RecommendationResult> cb) {
        ApiClient.run(() -> {
            JSONObject body = new JSONObject()
                    .put("gpu_id", Integer.parseInt(gpu.id))
                    .put("game_id", Integer.parseInt(game.id))
                    .put("resolution", ApiValues.resolutionKey(resolution))
                    .put("preference", ApiValues.preferenceKey(preference));
            ApiClient.Response r = ApiClient.ensureOk(ApiClient.call("POST", "recommendations/", body));
            return parse(new JSONObject(r.body));
        }, cb);
    }

    private static RecommendationResult parse(JSONObject o) throws JSONException {
        RecommendationResult res = new RecommendationResult();
        res.techKey = o.isNull("recommendation") ? null : o.getString("recommendation");
        res.techName = ApiClient.str(o, "recommendation_name");
        res.reason = ApiClient.str(o, "reason");
        res.confidence = ApiClient.str(o, "confidence");
        res.method = ApiClient.str(o, "method");

        JSONArray limits = o.optJSONArray("limitations");
        for (int i = 0; limits != null && i < limits.length(); i++) res.limitations.add(limits.getString(i));

        JSONArray options = o.optJSONArray("options");
        for (int i = 0; options != null && i < options.length(); i++) {
            JSONObject item = options.getJSONObject(i);
            JSONObject tech = item.getJSONObject("technology");
            JSONObject compat = item.optJSONObject("compatibility");

            RecommendationResult.Option opt = new RecommendationResult.Option();
            opt.techKey = ApiClient.str(tech, "key");
            opt.techName = ApiClient.str(tech, "name");
            opt.score = item.isNull("score") ? -1 : item.getDouble("score");
            opt.evidenceCount = item.optInt("evidence_count", 0);
            if (compat != null) {
                opt.performance = ApiClient.str(compat, "performance");
                opt.imageQuality = ApiClient.str(compat, "image_quality");
                opt.latency = ApiClient.str(compat, "latency");
            }
            JSONArray evidence = item.optJSONArray("evidence");
            for (int k = 0; evidence != null && k < evidence.length(); k++) {
                opt.sources.add(ApiClient.str(evidence.getJSONObject(k), "source_title"));
            }
            res.options.add(opt);
            if (res.techKey != null && res.techKey.equals(opt.techKey) && opt.score > 0) {
                res.bestScore = opt.score;
            }
        }
        return res;
    }
}
