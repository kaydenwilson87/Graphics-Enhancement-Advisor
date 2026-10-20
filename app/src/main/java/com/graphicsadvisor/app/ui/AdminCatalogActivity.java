package com.graphicsadvisor.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Repos;
import com.graphicsadvisor.app.data.Session;
import com.graphicsadvisor.app.model.CatalogItem;

/** Admin-only list + add/edit/delete for GPUs or games (full CRUD), chosen by the "type" extra. */
public class AdminCatalogActivity extends AppCompatActivity {
    private String type;
    private CatalogAdapter adapter;
    private TextView tvEmpty;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Session.current == null || !Session.current.admin) { // block non-admins
            finish();
            return;
        }
        setContentView(R.layout.activity_admin_catalog);
        type = getIntent().getStringExtra("type");
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(isGpu() ? "Manage GPUs" : "Manage games");
        toolbar.setNavigationOnClickListener(v -> finish());
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);

        adapter = new CatalogAdapter(this::showEditor);
        RecyclerView rv = findViewById(R.id.rvItems);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        findViewById(R.id.fabAdd).setOnClickListener(v -> showEditor(null));
        load();
    }

    private boolean isGpu() {
        return CatalogItem.TYPE_GPU.equals(type);
    }

    private void load() {
        Ui.show(progress, true);
        Repos.catalog().list(type, Callback.of(list -> {
            Ui.show(progress, false);
            adapter.submit(list);
            Ui.show(tvEmpty, list.isEmpty());
        }, err -> {
            Ui.show(progress, false);
            Ui.snack(tvEmpty, err);
        }));
    }

    /** Add (existing == null) or edit dialog. */
    private void showEditor(CatalogItem existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_catalog_edit, null);
        TextInputLayout nameLayout = view.findViewById(R.id.nameLayout);
        TextInputLayout makerLayout = view.findViewById(R.id.makerLayout);
        TextInputLayout detailLayout = view.findViewById(R.id.detailLayout);
        TextInputLayout yearLayout = view.findViewById(R.id.yearLayout);
        TextInputEditText etName = view.findViewById(R.id.etName);
        TextInputEditText etMaker = view.findViewById(R.id.etMaker);
        TextInputEditText etDetail = view.findViewById(R.id.etDetail);
        TextInputEditText etYear = view.findViewById(R.id.etYear);
        TextInputEditText etNotes = view.findViewById(R.id.etNotes);

        nameLayout.setHint(isGpu() ? "GPU name" : "Game name");
        makerLayout.setHint(isGpu() ? "Manufacturer (required)" : "Developer");
        detailLayout.setHint(isGpu() ? "Architecture" : "Engine");
        Ui.show(yearLayout, !isGpu());
        if (existing != null) {
            etName.setText(existing.name);
            etMaker.setText(existing.maker);
            etDetail.setText(existing.detail);
            etNotes.setText(existing.notes);
            if (existing.releaseYear > 0) etYear.setText(String.valueOf(existing.releaseYear));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle((existing == null ? "Add " : "Edit ") + (isGpu() ? "GPU" : "game"))
                .setView(view)
                .setPositiveButton("Save", null)   // listeners set below so invalid input keeps the dialog open
                .setNegativeButton("Cancel", null);
        if (existing != null) builder.setNeutralButton("Delete", null);
        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = Ui.text(etName);
            String maker = Ui.text(etMaker);
            String yearText = Ui.text(etYear);
            nameLayout.setError(null);
            makerLayout.setError(null);
            yearLayout.setError(null);

            boolean valid = true;
            if (name.length() < 2) {
                nameLayout.setError("Enter a name (at least 2 characters)");
                valid = false;
            }
            if (isGpu() && maker.isEmpty()) {
                makerLayout.setError("Manufacturer is required");
                valid = false;
            }
            int year = 0;
            if (!isGpu() && !yearText.isEmpty()) {
                try {
                    year = Integer.parseInt(yearText);
                } catch (NumberFormatException e) {
                    year = -1;
                }
                if (year < 1990 || year > 2100) {
                    yearLayout.setError("Enter a year between 1990 and 2100");
                    valid = false;
                }
            }
            if (!valid) return;

            CatalogItem item = new CatalogItem();
            item.id = existing == null ? null : existing.id;
            item.type = type;
            item.name = name;
            item.maker = maker;
            item.detail = Ui.text(etDetail);
            item.releaseYear = year;
            item.notes = Ui.text(etNotes);
            Repos.catalog().save(item, Callback.of(saved -> {
                dialog.dismiss();
                Ui.snack(tvEmpty, existing == null ? "Added " + name : "Saved " + name);
                load();
            }, err -> nameLayout.setError(err)));
        });

        if (existing != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Delete " + existing.name + "?")
                            .setMessage("Compatibility and research records linked to it are deleted too.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (d, w) ->
                                    Repos.catalog().delete(existing, Callback.of(r -> {
                                        dialog.dismiss();
                                        Ui.snack(tvEmpty, "Deleted " + existing.name);
                                        load();
                                    }, err -> Ui.snack(tvEmpty, err))))
                            .show());
        }
    }
}
