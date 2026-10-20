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
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;

/** Displays the recommendation returned by the Django evidence engine. */
public class ResultActivity extends AppCompatActivity {
    private MaterialButton btnSave;
    private View progress;
    private CatalogItem gpu, game;
    private String res, pref;
    private Result result;
    private TextView tvTech, tvFor, tvConfidence, tvReasons, tvCompare, tvLimits;
    private ProgressBar bar;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        gpu = (CatalogItem) getIntent().getSerializableExtra("gpu");
        game = (CatalogItem) getIntent().getSerializableExtra("game");
        res = getIntent().getStringExtra("res");
        pref = getIntent().getStringExtra("pref");
        tvTech=findViewById(R.id.tvTech); tvFor=findViewById(R.id.tvFor); tvConfidence=findViewById(R.id.tvConfidence);
        bar=findViewById(R.id.barConfidence); tvReasons=findViewById(R.id.tvReasons); btnSave=findViewById(R.id.btnSave);
        progress=findViewById(R.id.progress); tvCompare=findViewById(R.id.tvCompare); tvLimits=findViewById(R.id.tvLimits);
        View cardCompare=findViewById(R.id.cardCompare), cardLimits=findViewById(R.id.cardLimits);
        findViewById(R.id.btnCompare).setOnClickListener(v -> Ui.show(cardCompare, cardCompare.getVisibility()!=View.VISIBLE));
        findViewById(R.id.btnLimitations).setOnClickListener(v -> Ui.show(cardLimits, cardLimits.getVisibility()!=View.VISIBLE));
        Ui.show(progress,true); btnSave.setEnabled(false);
        String apiPreference = pref.equalsIgnoreCase("Image quality") ? "image_quality" : pref.toLowerCase();
        String apiResolution = "4K".equalsIgnoreCase(res) ? "2160p" : res;
        Repos.recommendations().recommend(gpu,game,apiResolution,apiPreference, Callback.of(this::showRecommendation, error -> {
            Ui.show(progress,false); tvTech.setText("Unable to get recommendation"); tvFor.setText(error);
            tvConfidence.setText(""); bar.setVisibility(View.GONE); tvReasons.setText(""); btnSave.setVisibility(View.GONE);
        }));
    }

    private void showRecommendation(JSONObject data) {
        Ui.show(progress,false);
        result = new Result();
        String selected = data.optString("recommendation", null);
        for (Technology t: Technology.values()) if (t.name().equalsIgnoreCase(selected)) result.best=t;
        result.bestScore=0;
        JSONArray options=data.optJSONArray("options"); StringBuilder cmp=new StringBuilder();
        if(options!=null) for(int i=0;i<options.length();i++) {
            JSONObject option=options.optJSONObject(i); if(option==null) continue;
            JSONObject tech=option.optJSONObject("technology"); if(tech==null) continue;
            String key=tech.optString("key"); Technology t=null;
            for(Technology candidate:Technology.values()) if(candidate.name().equalsIgnoreCase(key)) t=candidate;
            if(t==null) continue;
            if(option.isNull("score")) result.excluded.put(t,"Compatible, but no scored research evidence is available.");
            else {
                double score=option.optDouble("score"); result.scores.put(t,score);
                if(t==result.best) result.bestScore=score;
            }
            cmp.append(tech.optString("name",t.label)).append(" — ");
            if(option.isNull("score")) cmp.append("not ranked (insufficient evidence)");
            else cmp.append("evidence score ").append(option.optDouble("score")).append("/5").append(t==result.best?"  (recommended)":"");
            cmp.append("\n");
        }
        tvCompare.setText(cmp.length()==0?"No supported options were found.":cmp.toString().trim());
        JSONArray limits=data.optJSONArray("limitations"); StringBuilder lim=new StringBuilder();
        if(limits!=null) for(int i=0;i<limits.length();i++) lim.append("• ").append(limits.optString(i)).append("\n");
        tvLimits.setText(lim.length()==0?"No additional limitations were reported.":lim.toString().trim());
        if(result.best==null) {
            tvTech.setText("No evidence-based match"); tvFor.setText("No supported, scored recommendation is available for this setup.");
            tvConfidence.setText(""); bar.setVisibility(View.GONE); tvReasons.setText(data.optString("reason")); btnSave.setVisibility(View.GONE); return;
        }
        String techName=data.optString("recommendation_name",result.best.label);
        tvTech.setText(techName); tvTech.setTextColor(result.best.color);
        tvFor.setText("Recommended for "+gpu.name+" in "+game.name+" at "+res);
        tvConfidence.setText("Evidence score: "+result.bestScore+" / 5 • "+data.optString("confidence","unrated")+" confidence");
        bar.setProgress((int)Math.round(result.bestScore*20));
        tvReasons.setText(data.optString("reason"));
        btnSave.setEnabled(true);
        btnSave.setOnClickListener(v -> save(techName));
    }

    private void save(String techName) {
        Comparison c=new Comparison(); c.userId=Session.current.uid; c.gpuName=gpu.name; c.gameName=game.name;
        c.resolution=res; c.preset=pref; c.recommended=techName; c.reason=tvReasons.getText().toString(); c.note="";
        btnSave.setEnabled(false); Ui.show(progress,true);
        Repos.comparisons().save(c,Callback.of(saved->{Ui.show(progress,false);btnSave.setText("Saved");Ui.snack(btnSave,"Result saved");},err->{Ui.show(progress,false);btnSave.setEnabled(true);Ui.snack(btnSave,err);}));
    }
}
