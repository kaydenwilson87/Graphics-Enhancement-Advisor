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
    private boolean hasItems; // false when the server has nothing yet
    private CatalogAdapter adapter;
    private TextInputEditText etSearch;
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
        toolbar.setTitle(isGpu() ? R.string.admin_title_gpus : R.string.admin_title_games);
        toolbar.setNavigationOnClickListener(v -> finish());
        etSearch = findViewById(R.id.etSearch);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);

        adapter = new CatalogAdapter(this::showEditor);
        RecyclerView rv = findViewById(R.id.rvItems);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        Ui.onTextChanged(etSearch, () -> {
            adapter.setQuery(Ui.text(etSearch));
            updateEmpty();
        });
        findViewById(R.id.fabAdd).setOnClickListener(v -> showEditor(null));
        load();
    }

    private boolean isGpu() {
        return CatalogItem.TYPE_GPU.equals(type);
    }

    /** Loads the list; a failure offers a Retry button. */
    private void load() {
        Ui.show(progress, true);
        Repos.catalog().list(type, Callback.of(list -> {
            Ui.show(progress, false);
            hasItems = !list.isEmpty();
            adapter.submit(list);
            updateEmpty();
        }, err -> {
            Ui.show(progress, false);
            Ui.snackRetry(tvEmpty, err, this::load);
        }));
    }

    /** Shows "nothing here yet" or "no matches" when the visible list is empty. */
    private void updateEmpty() {
        boolean none = adapter.getItemCount() == 0;
        Ui.show(tvEmpty, none);
        tvEmpty.setText(hasItems ? R.string.no_matches : R.string.admin_empty);
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

        nameLayout.setHint(getString(isGpu() ? R.string.hint_gpu_name : R.string.hint_game_name));
        makerLayout.setHint(getString(isGpu() ? R.string.hint_manufacturer : R.string.hint_developer));
        detailLayout.setHint(getString(isGpu() ? R.string.hint_architecture : R.string.hint_engine));
        Ui.show(yearLayout, !isGpu());
        if (existing != null) {
            etName.setText(existing.name);
            etMaker.setText(existing.maker);
            etDetail.setText(existing.detail);
            etNotes.setText(existing.notes);
            if (existing.releaseYear > 0) etYear.setText(String.valueOf(existing.releaseYear));
        }

        int title = existing == null
                ? (isGpu() ? R.string.dialog_add_gpu : R.string.dialog_add_game)
                : (isGpu() ? R.string.dialog_edit_gpu : R.string.dialog_edit_game);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.btn_save, null) // listeners set below so invalid input keeps the dialog open
                .setNegativeButton(R.string.btn_cancel, null);
        if (existing != null) builder.setNeutralButton(R.string.btn_delete, null);
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
                nameLayout.setError(getString(R.string.err_name_short));
                valid = false;
            }
            if (isGpu() && maker.isEmpty()) {
                makerLayout.setError(getString(R.string.err_maker_required));
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
                    yearLayout.setError(getString(R.string.err_year));
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
                Ui.snack(tvEmpty, getString(existing == null ? R.string.snack_added : R.string.snack_saved_item, name));
                load();
            }, err -> nameLayout.setError(err)));
        });

        if (existing != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.dialog_delete_item_title, existing.name))
                            .setMessage(R.string.dialog_delete_item_msg)
                            .setNegativeButton(R.string.btn_cancel, null)
                            .setPositiveButton(R.string.btn_delete, (d, w) -> deleteItem(existing, dialog))
                            .show());
        }
    }

    private void deleteItem(CatalogItem item, AlertDialog editor) {
        Repos.catalog().delete(item, Callback.of(r -> {
            editor.dismiss();
            Ui.snack(tvEmpty, getString(R.string.snack_deleted, item.name));
            load();
        }, err -> Ui.snackRetry(tvEmpty, err, () -> deleteItem(item, editor))));
    }
}
