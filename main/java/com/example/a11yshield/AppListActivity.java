package com.example.a11yshield;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AppListActivity extends AppCompatActivity {

    private ListView appListView;
    private Button btnScanApps, btnToggleView, btnGenerateReport, btnStaticAnalysis;
    private ArrayList<String> allAppsList = new ArrayList<>();
    private ArrayList<String> accessibilityAppsList = new ArrayList<>();
    private boolean showAccessibilityApps = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);

        appListView = findViewById(R.id.appListView);
        btnScanApps = findViewById(R.id.btnScanApps);
        btnToggleView = findViewById(R.id.btnToggleView);
        btnGenerateReport = findViewById(R.id.btnGenerateReport);
        btnStaticAnalysis = findViewById(R.id.btnStaticAnalysis);

        btnScanApps.setOnClickListener(v -> scanAppsPermissions());
        btnToggleView.setOnClickListener(v -> toggleView());
        btnGenerateReport.setOnClickListener(v -> generatePdfReport());

        btnStaticAnalysis.setOnClickListener(v -> {
            Intent intent = new Intent(AppListActivity.this, StaticAnalysisActivity.class);
            startActivity(intent);
        });
    }


    private void scanAppsPermissions() {
        allAppsList.clear();
        accessibilityAppsList.clear();

        PackageManager pm = getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);

        for (PackageInfo packageInfo : packages) {
            String appName = packageInfo.applicationInfo.loadLabel(pm).toString();
            String packageName = packageInfo.packageName;

            StringBuilder permissionsBuilder = new StringBuilder();
            permissionsBuilder.append("Permissions:\n");

            boolean requestsAccessibility = false;

            if (packageInfo.requestedPermissions != null) {
                for (String permission : packageInfo.requestedPermissions) {
                    permissionsBuilder.append(" - ").append(permission).append("\n");
                    if (permission.equals("android.permission.BIND_ACCESSIBILITY_SERVICE")) {
                        requestsAccessibility = true;
                    }
                }
            } else {
                permissionsBuilder.append(" No permissions requested.\n");
            }

            String appDetails = appName + " (" + packageName + ")\n" + permissionsBuilder.toString();
            allAppsList.add(appDetails);

            if (requestsAccessibility) {
                accessibilityAppsList.add(appDetails);
            }
        }

        showAccessibilityApps = false; // Default to showing all apps
        updateAppListView();
        Toast.makeText(this, "All apps scanned successfully!", Toast.LENGTH_SHORT).show();
    }

    private void toggleView() {
        showAccessibilityApps = !showAccessibilityApps;
        updateAppListView();
    }

    private void updateAppListView() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                showAccessibilityApps ? accessibilityAppsList : allAppsList);
        appListView.setAdapter(adapter);

        btnToggleView.setText(showAccessibilityApps ? "Show All Apps" : "Show Accessibility Apps");
    }

    private void generatePdfReport() {
        List<String> data = showAccessibilityApps ? accessibilityAppsList : allAppsList;

        if (data.isEmpty()) {
            Toast.makeText(this, "No data available to generate report", Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument pdfDocument = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(300, 600, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);

        android.graphics.Paint paint = new android.graphics.Paint();
        int xPos = 10;
        int yPos = 20;
        int lineHeight = 20;
        int maxWidth = pageInfo.getPageWidth() - 20;

        for (String entry : data) {
            // Wrap text to fit within the page width
            List<String> wrappedLines = wrapText(entry, paint, maxWidth);

            for (String line : wrappedLines) {
                page.getCanvas().drawText(line, xPos, yPos, paint);
                yPos += lineHeight;

                if (yPos > pageInfo.getPageHeight() - 20) { // If content overflows, start a new page
                    pdfDocument.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(300, 600, pdfDocument.getPages().size() + 1).create();
                    page = pdfDocument.startPage(pageInfo);
                    yPos = 20;
                }
            }
        }

        pdfDocument.finishPage(page);

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File pdfFile = new File(downloadsDir, "AppReport.pdf");

        try {
            pdfDocument.writeTo(new FileOutputStream(pdfFile));
            pdfDocument.close();

            Toast.makeText(this, "Report saved to Downloads: " + pdfFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            openPdfFile(pdfFile);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save PDF report", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private List<String> wrapText(String text, android.graphics.Paint paint, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;

            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(currentLine.length() == 0 ? word : " " + word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }


    private void openPdfFile(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No application available to open PDF", Toast.LENGTH_SHORT).show();
        }
    }
}
