package com.graphicsadvisor.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.model.Comparison;
import com.graphicsadvisor.app.model.Technology;
import java.util.ArrayList;
import java.util.List;

/** Row list for saved comparisons. */
class ComparisonAdapter extends RecyclerView.Adapter<ComparisonAdapter.VH> {
    interface Listener {
        void onClick(Comparison c);
    }

    private final List<Comparison> items = new ArrayList<>();
    private final Listener listener;

    ComparisonAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<Comparison> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comparison, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Comparison c = items.get(position);
        h.title.setText(c.gameName + " • " + c.gpuName);
        h.meta.setText(c.resolution + " • " + c.preset + " • " + Ui.date(c.createdAt));
        h.rec.setText(c.recommended);
        h.rec.setTextColor(Technology.colorOf(c.recommended));
        h.itemView.setOnClickListener(v -> listener.onClick(c));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, meta, rec;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.tvTitle);
            meta = v.findViewById(R.id.tvMeta);
            rec = v.findViewById(R.id.tvRec);
        }
    }
}
