package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.data.api.ApiAuthRepository;
import com.graphicsadvisor.app.data.api.ApiCatalogRepository;
import com.graphicsadvisor.app.data.api.ApiComparisonRepository;
import com.graphicsadvisor.app.data.api.ApiRecommendationRepository;

/** Single place where the app picks its data source: the Django backend over HTTP. */
public final class Repos {
    private static final AuthRepository AUTH = new ApiAuthRepository();
    private static final CatalogRepository CATALOG = new ApiCatalogRepository();
    private static final ComparisonRepository COMPARISONS = new ApiComparisonRepository();
    private static final RecommendationRepository RECOMMENDATIONS = new ApiRecommendationRepository();

    private Repos() { }

    public static AuthRepository auth() { return AUTH; }

    public static CatalogRepository catalog() { return CATALOG; }

    public static ComparisonRepository comparisons() { return COMPARISONS; }

    public static RecommendationRepository recommendations() { return RECOMMENDATIONS; }
}
