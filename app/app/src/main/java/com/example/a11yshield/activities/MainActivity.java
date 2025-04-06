package com.example.a11yshield.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Use androidx Toolbar
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.a11yshield.R;
import com.example.a11yshield.fragments.ApkScannerFragment;
import com.example.a11yshield.fragments.AppScannerFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

// Removed Drawer/NavigationView imports

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "A11y_MainActivity_Simple"; // Simple Tag

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Ensure theme used (via Manifest or styles.xml) is Theme.A11yShield.NoActionBar
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Use the simple layout
        Log.d(TAG, "onCreate started (Simple)");

        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        // Setup Toolbar since theme is NoActionBar
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name); // Set title
        } else {
            Log.w(TAG,"getSupportActionBar is null after setting toolbar!");
            toolbar.setTitle(R.string.app_name); // Fallback
        }
        Log.d(TAG, "Toolbar setup complete");


        // Check views before setting up ViewPager
        if (viewPager == null || tabLayout == null) {
            Log.e(TAG, "ViewPager or TabLayout is null! Check layout IDs in activity_main.xml.");
            Toast.makeText(this, "Error initializing UI components.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // ViewPager Setup
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(getTabTitle(position))
        ).attach();
        Log.d(TAG, "ViewPager and TabLayout setup complete");

        Log.d(TAG, "onCreate finished (Simple)");
    }

    private String getTabTitle(int position) {
        switch (position) {
            case 0: return getString(R.string.tab_installed_apps);
            case 1: return getString(R.string.tab_apk_scanner);
            default: return null;
        }
    }

    // No Nav drawer methods needed

    // --- ViewPager Adapter (Unchanged) ---
    private static class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) { super(fragmentActivity); }
        @NonNull @Override public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new AppScannerFragment();
                case 1: return new ApkScannerFragment();
                default: throw new IllegalStateException("Unexpected position: " + position);
            }
        }
        @Override public int getItemCount() { return 2; }
    }
}