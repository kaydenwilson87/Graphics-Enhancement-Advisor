package com.graphicsadvisor.app.data.remote;

import com.graphicsadvisor.app.data.AuthRepository;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Session;
import com.graphicsadvisor.app.model.User;
import org.json.JSONObject;

public final class RemoteAuthRepository implements AuthRepository {

    @Override
    public void login(String email, String password, Callback<User> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", email.trim());
            body.put("password", password);
        } catch (Exception ignored) { }
        ApiClient.request("POST", "api/v1/auth/token/", body, Callback.of(tokens -> {
            ApiClient.setAccessToken(tokens.optString("access"));
            Session.refreshToken = tokens.optString("refresh");
            loadUser(email, cb);
        }, cb::onError));
    }

    @Override
    public void register(String email, String password, Callback<User> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email.trim());
            body.put("password", password);
        } catch (Exception ignored) { }
        ApiClient.request("POST", "api/v1/auth/register/", body, Callback.of(created -> login(email, password, cb), cb::onError));
    }

    @Override
    public void restore(String refreshToken, Callback<User> cb) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            cb.onError("No refresh token available.");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("refresh", refreshToken);
        } catch (Exception ignored) { }
        ApiClient.request("POST", "api/v1/auth/token/refresh/", body, Callback.of(tokens -> {
            ApiClient.setAccessToken(tokens.optString("access"));
            String newRefresh = tokens.optString("refresh", refreshToken);
            Session.refreshToken = newRefresh.isEmpty() ? refreshToken : newRefresh;
            loadUser("", cb);
        }, err -> {
            ApiClient.clearAccessToken();
            cb.onError(err);
        }));
    }

    private void loadUser(String fallbackEmail, Callback<User> cb) {
        ApiClient.request("GET", "api/v1/auth/me/", null, Callback.of(data -> {
            String email = data.optString("email", fallbackEmail);
            String name = data.optString("name", email);
            cb.onSuccess(new User(data.optString("id", email), name, email, data.optBoolean("is_staff")));
        }, err -> {
            ApiClient.clearAccessToken();
            cb.onError(err);
        }));
    }
}
