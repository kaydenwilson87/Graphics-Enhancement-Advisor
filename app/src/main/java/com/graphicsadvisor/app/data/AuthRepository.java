package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.model.User;

/** Contract for signing in, registering and restoring a saved session. */
public interface AuthRepository {
    void login(String email, String password, Callback<User> cb);

    void register(String email, String password, Callback<User> cb);

    /** Signs back in with a refresh token saved from an earlier login (see {@link TokenStore}). */
    void restore(String refreshToken, Callback<User> cb);
}
