package com.graphicsadvisor.app.logic;

import com.graphicsadvisor.app.model.CatalogItem;
import com.graphicsadvisor.app.model.Technology;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, rule-based recommendation: the same inputs always give the same answer.
 * It only uses stored data (no live benchmarking).
 *
 * NOTE: the EVIDENCE scores below are PLACEHOLDERS. Replace them with values taken from
 * the literature review before submitting.
 */
public final class RecommendationEngine {
    public static final String PRESET_PERFORMANCE = "Performance";
    public static final String PRESET_QUALITY = "Image quality";
    public static final String PRESET_BALANCED = "Balanced";

    /** Limitation titles and texts shown on the results screen (from the Figma design). */
    public static final String[][] LIMITATIONS = {
            {"Input latency", "Frame generation can add delay. It works best from a base of about 60 FPS."},
            {"Visual artifacts", "Fast motion and UI elements can show ghosting or shimmer."},
            {"Game support", "Only technologies the game supports are evaluated."},
            {"Hardware requirements", "DLSS needs an RTX GPU. Frame generation needs a recent GPU."},
            {"Test data", "Results vary with driver, game version and scene."}
    };

    // Order follows the Technology enum: DLSS, FSR, XeSS. Scale 0-10.
    private static final double[] PERFORMANCE = {9.0, 7.5, 7.0};
    private static final double[] QUALITY = {9.0, 6.5, 7.5};

    /** Everything the results screen needs. */
    public static class Result {
        public Technology best;                                           // null when nothing is usable
        public final Map<Technology, Double> scores = new EnumMap<>(Technology.class);
        public final Map<Technology, String> excluded = new EnumMap<>(Technology.class);
        public final List<String> reasons = new ArrayList<>();
        public double bestScore;
    }

    private RecommendationEngine() { }

    public static Result evaluate(CatalogItem gpu, CatalogItem game, String resolution, String preset) {
        Result r = new Result();
        double perfWeight = preset.equals(PRESET_PERFORMANCE) ? 0.7
                : preset.equals(PRESET_QUALITY) ? 0.3 : 0.5;

        for (Technology t : Technology.values()) {
            if (!game.supports(t)) {
                r.excluded.put(t, game.name + " does not support " + t.label + ".");
                continue;
            }
            if (!gpu.supports(t)) {
                r.excluded.put(t, gpu.name + " cannot run " + t.label + ".");
                continue;
            }
            int i = t.ordinal();
            double quality = QUALITY[i];
            if (resolution.equals("1080p")) {
                // Lower input resolution hurts image quality more for FSR and XeSS.
                quality -= (t == Technology.FSR) ? 1.0 : (t == Technology.XESS) ? 0.5 : 0.0;
            }
            double score = perfWeight * PERFORMANCE[i] + (1 - perfWeight) * quality;
            r.scores.put(t, Math.round(score * 10) / 10.0);
        }

        for (Map.Entry<Technology, Double> e : r.scores.entrySet()) {
            if (r.best == null || e.getValue() > r.bestScore) { // enum order breaks ties
                r.best = e.getKey();
                r.bestScore = e.getValue();
            }
        }
        if (r.best == null) return r;

        r.reasons.add("Supported by both " + game.name + " and " + gpu.name + ".");
        r.reasons.add("Best fit for your \"" + preset.toLowerCase() + "\" preference at " + resolution + ".");
        r.reasons.add(r.scores.size() == 1
                ? "It is the only supported option for this setup."
                : "Highest fit score (" + r.bestScore + "/10) of " + r.scores.size() + " supported options.");
        return r;
    }
}
