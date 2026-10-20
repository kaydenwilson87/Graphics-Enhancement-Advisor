package com.graphicsadvisor.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.graphicsadvisor.app.R;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.data.Repos;
import com.graphicsadvisor.app.data.Session;
import com.graphicsadvisor.app.model.User;

/** Login and register on one screen (toggle at the top). */
public class AuthActivity extends AppCompatActivity {
    private static final int MIN_PASSWORD_LENGTH = 10; // the server rejects shorter passwords

    private boolean registerMode;
    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText etEmail, etPassword;
    private TextView tvError;
    private View progress;
    private MaterialButton btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Session.current != null) { // already signed in
            openMain();
            return;
        }
        setContentView(R.layout.activity_auth);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvError = findViewById(R.id.tvError);
        progress = findViewById(R.id.progress);
        btnSubmit = findViewById(R.id.btnSubmit);

        MaterialButtonToggleGroup toggle = findViewById(R.id.toggle);
        toggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                registerMode = (id == R.id.tabRegister);
                render();
            }
        });
        btnSubmit.setOnClickListener(v -> submit());
        render();
    }

    /** Updates labels and clears messages for the current mode. */
    private void render() {
        Ui.show(tvError, false);
        btnSubmit.setText(registerMode ? "Create account" : "Sign in");
        passwordLayout.setHelperText(registerMode ? "At least " + MIN_PASSWORD_LENGTH + " characters" : null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
    }

    private void submit() {
        String email = Ui.text(etEmail);
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();

        emailLayout.setError(null);
        passwordLayout.setError(null);
        boolean valid = true;
        if (!Ui.validEmail(email)) {
            emailLayout.setError("Enter a valid email address");
            valid = false;
        }
        if (password.isEmpty()) {
            passwordLayout.setError("Enter your password");
            valid = false;
        } else if (registerMode && password.length() < MIN_PASSWORD_LENGTH) {
            passwordLayout.setError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        Callback<User> cb = Callback.of(user -> {
            Session.current = user;
            openMain();
        }, message -> {
            setLoading(false);
            tvError.setText(message);
            Ui.show(tvError, true);
        });
        if (registerMode) Repos.auth().register(email, password, cb);
        else Repos.auth().login(email, password, cb);
    }

    private void setLoading(boolean loading) {
        Ui.show(progress, loading);
        btnSubmit.setEnabled(!loading);
        if (loading) Ui.show(tvError, false);
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
