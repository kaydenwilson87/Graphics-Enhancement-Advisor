package com.graphicsadvisor.app.model;

import java.io.Serializable;

/** A saved recommendation result belonging to one user. */
public class Comparison implements Serializable {
    public String id;           // null until saved
    public String userId;
    public String gpuName;
    public String gameName;
    public String resolution;
    public String preset;
    public String recommended;  // technology label, e.g. "DLSS"
    public String reason;       // explanation text shown in the detail screen
    public String note;         // editable by the user
    public long createdAt;

    public Comparison() { }
}
