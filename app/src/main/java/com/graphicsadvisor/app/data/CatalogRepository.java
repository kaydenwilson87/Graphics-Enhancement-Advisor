package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.model.CatalogItem;
import java.util.List;

/** Contract for GPUs and games (admin CRUD module). */
public interface CatalogRepository {
    /** @param type CatalogItem.TYPE_GPU or CatalogItem.TYPE_GAME */
    void list(String type, Callback<List<CatalogItem>> cb);

    /** Creates the item when item.id is null, otherwise updates it. */
    void save(CatalogItem item, Callback<CatalogItem> cb);

    void delete(CatalogItem item, Callback<Void> cb);
}
