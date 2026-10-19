package com.graphicsadvisor.app.data.local;

import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.ComparisonRepository;
import com.graphicsadvisor.app.model.Comparison;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** In-memory saved comparisons for development. */
public class LocalComparisonRepository implements ComparisonRepository {
    private final List<Comparison> items = new ArrayList<>();

    @Override
    public void list(String userId, Callback<List<Comparison>> cb) {
        Async.later(() -> {
            List<Comparison> out = new ArrayList<>();
            for (Comparison c : items) if (c.userId.equals(userId)) out.add(c);
            Collections.sort(out, (a, b) -> Long.compare(b.createdAt, a.createdAt));
            cb.onSuccess(out);
        });
    }

    @Override
    public void save(Comparison c, Callback<Comparison> cb) {
        Async.later(() -> {
            if (c.id == null) {
                c.id = UUID.randomUUID().toString();
                c.createdAt = System.currentTimeMillis();
                items.add(c);
            } else {
                for (int k = 0; k < items.size(); k++) {
                    if (items.get(k).id.equals(c.id)) items.set(k, c);
                }
            }
            cb.onSuccess(c);
        });
    }

    @Override
    public void delete(Comparison c, Callback<Void> cb) {
        Async.later(() -> {
            items.removeIf(i -> i.id.equals(c.id));
            cb.onSuccess(null);
        });
    }
}
