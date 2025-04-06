package com.example.a11yshield.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.a11yshield.R;
import com.example.a11yshield.models.AppInfo;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    private List<AppInfo> appInfoList;
    private final Context context;
    private final OnAppClickListener listener;

    public interface OnAppClickListener {
        void onAppClick(AppInfo appInfo);
    }

    public AppListAdapter(Context context, OnAppClickListener listener) {
        this.context = context;
        // Initialize with an empty list, not null
        this.appInfoList = new ArrayList<>();
        this.listener = listener;
    }

    // Method to update the data safely
    public void updateData(List<AppInfo> newAppInfoList) {
        this.appInfoList.clear();
        if (newAppInfoList != null) {
            this.appInfoList.addAll(newAppInfoList);
        }
        // Use notifyDataSetChanged for simplicity, consider DiffUtil for large lists
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Ensure position is valid
        if (position >= 0 && position < appInfoList.size()) {
            AppInfo appInfo = appInfoList.get(position);
            holder.bind(appInfo, listener);
        }
    }

    @Override
    public int getItemCount() {
        return appInfoList.size();
    }

    // --- ViewHolder ---
    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAppIcon;
        TextView tvAppName;
        TextView tvAppStatus;
        TextView tvAppRisk;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAppIcon = itemView.findViewById(R.id.imgAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvAppStatus = itemView.findViewById(R.id.tvAppStatus);
            tvAppRisk = itemView.findViewById(R.id.tvAppRisk);
        }

        void bind(final AppInfo appInfo, final OnAppClickListener listener) {
            // Basic Info
            tvAppName.setText(appInfo.getAppName());
            // Check if icon is null before setting
            if (appInfo.getAppIcon() != null) {
                imgAppIcon.setImageDrawable(appInfo.getAppIcon());
            } else {
                // Set a placeholder if icon loading failed earlier
                imgAppIcon.setImageResource(R.mipmap.ic_launcher);
            }


            // Risk Level & Score
            AppInfo.Criticality level = appInfo.getCriticalityLevel();
            int score = appInfo.getCriticalityScore(); // Get the numerical score
            int riskColor = ContextCompat.getColor(context, level.colorResId);
            String riskLabel = context.getString(level.labelResId); // Get the text label

            // Set Status Text
            if (appInfo.usesAccessibility()) {
                tvAppStatus.setText(R.string.status_accessibility_declared);
                tvAppStatus.setTextColor(riskColor);
            } else {
                tvAppStatus.setText(R.string.status_no_accessibility);
                tvAppStatus.setTextColor(ContextCompat.getColor(context, R.color.risk_none));
            }

            // Set Combined Risk Label and Score Text
            String combinedRiskText = context.getString(R.string.risk_label_with_score, riskLabel, score);
            tvAppRisk.setText(combinedRiskText);

            // Set Risk Background Color
            // Use placeholder drawable defined in XML and mutate it
            Drawable background = ContextCompat.getDrawable(context, R.drawable.risk_background_placeholder);
            if (background instanceof GradientDrawable) {
                // Mutate ensures we don't modify the original drawable resource state
                GradientDrawable riskBackground = (GradientDrawable) background.mutate();
                riskBackground.setColor(riskColor);
                tvAppRisk.setBackground(riskBackground);
            } else {
                // Fallback if the drawable isn't a shape (less likely but safer)
                tvAppRisk.setBackgroundColor(riskColor);
            }


            // Set Click Listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAppClick(appInfo);
                }
            });
        }
    }
}