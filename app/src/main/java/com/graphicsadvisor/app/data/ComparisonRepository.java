package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.model.Comparison;
import java.util.List;

/** Contract for saved comparisons (user CRUD module). */
public interface ComparisonRepository {
    /** Newest first, only the given user's items. */
    void list(String userId, Callback<List<Comparison>> cb);

    /** Creates the comparison when id is null, otherwise updates it. */
    void save(Comparison c, Callback<Comparison> cb);

    void delete(Comparison c, Callback<Void> cb);
}
