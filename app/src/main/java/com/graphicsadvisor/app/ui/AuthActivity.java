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

/** Login and register on one screen (toggle at the top), plus forgot-password. */
public class AuthActivity extends AppCompatActivity {
    private boolean registerMode;
    private TextInputLayout nameLayout, emailLayout, passwordLayout;
    private TextInputEditText etName, etEmail, etPassword;
    private TextView tvError, tvForgot;
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
        nameLayout = findViewById(R.id.nameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvError = findViewById(R.id.tvError);
        tvForgot = findViewById(R.id.tvForgot);
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
        tvForgot.setOnClickListener(v -> forgotPassword());
        render();
    }

    /** Updates labels and visible fields for the current mode. */
    private void render() {
        Ui.show(nameLayout, registerMode);
        Ui.show(tvForgot, !registerMode);
        Ui.show(tvError, false);
        btnSubmit.setText(registerMode ? "Create account" : "Sign in");
        nameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
    }

    private void submit() {
        String name = Ui.text(etName);
        String email = Ui.text(etEmail);
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();

        nameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        boolean valid = true;
        if (registerMode && name.length() < 2) {
            nameLayout.setError("Enter your full name");
            valid = false;
        }
        if (!Ui.validEmail(email)) {
            emailLayout.setError("Enter a valid email address");
            valid = false;
        }
        if (password.length() < 10) {
            passwordLayout.setError("Password must be at least 10 characters");
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
        if (registerMode) Repos.auth().register(name, email, password, cb);
        else Repos.auth().login(email, password, cb);
    }

    private void forgotPassword() {
        String email = Ui.text(etEmail);
        if (!Ui.validEmail(email)) {
            emailLayout.setError("Enter your email above first");
            return;
        }
        Repos.auth().resetPassword(email, Callback.of(
                msg -> Ui.snack(btnSubmit, msg),
                err -> Ui.snack(btnSubmit, err)));
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
