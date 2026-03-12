package com.harsh.deepsolver;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * MainActivity is the entry point of the Deep Solver application.
 * It manages the user interface and handles the necessary permissions 
 * (Overlay and Accessibility) required for the app's core functionality.
 */
public class MainActivity extends AppCompatActivity {

    // UI components
    private MaterialButton btnToggleService;
    private TextView tvStatus;
    private ImageView ivStatusIcon;

    // Launcher to handle the result of the Overlay permission request
    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Settings.canDrawOverlays(this)) {
                    // Re-check permissions after returning from settings
                    checkAndRequestPermissions();
                } else {
                    Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        tvStatus = findViewById(R.id.tv_status);
        btnToggleService = findViewById(R.id.btn_toggle_service);
        ivStatusIcon = findViewById(R.id.iv_status_icon);

        // Set click listener for the toggle button
        btnToggleService.setOnClickListener(v -> checkAndRequestPermissions());
        
        // Auto-check on launch to prompt user if permissions are missing
        if (!canDrawOverlays() || !isAccessibilityServiceEnabled()) {
            checkAndRequestPermissions();
        }
    }

    private boolean canDrawOverlays() {
        return Settings.canDrawOverlays(this);
    }

    /**
     * Checks if all required permissions are granted. 
     * If not, it shows a rationale dialog before redirecting to settings.
     */
    private void checkAndRequestPermissions() {
        if (!canDrawOverlays()) {
            showPermissionRationale("Overlay & Background Popup", 
                "This app needs to draw over other apps and display pop-ups while in the background to show answers.\n\n" +
                "Please enable 'Display over other apps'.\n\n" +
                "Note: On some devices (like Xiaomi/MIUI), you must also manually enable 'Display pop-up windows while running in the background' in App Settings.", 
                this::requestOverlayPermission);
        } else if (!isAccessibilityServiceEnabled()) {
            showPermissionRationale("Accessibility Service", 
                "Accessibility Service is required to read MCQ questions from your screen. No data is stored or shared.", 
                this::requestAccessibilityPermission);
        } else {
            // Both permissions granted, toggle the service
            toggleService();
        }
    }

    /**
     * Shows an AlertDialog to explain why a specific permission is needed.
     */
    private void showPermissionRationale(String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Grant", (dialog, which) -> onConfirm.run())
                .setNegativeButton("Cancel", null)
                .setNeutralButton("App Details", (dialog, which) -> openAppSettings())
                .create().show();
    }

    /**
     * Opens the system app settings page for Deep Solver.
     */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    /**
     * Redirects the user to the system settings to grant Overlay permission.
     */
    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        overlayPermissionLauncher.launch(intent);
    }

    /**
     * Redirects the user to the system Accessibility settings.
     */
    private void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Find 'Deep Solver AI Assistant' and turn it ON", Toast.LENGTH_LONG).show();
    }

    /**
     * Checks if the Accessibility Service for this app is currently enabled.
     */
    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + DeepSolverAccessibilityService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Starts or stops the OverlayService based on its current state.
     */
    private void toggleService() {
        Intent intent = new Intent(this, OverlayService.class);
        if (OverlayService.isRunning) {
            stopService(intent);
            updateStatus(false);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            updateStatus(true);
        }
    }

    /**
     * Updates the UI (status text, icon, button) based on whether the service is running.
     */
    private void updateStatus(boolean running) {
        if (running) {
            tvStatus.setText(R.string.status_running);
            btnToggleService.setText(R.string.stop_service);
            btnToggleService.setIconResource(android.R.drawable.ic_media_pause);
            ivStatusIcon.setImageResource(android.R.drawable.ic_dialog_info);
            ivStatusIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        } else {
            tvStatus.setText(R.string.status_stopped);
            btnToggleService.setText(R.string.start_service);
            btnToggleService.setIconResource(android.R.drawable.ic_media_play);
            ivStatusIcon.setImageResource(android.R.drawable.ic_lock_idle_low_battery);
            ivStatusIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#6c757d")));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sync UI with the actual service state when returning to the app
        updateStatus(OverlayService.isRunning);
    }
}
