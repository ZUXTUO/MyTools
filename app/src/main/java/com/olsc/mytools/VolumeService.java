package com.olsc.mytools;

import android.app.Service;
import android.content.Intent;
import android.media.audiofx.Equalizer;
import android.os.IBinder;
import android.util.Log;

public class VolumeService extends Service {
    private static final String TAG = "VolumeService";
    private Equalizer equalizer;
    
    public static final String ACTION_UPDATE_VOLUME = "com.olsc.mytools.UPDATE_VOLUME";
    public static final String EXTRA_VOLUME_REDUCTION = "volume_reduction_level";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            // Audio Session 0 is the global audio session
            // Note: This might require specific permissions or might be blocked on some devices
            equalizer = new Equalizer(0, 0);
            equalizer.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Equalizer on session 0", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_UPDATE_VOLUME.equals(intent.getAction())) {
            float reduction = intent.getFloatExtra(EXTRA_VOLUME_REDUCTION, 0.0f);
            applyVolumeReduction(reduction);
        }
        return START_STICKY;
    }

    private void applyVolumeReduction(float reduction) {
        // reduction is 0 to 1, where 0 means no reduction, 1 means max reduction
        if (equalizer == null) return;

        try {
            short bands = equalizer.getNumberOfBands();
            short minLevel = equalizer.getBandLevelRange()[0]; // Usually -1500 (-15dB)
            short maxLevel = equalizer.getBandLevelRange()[1]; // Usually +1500 (+15dB)

            // We want to scale from 0 (standard) to minLevel (max reduction)
            short level = (short) (reduction * minLevel);
            
            for (short i = 0; i < bands; i++) {
                equalizer.setBandLevel(i, level);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying equalizer levels", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (equalizer != null) {
            equalizer.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
