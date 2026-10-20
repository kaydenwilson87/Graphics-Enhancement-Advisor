package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.data.local.LocalAuthRepository;
import com.graphicsadvisor.app.data.local.LocalCatalogRepository;
import com.graphicsadvisor.app.data.local.LocalComparisonRepository;

/**
 * Single place where the app picks its data source. To switch to Firebase, replace the
 * three Local* classes below with the Firebase implementations. No screen code changes.
 */
public final class Repos {
    private static final AuthRepository AUTH = new LocalAuthRepository();
    private static final CatalogRepository CATALOG = new LocalCatalogRepository();
    private static final ComparisonRepository COMPARISONS = new LocalComparisonRepository();

    private Repos() { }

    public static AuthRepository auth() { return AUTH; }

    public static CatalogRepository catalog() { return CATALOG; }

    public static ComparisonRepository comparisons() { return COMPARISONS; }
}
