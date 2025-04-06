package com.example.a11yshield.models;

import android.Manifest; // Import Manifest for permission constants
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.example.a11yshield.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppInfo implements Parcelable {
    private static final String TAG = "AppInfoModel_Enhanced";

    private String packageName;
    private String appName;
    private transient Drawable appIcon;
    private List<String> accessibilityServices;
    private List<String> requestedPermissions; // Store normal permissions
    private int criticalityScore;
    private boolean usesAccessibility;

    // Define sets of known service name patterns and sensitive permissions
    // (Refine these based on research)
    private static final Set<String> HIGH_RISK_SERVICE_PATTERNS = new HashSet<>(Arrays.asList(
            "screenreader", "talkback", "magnification", // Potential full screen access/control
            "password", "autofill", "credential",      // Access to sensitive input
            "remote", "mirroring", "control", "keylogger", "inputmethod" // Potential remote access/control/input capture
    ));
    private static final Set<String> MEDIUM_RISK_SERVICE_PATTERNS = new HashSet<>(Arrays.asList(
            "gesture", "voiceaccess", "automation",    // UI interaction/automation
            "keyinput", "keyboard", "inputservice",    // Potential input interception
            "switchaccess", "buttonmapper"             // Device interaction mapping
    ));
    private static final Set<String> SENSITIVE_PERMISSIONS = new HashSet<>(Arrays.asList(
            Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, // Still relevant for broad access
            "android.permission.MANAGE_EXTERNAL_STORAGE", // API 30+ broad access
            "android.permission.SYSTEM_ALERT_WINDOW", // Overlay permission
            "android.permission.PACKAGE_USAGE_STATS", // Usage access
            "android.permission.BIND_DEVICE_ADMIN" // Device admin rights
            // Add more as needed
    ));

    // Criticality Enum with Adjusted Score Mapping
    public enum Criticality {
        NONE(0, R.string.risk_none, R.color.risk_none),
        LOW(1, R.string.risk_low, R.color.risk_low),       // Score 1-3
        MEDIUM(2, R.string.risk_medium, R.color.risk_medium), // Score 4-6
        HIGH(3, R.string.risk_high, R.color.risk_high);     // Score 7+

        public final int score; public final int labelResId; public final int colorResId;
        Criticality(int score, int labelResId, int colorResId) { this.score = score; this.labelResId = labelResId; this.colorResId = colorResId; }

        public static Criticality fromScore(int score) {
            if (score <= 0) return NONE;   // Ensure score 0 maps to NONE
            if (score <= 3) return LOW;
            if (score <= 6) return MEDIUM;
            return HIGH;
        }
    }

    /** Constructor now includes requestedPermissions */
    public AppInfo(String packageName, String appName, Drawable appIcon,
                   List<String> accessibilityServices, List<String> requestedPermissions) {
        this.packageName = packageName;
        this.appName = appName != null ? appName : "Unknown App";
        this.appIcon = appIcon;
        this.accessibilityServices = accessibilityServices != null ? new ArrayList<>(accessibilityServices) : new ArrayList<>();
        this.requestedPermissions = requestedPermissions != null ? new ArrayList<>(requestedPermissions) : new ArrayList<>();
        this.usesAccessibility = !this.accessibilityServices.isEmpty();
        // Calculate score using the enhanced logic
        this.criticalityScore = calculateEnhancedCriticality(this.accessibilityServices, this.requestedPermissions);
        Log.d(TAG, "AppInfo created for " + this.packageName + ", Score: " + this.criticalityScore + ", Level: " + getCriticalityLevel());
    }

    /** Enhanced criticality calculation using service names and permissions */
    private int calculateEnhancedCriticality(List<String> services, List<String> permissions) {
        if (services == null || services.isEmpty()) {
            return Criticality.NONE.score; // 0
        }

        int totalScore = 0;
        Log.d(TAG, "Calculating score for " + packageName + " with " + services.size() + " services and " + (permissions != null ? permissions.size() : 0) + " permissions.");

        // --- Score based on specific accessibility service name patterns ---
        for (String serviceName : services) {
            if (serviceName == null) continue;
            String lowerServiceName = serviceName.toLowerCase(); // Compare case-insensitively
            int serviceScoreContribution = 0;
            boolean categorized = false;

            // Check High risk patterns
            for (String pattern : HIGH_RISK_SERVICE_PATTERNS) {
                if (lowerServiceName.contains(pattern)) { // Use lowercase pattern check
                    serviceScoreContribution = 3;
                    categorized = true;
                    Log.v(TAG, "Service '" + serviceName + "' matched HIGH pattern '" + pattern + "'. Score += 3");
                    break;
                }
            }
            if (!categorized) {
                // Check Medium risk patterns
                for (String pattern : MEDIUM_RISK_SERVICE_PATTERNS) {
                    if (lowerServiceName.contains(pattern)) {
                        serviceScoreContribution = 2;
                        categorized = true;
                        Log.v(TAG, "Service '" + serviceName + "' matched MEDIUM pattern '" + pattern + "'. Score += 2");
                        break;
                    }
                }
            }
            if (!categorized) {
                // If uncategorized, assign a base low score
                serviceScoreContribution = 1;
                Log.v(TAG, "Service '" + serviceName + "' is uncategorized. Score += 1");
            }
            totalScore += serviceScoreContribution;
        }
        Log.d(TAG,"Score after checking services: " + totalScore);

        // --- Add complementary score based on sensitive permissions ---
        int sensitivePermissionCount = 0;
        if (permissions != null) {
            for (String perm : permissions) {
                if (perm == null) continue;
                if (SENSITIVE_PERMISSIONS.contains(perm)) {
                    sensitivePermissionCount++;
                    Log.v(TAG, "Sensitive permission found: " + perm);
                }
            }
        }

        if (sensitivePermissionCount > 0) {
            // Boost score: Add 1 point for 1-2 sensitive perms, 2 points for 3+
            int permissionBoost = (sensitivePermissionCount >= 3) ? 2 : 1;
            totalScore += permissionBoost;
            Log.d(TAG, "Boosting score by " + permissionBoost + " due to " + sensitivePermissionCount + " sensitive permissions.");
        }

        Log.i(TAG, "Final calculated score for " + packageName + ": " + totalScore);
        return totalScore;
    }


    // --- Getters ---
    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public Drawable getAppIcon() { return appIcon; }
    public List<String> getAccessibilityServices() { return accessibilityServices; }
    public List<String> getRequestedPermissions() { return requestedPermissions; }
    public int getCriticalityScore() { return criticalityScore; }
    public boolean usesAccessibility() { return usesAccessibility; }
    public Criticality getCriticalityLevel() { return Criticality.fromScore(criticalityScore); }


    // --- Parcelable Implementation ---
    protected AppInfo(Parcel in) {
        packageName = in.readString();
        appName = in.readString();
        // appIcon is transient
        accessibilityServices = in.createStringArrayList();
        requestedPermissions = in.createStringArrayList(); // Read permissions
        criticalityScore = in.readInt();
        usesAccessibility = in.readByte() != 0;
    }

    public static final Creator<AppInfo> CREATOR = new Creator<AppInfo>() {
        @Override public AppInfo createFromParcel(Parcel in) { return new AppInfo(in); }
        @Override public AppInfo[] newArray(int size) { return new AppInfo[size]; }
    };

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeString(appName);
        // DO NOT write appIcon
        dest.writeStringList(accessibilityServices);
        dest.writeStringList(requestedPermissions); // Write permissions
        dest.writeInt(criticalityScore);
        dest.writeByte((byte) (usesAccessibility ? 1 : 0));
    }
}