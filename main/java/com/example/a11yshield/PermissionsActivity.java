package com.example.a11yshield;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class PermissionsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SYSTEM_ALERT = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        Button usageStatsButton = findViewById(R.id.usageStatsButton);
        Button drawOverlaysButton = findViewById(R.id.drawOverlaysButton);
        Button accessibilityServiceButton = findViewById(R.id.accessibilityServiceButton);
        Button btnContinue = findViewById(R.id.btnContinue);

        usageStatsButton.setOnClickListener(v -> requestUsageStatsPermission());
        drawOverlaysButton.setOnClickListener(v -> requestDrawOverlaysPermission());
        accessibilityServiceButton.setOnClickListener(v -> requestAccessibilityServicePermission());

        btnContinue.setOnClickListener(v -> goToNextActivity());
    }

    private void requestUsageStatsPermission() {
        if (!PermissionUtils.hasUsageStatsPermission(this)) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Usage Stats Permission Already Granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestDrawOverlaysPermission() {
        if (!PermissionUtils.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, REQUEST_CODE_SYSTEM_ALERT);
        } else {
            Toast.makeText(this, "Draw Overlays Permission Already Granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestAccessibilityServicePermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SYSTEM_ALERT) {
            if (PermissionUtils.canDrawOverlays(this)) {
                Toast.makeText(this, "Draw Overlays Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Draw Overlays Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void goToNextActivity() {
        Intent intent = new Intent(this, AppListActivity.class);
        startActivity(intent);
    }
}
