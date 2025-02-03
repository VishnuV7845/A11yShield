package com.example.a11yshield;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startButton = findViewById(R.id.startButton);

        startButton.setOnClickListener(v -> {
            // Check if the required permissions are granted
            if (!PermissionUtils.arePermissionsGranted(MainActivity.this)) {
                // If not, go to the permissions request page
                Intent intent = new Intent(MainActivity.this, PermissionsActivity.class);
                startActivity(intent);
            } else {
                // If permissions are granted, move to the main functionality
                Intent intent = new Intent(MainActivity.this, AppListActivity.class);
                startActivity(intent);
            }
        });
    }
}
