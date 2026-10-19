package com.graphicsadvisor.app.model;

import java.io.Serializable;

/**
 * A GPU or a game managed by admins. Both share the same shape: a name and the
 * technologies they support. Public fields + empty constructor keep it Firebase-friendly.
 */
public class CatalogItem implements Serializable {
    public static final String TYPE_GPU = "gpu";
    public static final String TYPE_GAME = "game";

    public String id;      // null until saved
    public String type;    // TYPE_GPU or TYPE_GAME
    public String name;
    public String vendor;  // NVIDIA / AMD / Intel for GPUs, empty for games
    public boolean dlss, fsr, xess;

    public CatalogItem() { }

    public boolean supports(Technology t) {
        switch (t) {
            case DLSS: return dlss;
            case FSR: return fsr;
            default: return xess;
        }
    }

    /** Short text such as "DLSS • FSR" for list rows. */
    public String techSummary() {
        StringBuilder sb = new StringBuilder();
        for (Technology t : Technology.values()) {
            if (supports(t)) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append(t.label);
            }
        }
        return sb.length() == 0 ? "No technologies" : sb.toString();
    }
}
