package com.graphicsadvisor.app.data.remote;

import com.graphicsadvisor.app.data.AuthRepository;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.model.User;
import org.json.JSONObject;

public final class RemoteAuthRepository implements AuthRepository {
    @Override public void login(String email, String password, Callback<User> cb) {
        JSONObject body = new JSONObject();
        try { body.put("username", email.trim()); body.put("password", password); } catch (Exception ignored) { }
        ApiClient.request("POST", "api/v1/auth/token/", body, Callback.of(tokens -> {
            ApiClient.setAccessToken(tokens.optString("access"));
            loadUser(email, cb);
        }, cb::onError));
    }
    @Override public void register(String name, String email, String password, Callback<User> cb) {
        JSONObject body = new JSONObject();
        try { body.put("email", email.trim()); body.put("password", password); } catch (Exception ignored) { }
        ApiClient.request("POST", "api/v1/auth/register/", body, Callback.of(created -> login(email, password, cb), cb::onError));
    }
    private void loadUser(String email, Callback<User> cb) {
        ApiClient.request("GET", "api/v1/auth/me/", null, Callback.of(data -> cb.onSuccess(
                new User(data.optString("id", email), data.optString("name", email), data.optString("email", email), data.optBoolean("is_staff"))), err -> {
            ApiClient.clearAccessToken(); cb.onError(err);
        }));
    }
    @Override public void resetPassword(String email, Callback<String> cb) { cb.onError("Password reset is not configured on this API."); }
    @Override public void logout() { ApiClient.clearAccessToken(); }
}
