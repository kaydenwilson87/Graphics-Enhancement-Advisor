package com.graphicsadvisor.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Repos;
import com.graphicsadvisor.app.model.Comparison;
import com.graphicsadvisor.app.model.RecommendationResult;
import com.graphicsadvisor.app.model.RecommendationResult.Option;
import com.graphicsadvisor.app.model.Technology;

/** Shows the server's recommendation with its evidence, and lets the user save it (CREATE). */
public class ResultActivity extends AppCompatActivity {
    private MaterialButton btnSave;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());

        RecommendationResult result = (RecommendationResult) getIntent().getSerializableExtra("result");
        String gpu = getIntent().getStringExtra("gpu");
        String game = getIntent().getStringExtra("game");
        String res = getIntent().getStringExtra("res");
        String pref = getIntent().getStringExtra("pref");

        TextView tvTech = findViewById(R.id.tvTech);
        TextView tvFor = findViewById(R.id.tvFor);
        TextView tvConfidence = findViewById(R.id.tvConfidence);
        ProgressBar bar = findViewById(R.id.barConfidence);
        btnSave = findViewById(R.id.btnSave);
        progress = findViewById(R.id.progress);

        ((TextView) findViewById(R.id.tvCompare)).setText(compareText(result));
        ((TextView) findViewById(R.id.tvLimits)).setText(limitationsText(result));
        View cardCompare = findViewById(R.id.cardCompare);
        View cardLimits = findViewById(R.id.cardLimits);
        findViewById(R.id.btnCompare).setOnClickListener(v ->
                Ui.show(cardCompare, cardCompare.getVisibility() != View.VISIBLE));
        findViewById(R.id.btnLimitations).setOnClickListener(v ->
                Ui.show(cardLimits, cardLimits.getVisibility() != View.VISIBLE));

        if (result.techKey == null) { // the server could not recommend anything
            tvTech.setText("No match");
            tvFor.setText(gpu + " in " + game + " at " + res);
            tvConfidence.setText("");
            bar.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.tvReasons)).setText(result.reason);
            btnSave.setVisibility(View.GONE);
            return;
        }

        Technology tech = Technology.fromKey(result.techKey);
        String label = tech != null ? tech.label : result.techName;
        tvTech.setText(label);
        if (tech != null) tvTech.setTextColor(tech.color);
        tvFor.setText("Recommended for " + gpu + " in " + game + " at " + res);
        tvConfidence.setText("Confidence: " + capitalise(result.confidence)
                + " • Evidence score " + result.bestScore + " / 5");
        bar.setProgress((int) (result.bestScore * 20));
        ((TextView) findViewById(R.id.tvReasons)).setText(reasonText(result));

        btnSave.setOnClickListener(v -> {
            Comparison c = new Comparison();
            c.gpuName = gpu;
            c.gameName = game;
            c.resolution = res;
            c.preset = pref;
            c.recommended = label;
            c.reason = result.reason;
            c.note = "";
            btnSave.setEnabled(false);
            Ui.show(progress, true);
            Repos.comparisons().save(c, Callback.of(saved -> {
                Ui.show(progress, false);
                btnSave.setText("Saved");
                Ui.snack(btnSave, "Result saved");
            }, err -> {
                Ui.show(progress, false);
                btnSave.setEnabled(true);
                Ui.snack(btnSave, err);
            }));
        });
    }

    /** The reason from the server plus the research sources behind the winning option. */
    private String reasonText(RecommendationResult r) {
        StringBuilder sb = new StringBuilder(r.reason);
        for (Option o : r.options) {
            if (o.techKey != null && o.techKey.equals(r.techKey) && !o.sources.isEmpty()) {
                sb.append("\n\nSources:");
                for (String s : o.sources) sb.append("\n• ").append(s);
            }
        }
        return sb.toString();
    }

    private String compareText(RecommendationResult r) {
        if (r.options.isEmpty()) return "No compatible technologies were found for this setup.";
        StringBuilder sb = new StringBuilder();
        for (Option o : r.options) {
            sb.append(o.techName);
            if (o.score >= 0) {
                sb.append(" — score ").append(o.score).append("/5 (").append(o.evidenceCount).append(" sources)");
            } else {
                sb.append(" — no scored evidence yet");
            }
            if (o.techKey != null && o.techKey.equals(r.techKey)) sb.append("  (recommended)");
            appendLine(sb, "Performance", o.performance);
            appendLine(sb, "Image quality", o.imageQuality);
            appendLine(sb, "Latency", o.latency);
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    private String limitationsText(RecommendationResult r) {
        StringBuilder sb = new StringBuilder();
        for (String l : r.limitations) sb.append("• ").append(l).append("\n");
        if (sb.length() == 0) sb.append("No specific limitations were reported.\n");
        if (!r.method.isEmpty()) sb.append("\nMethod: ").append(r.method);
        return sb.toString().trim();
    }

    private static void appendLine(StringBuilder sb, String title, String text) {
        if (!text.isEmpty()) sb.append("\n   ").append(title).append(": ").append(text);
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
