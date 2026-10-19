package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.model.User;

/** Contract for sign-in. The Firebase teammate implements this (FirebaseAuth). */
public interface AuthRepository {
    void login(String email, String password, Callback<User> cb);

    void register(String name, String email, String password, Callback<User> cb);

    void resetPassword(String email, Callback<String> cb);

    void logout();
}
