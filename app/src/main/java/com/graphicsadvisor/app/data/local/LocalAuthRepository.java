package com.graphicsadvisor.app.data.local;

import com.graphicsadvisor.app.data.AuthRepository;
import com.graphicsadvisor.app.data.Callback;
import com.graphicsadvisor.app.model.User;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** In-memory accounts for development. Data resets when the app is closed. */
public class LocalAuthRepository implements AuthRepository {
    private static class Account {
        final String password;
        final User user;

        Account(String password, User user) {
            this.password = password;
            this.user = user;
        }
    }

    private final Map<String, Account> accounts = new HashMap<>();

    public LocalAuthRepository() {
        add("Admin", "admin@advisor.com", "Admin123", true);   // demo admin
        add("Demo User", "user@advisor.com", "User1234", false); // demo gamer
    }

    private void add(String name, String email, String password, boolean admin) {
        accounts.put(email, new Account(password,
                new User(UUID.randomUUID().toString(), name, email, admin)));
    }

    @Override
    public void login(String email, String password, Callback<User> cb) {
        Async.later(() -> {
            Account a = accounts.get(email.trim().toLowerCase(Locale.ROOT));
            if (a != null && a.password.equals(password)) cb.onSuccess(a.user);
            else cb.onError("Incorrect email or password.");
        });
    }

    @Override
    public void register(String name, String email, String password, Callback<User> cb) {
        Async.later(() -> {
            String key = email.trim().toLowerCase(Locale.ROOT);
            if (accounts.containsKey(key)) {
                cb.onError("An account with this email already exists.");
                return;
            }
            add(name, key, password, false);
            cb.onSuccess(accounts.get(key).user);
        });
    }

    @Override
    public void resetPassword(String email, Callback<String> cb) {
        // Same message whether or not the account exists, so emails are not revealed.
        Async.later(() -> cb.onSuccess("If this email is registered, a reset link has been sent."));
    }

    @Override
    public void logout() { /* nothing to clear locally */ }
}
