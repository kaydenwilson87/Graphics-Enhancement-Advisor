package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.model.User;

/** Holds the signed-in user and their login tokens for the lifetime of the app process. */
public final class Session {
    public static User current;
    public static String accessToken;
    public static String refreshToken;

    private Session() { }

    /** Forgets the user and tokens (sign out). */
    public static void clear() {
        current = null;
        accessToken = null;
        refreshToken = null;
    }
}
