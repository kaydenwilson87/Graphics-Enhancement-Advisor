package com.graphicsadvisor.app.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.snackbar.Snackbar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small helpers shared by all screens. */
final class Ui {
    private Ui() { }

    static boolean validEmail(String s) {
        return s != null && Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches();
    }

    static void show(View v, boolean visible) {
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    static void snack(View anchor, String message) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show();
    }

    /** Trimmed text of an EditText/TextView, never null. */
    static String text(TextView t) {
        return t.getText() == null ? "" : t.getText().toString().trim();
    }

    static String date(long millis) {
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(millis));
    }

    static void onTextChanged(TextView t, Runnable r) {
        t.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { r.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }
}
