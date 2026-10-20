package com.graphicsadvisor.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Repos;
import com.graphicsadvisor.app.model.Comparison;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Comparisons tab: the user's saved results with search and a technology filter (READ). */
public class ComparisonsFragment extends Fragment {
    private final List<Comparison> all = new ArrayList<>();
    private String techFilter; // null = all
    private ComparisonAdapter adapter;
    private TextInputEditText etSearch;
    private TextView tvEmpty;
    private View progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_comparisons, container, false);
        etSearch = v.findViewById(R.id.etSearch);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        progress = v.findViewById(R.id.progress);

        adapter = new ComparisonAdapter(c -> {
            Intent i = new Intent(requireContext(), ComparisonDetailActivity.class);
            i.putExtra("comparison", c);
            startActivity(i);
        });
        RecyclerView rv = v.findViewById(R.id.rvComparisons);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        Ui.onTextChanged(etSearch, this::applyFilter);
        ChipGroup chips = v.findViewById(R.id.chips);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.chipAll : ids.get(0);
            techFilter = id == R.id.chipDlss ? "DLSS" : id == R.id.chipFsr ? "FSR"
                    : id == R.id.chipXess ? "XeSS" : null;
            applyFilter();
        });
        return v;
    }

    @Override
    public void onResume() { // reload after edits or deletes in the detail screen
        super.onResume();
        Ui.show(progress, true);
        Repos.comparisons().list(Callback.of(list -> {
            if (!isAdded()) return;
            Ui.show(progress, false);
            all.clear();
            all.addAll(list);
            applyFilter();
        }, err -> {
            if (!isAdded()) return;
            Ui.show(progress, false);
            Ui.snack(requireView(), err);
        }));
    }

    private void applyFilter() {
        String q = Ui.text(etSearch).toLowerCase(Locale.ROOT);
        List<Comparison> out = new ArrayList<>();
        for (Comparison c : all) {
            String hay = (c.gpuName + " " + c.gameName + " " + c.note).toLowerCase(Locale.ROOT);
            boolean matchesText = q.isEmpty() || hay.contains(q);
            boolean matchesTech = techFilter == null || techFilter.equals(c.recommended);
            if (matchesText && matchesTech) out.add(c);
        }
        adapter.submit(out);
        Ui.show(tvEmpty, out.isEmpty());
    }
}
