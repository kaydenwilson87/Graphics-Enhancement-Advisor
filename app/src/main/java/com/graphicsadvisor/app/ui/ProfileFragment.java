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
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Session;
import com.graphicsadvisor.app.model.CatalogItem;
import com.graphicsadvisor.app.model.User;

/** Profile tab: account details, admin entry points and sign out. */
public class ProfileFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);
        User u = Session.current;
        ((TextView) v.findViewById(R.id.tvAvatar)).setText(u.name.substring(0, 1).toUpperCase());
        ((TextView) v.findViewById(R.id.tvName)).setText(u.name);
        ((TextView) v.findViewById(R.id.tvEmail)).setText(u.email);
        ((TextView) v.findViewById(R.id.tvRole)).setText(u.admin ? "Administrator" : "Gamer");

        // Admin tools are only shown to admin accounts (and re-checked in AdminCatalogActivity).
        View gpus = v.findViewById(R.id.btnManageGpus);
        View games = v.findViewById(R.id.btnManageGames);
        Ui.show(gpus, u.admin);
        Ui.show(games, u.admin);
        gpus.setOnClickListener(x -> openAdmin(CatalogItem.TYPE_GPU));
        games.setOnClickListener(x -> openAdmin(CatalogItem.TYPE_GAME));

        v.findViewById(R.id.btnSignOut).setOnClickListener(x -> {
            Session.clear();
            Intent i = new Intent(requireContext(), AuthActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
        return v;
    }

    private void openAdmin(String type) {
        Intent i = new Intent(requireContext(), AdminCatalogActivity.class);
        i.putExtra("type", type);
        startActivity(i);
    }
}
