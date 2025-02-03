package com.example.a11yshield;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

public class PermissionUtils {

    // Check if Usage Stats permission is granted
    public static boolean hasUsageStatsPermission(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), "package_usage_stats");
        return !TextUtils.isEmpty(enabled);
    }

    // Check if the app can draw overlays
    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    // Check if the necessary permissions are granted
    public static boolean arePermissionsGranted(Context context) {
        return hasUsageStatsPermission(context) && canDrawOverlays(context);
    }
}
