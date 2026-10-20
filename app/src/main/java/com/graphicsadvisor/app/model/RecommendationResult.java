package com.graphicsadvisor.app.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** The server's recommendation for one GPU + game + resolution + preference. */
public class RecommendationResult implements Serializable {
    public String techKey;        // "dlss" / "fsr" / "xess", null when nothing can be recommended
    public String techName;       // e.g. "NVIDIA DLSS"
    public String reason = "";
    public String confidence = "low";   // low / medium / high
    public double bestScore;      // 1-5, 0 when there is no recommendation
    public String method = "";
    public final List<String> limitations = new ArrayList<>();
    public final List<Option> options = new ArrayList<>();

    /** One compatible technology and the evidence behind its score. */
    public static class Option implements Serializable {
        public String techKey;
        public String techName;
        public double score = -1;     // -1 = no scored evidence
        public int evidenceCount;
        public String performance = "";
        public String imageQuality = "";
        public String latency = "";
        public final List<String> sources = new ArrayList<>();
    }
}
