package com.example.a11yshield;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class YourAccessibilityService extends AccessibilityService {

    private static final String TAG = "A11yShieldService";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Handle different event types based on your needs.
        int eventType = event.getEventType();

        switch (eventType) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                Log.d(TAG, "View Clicked: " + event.getText());
                break;
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                Log.d(TAG, "View Scrolled: " + event.getText());
                break;
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                Log.d(TAG, "Text Changed: " + event.getText());
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                Log.d(TAG, "Window State Changed: " + event.getClassName());
                break;
            default:
                Log.d(TAG, "Unhandled Event: " + event.getEventType());
                break;
        }
    }

    @Override
    public void onInterrupt() {
        // Called when the service is interrupted, such as when the app is disabled.
        Log.d(TAG, "Accessibility Service Interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        // Set the configuration for the service (such as event types to listen to)
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED |
                AccessibilityEvent.TYPE_VIEW_SCROLLED |
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 100;

        // Apply the service info configuration
        setServiceInfo(info);
        Log.d(TAG, "Accessibility Service Connected and Configured");
    }
}
