package com.graphicsadvisor.app.model;

/** The signed-in person. {@code admin} unlocks the GPU/game management screens. */
public class User {
    public final String uid;
    public final String name;
    public final String email;
    public final boolean admin;

    public User(String uid, String name, String email, boolean admin) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.admin = admin;
    }
}
