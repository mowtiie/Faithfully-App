package com.mowtiie.faithfully.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivityLoginBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.NetworkHelper;

import java.util.Objects;

public class LoginActivity extends FaithfullyActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AuthHelper.hasChosenMode(this)) {
            goToMain();
            return;
        }

        EdgeToEdge.enable(this);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        binding.btnLogin.setOnClickListener(v -> attemptSignIn());

        binding.btnContinueAsGuest.setOnClickListener(v -> {
            AuthHelper.setGuestMode(this, true);
            Toast.makeText(LoginActivity.this, "Logging in as guest", Toast.LENGTH_SHORT).show();
            goToMain();
        });
    }

    private void attemptSignIn() {
        String email = Objects.requireNonNull(binding.inputEmail.getText()).toString().trim();
        String password = Objects.requireNonNull(binding.inputPassword.getText()).toString().trim();

        if (email.isEmpty()) {
            binding.layoutEmail.setError("This field is required");
            binding.inputEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            binding.layoutPassword.setError("This field is required");
            binding.inputPassword.requestFocus();
            return;
        }

        if (NetworkHelper.isOffline(this)) {
            Toast.makeText(this, "You're currently offline.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (!AuthHelper.canReadRealData()) {
                        mAuth.signOut();
                        setLoading(false);
                        Toast.makeText(this, "That account isn't authorized to view this app.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    setLoading(false);
                    AuthHelper.setGuestMode(this, false);

                    Toast.makeText(this, "Welcome back, Mowtiie.", Toast.LENGTH_SHORT).show();
                    goToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Wrong email or password.", Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnContinueAsGuest.setEnabled(!loading);

        binding.layoutEmail.setEnabled(!loading);
        binding.inputEmail.setEnabled(!loading);

        binding.layoutPassword.setEnabled(!loading);
        binding.inputPassword.setEnabled(!loading);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}