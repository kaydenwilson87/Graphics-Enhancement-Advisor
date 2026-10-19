package com.graphicsadvisor.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
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
import java.util.Arrays;
import java.util.List;

/** Admin-only list + add/edit/delete for GPUs or games (full CRUD), chosen by the "type" extra. */
public class AdminCatalogActivity extends AppCompatActivity {
    private static final List<String> VENDORS = Arrays.asList("NVIDIA", "AMD", "Intel");

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
        TextInputEditText etName = view.findViewById(R.id.etName);
        Spinner spVendor = view.findViewById(R.id.spVendor);
        CheckBox cbDlss = view.findViewById(R.id.cbDlss);
        CheckBox cbFsr = view.findViewById(R.id.cbFsr);
        CheckBox cbXess = view.findViewById(R.id.cbXess);
        TextView tvTechError = view.findViewById(R.id.tvTechError);

        nameLayout.setHint(isGpu() ? "GPU name" : "Game name");
        Ui.show(spVendor, isGpu());
        spVendor.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, VENDORS));
        if (existing != null) {
            etName.setText(existing.name);
            cbDlss.setChecked(existing.dlss);
            cbFsr.setChecked(existing.fsr);
            cbXess.setChecked(existing.xess);
            if (isGpu()) spVendor.setSelection(Math.max(0, VENDORS.indexOf(existing.vendor)));
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
            nameLayout.setError(null);
            Ui.show(tvTechError, false);
            boolean valid = true;
            if (name.length() < 2) {
                nameLayout.setError("Enter a name (at least 2 characters)");
                valid = false;
            }
            if (!cbDlss.isChecked() && !cbFsr.isChecked() && !cbXess.isChecked()) {
                Ui.show(tvTechError, true);
                valid = false;
            }
            if (!valid) return;

            CatalogItem item = new CatalogItem();
            item.id = existing == null ? null : existing.id;
            item.type = type;
            item.name = name;
            item.vendor = isGpu() ? (String) spVendor.getSelectedItem() : "";
            item.dlss = cbDlss.isChecked();
            item.fsr = cbFsr.isChecked();
            item.xess = cbXess.isChecked();
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
                            .setMessage("Saved comparisons keep their text, but the item can no longer be chosen.")
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
