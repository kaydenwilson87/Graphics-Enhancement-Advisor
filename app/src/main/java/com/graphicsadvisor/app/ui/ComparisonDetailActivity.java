package com.graphicsadvisor.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Repos;
import com.graphicsadvisor.app.model.Comparison;
import com.graphicsadvisor.app.model.Technology;

/** One saved comparison: view it, edit the note (UPDATE) or delete it (DELETE). */
public class ComparisonDetailActivity extends AppCompatActivity {
    private Comparison comparison;
    private TextInputEditText etNote;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comparison_detail);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        comparison = (Comparison) getIntent().getSerializableExtra("comparison");

        ((TextView) findViewById(R.id.tvTitle)).setText(comparison.gameName + " • " + comparison.gpuName);
        ((TextView) findViewById(R.id.tvMeta)).setText(comparison.resolution + " • "
                + comparison.preset + " • saved " + Ui.date(comparison.createdAt));
        TextView rec = findViewById(R.id.tvRec);
        rec.setText(comparison.recommended);
        rec.setTextColor(Technology.colorOf(comparison.recommended));
        ((TextView) findViewById(R.id.tvReason)).setText(comparison.reason);
        etNote = findViewById(R.id.etNote);
        etNote.setText(comparison.note);
        progress = findViewById(R.id.progress);

        findViewById(R.id.btnUpdate).setOnClickListener(v -> updateNote());
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
    }

    private void updateNote() {
        String note = Ui.text(etNote);
        if (note.length() > 200) {
            ((com.google.android.material.textfield.TextInputLayout) findViewById(R.id.noteLayout))
                    .setError("Keep notes under 200 characters");
            return;
        }
        comparison.note = note;
        Ui.show(progress, true);
        Repos.comparisons().save(comparison, Callback.of(saved -> {
            Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show();
            finish();
        }, err -> {
            Ui.show(progress, false);
            Ui.snack(etNote, err);
        }));
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete comparison?")
                .setMessage("This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    Ui.show(progress, true);
                    Repos.comparisons().delete(comparison, Callback.of(r -> {
                        Toast.makeText(this, "Comparison deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    }, err -> {
                        Ui.show(progress, false);
                        Ui.snack(etNote, err);
                    }));
                })
                .show();
    }
}
