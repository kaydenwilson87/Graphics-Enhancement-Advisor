package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.data.remote.RemoteAuthRepository;
import com.graphicsadvisor.app.data.remote.RemoteCatalogRepository;
import com.graphicsadvisor.app.data.remote.RemoteRecommendationRepository;
import com.graphicsadvisor.app.data.remote.RemoteComparisonRepository;

/**
 * Single place where the app selects its remote Django API repositories. Saved comparisons are scoped to the authenticated account by the Django API.
 */
public final class Repos {
    private static final AuthRepository AUTH = new RemoteAuthRepository();
    private static final CatalogRepository CATALOG = new RemoteCatalogRepository();
    private static final RecommendationRepository RECOMMENDATIONS = new RemoteRecommendationRepository();
    private static final ComparisonRepository COMPARISONS = new RemoteComparisonRepository();

    private Repos() { }

    public static AuthRepository auth() { return AUTH; }

    public static CatalogRepository catalog() { return CATALOG; }

    public static RecommendationRepository recommendations() { return RECOMMENDATIONS; }

    public static ComparisonRepository comparisons() { return COMPARISONS; }
}
