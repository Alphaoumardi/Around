package com.around.appl;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText emailInput, passwordInput;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);

        // If already logged in → go to home
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }

        loginButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        Toast.makeText(this, "Login success!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        // If login fails, try register
                        auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener(result -> {
                                    Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(this, MainActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(err ->
                                        Toast.makeText(this, "Error: " + err.getMessage(), Toast.LENGTH_SHORT).show());
                    });
        });
    }
}

