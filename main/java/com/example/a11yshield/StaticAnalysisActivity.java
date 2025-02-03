package com.example.a11yshield;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StaticAnalysisActivity extends AppCompatActivity {

    private static final int PICK_XML_FILE = 1;
    private Button btnUploadManifest, btnSavePdf;
    private TextView tvAnalysisReport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_static_analysis);

        btnUploadManifest = findViewById(R.id.btnUploadApk); // Using the old APK button ID
        btnSavePdf = findViewById(R.id.btnSavePdf);
        tvAnalysisReport = findViewById(R.id.tvAnalysisReport);

        // Handle file selection
        btnUploadManifest.setOnClickListener(v -> selectManifestFile());

        // Handle PDF saving (functionality needs to be implemented later)
        btnSavePdf.setOnClickListener(v -> generatePdfReport(tvAnalysisReport.getText().toString()));
    }

    // Select Manifest XML file using a file picker
    private void selectManifestFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // Allow selecting any file
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select AndroidManifest.xml"), PICK_XML_FILE);
    }

    // Handle the result of Manifest file selection
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_XML_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri xmlUri = data.getData();
                Toast.makeText(this, "Manifest Selected", Toast.LENGTH_SHORT).show();

                // Read the manifest XML file
                String manifestXml = readXmlFile(xmlUri);

                // Generate and display the analysis report
                String report = generateStaticAnalysisReport(manifestXml);
                tvAnalysisReport.setText(report);

                // Show the "Save PDF" button
                btnSavePdf.setVisibility(Button.VISIBLE);
            }
        }
    }

    // Read XML file content from Uri
    private String readXmlFile(Uri uri) {
        StringBuilder xmlContent = new StringBuilder();
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    xmlContent.append(line).append("\n");
                }
                reader.close();
                inputStream.close();
            }
        } catch (IOException e) {
            Log.e("StaticAnalysis", "Error reading manifest file: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Error reading manifest file.", Toast.LENGTH_SHORT).show();
        }
        return xmlContent.toString();
    }

    // Generate a static analysis report based on the manifest
    private String generateStaticAnalysisReport(String manifestXml) {
        StringBuilder report = new StringBuilder();
        report.append("### Static Analysis Report ###\n\n");

        // Placeholder for general app info
        report.append("**App Information:**\n");
        report.append("- Name: [PLACEHOLDER]\n");
        report.append("- Package: [PLACEHOLDER]\n");
        report.append("- Version: [PLACEHOLDER]\n\n");

        // Placeholder for permissions summary
        report.append("**Permissions Analysis:**\n");
        boolean hasIssues = false;

        if (manifestXml.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")) {
            report.append("- **High Risk:** Accessibility service detected.\n");
            report.append("  - Malicious apps can use this to control device actions.\n\n");
            hasIssues = true;
        }

        if (manifestXml.contains("<service") && manifestXml.contains("accessibilityservice")) {
            report.append("- **High Risk:** App declares an Accessibility Service.\n");
            report.append("  - Potential for abuse in monitoring or controlling user actions.\n\n");
            hasIssues = true;
        }

        if (manifestXml.contains("android.permission.SYSTEM_ALERT_WINDOW")) {
            report.append("- **High Risk:** SYSTEM_ALERT_WINDOW permission granted.\n");
            report.append("  - App can overlay UI elements over other apps (phishing risk).\n\n");
            hasIssues = true;
        }

        if (manifestXml.contains("android.permission.PACKAGE_USAGE_STATS")) {
            report.append("- **Medium Risk:** PACKAGE_USAGE_STATS permission granted.\n");
            report.append("  - Allows access to user app usage history.\n\n");
            hasIssues = true;
        }

        if (!hasIssues) {
            report.append("- No high-risk permissions detected.\n\n");
        }

        // Placeholder for accessibility services analysis
        report.append("**Accessibility Services:**\n");
        report.append("[PLACEHOLDER - List detected services and their descriptions]\n\n");

        // Placeholder for additional security risks
        report.append("**Additional Security Concerns:**\n");
        report.append("[PLACEHOLDER - Any other potential risks]\n\n");

        // Placeholder for overall risk assessment
        report.append("**Overall Risk Assessment:**\n");
        report.append("[PLACEHOLDER - Final risk score and recommendation]\n\n");

        report.append("### Conclusion ###\n");
        report.append("- Review app permissions and services for potential risks.\n");
        report.append("- Conduct further dynamic analysis if necessary.\n");

        return report.toString();
    }

    // Placeholder method for PDF generation (implement later)
    private void generatePdfReport(String reportContent) {
        // Implement PDF generation logic here
    }
}
