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
        TextView tvReasons = findViewById(R.id.tvReasons);
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
            tvTech.setText(result.options.isEmpty()
                    ? R.string.result_no_match : R.string.result_compatible_unranked);
            tvFor.setText(getString(R.string.result_setup, gpu, game, res));
            tvConfidence.setText("");
            bar.setVisibility(View.GONE);
            tvReasons.setText(result.reason);
            btnSave.setVisibility(View.GONE);
            return;
        }

        Technology tech = Technology.fromKey(result.techKey);
        String label = tech != null ? tech.label : result.techName;
        tvTech.setText(label);
        if (tech != null) tvTech.setTextColor(tech.color);
        tvFor.setText(getString(R.string.result_for, gpu, game, res));
        tvConfidence.setText(getString(R.string.result_confidence,
                capitalise(result.confidence), String.valueOf(result.bestScore)));
        bar.setProgress((int) (result.bestScore * 20));
        tvReasons.setText(reasonText(result));

        Comparison c = new Comparison();
        c.gpuName = gpu;
        c.gameName = game;
        c.resolution = res;
        c.preset = pref;
        c.recommended = label;
        c.reason = result.reason;
        c.note = "";
        btnSave.setOnClickListener(v -> save(c));
    }

    /** Saves the result; a failure offers a Retry button. */
    private void save(Comparison c) {
        btnSave.setEnabled(false);
        Ui.show(progress, true);
        Repos.comparisons().save(c, Callback.of(saved -> {
            Ui.show(progress, false);
            btnSave.setText(R.string.result_saved);
            Ui.snack(btnSave, getString(R.string.result_saved_msg));
        }, err -> {
            Ui.show(progress, false);
            btnSave.setEnabled(true);
            Ui.snackRetry(btnSave, err, () -> save(c));
        }));
    }

    /** The reason from the server plus the research sources behind the winning option. */
    private String reasonText(RecommendationResult r) {
        StringBuilder sb = new StringBuilder(r.reason);
        for (Option o : r.options) {
            if (o.techKey != null && o.techKey.equals(r.techKey) && !o.sources.isEmpty()) {
                sb.append("\n\n").append(getString(R.string.result_sources));
                for (String s : o.sources) sb.append("\n• ").append(s);
            }
        }
        return sb.toString();
    }

    private String compareText(RecommendationResult r) {
        if (r.options.isEmpty()) return getString(R.string.compare_none);
        StringBuilder sb = new StringBuilder();
        for (Option o : r.options) {
            if (o.score >= 0) sb.append(getString(R.string.compare_score, o.techName, String.valueOf(o.score), o.evidenceCount));
            else sb.append(getString(R.string.compare_no_score, o.techName));
            if (o.techKey != null && o.techKey.equals(r.techKey)) {
                sb.append("  ").append(getString(R.string.compare_recommended));
            }
            appendLine(sb, getString(R.string.label_performance), o.performance);
            appendLine(sb, getString(R.string.label_image_quality), o.imageQuality);
            appendLine(sb, getString(R.string.label_latency), o.latency);
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    private String limitationsText(RecommendationResult r) {
        StringBuilder sb = new StringBuilder();
        for (String l : r.limitations) sb.append("• ").append(l).append("\n");
        if (sb.length() == 0) sb.append(getString(R.string.limits_none)).append("\n");
        if (!r.method.isEmpty()) sb.append("\n").append(getString(R.string.limits_method, r.method));
        return sb.toString().trim();
    }

    private static void appendLine(StringBuilder sb, String title, String text) {
        if (!text.isEmpty()) sb.append("\n   ").append(title).append(": ").append(text);
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
