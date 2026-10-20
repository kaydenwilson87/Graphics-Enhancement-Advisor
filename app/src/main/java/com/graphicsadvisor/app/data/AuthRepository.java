package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.model.User;

/** Contract for signing in and registering. */
public interface AuthRepository {
    void login(String email, String password, Callback<User> cb);

    void register(String email, String password, Callback<User> cb);
}
