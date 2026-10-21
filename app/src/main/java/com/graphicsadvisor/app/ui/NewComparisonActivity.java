package com.graphicsadvisor.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Repos;
import com.graphicsadvisor.app.model.CatalogItem;
import java.util.ArrayList;
import java.util.List;

/** 3-step wizard: 1) choose GPU, 2) choose game, 3) resolution + preference, then generate. */
public class NewComparisonActivity extends AppCompatActivity {
    private int step = 0;
    private CatalogItem gpu, game;
    private List<CatalogItem> gpus = new ArrayList<>();
    private List<CatalogItem> games = new ArrayList<>();
    private CatalogAdapter adapter;
    private TextView tvStep, tvHint, tvSummary;
    private TextInputEditText etSearch;
    private RecyclerView rv;
    private View step3, searchLayout, progress;
    private MaterialButton btnNext, btnBack;
    private ChipGroup resGroup, prefGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_comparison);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        tvStep = findViewById(R.id.tvStep);
        tvHint = findViewById(R.id.tvHint);
        tvSummary = findViewById(R.id.tvSummary);
        etSearch = findViewById(R.id.etSearch);
        searchLayout = findViewById(R.id.searchLayout);
        rv = findViewById(R.id.rvItems);
        step3 = findViewById(R.id.step3);
        progress = findViewById(R.id.progress);
        btnNext = findViewById(R.id.btnNext);
        btnBack = findViewById(R.id.btnBack);
        resGroup = findViewById(R.id.resGroup);
        prefGroup = findViewById(R.id.prefGroup);

        adapter = new CatalogAdapter(item -> {
            if (step == 0) gpu = item; else game = item;
            adapter.setSelected(item.id);
        });
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        Ui.onTextChanged(etSearch, () -> {
            adapter.setQuery(Ui.text(etSearch));
            updateHint();
        });
        btnNext.setOnClickListener(v -> next());
        btnBack.setOnClickListener(v -> { step--; render(); });
        loadLists();
    }

    /** Loads both lists once, then shows step 1. A failure offers a Retry button. */
    private void loadLists() {
        Ui.show(progress, true);
        Repos.catalog().list(CatalogItem.TYPE_GPU, Callback.of(g -> {
            gpus = g;
            Repos.catalog().list(CatalogItem.TYPE_GAME, Callback.of(gm -> {
                games = gm;
                Ui.show(progress, false);
                render();
            }, this::loadFailed));
        }, this::loadFailed));
    }

    private void loadFailed(String message) {
        Ui.show(progress, false);
        Ui.snackRetry(btnNext, message, this::loadLists);
    }

    /** Shows the UI for the current step. */
    private void render() {
        Ui.show(rv, step < 2);
        Ui.show(searchLayout, step < 2);
        Ui.show(step3, step == 2);
        Ui.show(btnBack, step > 0);
        etSearch.setText(""); // start each step with an empty search
        if (step == 0) {
            tvStep.setText(R.string.step1_title);
            adapter.submit(gpus);
            adapter.setSelected(gpu == null ? null : gpu.id);
            btnNext.setText(R.string.btn_next_game);
        } else if (step == 1) {
            tvStep.setText(R.string.step2_title);
            adapter.submit(games);
            adapter.setSelected(game == null ? null : game.id);
            btnNext.setText(R.string.btn_next_resolution);
        } else {
            tvStep.setText(R.string.step3_title);
            tvSummary.setText(gpu.name + " • " + game.name);
            btnNext.setText(R.string.btn_generate);
        }
        updateHint();
    }

    /** Explains an empty list: nothing on the server yet, or nothing matching the search. */
    private void updateHint() {
        boolean noData = step < 2 && (step == 0 ? gpus : games).isEmpty();
        boolean noMatch = step < 2 && !noData && adapter.getItemCount() == 0;
        Ui.show(tvHint, noData || noMatch);
        if (noData) tvHint.setText(step == 0 ? R.string.empty_gpus : R.string.empty_games);
        else if (noMatch) tvHint.setText(R.string.no_matches);
    }

    private void next() {
        if (step == 0) {
            if (gpu == null) { Ui.snack(btnNext, getString(R.string.snack_select_gpu)); return; }
            step = 1;
            render();
        } else if (step == 1) {
            if (game == null) { Ui.snack(btnNext, getString(R.string.snack_select_game)); return; }
            step = 2;
            render();
        } else {
            generate();
        }
    }

    /** Asks the server for the recommendation; a failure offers a Retry button. */
    private void generate() {
        String res = checkedText(resGroup);
        String pref = checkedText(prefGroup);
        Ui.show(progress, true);
        btnNext.setEnabled(false);
        Repos.recommendations().recommend(gpu, game, res, pref, Callback.of(result -> {
            Ui.show(progress, false);
            btnNext.setEnabled(true);
            Intent i = new Intent(this, ResultActivity.class);
            i.putExtra("result", result);
            i.putExtra("gpu", gpu.name);
            i.putExtra("game", game.name);
            i.putExtra("res", res);
            i.putExtra("pref", pref);
            startActivity(i);
        }, err -> {
            Ui.show(progress, false);
            btnNext.setEnabled(true);
            Ui.snackRetry(btnNext, err, this::generate);
        }));
    }

    private String checkedText(ChipGroup group) {
        Chip chip = findViewById(group.getCheckedChipId());
        return chip.getText().toString();
    }
}
