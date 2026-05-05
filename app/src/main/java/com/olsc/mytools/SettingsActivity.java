package com.olsc.mytools;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.olsc.mytools.util.PrefsHelper;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import android.view.ViewGroup;

public class SettingsActivity extends AppCompatActivity {
    private static final int REQUEST_NOTIF_PERMISSION = 101;
    private PrefsHelper prefsHelper;
    private SwitchMaterial switchNotif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        androidx.core.view.WindowInsetsControllerCompat controller = androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean isDarkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!isDarkMode);
        
        setContentView(R.layout.activity_settings);

        prefsHelper = new PrefsHelper(this);

        switchNotif = findViewById(R.id.switch_notification);
        switchNotif.setChecked(prefsHelper.isNotifEnabled());
        switchNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (checkNotifPermission()) {
                    enableNotificationServices();
                } else {
                    switchNotif.setChecked(false);
                }
            } else {
                disableNotificationServices();
            }
        });

        findViewById(R.id.btn_clear_data).setOnClickListener(v -> {
            prefsHelper.clearAll();
            Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show();
            switchNotif.setChecked(prefsHelper.isNotifEnabled());
        });

        View colorPreview = findViewById(R.id.view_current_color);
        colorPreview.setBackgroundColor(prefsHelper.getDotColor());
        findViewById(R.id.btn_dot_color).setOnClickListener(v -> showColorPickerDialog(colorPreview));
    }

    private boolean checkNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIF_PERMISSION);
                return false;
            }
        }
        return true;
    }

    private void enableNotificationServices() {
        prefsHelper.setNotifEnabled(true);
        startService(new Intent(this, VolumeService.class));
        Intent brightnessIntent = new Intent(this, BrightnessService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(brightnessIntent);
        } else {
            startService(brightnessIntent);
        }
    }

    private void disableNotificationServices() {
        prefsHelper.setNotifEnabled(false);
        stopService(new Intent(this, BrightnessService.class));
        stopService(new Intent(this, VolumeService.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIF_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switchNotif.setChecked(true);
                enableNotificationServices();
            } else {
                switchNotif.setChecked(false);
                Toast.makeText(this, R.string.perm_notif_request, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showColorPickerDialog(View preview) {
        int[] colors = {
            Color.WHITE, Color.BLACK, Color.GRAY, 
            Color.RED, Color.GREEN, Color.BLUE, 
            Color.YELLOW, Color.CYAN, Color.MAGENTA
        };
        
        String[] colorNames = getResources().getStringArray(R.array.motion_color_options);

        new AlertDialog.Builder(this)
            .setTitle(R.string.anti_motion_color_title)
            .setItems(colorNames, (dialog, which) -> {
                int selectedColor = colors[which];
                prefsHelper.setDotColor(selectedColor);
                preview.setBackgroundColor(selectedColor);
                
                // If Anti-Motion service is running, restart it to apply color
                if (isServiceRunning(AntiMotionSicknessService.class)) {
                    stopService(new Intent(this, AntiMotionSicknessService.class));
                    Intent intent = new Intent(this, AntiMotionSicknessService.class);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                }
            })
            .show();
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(android.content.Context.ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
