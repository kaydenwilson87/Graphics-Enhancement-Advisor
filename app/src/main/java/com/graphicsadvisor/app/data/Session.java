package com.graphicsadvisor.app.data;

import com.graphicsadvisor.app.model.User;

/** Holds the signed-in user for the lifetime of the app process. */
public final class Session {
    public static User current;

    private Session() { }
}
