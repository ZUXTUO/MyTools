package com.olsc.mytools;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.olsc.mytools.ai.AiChatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SeekBar brightnessSeekBar;
    private TextView brightnessValueText;
    private SeekBar volumeSeekBar;
    private TextView volumeValueText;
    
    private TextView randomResultText;
    private Button generateRandomBtn;
    
    private SensorManager sensorManager;
    private float pressureValue = 0f;
    private float[] gravityValues = new float[3];
    
    // Part of Diamond Sutra (金刚经) for matrix calculation
    private static final String DIAMOND_SUTRA_EXCERPT = "一切有为法，如梦幻泡影，如露亦如电，应作如是观。";

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_NOTIF_PERMISSION = 1002;

    private com.olsc.mytools.util.PrefsHelper prefsHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        androidx.core.view.WindowInsetsControllerCompat controller = androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean isDarkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!isDarkMode);
        controller.setAppearanceLightNavigationBars(!isDarkMode);

        setContentView(R.layout.activity_main);

        // Handle window insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        registerSensors();

        prefsHelper = new com.olsc.mytools.util.PrefsHelper(this);

        checkPermissions();
        setupDesktopIcons();
        
        // Start essential services
        if (prefsHelper.isNotifEnabled()) {
            startService(new Intent(this, VolumeService.class));
            startBrightnessService();
        }
    }

    private void setupDesktopIcons() {
        findViewById(R.id.app_brightness).setOnClickListener(v -> showBrightnessDialog());
        findViewById(R.id.app_volume).setOnClickListener(v -> showVolumeDialog());
        findViewById(R.id.app_random).setOnClickListener(v -> showRandomDialog());
        findViewById(R.id.app_ai_chat).setOnClickListener(v -> {
            startActivity(intentWithProvider(AiChatActivity.class));
        });
        findViewById(R.id.app_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
        findViewById(R.id.app_anti_motion).setOnClickListener(v -> toggleAntiMotionMode());
    }

    private Intent intentWithProvider(Class<?> cls) {
        return new Intent(this, cls);
    }

    private void showBrightnessDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_control_slider, null);
        
        TextView title = view.findViewById(R.id.dialog_title);
        TextView desc = view.findViewById(R.id.dialog_description);
        TextView valueText = view.findViewById(R.id.dialog_value);
        SeekBar seekBar = view.findViewById(R.id.dialog_seekbar);
        
        title.setText(R.string.brightness_title);
        desc.setText(R.string.brightness_desc);
        
        // Current value logic: load from prefs
        int savedProgress = prefsHelper.getBrightness();
        seekBar.setProgress(savedProgress);
        valueText.setText(savedProgress + "%");
        
        // Apply immediately when clicking/opening if it's the first time in this session or as requested
        applyBrightness(savedProgress);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueText.setText(progress + "%");
                applyBrightness(progress);
                if (fromUser) {
                    prefsHelper.saveBrightness(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void showVolumeDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_control_slider, null);
        
        TextView title = view.findViewById(R.id.dialog_title);
        TextView desc = view.findViewById(R.id.dialog_description);
        TextView valueText = view.findViewById(R.id.dialog_value);
        SeekBar seekBar = view.findViewById(R.id.dialog_seekbar);
        
        title.setText(R.string.volume_title);
        desc.setText(R.string.volume_desc);
        
        int savedVolume = prefsHelper.getVolume();
        seekBar.setProgress(savedVolume);
        valueText.setText(savedVolume + "%");
        
        // Apply immediately
        applyVolume(savedVolume);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueText.setText(progress + "%");
                applyVolume(progress);
                if (fromUser) {
                    prefsHelper.saveVolume(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void showRandomDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_random_result, null);
        
        TextView resultText = view.findViewById(R.id.random_result_text);
        Button btnRetry = view.findViewById(R.id.btn_retry);
        
        Runnable generate = () -> {
            String result = generateTrueRandomResult();
            resultText.setText(result);
            if (result.equals(getString(R.string.random_res_plus))) {
                resultText.setTextColor(android.graphics.Color.parseColor("#000000"));
            } else {
                resultText.setTextColor(android.graphics.Color.parseColor("#666666"));
            }
            resultText.setAlpha(0f);
            resultText.animate().alpha(1f).setDuration(500).start();
        };

        btnRetry.setOnClickListener(v -> generate.run());
        generate.run();

        dialog.setContentView(view);
        dialog.show();
    }

    private String generateTrueRandomResult() {
        try {
            long timestamp = System.nanoTime();
            int batteryLevel = getBatteryLevel();
            StringBuilder entropySource = new StringBuilder();
            entropySource.append(timestamp).append(pressureValue);
            entropySource.append(gravityValues[0]).append(gravityValues[1]).append(gravityValues[2]);
            entropySource.append(batteryLevel);
            
            byte[] entropyHash = sha256(entropySource.toString());
            byte[] sutraBytes = DIAMOND_SUTRA_EXCERPT.getBytes(StandardCharsets.UTF_8);
            
            long finalValue = 0;
            for (int i = 0; i < entropyHash.length; i++) {
                int mixed = (entropyHash[i] ^ sutraBytes[i % sutraBytes.length]) & 0xFF;
                finalValue += mixed;
                finalValue = Long.rotateLeft(finalValue, 3);
            }
            
            return (finalValue % 2 == 0) ? getString(R.string.random_res_plus) : getString(R.string.random_res_minus);
        } catch (Exception e) {
            return "?";
        }
    }

    private void registerSensors() {
        if (sensorManager != null) {
            Sensor pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            if (pressure != null) sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL);
            Sensor gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (gravity != null) sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private byte[] sha256(String text) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) pressureValue = event.values[0];
        else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) gravityValues = event.values.clone();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override protected void onPause() { super.onPause(); sensorManager.unregisterListener(this); }
    @Override protected void onResume() { super.onResume(); registerSensors(); }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                return; 
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIF_PERMISSION);
                return;
            }
        }
        if (prefsHelper.isNotifEnabled()) {
            startBrightnessService();
        }
    }

    private void applyBrightness(int progress) {
        Intent intent = new Intent(BrightnessService.ACTION_UPDATE_BRIGHTNESS);
        intent.putExtra(BrightnessService.EXTRA_BRIGHTNESS, progress / 100.0f);
        sendBroadcast(intent);
        intent.setClass(this, BrightnessService.class);
        if (prefsHelper.isNotifEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
        } else {
            startService(intent);
        }
    }

    private void applyVolume(int progress) {
        float reduction = 1.0f - (progress / 100.0f);
        Intent intent = new Intent(this, VolumeService.class);
        intent.setAction(VolumeService.ACTION_UPDATE_VOLUME);
        intent.putExtra(VolumeService.EXTRA_VOLUME_REDUCTION, reduction);
        startService(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIF_PERMISSION) startBrightnessService();
    }

    private void startBrightnessService() {
        Intent intent = new Intent(this, BrightnessService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) checkPermissions(); 
    }

    private void toggleAntiMotionMode() {
        Intent intent = new Intent(this, AntiMotionSicknessService.class);
        boolean isRunning = isServiceRunning(AntiMotionSicknessService.class);
        if (isRunning) {
            stopService(intent);
            Toast.makeText(this, R.string.anti_motion_disabled, Toast.LENGTH_SHORT).show();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
            Toast.makeText(this, R.string.anti_motion_enabled, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
