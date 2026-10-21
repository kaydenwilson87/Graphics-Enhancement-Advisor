package com.graphicsadvisor.app.data.remote;

import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.CatalogRepository;
import com.graphicsadvisor.app.model.CatalogItem;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class RemoteCatalogRepository implements CatalogRepository {
    @Override public void list(String type, Callback<List<CatalogItem>> cb) {
        String path = CatalogItem.TYPE_GPU.equals(type) ? "api/v1/gpus/" : "api/v1/games/";
        ApiClient.request("GET", path, null, Callback.of(data -> {
            JSONArray rows = data.optJSONArray("results"); if (rows == null) rows = new JSONArray();
            List<CatalogItem> items = new ArrayList<>();
            for (int i=0;i<rows.length();i++) { JSONObject row=rows.optJSONObject(i); if(row!=null) items.add(fromJson(type,row)); }
            cb.onSuccess(items);
        }, cb::onError));
    }
    @Override public void save(CatalogItem item, Callback<CatalogItem> cb) {
        boolean gpu = CatalogItem.TYPE_GPU.equals(item.type);
        JSONObject body = new JSONObject();
        try {
            body.put("name", item.name);
            if (gpu) { body.put("manufacturer", item.vendor); body.put("architecture", ""); body.put("notes", ""); }
            else { body.put("developer", ""); body.put("engine", ""); body.put("notes", ""); }
        } catch (Exception ignored) { }
        String base = gpu ? "api/v1/gpus/" : "api/v1/games/";
        ApiClient.request(item.id == null ? "POST" : "PATCH", base + (item.id == null ? "" : item.id + "/"), body, Callback.of(row -> cb.onSuccess(fromJson(item.type,row)), cb::onError));
    }
    @Override public void delete(CatalogItem item, Callback<Void> cb) {
        String base = CatalogItem.TYPE_GPU.equals(item.type) ? "api/v1/gpus/" : "api/v1/games/";
        ApiClient.request("DELETE", base + item.id + "/", null, Callback.of(row -> cb.onSuccess(null), cb::onError));
    }
    private CatalogItem fromJson(String type, JSONObject row) {
        CatalogItem item = new CatalogItem(); item.type=type; item.id=row.optString("id"); item.name=row.optString("name");
        if (CatalogItem.TYPE_GPU.equals(type)) item.vendor=row.optString("manufacturer"); else item.vendor="";
        JSONArray supported=row.optJSONArray("supported_technologies");
        if (supported!=null) for(int i=0;i<supported.length();i++) {
            String key=supported.optString(i);
            if("dlss".equals(key)) item.dlss=true;
            else if("fsr".equals(key)) item.fsr=true;
            else if("xess".equals(key)) item.xess=true;
        }
        return item;
    }
}
