package com.example.a11yshield.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.a11yshield.R;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        Button btnGetStarted = findViewById(R.id.btnGetStarted);

        btnGetStarted.setOnClickListener(v -> {
            Intent intent = new Intent(SplashActivity.this, ConsentActivity.class);
            startActivity(intent);
            finish(); // Prevent going back to splash
        });

        // Optional: Add a slight delay before enabling the button or auto-navigating
        // btnGetStarted.setEnabled(false);
        // new Handler(Looper.getMainLooper()).postDelayed(() -> {
        //     btnGetStarted.setEnabled(true);
        // }, 1500); // 1.5 second delay
    }
}