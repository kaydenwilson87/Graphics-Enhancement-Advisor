package com.graphicsadvisor.app.data.api;

import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.CatalogRepository;
import com.graphicsadvisor.app.model.CatalogItem;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** GPUs and games from the /gpus/ and /games/ endpoints. Only staff accounts may change them. */
public class ApiCatalogRepository implements CatalogRepository {

    @Override
    public void list(String type, Callback<List<CatalogItem>> cb) {
        ApiClient.run(() -> {
            List<CatalogItem> out = new ArrayList<>();
            for (JSONObject o : ApiClient.getAll(path(type))) out.add(fromJson(type, o));
            return out;
        }, cb);
    }

    @Override
    public void save(CatalogItem item, Callback<CatalogItem> cb) {
        ApiClient.run(() -> {
            boolean isNew = item.id == null;
            String method = isNew ? "POST" : "PUT";
            String path = isNew ? path(item.type) : path(item.type) + item.id + "/";
            ApiClient.Response r = ApiClient.ensureOk(ApiClient.call(method, path, toJson(item)));
            return fromJson(item.type, new JSONObject(r.body));
        }, cb);
    }

    @Override
    public void delete(CatalogItem item, Callback<Void> cb) {
        ApiClient.run(() -> {
            ApiClient.ensureOk(ApiClient.call("DELETE", path(item.type) + item.id + "/", null));
            return null;
        }, cb);
    }

    private static String path(String type) {
        return CatalogItem.TYPE_GPU.equals(type) ? "gpus/" : "games/";
    }

    private static CatalogItem fromJson(String type, JSONObject o) throws org.json.JSONException {
        CatalogItem i = new CatalogItem();
        i.id = String.valueOf(o.getInt("id"));
        i.type = type;
        i.name = ApiClient.str(o, "name");
        i.notes = ApiClient.str(o, "notes");
        if (CatalogItem.TYPE_GPU.equals(type)) {
            i.maker = ApiClient.str(o, "manufacturer");
            i.detail = ApiClient.str(o, "architecture");
        } else {
            i.maker = ApiClient.str(o, "developer");
            i.detail = ApiClient.str(o, "engine");
            i.releaseYear = o.isNull("release_year") ? 0 : o.optInt("release_year", 0);
        }
        return i;
    }

    private static JSONObject toJson(CatalogItem i) throws org.json.JSONException {
        JSONObject o = new JSONObject().put("name", i.name).put("notes", i.notes);
        if (CatalogItem.TYPE_GPU.equals(i.type)) {
            o.put("manufacturer", i.maker).put("architecture", i.detail);
        } else {
            o.put("developer", i.maker).put("engine", i.detail)
                    .put("release_year", i.releaseYear > 0 ? i.releaseYear : JSONObject.NULL);
        }
        return o;
    }
}
