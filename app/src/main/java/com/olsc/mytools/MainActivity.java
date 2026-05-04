package com.olsc.mytools;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_main);

        // Handle window insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        brightnessSeekBar = findViewById(R.id.brightness_seekbar);
        brightnessValueText = findViewById(R.id.brightness_value);
        volumeSeekBar = findViewById(R.id.volume_seekbar);
        volumeValueText = findViewById(R.id.volume_value);
        
        randomResultText = findViewById(R.id.random_result_text);
        generateRandomBtn = findViewById(R.id.btn_generate_random);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        registerSensors();

        checkPermissions();
        setupBrightnessControl();
        setupVolumeControl();
        setupRandomControl();
        
        // Start services
        startService(new Intent(this, VolumeService.class));
    }

    private void registerSensors() {
        if (sensorManager != null) {
            Sensor pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            if (pressure != null) {
                sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL);
            }
            Sensor gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (gravity != null) {
                sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    private void setupRandomControl() {
        generateRandomBtn.setOnClickListener(v -> {
            generateTrueRandom();
        });
    }

    private void generateTrueRandom() {
        try {
            // 1. Collect Entropy from multiple sources
            long timestamp = System.nanoTime();
            int batteryLevel = getBatteryLevel();
            int voltage = getBatteryVoltage();
            
            StringBuilder entropySource = new StringBuilder();
            entropySource.append(timestamp);
            entropySource.append(pressureValue);
            entropySource.append(gravityValues[0]).append(gravityValues[1]).append(gravityValues[2]);
            entropySource.append(batteryLevel);
            entropySource.append(voltage);
            entropySource.append(Runtime.getRuntime().freeMemory());
            
            // 2. Matrix Calculation with Diamond Sutra
            byte[] entropyHash = sha256(entropySource.toString());
            byte[] sutraBytes = DIAMOND_SUTRA_EXCERPT.getBytes(StandardCharsets.UTF_8);
            
            // Simple XOR matrix calculation
            long finalValue = 0;
            for (int i = 0; i < entropyHash.length; i++) {
                int sutraIndex = i % sutraBytes.length;
                // XOR entropy with sutra, then mix with bit rotation
                int mixed = (entropyHash[i] ^ sutraBytes[sutraIndex]) & 0xFF;
                finalValue += mixed;
                finalValue = Long.rotateLeft(finalValue, 3);
            }
            
            // 3. Determine Result (+ or -)
            if (finalValue % 2 == 0) {
                randomResultText.setText(getString(R.string.random_res_plus));
                randomResultText.setTextColor(android.graphics.Color.parseColor("#4FACFE"));
            } else {
                randomResultText.setText(getString(R.string.random_res_minus));
                randomResultText.setTextColor(android.graphics.Color.parseColor("#FF4B2B"));
            }
            
            // Animation effect
            randomResultText.setAlpha(0f);
            randomResultText.animate().alpha(1f).setDuration(500).start();
            
        } catch (Exception e) {
            Toast.makeText(this, "Randomization failed", Toast.LENGTH_SHORT).show();
        }
    }

    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private int getBatteryVoltage() {
        Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return intent != null ? intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) : 0;
    }

    private byte[] sha256(String text) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            pressureValue = event.values[0];
        } else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            gravityValues = event.values.clone();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerSensors();
    }

    private void checkPermissions() {
        // 1. Check Overlay Permission (Critical for Brightness)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                Toast.makeText(this, getString(R.string.perm_request), Toast.LENGTH_LONG).show();
                return; 
            }
        }
        
        // 2. Check Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, 
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIF_PERMISSION);
                return;
            }
        }

        startBrightnessService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIF_PERMISSION) {
            startBrightnessService();
        }
    }

    private void startBrightnessService() {
        Intent intent = new Intent(this, BrightnessService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            checkPermissions(); 
        }
    }

    private void setupBrightnessControl() {
        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightnessValueText.setText(progress + "%");
                Intent intent = new Intent(BrightnessService.ACTION_UPDATE_BRIGHTNESS);
                intent.putExtra(BrightnessService.EXTRA_BRIGHTNESS, progress / 100.0f);
                sendBroadcast(intent);
                
                // Also update via startService in case broadcast is slow/not caught
                intent.setClass(MainActivity.this, BrightnessService.class);
                startService(intent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupVolumeControl() {
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeValueText.setText(progress + "%");
                
                // progress 100% -> reduction 0.0
                // progress 0% -> reduction 1.0 (max reduction)
                float reduction = 1.0f - (progress / 100.0f);
                
                Intent intent = new Intent(MainActivity.this, VolumeService.class);
                intent.setAction(VolumeService.ACTION_UPDATE_VOLUME);
                intent.putExtra(VolumeService.EXTRA_VOLUME_REDUCTION, reduction);
                startService(intent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
}
