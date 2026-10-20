package com.graphicsadvisor.app.data.remote;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.RecommendationRepository;
import com.graphicsadvisor.app.model.CatalogItem;
import org.json.JSONObject;
public final class RemoteRecommendationRepository implements RecommendationRepository {
    @Override public void recommend(CatalogItem gpu, CatalogItem game, String resolution, String preference, Callback<JSONObject> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("gpu_id", Integer.parseInt(gpu.id)); body.put("game_id", Integer.parseInt(game.id));
            body.put("resolution", resolution); body.put("preference", preference);
        } catch (Exception e) { cb.onError("This catalog item has no valid API ID."); return; }
        ApiClient.request("POST", "api/v1/recommendations/", body, cb);
    }
}
