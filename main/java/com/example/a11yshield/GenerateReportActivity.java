package com.example.a11yshield;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.FileOutputStream;
import java.io.IOException;

public class GenerateReportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_report);

        Button generateReportButton = findViewById(R.id.generateReportButton);
        generateReportButton.setOnClickListener(v -> generateReport());
    }

    private void generateReport() {
        try {
            FileOutputStream fos = openFileOutput("report.txt", MODE_PRIVATE);
            String report = "App1 - Package Name - Suspicious Permission\n";
            fos.write(report.getBytes());
            fos.close();
            Toast.makeText(this, "Report Generated", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error generating report", Toast.LENGTH_SHORT).show();
        }
    }
}
