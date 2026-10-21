package com.graphicsadvisor.app.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.model.CatalogItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Row list for GPUs/games. Used for choosing (wizard) and for admin editing. */
class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.VH> {
    interface Listener {
        void onClick(CatalogItem item);
    }

    private final List<CatalogItem> all = new ArrayList<>();   // everything from the server
    private final List<CatalogItem> items = new ArrayList<>(); // what the search currently shows
    private final Listener listener;
    private String selectedId;
    private String query = "";

    CatalogAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<CatalogItem> list) {
        all.clear();
        all.addAll(list);
        applyFilter();
    }

    /** Shows only items whose name or description contains the text (case-insensitive). */
    void setQuery(String text) {
        query = text.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    private void applyFilter() {
        items.clear();
        for (CatalogItem item : all) {
            String haystack = (item.name + " " + item.subtitle()).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || haystack.contains(query)) items.add(item);
        }
        notifyDataSetChanged();
    }

    void setSelected(String id) {
        selectedId = id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_catalog, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CatalogItem item = items.get(position);
        h.name.setText(item.name);
        h.meta.setText(item.subtitle());
        boolean selected = item.id.equals(selectedId);
        h.card.setStrokeWidth(selected ? 5 : 2);
        h.card.setStrokeColor(selected ? Color.BLACK : Color.parseColor("#E0E0E0"));
        h.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView name, meta;

        VH(View v) {
            super(v);
            card = (MaterialCardView) v;
            name = v.findViewById(R.id.tvName);
            meta = v.findViewById(R.id.tvMeta);
        }
    }
}
