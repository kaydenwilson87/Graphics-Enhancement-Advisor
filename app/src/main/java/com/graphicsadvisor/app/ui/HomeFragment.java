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
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Repos;
import com.graphicsadvisor.app.data.Session;
import com.graphicsadvisor.app.model.CatalogItem;

/** Home tab: greeting, quick stats and the three most recent comparisons. */
public class HomeFragment extends Fragment {
    private TextView tvGames, tvSaved, tvEmpty;
    private ComparisonAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);
        ((TextView) v.findViewById(R.id.tvGreeting)).setText(getString(R.string.welcome_back, Session.current.name));
        tvGames = v.findViewById(R.id.tvGameCount);
        tvSaved = v.findViewById(R.id.tvSavedCount);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        v.findViewById(R.id.btnNew).setOnClickListener(x ->
                startActivity(new Intent(requireContext(), NewComparisonActivity.class)));

        adapter = new ComparisonAdapter(c -> {
            Intent i = new Intent(requireContext(), ComparisonDetailActivity.class);
            i.putExtra("comparison", c);
            startActivity(i);
        });
        RecyclerView rv = v.findViewById(R.id.rvRecent);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        // The game count is a nice-to-have, so a failure there is ignored; the list below reports errors.
        Repos.catalog().list(CatalogItem.TYPE_GAME, Callback.of(
                games -> { if (isAdded()) tvGames.setText(String.valueOf(games.size())); },
                err -> { }));
        Repos.comparisons().list(Callback.of(list -> {
            if (!isAdded()) return;
            tvSaved.setText(String.valueOf(list.size()));
            adapter.submit(list.subList(0, Math.min(3, list.size())));
            Ui.show(tvEmpty, list.isEmpty());
        }, err -> {
            if (isAdded()) Ui.snackRetry(requireView(), err, this::load);
        }));
    }
}
