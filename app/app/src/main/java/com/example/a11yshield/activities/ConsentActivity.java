package com.example.a11yshield.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog; // Use AppCompat Dialog
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.a11yshield.activities.MainActivity;
import com.example.a11yshield.R;
import com.google.android.material.snackbar.Snackbar; // Keep Snackbar for settings guidance

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConsentActivity extends AppCompatActivity {

    private static final String TAG = "A11y_Consent_Internal"; // New Tag
    private Button btnGrantPermissions, btnProceed;
    private View containerView;
    // Removed About Risks link TextView as per undo request (can be added back if needed)
    // private TextView tvAboutRisksLink;

    // Define permissions needed: ONLY QUERY_ALL_PACKAGES on R+
    private static final String queryPackagesPermission = Manifest.permission.QUERY_ALL_PACKAGES;
    private static final String[] REQUIRED_PERMISSIONS;
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            REQUIRED_PERMISSIONS = new String[]{queryPackagesPermission};
            Log.i(TAG, "Required permissions (API " + Build.VERSION.SDK_INT + "): " + String.join(", ", REQUIRED_PERMISSIONS));
        } else {
            REQUIRED_PERMISSIONS = new String[]{};
            Log.i(TAG, "Required permissions (API " + Build.VERSION.SDK_INT + "): None");
        }
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                Log.d(TAG, "--- Permission Result Callback START ---");
                boolean needsSettingsGuidance = false;

                Log.d(TAG, "Callback Results Map: " + permissions);

                // Check if the required permission (if any) was granted IN THIS CALLBACK
                boolean grantedInCallback = true; // Assume true if no permissions requested
                if (REQUIRED_PERMISSIONS.length > 0) {
                    grantedInCallback = permissions.getOrDefault(REQUIRED_PERMISSIONS[0], false);
                }

                if (!grantedInCallback) {
                    Log.w(TAG, "Callback: Permission " + (REQUIRED_PERMISSIONS.length > 0 ? REQUIRED_PERMISSIONS[0] : "N/A") + " was DENIED in callback.");
                    // Check rationale *after* denial
                    if (REQUIRED_PERMISSIONS.length > 0 && !ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])) {
                        Log.w(TAG, "Callback - Rationale NOT shown. Potential permanent denial or OS issue (like Android 15 Beta?). Guiding to settings.");
                        needsSettingsGuidance = true;
                    } else {
                        Log.i(TAG, "Callback - Rationale SHOULD be shown or no rationale needed.");
                        // Show simple denial, user can press button again
                    }
                    showPermissionDeniedSnackbar(needsSettingsGuidance);
                } else if (REQUIRED_PERMISSIONS.length > 0) {
                    Log.i(TAG, "Callback: Permission " + REQUIRED_PERMISSIONS[0] + " GRANTED in callback.");
                    // Success toast is shown in updateUIBasedOnPermissions
                } else {
                    Log.i(TAG, "Callback: No permissions needed/requested, proceeding.");
                }

                Log.d(TAG, "Callback - Calling updateUiBasedOnPermissions() post-callback.");
                updateUIBasedOnPermissions(); // Always refresh UI based on current state
                Log.d(TAG, "--- Permission Result Callback END ---");
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent); // Ensure this layout is the simpler one
        Log.d(TAG, "--- onCreate START --- API Level: " + Build.VERSION.SDK_INT);

        containerView = findViewById(R.id.consent_container);
        if (containerView == null) { containerView = findViewById(android.R.id.content); }
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions);
        btnProceed = findViewById(R.id.btnProceed);
        // tvAboutRisksLink = findViewById(R.id.tvAboutRisksLink); // Remove if link is removed

        // setupAboutRisksLink(); // Remove if link is removed

        btnGrantPermissions.setOnClickListener(v -> {
            Log.i(TAG, "Grant Permissions button clicked.");
            checkAndRequestPermissions();
        });
        btnProceed.setOnClickListener(v -> {
            Log.i(TAG, "Proceed button clicked.");
            proceedToDashboard();
        });

        Log.d(TAG, "onCreate - Performing initial UI update.");
        updateUIBasedOnPermissions();
        Log.d(TAG, "--- onCreate END ---");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "--- onResume START - Updating UI ---");
        updateUIBasedOnPermissions();
        Log.d(TAG, "--- onResume END ---");
    }

    /* // Remove setupAboutRisksLink if link is removed
    private void setupAboutRisksLink() { ... }
    */

    // Checks the current state of required permissions
    private boolean checkPermissions() {
        if (REQUIRED_PERMISSIONS.length == 0) {
            Log.d(TAG, "checkPermissions: No permissions required for this API level.");
            return true; // No permissions needed
        }
        // Check the single required permission (QAP on R+)
        String permissionToCheck = REQUIRED_PERMISSIONS[0];
        boolean granted = (ContextCompat.checkSelfPermission(this, permissionToCheck) == PackageManager.PERMISSION_GRANTED);
        Log.d(TAG, "checkPermissions: Checking " + permissionToCheck + " | Granted: " + granted);
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Log specifically for Android 15 Beta observation
            Log.w(TAG, queryPackagesPermission + " check returned DENIED. If on Android 15 Beta, runtime request might fail. Try granting via ADB: 'adb shell pm grant " + getPackageName() + " " + queryPackagesPermission + "'");
        }
        return granted;
    }

    // Requests permissions ONLY if missing
    private void checkAndRequestPermissions() {
        Log.d(TAG, "--- checkAndRequestPermissions START ---");
        if (REQUIRED_PERMISSIONS.length == 0) {
            Log.i(TAG,"No permissions defined for this API level.");
            updateUIBasedOnPermissions();
            return;
        }

        if (!checkPermissions()) {
            String permissionToRequest = REQUIRED_PERMISSIONS[0];
            Log.i(TAG, "Requesting missing permission: " + permissionToRequest);
            Toast.makeText(this, R.string.query_permission_info, Toast.LENGTH_LONG).show(); // Inform user about QAP
            requestPermissionLauncher.launch(new String[]{permissionToRequest});
        } else {
            Log.i(TAG, "Permission already granted, no request needed.");
            Toast.makeText(this, R.string.permissions_already_granted, Toast.LENGTH_SHORT).show();
            updateUIBasedOnPermissions();
        }
        Log.d(TAG, "--- checkAndRequestPermissions END ---");
    }

    // Shows Snackbar (Simple denial or Guide to Settings)
    private void showPermissionDeniedSnackbar(boolean guideToSettings) {
        if (containerView == null) return;
        int messageResId = guideToSettings ? R.string.permission_denied_settings : R.string.permission_denied_basic;
        Log.d(TAG, "Showing Snackbar: " + getString(messageResId));
        Snackbar snackbar = Snackbar.make(containerView, messageResId, Snackbar.LENGTH_LONG);
        if (guideToSettings) {
            snackbar.setAction(R.string.settings, v -> {
                Log.i(TAG, "Snackbar 'Settings' action clicked.");
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG,"Failed to open app settings", e);
                    Toast.makeText(this, R.string.cannot_open_settings, Toast.LENGTH_SHORT).show();
                }
            });
        }
        snackbar.show();
    }

    // Updates UI based on current permission state
    private void updateUIBasedOnPermissions() {
        Log.d(TAG, "--- updateUIBasedOnPermissions START ---");
        boolean hasAllPermissions = checkPermissions();
        Log.d(TAG, "Result of checkPermissions() for UI update: " + hasAllPermissions);

        btnProceed.setEnabled(hasAllPermissions);

        // Only show Grant button if permissions are required AND not yet granted
        boolean needsGrantButton = (REQUIRED_PERMISSIONS.length > 0 && !hasAllPermissions);
        btnGrantPermissions.setEnabled(needsGrantButton);
        btnGrantPermissions.setVisibility(needsGrantButton ? View.VISIBLE : View.GONE); // Hide if not needed

        // Show success toast only once when state changes to granted
        if (hasAllPermissions && !btnGrantPermissions.isEnabled() && btnGrantPermissions.getVisibility() == View.GONE) {
            // Check if it *was* previously denied (button was visible/enabled)
            // This logic is tricky, maybe just check if Proceed button *was* disabled
            if (!btnProceed.isEnabled()) { // If proceed button *was* disabled before this update
                // Toast.makeText(this, R.string.permissions_granted_success, Toast.LENGTH_SHORT).show();
                // ^ Consider if this toast is annoying after granting via settings/ADB
            }
        }

        Log.d(TAG, "UI Update: Proceed Enabled=" + hasAllPermissions + ", Grant Button Visible/Enabled=" + needsGrantButton);
        Log.d(TAG, "--- updateUIBasedOnPermissions END ---");
    }

    // Proceeds to dashboard if permissions are met
    private void proceedToDashboard() {
        Log.d(TAG, "--- proceedToDashboard START ---");
        if (checkPermissions()) {
            Log.i(TAG, "Permissions confirmed. Proceeding to MainActivity.");
            Intent intent = new Intent(ConsentActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // Prevent returning to Consent screen
        } else {
            Log.e(TAG, "Proceed button clicked, but permissions check failed!");
            // Show specific message for QAP if that's the missing one on R+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && REQUIRED_PERMISSIONS.length > 0 && ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[0]) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Query All Packages permission is required.", Toast.LENGTH_LONG).show();
                // Guide to settings strongly if runtime request seems broken
                showPermissionDeniedSnackbar(true);
            } else {
                Toast.makeText(this, R.string.please_grant_permissions, Toast.LENGTH_SHORT).show();
            }
            updateUIBasedOnPermissions(); // Re-sync UI
        }
        Log.d(TAG, "--- proceedToDashboard END ---");
    }
}