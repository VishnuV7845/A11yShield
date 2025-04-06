package com.example.a11yshield.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.a11yshield.R;
import com.example.a11yshield.activities.AppDetailActivity;
import com.example.a11yshield.adapters.AppListAdapter;
import com.example.a11yshield.models.AppInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays; // Import Arrays
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppScannerFragment extends Fragment implements AppListAdapter.OnAppClickListener {

    private static final String TAG = "A11y_AppScanner_Enh"; // Updated Tag
    // PDF Constants
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;
    private static final String FILENAME_FORMAT = "yyyyMMdd_HHmmss";
    private static final String REPORTS_SUBDIR = "reports"; // Internal storage subdir

    // UI Elements
    private RecyclerView recyclerViewApps;
    private AppListAdapter appListAdapter;
    private Button btnScanApps;
    private Button btnGenerateReport;
    private ProgressBar progressBar;
    private TextView tvAppCount;
    private LinearLayout layoutRiskCounts;
    private TextView tvHighRiskCount;
    private TextView tvMediumRiskCount;
    private TextView tvLowRiskCount;
    private TextView tvNoneRiskCount;

    // Data & Threading
    private List<AppInfo> scannedAppsList = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_scanner, container, false);
        Log.d(TAG, "onCreateView called");
        findViews(view); // Use helper to find views
        setupRecyclerView();
        setupListeners();
        return view;
    }

    private void findViews(@NonNull View view) {
        recyclerViewApps = view.findViewById(R.id.recyclerViewApps);
        btnScanApps = view.findViewById(R.id.btnScanApps);
        btnGenerateReport = view.findViewById(R.id.btnGenerateReport);
        progressBar = view.findViewById(R.id.progressBar);
        tvAppCount = view.findViewById(R.id.tvAppCount);
        layoutRiskCounts = view.findViewById(R.id.layoutRiskCounts);
        tvHighRiskCount = view.findViewById(R.id.tvHighRiskCount);
        tvMediumRiskCount = view.findViewById(R.id.tvMediumRiskCount);
        tvLowRiskCount = view.findViewById(R.id.tvLowRiskCount);
        tvNoneRiskCount = view.findViewById(R.id.tvNoneRiskCount);
    }

    private void setupListeners() {
        btnScanApps.setOnClickListener(v -> startAppScan());
        btnGenerateReport.setOnClickListener(v -> generateAndOpenPdfReport());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView called.");
        if (recyclerViewApps != null) { recyclerViewApps.setAdapter(null); }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called. Shutting down ExecutorService.");
        if (!executorService.isShutdown()) { executorService.shutdown(); }
    }

    // --- UI Setup ---
    private void setupRecyclerView() {
        if (getContext() == null) { Log.e(TAG, "Context null in setupRecyclerView"); return; }
        Log.d(TAG, "Setting up RecyclerView.");
        appListAdapter = new AppListAdapter(requireContext(), this);
        recyclerViewApps.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewApps.setAdapter(appListAdapter);
    }

    // --- App Scanning Logic ---
    private void startAppScan() {
        if (getContext() == null) { Toast.makeText(getActivity(), "Error: Cannot perform scan.", Toast.LENGTH_SHORT).show(); return; }
        if (!isQueryPermissionGranted()) {
            Log.w(TAG, "Scan aborted: QUERY_ALL_PACKAGES permission not granted.");
            Toast.makeText(getContext(), R.string.scan_failed_permission, Toast.LENGTH_LONG).show();
            return;
        }
        Log.d(TAG, "Starting app scan...");
        // Reset UI
        progressBar.setVisibility(View.VISIBLE);
        recyclerViewApps.setVisibility(View.GONE);
        tvAppCount.setVisibility(View.GONE);
        layoutRiskCounts.setVisibility(View.GONE);
        btnGenerateReport.setVisibility(View.GONE);
        scannedAppsList.clear();
        if(appListAdapter!=null) appListAdapter.updateData(null);

        executorService.execute(() -> {
            Log.d(TAG, "Background scan thread started.");
            Context safeContext = getContext(); if (safeContext == null) { mainHandler.post(() -> {if(isAdded()) progressBar.setVisibility(View.GONE);}); return; }

            // Perform the scan to get AppInfo list (already includes new scoring)
            List<AppInfo> appInfos = scanInstalledApps(safeContext);

            // Calculate Counts based on the new scores in AppInfo objects
            int highRiskCount = 0; int mediumRiskCount = 0; int lowRiskCount = 0; int noneRiskCount = 0;
            for (AppInfo info : appInfos) {
                switch (info.getCriticalityLevel()) { // Use the Enum level
                    case HIGH: highRiskCount++; break;
                    case MEDIUM: mediumRiskCount++; break;
                    case LOW: lowRiskCount++; break;
                    case NONE: default: noneRiskCount++; break;
                }
            }
            final int finalHigh = highRiskCount; final int finalMedium = mediumRiskCount;
            final int finalLow = lowRiskCount; final int finalNone = noneRiskCount;

            mainHandler.post(() -> {
                scannedAppsList.clear(); scannedAppsList.addAll(appInfos); // Update member list
                updateScanUI(scannedAppsList, finalHigh, finalMedium, finalLow, finalNone); // Update UI
            });
        });
    }

    /** Updates the UI after the scan finishes, including risk counts */
    private void updateScanUI(List<AppInfo> appInfos, int highCount, int medCount, int lowCount, int noneCount) {
        if (!isAdded() || getContext() == null) { Log.w(TAG, "Fragment detached, cannot update UI."); return; }
        Log.d(TAG, "Updating UI with " + appInfos.size() + " results and counts.");
        progressBar.setVisibility(View.GONE);

        if (appListAdapter != null) { appListAdapter.updateData(appInfos); }
        else { Log.e(TAG,"AppListAdapter null during UI update!"); }

        recyclerViewApps.setVisibility(View.VISIBLE);
        tvAppCount.setText(getString(R.string.total_apps_scanned, appInfos.size()));
        tvAppCount.setVisibility(View.VISIBLE);

        // Update Risk Counts UI
        if (tvHighRiskCount != null) tvHighRiskCount.setText(getString(R.string.high_risk_count_label, highCount));
        if (tvMediumRiskCount != null) tvMediumRiskCount.setText(getString(R.string.medium_risk_count_label, medCount));
        if (tvLowRiskCount != null) tvLowRiskCount.setText(getString(R.string.low_risk_count_label, lowCount));
        if (tvNoneRiskCount != null) tvNoneRiskCount.setText(getString(R.string.none_risk_count_label, noneCount));
        if (layoutRiskCounts != null) layoutRiskCounts.setVisibility(View.VISIBLE);

        btnGenerateReport.setVisibility(!appInfos.isEmpty() ? View.VISIBLE : View.GONE);
        Log.d(TAG, "UI update finished.");
    }

    private boolean isQueryPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { Context context = getContext(); if (context == null) return false; boolean granted = ContextCompat.checkSelfPermission(context, Manifest.permission.QUERY_ALL_PACKAGES) == PackageManager.PERMISSION_GRANTED; Log.d(TAG,"isQueryPermissionGranted (API " + Build.VERSION.SDK_INT + "): " + granted); return granted; } return true;
    }

    /** Updated to fetch permissions and pass to AppInfo constructor */
    private List<AppInfo> scanInstalledApps(@NonNull Context context) {
        Log.d(TAG, "scanInstalledApps started.");
        PackageManager pm = context.getPackageManager();
        List<AppInfo> appList = new ArrayList<>();
        List<PackageInfo> packages;
        // Flags NEED GET_PERMISSIONS now
        int flags = PackageManager.GET_SERVICES | PackageManager.GET_PERMISSIONS;

        try {
            Log.d(TAG,"Getting installed packages with flags: GET_SERVICES | GET_PERMISSIONS");
            packages = pm.getInstalledPackages(flags);
            Log.d(TAG,"Found " + packages.size() + " packages (incl. system).");
        } catch (Exception e) {
            Log.e(TAG, "Error getting installed packages.", e);
            return appList;
        }

        int count = 0;
        for (PackageInfo packageInfo : packages) {
            if (packageInfo == null || packageInfo.applicationInfo == null) continue; // Safety check
            if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

            String appName; try { appName = packageInfo.applicationInfo.loadLabel(pm).toString(); } catch (Exception e) { appName = packageInfo.packageName; }
            String packageName = packageInfo.packageName; Drawable appIcon = null; try { appIcon = packageInfo.applicationInfo.loadIcon(pm); } catch (Exception e) {} if (appIcon == null) { appIcon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher); }

            List<String> declaredServices = findDeclaredAccessibilityServices(packageInfo, pm);

            // Get Requested Permissions
            List<String> permissionsList = new ArrayList<>();
            if (packageInfo.requestedPermissions != null) {
                permissionsList.addAll(Arrays.asList(packageInfo.requestedPermissions));
            }

            // Pass permissions list to constructor
            AppInfo appInfo = new AppInfo(packageName, appName, appIcon, declaredServices, permissionsList);

            appList.add(appInfo);
            count++;
        }
        Log.i(TAG, "Scan complete. Found " + count + " non-system apps.");
        try { appList.sort((a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName())); } catch (Exception ignore) {}
        return appList;
    }

    private List<String> findDeclaredAccessibilityServices(PackageInfo packageInfo, PackageManager pm) {
        List<String> servicesFound = new ArrayList<>(); if (packageInfo == null || packageInfo.services == null) return servicesFound;
        for (ServiceInfo serviceInfo : packageInfo.services) { if (Manifest.permission.BIND_ACCESSIBILITY_SERVICE.equals(serviceInfo.permission)) { String serviceName = serviceInfo.name; if (serviceName != null && !serviceName.isEmpty()) { if (serviceName.contains(".")) { serviceName = serviceName.substring(serviceName.lastIndexOf('.') + 1); } servicesFound.add(serviceName); } } } return servicesFound;
    }

    @Override
    public void onAppClick(AppInfo appInfo) {
        if (getActivity() == null) return;
        Intent intent = new Intent(requireActivity(), AppDetailActivity.class);
        intent.putExtra(AppDetailActivity.EXTRA_APP_INFO, appInfo); // Pass the AppInfo with new score
        startActivity(intent);
    }

    // --- PDF Generation (Internal Storage) & Opening ---
    private void generateAndOpenPdfReport() {
        if (!isAdded() || getContext() == null) { Toast.makeText(getActivity(), "Error: Cannot generate report.", Toast.LENGTH_SHORT).show(); return; }
        if (scannedAppsList.isEmpty()) { Toast.makeText(requireContext(), R.string.no_data_to_report, Toast.LENGTH_SHORT).show(); return; }

        Log.d(TAG, "Starting PDF generation for GENERAL report (Internal)...");
        progressBar.setVisibility(View.VISIBLE); btnGenerateReport.setEnabled(false);

        executorService.execute(() -> {
            String timeStamp = new SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()).format(new Date()); String fileName = "A11yShield_General_Report_" + timeStamp + ".pdf"; File pdfFile = null; Context context = getContext();
            if (context == null) { mainHandler.post(() -> { if(isAdded()){ progressBar.setVisibility(View.GONE); btnGenerateReport.setEnabled(true); }}); return; }
            boolean generationSuccess = false;
            try {
                File internalDir = context.getFilesDir(); File reportSubDir = new File(internalDir, REPORTS_SUBDIR); if (!reportSubDir.exists() && !reportSubDir.mkdirs()) { throw new IOException("Failed to create internal reports directory."); } pdfFile = new File(reportSubDir, fileName);
                Log.i(TAG, "Target internal PDF path: " + pdfFile.getAbsolutePath()); List<AppInfo> listCopy = new ArrayList<>(scannedAppsList);
                // Pass context for resource loading inside PDF generation
                generatePdfContent(context, pdfFile, listCopy, "A11yShield Scan Report (General)");
                generationSuccess = true;
            } catch (IOException ioEx) { Log.e(TAG, "IOException during PDF generation/saving", ioEx); pdfFile = null;
            } catch (Exception e) { Log.e(TAG, "Unexpected error during PDF generation", e); pdfFile = null; }

            final File finalPdfFile = pdfFile; final boolean finalSuccess = generationSuccess;
            mainHandler.post(() -> {
                if (!isAdded() || getContext() == null) return;
                progressBar.setVisibility(View.GONE); btnGenerateReport.setEnabled(true);
                if (finalSuccess && finalPdfFile != null && finalPdfFile.exists() && finalPdfFile.length() > 0) {
                    Log.i(TAG,"PDF Generation Successful: " + finalPdfFile.getName()); Uri fileUri = getUriForFile(requireContext(), finalPdfFile);
                    if (fileUri != null) { Toast.makeText(requireContext(), getString(R.string.report_generated_opening), Toast.LENGTH_SHORT).show(); openPdfFile(fileUri); }
                    else { Toast.makeText(requireContext(), R.string.report_saved_error_opening, Toast.LENGTH_LONG).show(); }
                } else { Toast.makeText(requireContext(), R.string.report_generation_failed, Toast.LENGTH_LONG).show(); }
            });
        });
    }

    /** PDF drawing logic (Needs Context). */
    private void generatePdfContent(@NonNull Context context, @NonNull File pdfFile, @NonNull List<AppInfo> appsToInclude, @NonNull String reportTitle) throws IOException {
        Log.d(TAG, "generatePdfContent started for: " + pdfFile.getName() + " with " + appsToInclude.size() + " apps.");
        PdfDocument pdfDocument = null; PdfDocument.Page page = null;
        int contentWidth = PAGE_WIDTH - 2 * MARGIN;
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
            textPaint.setTextAlign(Paint.Align.LEFT); textPaint.setTextSize(12f); textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); StaticLayout summaryLayout = new StaticLayout("Applications in Report: " + appsToInclude.size(), textPaint, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false); canvas.save(); canvas.translate(MARGIN, yPos); summaryLayout.draw(canvas); canvas.restore(); yPos += summaryLayout.getHeight() + 5;
            textPaint.setTextSize(9f); textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)); StaticLayout noteLayout = new StaticLayout("Note: 'Accessibility Service Declared' means the app includes components designed to be an Accessibility Service. Risk assessment is based on this declaration and requested permissions.", textPaint, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false); canvas.save(); canvas.translate(MARGIN, yPos); noteLayout.draw(canvas); canvas.restore(); yPos += noteLayout.getHeight() + 15;
            textPaint.setTypeface(Typeface.DEFAULT); textPaint.setTextSize(10f); textPaint.setColor(Color.BLACK);
            // Loop through Apps
            for (AppInfo app : appsToInclude) {
                int estimatedHeight = 100 + (app.usesAccessibility() ? app.getAccessibilityServices().size() * 15 : 0) + (app.getRequestedPermissions() != null ? app.getRequestedPermissions().size() * 15 / 2 : 0) ; // Rough estimate including permissions
                if (yPos + estimatedHeight > PAGE_HEIGHT - MARGIN) { pdfDocument.finishPage(page); pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdfDocument.getPages().size() + 1).create(); page = pdfDocument.startPage(pageInfo); canvas = page.getCanvas(); yPos = MARGIN; }
                // Draw App Details
                canvas.drawText(app.getAppName(), MARGIN, yPos, subHeaderPaint); yPos += (int) (subHeaderPaint.descent() - subHeaderPaint.ascent()) + 2; canvas.drawText("Package: " + app.getPackageName(), MARGIN, yPos, regularTextPaint); yPos += (int) (regularTextPaint.descent() - regularTextPaint.ascent()) + 5; AppInfo.Criticality level = app.getCriticalityLevel(); String riskLabel = context.getString(level.labelResId); riskPaint.setColor(ContextCompat.getColor(context, level.colorResId)); canvas.drawText("Risk Assessment: " + riskLabel + " (Score: " + app.getCriticalityScore() + ")", MARGIN, yPos, riskPaint); yPos += (int) (riskPaint.descent() - riskPaint.ascent()) + 8; riskPaint.setColor(Color.BLACK); canvas.drawText("Declared Accessibility Services:", MARGIN, yPos, riskPaint); yPos += (int) (riskPaint.descent() - riskPaint.ascent()) + 2; int serviceIndent = MARGIN + 15;
                if (app.usesAccessibility() && !app.getAccessibilityServices().isEmpty()) { for (String service : app.getAccessibilityServices()) { StaticLayout serviceLayout = new StaticLayout("- " + service, textPaint, contentWidth - 15, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false); canvas.save(); canvas.translate(serviceIndent, yPos); serviceLayout.draw(canvas); canvas.restore(); yPos += serviceLayout.getHeight() + 1; } } else { canvas.drawText("- None Declared", serviceIndent, yPos, regularTextPaint); yPos += (int) (regularTextPaint.descent() - regularTextPaint.ascent()) + 1; }
                // --- Add Requested Permissions to PDF ---
                riskPaint.setColor(Color.BLACK); // Reuse riskPaint style for title
                yPos += 5; // Add some space
                canvas.drawText("Requested Permissions:", MARGIN, yPos, riskPaint);
                yPos += (int) (riskPaint.descent() - riskPaint.ascent()) + 2;
                List<String> perms = app.getRequestedPermissions();
                if (perms != null && !perms.isEmpty()) {
                    Collections.sort(perms); // Sort for consistency
                    for (String perm : perms) {
                        String simplePerm = perm.startsWith("android.permission.") ? perm.substring(19) : perm;
                        // Use StaticLayout for potentially long permission names
                        StaticLayout permLayout = new StaticLayout("- " + simplePerm, textPaint, contentWidth - 15, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        // Check page break *before* drawing permission
                        if (yPos + permLayout.getHeight() > PAGE_HEIGHT - MARGIN) {
                            pdfDocument.finishPage(page); pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdfDocument.getPages().size() + 1).create(); page = pdfDocument.startPage(pageInfo); canvas = page.getCanvas(); yPos = MARGIN;
                            // Optional: Redraw app header on new page if needed
                            canvas.drawText(app.getAppName() + " (cont.)", MARGIN, yPos, subHeaderPaint); yPos += (int) (subHeaderPaint.descent() - subHeaderPaint.ascent()) + 8;
                            canvas.drawText("Requested Permissions (cont.):", MARGIN, yPos, riskPaint); yPos += (int) (riskPaint.descent() - riskPaint.ascent()) + 2;
                        }
                        canvas.save(); canvas.translate(serviceIndent, yPos); permLayout.draw(canvas); canvas.restore();
                        yPos += permLayout.getHeight() + 1;
                    }
                } else {
                    canvas.drawText("- None Declared", serviceIndent, yPos, regularTextPaint);
                    yPos += (int) (regularTextPaint.descent() - regularTextPaint.ascent()) + 1;
                }
                // --- End Requested Permissions ---

                yPos += 10; canvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, separatorPaint); yPos += 10;
            }
            pdfDocument.finishPage(page); page = null;
            try (FileOutputStream fos = new FileOutputStream(pdfFile)) { pdfDocument.writeTo(fos); }
            Log.d(TAG, "generatePdfContent finished successfully.");
        } finally { if (pdfDocument != null) { if (page != null) { try { pdfDocument.finishPage(page); } catch (Exception ignore) {} } pdfDocument.close(); } }
    }

    /** Helper to get Content URI using FileProvider. */
    private Uri getUriForFile(@NonNull Context context, @NonNull File file) {
        try { String authority = context.getPackageName() + ".provider"; Log.d(TAG, "Getting URI for file: " + file.getAbsolutePath() + " with authority: " + authority); Uri uri = FileProvider.getUriForFile(context, authority, file); Log.d(TAG, "Generated URI: " + uri); return uri; }
        catch (Exception e) { Log.e(TAG, "Error getting URI via FileProvider.", e); return null; }
    }

    /** Helper to launch an ACTION_VIEW intent to open the generated PDF. */
    private void openPdfFile(Uri fileUri) {
        if (fileUri == null || getActivity() == null || getContext() == null) { Log.e(TAG,"Cannot open PDF, uri/activity/context is null."); Toast.makeText(requireContext(), R.string.cannot_open_pdf_uri_null, Toast.LENGTH_SHORT).show(); return; }
        Intent intent = new Intent(Intent.ACTION_VIEW); intent.setDataAndType(fileUri, "application/pdf"); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Log.i(TAG, "Attempting ACTION_VIEW for PDF URI: " + fileUri);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            try { startActivity(intent); Log.i(TAG, "Started PDF viewer activity.");}
            catch (SecurityException se) { Log.e(TAG, "SecurityException starting PDF viewer.", se); Toast.makeText(requireContext(), "Error: Permission denied opening PDF.", Toast.LENGTH_SHORT).show(); }
            catch (Exception e) { Log.e(TAG, "Error starting PDF viewer activity", e); Toast.makeText(requireContext(), R.string.cannot_open_pdf_viewer_error, Toast.LENGTH_SHORT).show(); }
        } else { Log.w(TAG, "No application found to handle PDF VIEW intent."); Toast.makeText(requireContext(), R.string.cannot_open_pdf, Toast.LENGTH_SHORT).show(); }
    }
}