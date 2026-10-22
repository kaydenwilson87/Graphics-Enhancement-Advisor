package com.graphicsadvisor.app.data.remote;

import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.ComparisonRepository;
import com.graphicsadvisor.app.model.Comparison;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Per-account saved comparison CRUD through the Django API. */
public final class RemoteComparisonRepository implements ComparisonRepository {
    private static final String PATH = "api/v1/saved-comparisons/";

    @Override public void list(Callback<List<Comparison>> cb) {
        ApiClient.request("GET", PATH, null, Callback.of(data -> {
            JSONArray rows=data.optJSONArray("results"); if(rows==null) rows=new JSONArray();
            List<Comparison> items=new ArrayList<>();
            for(int i=0;i<rows.length();i++) { JSONObject row=rows.optJSONObject(i); if(row!=null) items.add(fromJson(row)); }
            cb.onSuccess(items);
        },cb::onError));
    }
    @Override public void save(Comparison c, Callback<Comparison> cb) {
        JSONObject body=new JSONObject();
        try {
            body.put("gpu_name",c.gpuName); body.put("game_name",c.gameName); body.put("resolution",apiResolution(c.resolution));
            body.put("preference",apiPreference(c.preset)); body.put("recommendation",c.recommended); body.put("reason",c.reason); body.put("note",c.note==null?"":c.note);
        } catch(Exception e) { cb.onError("Could not prepare this saved comparison."); return; }
        boolean create=c.id==null;
        ApiClient.request(create?"POST":"PUT",PATH+(create?"":c.id+"/"),body,Callback.of(row->{
            Comparison saved=fromJson(row); c.id=saved.id; c.createdAt=saved.createdAt; cb.onSuccess(saved);
        },cb::onError));
    }
    @Override public void delete(Comparison c, Callback<Void> cb) {
        ApiClient.request("DELETE",PATH+c.id+"/",null,Callback.of(ignored->cb.onSuccess(null),cb::onError));
    }
    private Comparison fromJson(JSONObject row) {
        Comparison c=new Comparison(); c.id=row.optString("id"); c.gpuName=row.optString("gpu_name");
        c.gameName=row.optString("game_name"); c.resolution=displayResolution(row.optString("resolution")); c.preset=displayPreference(row.optString("preference"));
        c.recommended=row.optString("recommendation"); c.reason=row.optString("reason"); c.note=row.optString("note");
        c.createdAt=parseDate(row.optString("created_at")); return c;
    }
    private String apiResolution(String value) { return "4K".equalsIgnoreCase(value)?"2160p":value; }
    private String displayResolution(String value) { return "2160p".equals(value)?"4K":value; }
    private String apiPreference(String value) {
        if("Image quality".equalsIgnoreCase(value)) return "image_quality";
        if("Performance".equalsIgnoreCase(value)) return "performance";
        return "balanced";
    }
    private String displayPreference(String value) {
        if("image_quality".equals(value)) return "Image quality";
        if("performance".equals(value)) return "Performance";
        return "Balanced";
    }
    private long parseDate(String value) {
        if(value==null||value.length()<19) return System.currentTimeMillis();
        String base=value.substring(0,19);
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US); format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date parsed=format.parse(base,new ParsePosition(0)); return parsed==null?System.currentTimeMillis():parsed.getTime();
    }
}
