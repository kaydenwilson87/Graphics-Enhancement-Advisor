package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.model.CatalogItem;
import com.graphicsadvisor.app.model.RecommendationResult;

/** Contract for asking the server which technology to use. */
public interface RecommendationRepository {
    /**
     * @param resolution display text: "1080p", "1440p" or "4K"
     * @param preference display text: "Performance", "Image quality" or "Balanced"
     */
    void recommend(CatalogItem gpu, CatalogItem game, String resolution, String preference,
                   Callback<RecommendationResult> cb);
}
