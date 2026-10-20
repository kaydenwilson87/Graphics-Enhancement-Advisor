package com.graphicsadvisor.app.data;
import com.graphicsadvisor.app.model.CatalogItem;
import org.json.JSONObject;
public interface RecommendationRepository {
    void recommend(CatalogItem gpu, CatalogItem game, String resolution, String preference, Callback<JSONObject> cb);
}
