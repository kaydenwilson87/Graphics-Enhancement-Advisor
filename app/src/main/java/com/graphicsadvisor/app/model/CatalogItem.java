package com.graphicsadvisor.app.model;

import java.io.Serializable;

/**
 * A GPU or a game managed by admins. The two share one class so the same screens can handle both.
 * GPU:  maker = manufacturer, detail = architecture.
 * Game: maker = developer,    detail = engine, releaseYear = release year.
 */
public class CatalogItem implements Serializable {
    public static final String TYPE_GPU = "gpu";
    public static final String TYPE_GAME = "game";

    public String id;        // null until saved
    public String type;      // TYPE_GPU or TYPE_GAME
    public String name = "";
    public String maker = "";
    public String detail = "";
    public int releaseYear;  // games only, 0 = unknown
    public String notes = "";

    public CatalogItem() { }

    /** One-line description for list rows, e.g. "NVIDIA • Ada Lovelace". */
    public String subtitle() {
        StringBuilder sb = new StringBuilder();
        append(sb, maker);
        append(sb, detail);
        if (releaseYear > 0) append(sb, String.valueOf(releaseYear));
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part == null || part.isEmpty()) return;
        if (sb.length() > 0) sb.append(" • ");
        sb.append(part);
    }
}
