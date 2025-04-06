package com.example.a11yshield.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build; // Keep import
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.a11yshield.R;
import com.example.a11yshield.models.AppInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApkScannerFragment extends Fragment {

    // --- Constants ---
    private static final String TAG = "A11y_ApkScanner_Score"; // Updated Tag
    private static final String TEMP_APK_PREFIX = "temp_apk_scan_";
    private static final String FILENAME_FORMAT = "yyyyMMdd_HHmmss";
    private static final String REPORTS_SUBDIR = "reports";
    // PDF Constants
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;

    // --- UI Elements ---
    private Button btnSelectApk;
    private TextView tvSelectedApkFile;
    private Button btnScanApk;
    private ProgressBar progressBar;
    private ScrollView scrollViewResults;
    private TextView tvApkAppName, tvApkPackageName, tvApkRiskLevel, tvApkServicesList;
    private TextView tvApkPermissionsTitle, tvApkPermissionsList;
    private Button btnGenerateApkReport;

    // --- State Variables ---
    private Uri selectedApkUri = null;
    private String selectedApkFilename = null;
    private PackageInfo scannedPackageInfo = null; // Store original PackageInfo
    private AppInfo scannedAppInfoResult = null;   // Store processed AppInfo with score

    // --- Threading ---
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // --- Activity Result Launcher ---
    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                handleSelectedApk(uri);
            });

    // --- Fragment Lifecycle Methods ---
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_apk_scanner, container, false);
        Log.d(TAG, "onCreateView called");
        findViews(view); setupListeners(); updateSelectedFileUI(); return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy(); Log.d(TAG, "onDestroy called."); if (executorService != null && !executorService.isShutdown()) { executorService.shutdown(); }
    }

    // --- View Initialization ---
    private void findViews(@NonNull View view) {
        btnSelectApk = view.findViewById(R.id.btnSelectApk); tvSelectedApkFile = view.findViewById(R.id.tvSelectedApkFile); btnScanApk = view.findViewById(R.id.btnScanApk);
        progressBar = view.findViewById(R.id.progressBarApk); scrollViewResults = view.findViewById(R.id.scrollViewResults); tvApkAppName = view.findViewById(R.id.tvApkAppName);
        tvApkPackageName = view.findViewById(R.id.tvApkPackageName); tvApkRiskLevel = view.findViewById(R.id.tvApkRiskLevel); tvApkServicesList = view.findViewById(R.id.tvApkServicesList);
        tvApkPermissionsTitle = view.findViewById(R.id.tvApkPermissionsTitle); tvApkPermissionsList = view.findViewById(R.id.tvApkPermissionsList); btnGenerateApkReport = view.findViewById(R.id.btnGenerateApkReport);
    }

    private void setupListeners() { btnSelectApk.setOnClickListener(v -> selectApkFile()); btnScanApk.setOnClickListener(v -> startApkScan()); btnGenerateApkReport.setOnClickListener(v -> generateAndOpenPdfReportForApk()); }

    // --- File Selection Logic ---
    private void selectApkFile() { Log.d(TAG, "selectApkFile: Launching document picker."); openDocumentLauncher.launch(new String[]{"application/vnd.android.package-archive"}); }
    private void handleSelectedApk(Uri uri) { scannedPackageInfo = null; scannedAppInfoResult = null; if (uri != null) { this.selectedApkUri = uri; this.selectedApkFilename = getFileNameFromUri(uri); Log.i(TAG, "handleSelectedApk: URI=" + uri + ", Filename=" + selectedApkFilename); } else { this.selectedApkUri = null; this.selectedApkFilename = null; Log.w(TAG, "handleSelectedApk: URI is null."); } updateSelectedFileUI(); resetResultsUI(); }
    private void updateSelectedFileUI() { if (!isAdded() || tvSelectedApkFile == null || btnScanApk == null) return; boolean apkSelected = (selectedApkUri != null && selectedApkFilename != null); tvSelectedApkFile.setText(apkSelected ? getString(R.string.selected_file_prefix, selectedApkFilename) : getString(R.string.no_apk_selected)); btnScanApk.setEnabled(apkSelected); }
    private void resetResultsUI() { if (!isAdded()) return; Log.d(TAG, "resetResultsUI: Hiding results."); if (scrollViewResults != null) scrollViewResults.setVisibility(View.GONE); if (btnGenerateApkReport != null) btnGenerateApkReport.setVisibility(View.GONE); if (tvApkAppName != null) tvApkAppName.setText(""); if (tvApkPackageName != null) tvApkPackageName.setText(""); if (tvApkRiskLevel != null) tvApkRiskLevel.setText(""); if (tvApkServicesList != null) tvApkServicesList.setText(""); if (tvApkPermissionsTitle != null) tvApkPermissionsTitle.setVisibility(View.GONE); if (tvApkPermissionsList != null) tvApkPermissionsList.setText(""); }
    private String getFileNameFromUri(Uri uri) { String r = null; if (uri == null || getContext() == null) return "Unknown"; if ("content".equals(uri.getScheme())) { try (Cursor c = getContext().getContentResolver().query(uri, null, null, null, null)) { if (c != null && c.moveToFirst()) { int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i != -1) r = c.getString(i); } } catch (Exception e) { Log.e(TAG, "Error getting filename", e); } } if (r == null) { r = uri.getPath(); if (r != null) { int i = r.lastIndexOf('/'); if (i != -1) r = r.substring(i + 1); } } return r != null ? r : "Unknown.apk"; }

    // --- APK Scanning Logic ---
    private void startApkScan() {
        if (selectedApkUri == null || getContext() == null) { Toast.makeText(getContext(), R.string.please_select_apk, Toast.LENGTH_SHORT).show(); return; }
        Log.i(TAG, "Starting APK scan for: " + selectedApkFilename); resetResultsUI(); progressBar.setVisibility(View.VISIBLE); btnScanApk.setEnabled(false); btnSelectApk.setEnabled(false); btnGenerateApkReport.setVisibility(View.GONE);
        scannedPackageInfo = null; scannedAppInfoResult = null; // Clear results

        executorService.execute(() -> {
            Context context = getContext(); if (context == null) { mainHandler.post(this::resetScanUIOnError); return; }
            File tempApkFile = null; PackageInfo packageInfo = null; String errorMessage = null;

            try {
                Log.d(TAG, "Copying APK to cache..."); tempApkFile = copyApkToCache(context, selectedApkUri); if (tempApkFile == null || !tempApkFile.exists()) throw new IOException("Failed to copy APK.");
                Log.d(TAG, "APK copied to: " + tempApkFile.getAbsolutePath()); PackageManager pm = context.getPackageManager(); int flags = PackageManager.GET_SERVICES | PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA; Log.d(TAG, "Parsing APK..."); packageInfo = pm.getPackageArchiveInfo(tempApkFile.getAbsolutePath(), flags); if (packageInfo == null) throw new PackageManager.NameNotFoundException("Could not parse APK.");
                if (packageInfo.applicationInfo != null) { packageInfo.applicationInfo.sourceDir = tempApkFile.getAbsolutePath(); packageInfo.applicationInfo.publicSourceDir = tempApkFile.getAbsolutePath(); }
                Log.i(TAG, "Successfully parsed APK: " + packageInfo.packageName);
            } catch (IOException e) { errorMessage = "Error reading/copying APK."; Log.e(TAG, errorMessage, e);
            } catch (PackageManager.NameNotFoundException e) { errorMessage = "Could not parse APK."; Log.e(TAG, errorMessage, e);
            } catch (Exception e) { errorMessage = "Unexpected scan error."; Log.e(TAG, errorMessage, e);
            } finally { if (tempApkFile != null && tempApkFile.exists()) { if (!tempApkFile.delete()) Log.w(TAG, "Failed delete temp APK."); else Log.d(TAG,"Temp APK deleted.");}}

            // Create AppInfo using helper (includes enhanced scoring)
            final AppInfo finalAppInfoResult = (packageInfo != null) ? createAppInfoFromPackageInfo(packageInfo) : null;
            final String finalErrorMessage = errorMessage; final PackageInfo finalScannedPackageInfo = packageInfo;

            mainHandler.post(() -> {
                if (!isAdded()) return; resetScanUIOnError(); // Reset progress/buttons
                if (finalAppInfoResult != null) {
                    Log.i(TAG, "Scan successful, displaying results.");
                    scannedPackageInfo = finalScannedPackageInfo; // Store original
                    scannedAppInfoResult = finalAppInfoResult;   // Store processed
                    displayScanResults(scannedAppInfoResult); // Update UI
                    if (btnGenerateApkReport != null) btnGenerateApkReport.setVisibility(View.VISIBLE);
                } else { Log.e(TAG, "APK Scan failed: " + finalErrorMessage); Toast.makeText(getContext(), "Scan Failed: " + finalErrorMessage, Toast.LENGTH_LONG).show(); resetResultsUI(); }
            });
        });
    }

    private void resetScanUIOnError() { if (!isAdded()) return; Log.d(TAG,"Resetting scan UI elements."); progressBar.setVisibility(View.GONE); btnScanApk.setEnabled(selectedApkUri != null); btnSelectApk.setEnabled(true); }
    private File copyApkToCache(Context context, Uri apkUri) throws IOException { InputStream i = null; OutputStream o = null; File t = null; String n = TEMP_APK_PREFIX + System.currentTimeMillis(); try { i = context.getContentResolver().openInputStream(apkUri); if (i == null) throw new IOException("Null InputStream."); t = File.createTempFile(n, ".apk", context.getCacheDir()); t.deleteOnExit(); o = new FileOutputStream(t); byte[] b = new byte[8192]; int r; while ((r = i.read(b)) != -1) o.write(b, 0, r); o.flush(); return t; } finally { try { if (i != null) i.close(); } catch (IOException e) {} try { if (o != null) o.close(); } catch (IOException e) {} } }

    // --- Display Scan Results (Uses Combined Score Text) ---
    private void displayScanResults(@NonNull AppInfo appInfoResult) {
        if (!isAdded() || getContext() == null || scrollViewResults == null) { Log.w(TAG,"Cannot display results, fragment/context/view null."); return; }
        String appName = appInfoResult.getAppName(); String packageName = appInfoResult.getPackageName(); List<String> accServices = appInfoResult.getAccessibilityServices(); AppInfo.Criticality level = appInfoResult.getCriticalityLevel(); int score = appInfoResult.getCriticalityScore(); List<String> permissions = appInfoResult.getRequestedPermissions();
        Log.d(TAG,"Displaying results for: " + packageName + " | Score: " + score + " | Level: " + level);

        tvApkAppName.setText(appName); tvApkPackageName.setText(packageName);
        // Set Combined Risk Text
        String riskLabel = getString(level.labelResId); String combinedRiskText = getString(R.string.risk_label_with_score, riskLabel, score);
        tvApkRiskLevel.setText(combinedRiskText); tvApkRiskLevel.setTextColor(ContextCompat.getColor(requireContext(), level.colorResId));

        if (!accServices.isEmpty()) { StringBuilder sb = new StringBuilder(); for (String s : accServices) sb.append("- ").append(s).append("\n"); if(sb.length()>0) sb.setLength(sb.length()-1); tvApkServicesList.setText(sb.toString()); } else { tvApkServicesList.setText(R.string.none_declared); }
        if (permissions != null && !permissions.isEmpty()) { StringBuilder sb = new StringBuilder(); List<String> sorted = new ArrayList<>(permissions); Collections.sort(sorted); for(String p:sorted){ String s = p.startsWith("android.permission.")?p.substring(19):p; sb.append("- ").append(s).append("\n");} if(sb.length()>0) sb.setLength(sb.length()-1); tvApkPermissionsList.setText(sb.toString()); tvApkPermissionsTitle.setVisibility(View.VISIBLE); tvApkPermissionsList.setVisibility(View.VISIBLE); } else { tvApkPermissionsList.setText(R.string.none_declared); tvApkPermissionsTitle.setVisibility(View.VISIBLE); tvApkPermissionsList.setVisibility(View.VISIBLE); }
        scrollViewResults.setVisibility(View.VISIBLE);
    }

    // --- Report Generation for Scanned APK ---
    private void generateAndOpenPdfReportForApk() {
        if (scannedAppInfoResult == null) { Toast.makeText(getContext(), "Please scan an APK successfully first.", Toast.LENGTH_SHORT).show(); return; }
        if (!isAdded() || getContext() == null) { Toast.makeText(getActivity(), "Error: Cannot generate report.", Toast.LENGTH_SHORT).show(); return; }
        Log.i(TAG, "Starting PDF generation for scanned APK: " + scannedAppInfoResult.getPackageName());
        progressBar.setVisibility(View.VISIBLE); btnGenerateApkReport.setEnabled(false); btnScanApk.setEnabled(false); btnSelectApk.setEnabled(false);
        final AppInfo reportAppInfo = scannedAppInfoResult;

        executorService.execute(() -> {
            String sanitizedAppName = sanitizeFilename(reportAppInfo.getAppName()); String timeStamp = new SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()).format(new Date()); String fileName = "A11yShield_" + sanitizedAppName + "_Report_" + timeStamp + ".pdf";
            File pdfFile = null; Context context = getContext(); boolean generationSuccess = false;
            if (context == null) { mainHandler.post(this::resetReportGenUI); return; }
            try {
                File internalDir = context.getFilesDir(); File reportSubDir = new File(internalDir, REPORTS_SUBDIR); if (!reportSubDir.exists() && !reportSubDir.mkdirs()) throw new IOException("Failed report dir."); pdfFile = new File(reportSubDir, fileName);
                Log.i(TAG, "APK Report: Target internal PDF path: " + pdfFile.getAbsolutePath());
                generatePdfContent(context, pdfFile, Collections.singletonList(reportAppInfo), "A11yShield Report: " + reportAppInfo.getAppName());
                generationSuccess = true;
            } catch (Exception e) { Log.e(TAG, "Error during APK report generation", e); pdfFile = null; }

            final File finalPdfFile = pdfFile; final boolean finalSuccess = generationSuccess;
            mainHandler.post(() -> {
                if (!isAdded() || getContext() == null) return; resetReportGenUI(); // Reset UI
                if (finalSuccess && finalPdfFile != null && finalPdfFile.exists() && finalPdfFile.length() > 0) {
                    Log.i(TAG,"APK Report PDF Generation Successful: " + finalPdfFile.getName()); Uri fileUri = getUriForFile(requireContext(), finalPdfFile); if (fileUri != null) { Toast.makeText(requireContext(), getString(R.string.report_generated_opening), Toast.LENGTH_SHORT).show(); openPdfFile(fileUri); } else { Toast.makeText(requireContext(), R.string.report_saved_error_opening, Toast.LENGTH_LONG).show(); }
                } else { Toast.makeText(requireContext(), R.string.report_generation_failed, Toast.LENGTH_LONG).show(); }
            });
        });
    }

    private void resetReportGenUI() { if (!isAdded()) return; Log.d(TAG,"Resetting report gen UI."); progressBar.setVisibility(View.GONE); btnGenerateApkReport.setEnabled(scannedAppInfoResult != null); btnScanApk.setEnabled(selectedApkUri != null); btnSelectApk.setEnabled(true); }
    private AppInfo createAppInfoFromPackageInfo(PackageInfo pi) { if (pi == null || getContext() == null) return null; PackageManager pm = getContext().getPackageManager(); String appName = "Unknown"; String packageName = pi.packageName != null ? pi.packageName : "?"; if (pi.applicationInfo != null) { try { if (pi.applicationInfo.sourceDir != null || pi.applicationInfo.publicSourceDir != null) appName = pm.getApplicationLabel(pi.applicationInfo).toString(); else appName = packageName; } catch (Exception e) { appName = packageName; } } else appName = packageName; List<String> services = findDeclaredAccessibilityServices(pi, pm); List<String> permissions = new ArrayList<>(); if (pi.requestedPermissions != null) permissions.addAll(Arrays.asList(pi.requestedPermissions)); return new AppInfo(packageName, appName, null, services, permissions); }
    private String sanitizeFilename(String n) { if (n == null) return "UnknownApp"; String s = n.replaceAll("[^a-zA-Z0-9.\\-]", "_").replaceAll("\\s+", "_"); return s.length() > 50 ? s.substring(0, 50) : s; }

    // --- Copied Helper Methods ---
    private List<String> findDeclaredAccessibilityServices(PackageInfo pi, PackageManager pm) { List<String> f = new ArrayList<>(); if (pi == null || pi.services == null) return f; for (ServiceInfo si : pi.services) { if (Manifest.permission.BIND_ACCESSIBILITY_SERVICE.equals(si.permission)) { String n = si.name; if (n != null && !n.isEmpty()) { if (n.contains(".")) n = n.substring(n.lastIndexOf('.') + 1); f.add(n); } } } return f; }
    private void generatePdfContent(@NonNull Context ctx, @NonNull File pf, @NonNull List<AppInfo> apps, @NonNull String title) throws IOException { Log.d(TAG, "generatePdfContent for: " + pf.getName()); PdfDocument doc = null; PdfDocument.Page pg = null; int cw = PAGE_WIDTH - 2 * MARGIN; try { doc = new PdfDocument(); PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create(); pg = doc.startPage(pi); Canvas cvs = pg.getCanvas(); int y = MARGIN; Paint tp = new Paint(); tp.setColor(ContextCompat.getColor(ctx, R.color.colorAccent)); tp.setTextSize(18f); tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); tp.setTextAlign(Paint.Align.CENTER); TextPaint tpt = new TextPaint(); tpt.setColor(Color.DKGRAY); tpt.setTextSize(10f); tpt.setTextAlign(Paint.Align.CENTER); Paint shp = new Paint(); shp.setColor(Color.BLACK); shp.setTextSize(14f); shp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); Paint rgtp = new Paint(); rgtp.setColor(Color.BLACK); rgtp.setTextSize(10f); Paint rskp = new Paint(rgtp); rskp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); rskp.setTextSize(11f); Paint spp = new Paint(); spp.setColor(Color.LTGRAY); spp.setStrokeWidth(1f); cvs.drawText(title, PAGE_WIDTH / 2f, y, tp); y += (int) (tp.descent() - tp.ascent()) + 10; String ts = "Generated on: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()); StaticLayout tl = new StaticLayout(ts, tpt, cw, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false); cvs.save(); cvs.translate(MARGIN, y); tl.draw(cvs); cvs.restore(); y += tl.getHeight() + 20; tpt.setTextAlign(Paint.Align.LEFT); tpt.setTextSize(12f); tpt.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); StaticLayout sl = new StaticLayout("Application(s) in Report: " + apps.size(), tpt, cw, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false); cvs.save(); cvs.translate(MARGIN, y); sl.draw(cvs); cvs.restore(); y += sl.getHeight() + 5; tpt.setTextSize(9f); tpt.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)); StaticLayout nl = new StaticLayout("Note: Risk assessment based on declared Accessibility Services and requested permissions.", tpt, cw, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false); cvs.save(); cvs.translate(MARGIN, y); nl.draw(cvs); cvs.restore(); y += nl.getHeight() + 15; tpt.setTypeface(Typeface.DEFAULT); tpt.setTextSize(10f); tpt.setColor(Color.BLACK); for (AppInfo app : apps) { int eh = 100 + (app.usesAccessibility()?app.getAccessibilityServices().size()*15:0) + (app.getRequestedPermissions()!=null?app.getRequestedPermissions().size()*15/2:0); if (y + eh > PAGE_HEIGHT - MARGIN) { doc.finishPage(pg); pi = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, doc.getPages().size() + 1).create(); pg = doc.startPage(pi); cvs = pg.getCanvas(); y = MARGIN; } cvs.drawText(app.getAppName(), MARGIN, y, shp); y += (int) (shp.descent() - shp.ascent()) + 2; cvs.drawText("Package: " + app.getPackageName(), MARGIN, y, rgtp); y += (int) (rgtp.descent() - rgtp.ascent()) + 5; AppInfo.Criticality lvl = app.getCriticalityLevel(); String rl = ctx.getString(lvl.labelResId); int scr = app.getCriticalityScore(); String prt = ctx.getString(R.string.risk_label_with_score, rl, scr); rskp.setColor(ContextCompat.getColor(ctx, lvl.colorResId)); cvs.drawText("Risk Assessment: " + prt, MARGIN, y, rskp); y += (int) (rskp.descent() - rskp.ascent()) + 8; rskp.setColor(Color.BLACK); cvs.drawText("Declared Accessibility Services:", MARGIN, y, rskp); y += (int) (rskp.descent() - rskp.ascent()) + 2; int si = MARGIN + 15; List<String> svcs = app.getAccessibilityServices(); if (svcs != null && !svcs.isEmpty()) { for (String s : svcs) { StaticLayout svcl = new StaticLayout("- " + s, tpt, cw - 15, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false); cvs.save(); cvs.translate(si, y); svcl.draw(cvs); cvs.restore(); y += svcl.getHeight() + 1; } } else { cvs.drawText("- None Declared", si, y, rgtp); y += (int) (rgtp.descent() - rgtp.ascent()) + 1; } rskp.setColor(Color.BLACK); y += 5; cvs.drawText("Requested Permissions:", MARGIN, y, rskp); y += (int) (rskp.descent() - rskp.ascent()) + 2; List<String> prms = app.getRequestedPermissions(); if (prms != null && !prms.isEmpty()) { Collections.sort(prms); for (String p : prms) { String sp = p.startsWith("android.permission.") ? p.substring(19) : p; StaticLayout pl = new StaticLayout("- " + sp, tpt, cw - 15, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false); if (y + pl.getHeight() > PAGE_HEIGHT - MARGIN) { doc.finishPage(pg); pi = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, doc.getPages().size() + 1).create(); pg = doc.startPage(pi); cvs = pg.getCanvas(); y = MARGIN; cvs.drawText(app.getAppName()+" Permissions (cont.):", MARGIN, y, shp); y+= (int)(shp.descent()-shp.ascent())+8;} cvs.save(); cvs.translate(si, y); pl.draw(cvs); cvs.restore(); y += pl.getHeight() + 1; } } else { cvs.drawText("- None Declared", si, y, rgtp); y += (int) (rgtp.descent() - rgtp.ascent()) + 1; } y += 10; cvs.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, spp); y += 10; } doc.finishPage(pg); pg = null; try (FileOutputStream fos = new FileOutputStream(pf)) { doc.writeTo(fos); } Log.d(TAG, "generatePdfContent finished successfully."); } finally { if (doc != null) { if (pg != null) { try { doc.finishPage(pg); } catch (Exception ignore) {} } doc.close(); } } }
    private Uri getUriForFile(@NonNull Context ctx, @NonNull File f) { try { String a = ctx.getPackageName() + ".provider"; return FileProvider.getUriForFile(ctx, a, f); } catch (Exception e) { Log.e(TAG, "Error getUriForFile.", e); return null; } }
    private void openPdfFile(Uri u) { if (u == null || getActivity() == null || getContext() == null) { Toast.makeText(requireContext(), R.string.cannot_open_pdf_uri_null, Toast.LENGTH_SHORT).show(); return; } Intent i = new Intent(Intent.ACTION_VIEW); i.setDataAndType(u, "application/pdf"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); if (i.resolveActivity(requireActivity().getPackageManager()) != null) { try { startActivity(i); } catch (Exception e) { Toast.makeText(requireContext(), R.string.cannot_open_pdf_viewer_error, Toast.LENGTH_SHORT).show(); } } else { Toast.makeText(requireContext(), R.string.cannot_open_pdf, Toast.LENGTH_SHORT).show(); } }
} // End of Fragment