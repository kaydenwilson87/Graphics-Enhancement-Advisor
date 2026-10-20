package com.graphicsadvisor.app.data.api;

/** Converts between the text shown in the app and the values the backend uses. */
final class ApiValues {
    private ApiValues() { }

    static String resolutionKey(String label) {
        return "4K".equals(label) ? "2160p" : label;
    }

    static String resolutionLabel(String key) {
        return "2160p".equals(key) ? "4K" : key;
    }

    static String preferenceKey(String label) {
        if ("Performance".equals(label)) return "performance";
        if ("Image quality".equals(label)) return "image_quality";
        return "balanced";
    }

    static String preferenceLabel(String key) {
        if ("performance".equals(key)) return "Performance";
        if ("image_quality".equals(key)) return "Image quality";
        return "Balanced";
    }
}
