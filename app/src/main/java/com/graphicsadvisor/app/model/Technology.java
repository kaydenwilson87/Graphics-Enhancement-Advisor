package com.graphicsadvisor.app.model;

/** The three upscaling technologies the app compares, with one fixed brand colour each. */
public enum Technology {
    DLSS("DLSS", 0xFF00AC48),
    FSR("FSR", 0xFFE1624B),
    XESS("XeSS", 0xFF178DB9);

    public final String label;
    public final int color;

    Technology(String label, int color) {
        this.label = label;
        this.color = color;
    }

    /** Returns the brand colour for a label such as "DLSS" (black if unknown). */
    public static int colorOf(String label) {
        for (Technology t : values()) {
            if (t.label.equals(label)) return t.color;
        }
        return 0xFF000000;
    }
}
