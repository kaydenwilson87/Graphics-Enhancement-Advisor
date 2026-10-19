package com.graphicsadvisor.app.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Session;

/** Hosts the three bottom-tab screens: Home, Comparisons, Profile. */
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Session.current == null) { // access only for signed-in users
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f;
            if (id == R.id.navComparisons) f = new ComparisonsFragment();
            else if (id == R.id.navProfile) f = new ProfileFragment();
            else f = new HomeFragment();
            getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit();
            return true;
        });
        if (savedInstanceState == null) nav.setSelectedItemId(R.id.navHome);
    }
}
