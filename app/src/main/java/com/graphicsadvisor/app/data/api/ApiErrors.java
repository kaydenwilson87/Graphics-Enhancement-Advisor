package com.graphicsadvisor.app.data.api;

import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Turns a Django REST error response into one short message that is safe to show to the user. */
public final class ApiErrors {
    private ApiErrors() { }

    /**
     * @param code HTTP status code
     * @param body response body, which may be empty or not JSON
     */
    public static String message(int code, String body) {
        try {
            JSONObject o = new JSONObject(body == null ? "" : body);
            if (o.has("detail")) return o.getString("detail");
            Iterator<String> keys = o.keys();
            if (keys.hasNext()) {
                String field = keys.next();
                Object value = o.get(field);
                String msg = value instanceof JSONArray ? ((JSONArray) value).optString(0) : String.valueOf(value);
                if (field.equals("non_field_errors")) return msg;
                String label = field.replace('_', ' ');
                return Character.toUpperCase(label.charAt(0)) + label.substring(1) + ": " + msg;
            }
        } catch (JSONException ignored) {
            // body was not JSON; fall through to the generic messages
        }
        if (code == 403) return "You do not have permission to do that.";
        return "Server error (" + code + ").";
    }
}
