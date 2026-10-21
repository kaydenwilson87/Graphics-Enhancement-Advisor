package com.graphicsadvisor.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Repos;
import com.graphicsadvisor.app.model.Comparison;
import com.graphicsadvisor.app.model.Technology;

/** One saved comparison: view it, edit the note (UPDATE) or delete it (DELETE). */
public class ComparisonDetailActivity extends AppCompatActivity {
    private Comparison comparison;
    private TextInputEditText etNote;
    private TextInputLayout noteLayout;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comparison_detail);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        comparison = (Comparison) getIntent().getSerializableExtra("comparison");

        ((TextView) findViewById(R.id.tvTitle)).setText(comparison.gameName + " • " + comparison.gpuName);
        ((TextView) findViewById(R.id.tvMeta)).setText(getString(R.string.detail_meta,
                comparison.resolution, comparison.preset, Ui.date(comparison.createdAt)));
        TextView rec = findViewById(R.id.tvRec);
        rec.setText(comparison.recommended);
        rec.setTextColor(Technology.colorOf(comparison.recommended));
        ((TextView) findViewById(R.id.tvReason)).setText(comparison.reason);
        noteLayout = findViewById(R.id.noteLayout);
        etNote = findViewById(R.id.etNote);
        etNote.setText(comparison.note);
        progress = findViewById(R.id.progress);

        findViewById(R.id.btnUpdate).setOnClickListener(v -> updateNote());
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
    }

    private void updateNote() {
        String note = Ui.text(etNote);
        noteLayout.setError(null);
        if (note.length() > 200) {
            noteLayout.setError(getString(R.string.err_note_long));
            return;
        }
        comparison.note = note;
        Ui.show(progress, true);
        Repos.comparisons().save(comparison, Callback.of(saved -> {
            Toast.makeText(this, R.string.note_saved, Toast.LENGTH_SHORT).show();
            finish();
        }, err -> {
            Ui.show(progress, false);
            Ui.snackRetry(etNote, err, this::updateNote);
        }));
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_comparison_title)
                .setMessage(R.string.dialog_cannot_undo)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (d, w) -> delete())
                .show();
    }

    private void delete() {
        Ui.show(progress, true);
        Repos.comparisons().delete(comparison, Callback.of(r -> {
            Toast.makeText(this, R.string.comparison_deleted, Toast.LENGTH_SHORT).show();
            finish();
        }, err -> {
            Ui.show(progress, false);
            Ui.snackRetry(etNote, err, this::delete);
        }));
    }
}
