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
import com.graphicsadvisor.app.data.TokenStore;
import com.graphicsadvisor.app.model.User;

/** Login and register on one screen. Also restores a saved login so users stay signed in. */
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
        restoreSavedLogin();
    }

    /** If the user signed in before, log them straight back in without asking again. */
    private void restoreSavedLogin() {
        String saved = TokenStore.load(this);
        if (saved == null) return;
        setLoading(true);
        Repos.auth().restore(saved, Callback.of(user -> {
            Session.current = user;
            TokenStore.save(this, Session.refreshToken); // keep it fresh if the server rotated it
            openMain();
        }, message -> setLoading(false))); // expired or server offline: just show the login form
    }

    /** Updates labels and clears messages for the current mode. */
    private void render() {
        Ui.show(tvError, false);
        btnSubmit.setText(registerMode ? R.string.btn_create_account : R.string.btn_sign_in);
        passwordLayout.setHelperText(registerMode ? getString(R.string.password_helper, MIN_PASSWORD_LENGTH) : null);
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
            emailLayout.setError(getString(R.string.err_email));
            valid = false;
        }
        if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.err_password_required));
            valid = false;
        } else if (registerMode && password.length() < MIN_PASSWORD_LENGTH) {
            passwordLayout.setError(getString(R.string.err_password_short, MIN_PASSWORD_LENGTH));
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        Callback<User> cb = Callback.of(user -> {
            Session.current = user;
            TokenStore.save(this, Session.refreshToken);
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
