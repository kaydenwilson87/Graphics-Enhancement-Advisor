package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.model.Comparison;
import java.util.List;

/** Contract for the signed-in user's saved comparisons (CRUD module 1). */
public interface ComparisonRepository {
    /** Newest first. The server only returns the current user's items. */
    void list(Callback<List<Comparison>> cb);

    /** Creates the comparison when id is null, otherwise updates it. */
    void save(Comparison c, Callback<Comparison> cb);

    void delete(Comparison c, Callback<Void> cb);
}
