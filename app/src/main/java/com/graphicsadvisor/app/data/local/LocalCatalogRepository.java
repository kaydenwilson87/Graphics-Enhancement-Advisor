package com.graphicsadvisor.app.data.local;

import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.CatalogRepository;
import com.graphicsadvisor.app.model.CatalogItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** In-memory GPUs and games, seeded with the examples from the Figma design. */
public class LocalCatalogRepository implements CatalogRepository {
    private final List<CatalogItem> items = new ArrayList<>();

    public LocalCatalogRepository() {
        seed(CatalogItem.TYPE_GPU, "RTX 4070 Super", "NVIDIA", true, true, true);
        seed(CatalogItem.TYPE_GPU, "RX 7800 XT", "AMD", false, true, true);
        seed(CatalogItem.TYPE_GPU, "Arc B580", "Intel", false, true, true);
        seed(CatalogItem.TYPE_GAME, "Cyberpunk 2077", "", true, true, true);
        seed(CatalogItem.TYPE_GAME, "Alan Wake 2", "", true, true, false);
        seed(CatalogItem.TYPE_GAME, "Starfield", "", false, true, true);
    }

    private void seed(String type, String name, String vendor, boolean d, boolean f, boolean x) {
        CatalogItem i = new CatalogItem();
        i.id = UUID.randomUUID().toString();
        i.type = type;
        i.name = name;
        i.vendor = vendor;
        i.dlss = d;
        i.fsr = f;
        i.xess = x;
        items.add(i);
    }

    @Override
    public void list(String type, Callback<List<CatalogItem>> cb) {
        Async.later(() -> {
            List<CatalogItem> out = new ArrayList<>();
            for (CatalogItem i : items) if (i.type.equals(type)) out.add(i);
            Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
            cb.onSuccess(out);
        });
    }

    @Override
    public void save(CatalogItem item, Callback<CatalogItem> cb) {
        Async.later(() -> {
            for (CatalogItem i : items) {
                boolean sameName = i.type.equals(item.type) && i.name.equalsIgnoreCase(item.name);
                if (sameName && (item.id == null || !i.id.equals(item.id))) {
                    cb.onError("\"" + item.name + "\" already exists.");
                    return;
                }
            }
            if (item.id == null) {
                item.id = UUID.randomUUID().toString();
                items.add(item);
            } else {
                for (int k = 0; k < items.size(); k++) {
                    if (items.get(k).id.equals(item.id)) items.set(k, item);
                }
            }
            cb.onSuccess(item);
        });
    }

    @Override
    public void delete(CatalogItem item, Callback<Void> cb) {
        Async.later(() -> {
            items.removeIf(i -> i.id.equals(item.id));
            cb.onSuccess(null);
        });
    }
}
