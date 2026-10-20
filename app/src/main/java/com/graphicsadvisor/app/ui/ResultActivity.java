package com.graphicsadvisor.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Repos;
import com.graphicsadvisor.app.data.Session;
import com.graphicsadvisor.app.logic.RecommendationEngine;
import com.graphicsadvisor.app.logic.RecommendationEngine.Result;
import com.graphicsadvisor.app.model.CatalogItem;
import com.graphicsadvisor.app.model.Comparison;
import com.graphicsadvisor.app.model.Technology;
import java.util.Map;

/** Shows the recommendation, why, a comparison of all options, limitations, and lets the user save (CREATE). */
public class ResultActivity extends AppCompatActivity {
    private MaterialButton btnSave;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());

        CatalogItem gpu = (CatalogItem) getIntent().getSerializableExtra("gpu");
        CatalogItem game = (CatalogItem) getIntent().getSerializableExtra("game");
        String res = getIntent().getStringExtra("res");
        String pref = getIntent().getStringExtra("pref");
        Result result = RecommendationEngine.evaluate(gpu, game, res, pref);

        TextView tvTech = findViewById(R.id.tvTech);
        TextView tvFor = findViewById(R.id.tvFor);
        TextView tvConfidence = findViewById(R.id.tvConfidence);
        ProgressBar bar = findViewById(R.id.barConfidence);
        TextView tvReasons = findViewById(R.id.tvReasons);
        btnSave = findViewById(R.id.btnSave);
        progress = findViewById(R.id.progress);

        // Comparison and limitations text.
        StringBuilder cmp = new StringBuilder();
        for (Technology t : Technology.values()) {
            Double score = result.scores.get(t);
            if (score != null) {
                cmp.append(t.label).append(" — fit score ").append(score).append("/10")
                        .append(t == result.best ? "  (recommended)" : "").append("\n");
            } else {
                cmp.append(t.label).append(" — unavailable: ").append(result.excluded.get(t)).append("\n");
            }
        }
        ((TextView) findViewById(R.id.tvCompare)).setText(cmp.toString().trim());

        StringBuilder lim = new StringBuilder();
        for (String[] l : RecommendationEngine.LIMITATIONS) {
            lim.append("• ").append(l[0]).append(": ").append(l[1]).append("\n");
        }
        ((TextView) findViewById(R.id.tvLimits)).setText(lim.toString().trim());

        View cardCompare = findViewById(R.id.cardCompare);
        View cardLimits = findViewById(R.id.cardLimits);
        findViewById(R.id.btnCompare).setOnClickListener(v ->
                Ui.show(cardCompare, cardCompare.getVisibility() != View.VISIBLE));
        findViewById(R.id.btnLimitations).setOnClickListener(v ->
                Ui.show(cardLimits, cardLimits.getVisibility() != View.VISIBLE));

        if (result.best == null) { // no technology works for this GPU + game
            tvTech.setText("No match");
            tvFor.setText("None of the technologies work for this setup.");
            tvConfidence.setText("");
            bar.setVisibility(View.GONE);
            StringBuilder why = new StringBuilder();
            for (Map.Entry<Technology, String> e : result.excluded.entrySet()) {
                why.append("• ").append(e.getValue()).append("\n");
            }
            tvReasons.setText(why.toString().trim());
            btnSave.setVisibility(View.GONE);
            return;
        }

        tvTech.setText(result.best.label);
        tvTech.setTextColor(result.best.color);
        tvFor.setText("Recommended for " + gpu.name + " in " + game.name + " at " + res);
        tvConfidence.setText("Fit score: " + result.bestScore + " / 10");
        bar.setProgress((int) (result.bestScore * 10));
        tvReasons.setText("• " + TextUtils.join("\n• ", result.reasons));

        btnSave.setOnClickListener(v -> {
            Comparison c = new Comparison();
            c.userId = Session.current.uid;
            c.gpuName = gpu.name;
            c.gameName = game.name;
            c.resolution = res;
            c.preset = pref;
            c.recommended = result.best.label;
            c.reason = TextUtils.join("\n", result.reasons);
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
}
