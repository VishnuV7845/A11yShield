package com.example.a11yshield.activities;

// Android & System Imports
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

// AndroidX Imports
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

// Project Specific Imports
import com.example.a11yshield.R;
import com.example.a11yshield.models.AppInfo;

// Java Util Imports
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppDetailActivity extends AppCompatActivity {

    // Constants
    public static final String EXTRA_APP_INFO = "com.example.a11yshield.EXTRA_APP_INFO";
    private static final String TAG = "A11y_AppDetail_Score"; // Updated Tag
    private static final String FILENAME_FORMAT = "yyyyMMdd_HHmmss";
    private static final String REPORTS_SUBDIR = "reports";
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;

    // UI Elements
    private ImageView imgAppIcon;
    private TextView tvAppName;
    private TextView tvPackageName;
    private TextView tvRiskLevel; // TextView to show combined risk label and score
    private TextView tvServicesList;
    private TextView tvDetailPermissionsTitle;
    private TextView tvDetailPermissionsList;
    private Button btnManagePermissions;
    private Button btnGenerateAppReportDetail;
    private ProgressBar progressBar;
    private Toolbar toolbar;

    // Data & Threading
    private AppInfo appInfo;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_detail);
        Log.d(TAG, "onCreate started");

        // Find Views
        toolbar = findViewById(R.id.detailToolbar);
        imgAppIcon = findViewById(R.id.imgDetailAppIcon);
        tvAppName = findViewById(R.id.tvDetailAppName);
        tvPackageName = findViewById(R.id.tvDetailPackageName);
        tvRiskLevel = findViewById(R.id.tvDetailRiskLevel); // Find the risk level view
        tvServicesList = findViewById(R.id.tvDetailServicesList);
        tvDetailPermissionsTitle = findViewById(R.id.tvDetailPermissionsTitle);
        tvDetailPermissionsList = findViewById(R.id.tvDetailPermissionsList);
        btnManagePermissions = findViewById(R.id.btnManagePermissions);
        btnGenerateAppReportDetail = findViewById(R.id.btnGenerateAppReportDetail);
        progressBar = findViewById(R.id.progressBarAppDetail);

        // Verify crucial views
        if (toolbar == null || tvRiskLevel == null || tvServicesList == null || tvDetailPermissionsList == null || btnGenerateAppReportDetail == null) {
            Log.e(TAG, "FATAL: Required views not found!"); Toast.makeText(this, "UI Error.", Toast.LENGTH_LONG).show(); finish(); return;
        }

        // Setup Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) { getSupportActionBar().setDisplayHomeAsUpEnabled(true); getSupportActionBar().setDisplayShowTitleEnabled(false); }

        appInfo = getIntent().getParcelableExtra(EXTRA_APP_INFO);

        if (appInfo != null) {
            populateUI();
            btnManagePermissions.setOnClickListener(v -> openAppSettings(appInfo.getPackageName()));
            btnGenerateAppReportDetail.setOnClickListener(v -> generateAndOpenPdfReportForApp());
        } else {
            Log.e(TAG, "AppInfo was null in Intent."); Toast.makeText(this, "Error: App details missing.", Toast.LENGTH_SHORT).show(); finish();
        }
        Log.d(TAG, "onCreate finished");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called.");
        if (executorService != null && !executorService.isShutdown()) { executorService.shutdown(); }
    }

    private void populateUI() {
        if (appInfo == null) return;
        // Set Title & Basic Info
        if (getSupportActionBar() != null) { getSupportActionBar().setTitle(appInfo.getAppName()); getSupportActionBar().setDisplayShowTitleEnabled(true); } else { toolbar.setTitle(appInfo.getAppName()); }
        tvAppName.setText(appInfo.getAppName()); tvPackageName.setText(appInfo.getPackageName());

        // Load Icon
        try { Drawable icon = getPackageManager().getApplicationIcon(appInfo.getPackageName()); imgAppIcon.setImageDrawable(icon); }
        catch (Exception e) { Log.w(TAG, "Error loading icon", e); imgAppIcon.setImageResource(R.mipmap.ic_launcher); }

        // Set Combined Risk Label and Score
        AppInfo.Criticality level = appInfo.getCriticalityLevel();
        int score = appInfo.getCriticalityScore();
        String riskLabel = getString(level.labelResId);
        String combinedRiskText = getString(R.string.risk_label_with_score, riskLabel, score); // Combine using string resource
        tvRiskLevel.setText(combinedRiskText); // Set combined text
        tvRiskLevel.setTextColor(ContextCompat.getColor(this, level.colorResId)); // Set color

        // Set Accessibility Services
        List<String> services = appInfo.getAccessibilityServices();
        if (services != null && !services.isEmpty()) { StringBuilder sb = new StringBuilder(); for (String s : services) sb.append("- ").append(s).append("\n"); if(sb.length()>0) sb.setLength(sb.length()-1); tvServicesList.setText(sb.toString()); }
        else { tvServicesList.setText(R.string.none_declared); }

        // Fetch and Display Normal Permissions (using the list already in AppInfo is more efficient)
        displayPermissionsFromAppInfo(appInfo);
    }

    // Modified to use permissions already stored in AppInfo
    private void displayPermissionsFromAppInfo(AppInfo appInfo) {
        List<String> requestedPermissions = appInfo.getRequestedPermissions();
        if (requestedPermissions != null && !requestedPermissions.isEmpty()) {
            Log.i(TAG, "Displaying " + requestedPermissions.size() + " requested permissions from AppInfo.");
            StringBuilder permissionsText = new StringBuilder();
            List<String> permissionList = new ArrayList<>(requestedPermissions); // Copy for sorting
            Collections.sort(permissionList);
            for (String permission : permissionList) {
                String simpleName = permission.startsWith("android.permission.") ? permission.substring(19) : permission;
                permissionsText.append("- ").append(simpleName).append("\n");
            }
            if (permissionsText.length() > 0) { permissionsText.setLength(permissionsText.length() - 1); }
            tvDetailPermissionsList.setText(permissionsText.toString());
            tvDetailPermissionsTitle.setVisibility(View.VISIBLE);
            tvDetailPermissionsList.setVisibility(View.VISIBLE);
        } else {
            Log.i(TAG, "No requested permissions found in AppInfo for " + appInfo.getPackageName());
            tvDetailPermissionsList.setText(R.string.none_declared);
            tvDetailPermissionsTitle.setVisibility(View.VISIBLE);
            tvDetailPermissionsList.setVisibility(View.VISIBLE);
        }
    }

    private void openAppSettings(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        try { Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); Uri uri = Uri.fromParts("package", packageName, null); intent.setData(uri); startActivity(intent); }
        catch (Exception e) { Log.e(TAG, "Could not open settings", e); Toast.makeText(this, R.string.cannot_open_settings, Toast.LENGTH_SHORT).show(); }
    }

    // --- Report Generation Logic ---
    private String sanitizeFilename(String inputName) {
        if (inputName == null) return "UnknownApp"; String sanitized = inputName.replaceAll("[^a-zA-Z0-9.-]", "_").replaceAll("\\s+", "_"); int maxLength = 50; return sanitized.length() > maxLength ? sanitized.substring(0, maxLength) : sanitized;
    }

    private void generateAndOpenPdfReportForApp() {
        if (appInfo == null) { Toast.makeText(this, "Error: App data missing.", Toast.LENGTH_SHORT).show(); return; }
        Log.d(TAG, "Starting PDF generation for specific app: " + appInfo.getAppName());
        progressBar.setVisibility(View.VISIBLE); btnGenerateAppReportDetail.setEnabled(false); btnManagePermissions.setEnabled(false);

        executorService.execute(() -> {
            String sanitizedAppName = sanitizeFilename(appInfo.getAppName()); String timeStamp = new SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()).format(new Date()); String fileName = "A11yShield_" + sanitizedAppName + "_Report_" + timeStamp + ".pdf";
            File pdfFile = null; Context context = getApplicationContext(); boolean generationSuccess = false;

            try {
                File internalDir = context.getFilesDir(); File reportSubDir = new File(internalDir, REPORTS_SUBDIR); if (!reportSubDir.exists() && !reportSubDir.mkdirs()) throw new IOException("Failed to create report dir."); pdfFile = new File(reportSubDir, fileName);
                Log.i(TAG, "Target internal PDF path: " + pdfFile.getAbsolutePath());
                // Use the current appInfo object
                generatePdfContent(context, pdfFile, Collections.singletonList(appInfo), "A11yShield Report: " + appInfo.getAppName());
                generationSuccess = true;
            } catch (Exception e) { Log.e(TAG, "Error during app report generation", e); pdfFile = null; }

            final File finalPdfFile = pdfFile; final boolean finalSuccess = generationSuccess;
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE); btnGenerateAppReportDetail.setEnabled(true); btnManagePermissions.setEnabled(true);
                if (finalSuccess && finalPdfFile != null && finalPdfFile.exists() && finalPdfFile.length() > 0) {
                    Log.i(TAG,"PDF Generation Successful: " + finalPdfFile.getName());
                    Uri fileUri = getUriForFile(this, finalPdfFile); // Use Activity context
                    if (fileUri != null) { Toast.makeText(this, getString(R.string.report_generated_opening), Toast.LENGTH_SHORT).show(); openPdfFile(fileUri); }
                    else { Toast.makeText(this, R.string.report_saved_error_opening, Toast.LENGTH_LONG).show(); }
                } else { Toast.makeText(this, R.string.report_generation_failed, Toast.LENGTH_LONG).show();}
            });
        });
    }

    /** PDF drawing logic. Copied/Adapted from Fragment. Includes score. */
    private void generatePdfContent(@NonNull Context context, @NonNull File pdfFile, @NonNull List<AppInfo> appsToInclude, @NonNull String reportTitle) throws IOException {
        Log.d(TAG, "generatePdfContent (Detail) started for: " + pdfFile.getName());
        PdfDocument pdfDocument = null; PdfDocument.Page page = null; int contentWidth = PAGE_WIDTH - 2 * MARGIN;
        try {
            pdfDocument = new PdfDocument(); PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
            page = pdfDocument.startPage(pageInfo); Canvas canvas = page.getCanvas(); int yPos = MARGIN;
            // Define Paints
            Paint titlePaint = new Paint(); titlePaint.setColor(ContextCompat.getColor(context, R.color.colorAccent)); titlePaint.setTextSize(18f); titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); titlePaint.setTextAlign(Paint.Align.CENTER);
            TextPaint textPaint = new TextPaint(); textPaint.setColor(Color.DKGRAY); textPaint.setTextSize(10f); textPaint.setTextAlign(Paint.Align.CENTER);
            Paint subHeaderPaint = new Paint(); subHeaderPaint.setColor(Color.BLACK); subHeaderPaint.setTextSize(14f); subHeaderPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            Paint regularTextPaint = new Paint(); regularTextPaint.setColor(Color.BLACK); regularTextPaint.setTextSize(10f);
            Paint riskPaint = new Paint(regularTextPaint); riskPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); riskPaint.setTextSize(11f);
            Paint separatorPaint = new Paint(); separatorPaint.setColor(Color.LTGRAY); separatorPaint.setStrokeWidth(1f);
            // Draw Header
            canvas.drawText(reportTitle, PAGE_WIDTH / 2f, yPos, titlePaint); yPos += (int) (titlePaint.descent() - titlePaint.ascent()) + 10;
            String timestamp = "Generated on: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()); StaticLayout timeLayout = new StaticLayout(timestamp, textPaint, contentWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false); canvas.save(); canvas.translate(MARGIN, yPos); timeLayout.draw(canvas); canvas.restore(); yPos += timeLayout.getHeight() + 20;
            textPaint.setTextAlign(Paint.Align.LEFT); textPaint.setTextSize(12f); textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); StaticLayout summaryLayout = new StaticLayout("Application in Report: " + appsToInclude.size(), textPaint, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false); canvas.save(); canvas.translate(MARGIN, yPos); summaryLayout.draw(canvas); canvas.restore(); yPos += summaryLayout.getHeight() + 5;
            textPaint.setTextSize(9f); textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)); StaticLayout noteLayout = new StaticLayout("Note: Risk assessment based on declared Accessibility Services and requested permissions.", textPaint, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false); canvas.save(); canvas.translate(MARGIN, yPos); noteLayout.draw(canvas); canvas.restore(); yPos += noteLayout.getHeight() + 15;
            textPaint.setTypeface(Typeface.DEFAULT); textPaint.setTextSize(10f); textPaint.setColor(Color.BLACK);
            // Loop (only runs once for single app report)
            for (AppInfo app : appsToInclude) {
                int estimatedHeight = 100 + (app.usesAccessibility() ? app.getAccessibilityServices().size() * 15 : 0) + (app.getRequestedPermissions() != null ? app.getRequestedPermissions().size() * 15 / 2 : 0) ; if (yPos + estimatedHeight > PAGE_HEIGHT - MARGIN) { /* pagination */ pdfDocument.finishPage(page); pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdfDocument.getPages().size() + 1).create(); page = pdfDocument.startPage(pageInfo); canvas = page.getCanvas(); yPos = MARGIN;}
                // Draw App Details
                canvas.drawText(app.getAppName(), MARGIN, yPos, subHeaderPaint); yPos += (int) (subHeaderPaint.descent() - subHeaderPaint.ascent()) + 2; canvas.drawText("Package: " + app.getPackageName(), MARGIN, yPos, regularTextPaint); yPos += (int) (regularTextPaint.descent() - regularTextPaint.ascent()) + 5;
                // Draw Combined Risk Label and Score
                AppInfo.Criticality level = app.getCriticalityLevel(); String riskLabel = context.getString(level.labelResId); int score = app.getCriticalityScore(); String pdfRiskText = "Risk Assessment: " + context.getString(R.string.risk_label_with_score, riskLabel, score); riskPaint.setColor(ContextCompat.getColor(context, level.colorResId)); canvas.drawText(pdfRiskText, MARGIN, yPos, riskPaint); yPos += (int) (riskPaint.descent() - riskPaint.ascent()) + 8;
                // Draw A11y Services
                riskPaint.setColor(Color.BLACK); canvas.drawText("Declared Accessibility Services:", MARGIN, yPos, riskPaint); yPos += (int) (riskPaint.descent() - riskPaint.ascent()) + 2; int serviceIndent = MARGIN + 15;
                if (app.usesAccessibility() && !app.getAccessibilityServices().isEmpty()) { for (String service : app.getAccessibilityServices()) { StaticLayout serviceLayout = new StaticLayout("- " + service, textPaint, contentWidth - 15, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false); canvas.save(); canvas.translate(serviceIndent, yPos); serviceLayout.draw(canvas); canvas.restore(); yPos += serviceLayout.getHeight() + 1; } } else { canvas.drawText("- None Declared", serviceIndent, yPos, regularTextPaint); yPos += (int) (regularTextPaint.descent() - regularTextPaint.ascent()) + 1; }
                // Draw Requested Permissions
                riskPaint.setColor(Color.BLACK); yPos += 5; canvas.drawText("Requested Permissions:", MARGIN, yPos, riskPaint); yPos += (int) (riskPaint.descent() - riskPaint.ascent()) + 2;
                List<String> perms = app.getRequestedPermissions();
                if (perms != null && !perms.isEmpty()) {
                    Collections.sort(perms);
                    for (String perm : perms) {
                        String simplePerm = perm.startsWith("android.permission.") ? perm.substring(19) : perm; StaticLayout permLayout = new StaticLayout("- " + simplePerm, textPaint, contentWidth - 15, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (yPos + permLayout.getHeight() > PAGE_HEIGHT - MARGIN) { pdfDocument.finishPage(page); pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdfDocument.getPages().size() + 1).create(); page = pdfDocument.startPage(pageInfo); canvas = page.getCanvas(); yPos = MARGIN; canvas.drawText(app.getAppName() + " Permissions (cont.):", MARGIN, yPos, subHeaderPaint); yPos += (int) (subHeaderPaint.descent() - subHeaderPaint.ascent()) + 8;}
                        canvas.save(); canvas.translate(serviceIndent, yPos); permLayout.draw(canvas); canvas.restore(); yPos += permLayout.getHeight() + 1;
                    }
                } else { canvas.drawText("- None Declared", serviceIndent, yPos, regularTextPaint); yPos += (int) (regularTextPaint.descent() - regularTextPaint.ascent()) + 1; }
                // Separator
                yPos += 10; canvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, separatorPaint); yPos += 10;
            }
            pdfDocument.finishPage(page); page = null;
            try (FileOutputStream fos = new FileOutputStream(pdfFile)) { pdfDocument.writeTo(fos); }
            Log.d(TAG, "generatePdfContent (Detail) finished successfully.");
        } finally { if (pdfDocument != null) { if (page != null) { try { pdfDocument.finishPage(page); } catch (Exception ignore) {} } pdfDocument.close(); } }
    }

    /** Helper to get Content URI using FileProvider. */
    private Uri getUriForFile(@NonNull Context context, @NonNull File file) {
        try { String authority = context.getPackageName() + ".provider"; return FileProvider.getUriForFile(context, authority, file); }
        catch (Exception e) { Log.e(TAG, "Error getting URI via FileProvider.", e); return null; }
    }

    /** Helper to launch an ACTION_VIEW intent to open the generated PDF. */
    private void openPdfFile(Uri fileUri) {
        if (fileUri == null) { Toast.makeText(this, R.string.cannot_open_pdf_uri_null, Toast.LENGTH_SHORT).show(); return; }
        Intent intent = new Intent(Intent.ACTION_VIEW); intent.setDataAndType(fileUri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try { startActivity(intent); }
            catch (Exception e) { Log.e(TAG, "Error opening PDF", e); Toast.makeText(this, R.string.cannot_open_pdf_viewer_error, Toast.LENGTH_SHORT).show(); }
        } else { Log.w(TAG, "No PDF viewer found."); Toast.makeText(this, R.string.cannot_open_pdf, Toast.LENGTH_SHORT).show(); }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}