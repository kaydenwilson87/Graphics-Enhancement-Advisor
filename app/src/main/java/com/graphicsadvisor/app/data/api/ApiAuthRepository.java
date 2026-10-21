package com.graphicsadvisor.app.data.api;

import com.graphicsadvisor.app.data.AuthRepository;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Session;
import com.graphicsadvisor.app.model.User;
import java.util.Locale;
import org.json.JSONObject;

/** Sign-in against the Django JWT endpoints. The email is used as the username. */
public class ApiAuthRepository implements AuthRepository {

    @Override
    public void login(String email, String password, Callback<User> cb) {
        ApiClient.run(() -> signIn(email, password), cb);
    }

    @Override
    public void register(String email, String password, Callback<User> cb) {
        ApiClient.run(() -> {
            JSONObject body = new JSONObject()
                    .put("email", email.trim().toLowerCase(Locale.ROOT))
                    .put("password", password);
            ApiClient.ensureOk(ApiClient.call("POST", "auth/register/", body));
            return signIn(email, password); // log the new account straight in
        }, cb);
    }

    @Override
    public void restore(String refreshToken, Callback<User> cb) {
        ApiClient.run(() -> {
            Session.clear();
            Session.refreshToken = refreshToken;
            if (!ApiClient.refreshSession()) { // exchanges the refresh token for a new access token
                Session.clear();
                throw new ApiClient.ApiException("Your session expired. Please sign in again.");
            }
            return loadUser("");
        }, cb);
    }

    private User signIn(String email, String password) throws Exception {
        Session.clear();
        String username = email.trim().toLowerCase(Locale.ROOT);
        JSONObject body = new JSONObject().put("username", username).put("password", password);
        ApiClient.Response r = ApiClient.call("POST", "auth/token/", body);
        if (r.code == 400 || r.code == 401) throw new ApiClient.ApiException("Incorrect email or password.");
        ApiClient.ensureOk(r);
        JSONObject tokens = new JSONObject(r.body);
        Session.accessToken = tokens.getString("access");
        Session.refreshToken = tokens.getString("refresh");
        return loadUser(username);
    }

    /** The token only proves who you are; /auth/me/ tells us the email and whether you are an admin. */
    private User loadUser(String fallbackEmail) throws Exception {
        JSONObject me = new JSONObject(ApiClient.ensureOk(ApiClient.call("GET", "auth/me/", null)).body);
        String mail = ApiClient.str(me, "email").isEmpty() ? fallbackEmail : ApiClient.str(me, "email");
        return new User(String.valueOf(me.getInt("id")), displayName(mail), mail, me.optBoolean("is_staff", false));
    }

    /** "sam.jones@mail.com" becomes "Sam.jones". */
    private static String displayName(String email) {
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        if (local.isEmpty()) return "User";
        return Character.toUpperCase(local.charAt(0)) + local.substring(1);
    }
}
