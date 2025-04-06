package com.example.a11yshield.models;

import android.graphics.drawable.Drawable;

import java.util.List;

/**
 * Represents the results of scanning a single APK file.
 */
public class ApkInfo {
    private final AppInfo appInfo; // Reuse AppInfo for common fields and risk calculation
    private final List<String> otherPermissions; // Permissions other than A11y service binding

    public ApkInfo(AppInfo appInfo, List<String> otherPermissions) {
        this.appInfo = appInfo;
        this.otherPermissions = otherPermissions;
    }

    // --- Delegate getters to internal AppInfo ---
    public String getAppName() {
        return appInfo.getAppName();
    }

    public String getPackageName() {
        return appInfo.getPackageName();
    }

    public Drawable getAppIcon() {
        return appInfo.getAppIcon();
    }

    public List<String> getAccessibilityServices() {
        return appInfo.getAccessibilityServices();
    }

    public AppInfo.Criticality getCriticalityLevel() {
        return appInfo.getCriticalityLevel();
    }

    // --- Getter for additional APK-specific info ---
    public List<String> getOtherPermissions() {
        return otherPermissions;
    }
}