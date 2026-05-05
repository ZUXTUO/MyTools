package com.olsc.mytools;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

public class BrightnessService extends Service {
    private WindowManager windowManager;
    private View overlayView;
    private static final String CHANNEL_ID = "BrightnessServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_UPDATE_BRIGHTNESS = "com.olsc.mytools.UPDATE_BRIGHTNESS";
    public static final String EXTRA_BRIGHTNESS = "brightness_level";

    public static final String ACTION_NOTIF_BRIGHTNESS_PLUS = "com.olsc.mytools.NOTIF_BRIGHTNESS_PLUS";
    public static final String ACTION_NOTIF_BRIGHTNESS_MINUS = "com.olsc.mytools.NOTIF_BRIGHTNESS_MINUS";
    public static final String ACTION_NOTIF_VOLUME_PLUS = "com.olsc.mytools.NOTIF_VOLUME_PLUS";
    public static final String ACTION_NOTIF_VOLUME_MINUS = "com.olsc.mytools.NOTIF_VOLUME_MINUS";

    private com.olsc.mytools.util.PrefsHelper prefsHelper;
    private android.widget.RemoteViews remoteViews;
    private int currentBrightness;
    private int currentVolume;

    @Override
    public void onCreate() {
        super.onCreate();
        prefsHelper = new com.olsc.mytools.util.PrefsHelper(this);
        currentBrightness = prefsHelper.getBrightness();
        currentVolume = prefsHelper.getVolume();

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification());
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new View(this);
        overlayView.setBackgroundColor(Color.BLACK);
        overlayView.setAlpha(0.0f); // Initially invisible

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT
        );

        windowManager.addView(overlayView, params);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_UPDATE_BRIGHTNESS.equals(action)) {
                float brightnessFactor = intent.getFloatExtra(EXTRA_BRIGHTNESS, 1.0f);
                currentBrightness = (int) (brightnessFactor * 100);
                updateOverlayAlpha(brightnessFactor);
                updateNotification();
            } else if (ACTION_NOTIF_BRIGHTNESS_PLUS.equals(action)) {
                adjustBrightness(5);
            } else if (ACTION_NOTIF_BRIGHTNESS_MINUS.equals(action)) {
                adjustBrightness(-5);
            } else if (ACTION_NOTIF_VOLUME_PLUS.equals(action)) {
                adjustVolume(5);
            } else if (ACTION_NOTIF_VOLUME_MINUS.equals(action)) {
                adjustVolume(-5);
            }
        }
        return START_STICKY;
    }

    private void adjustBrightness(int delta) {
        currentBrightness = Math.max(0, Math.min(100, currentBrightness + delta));
        prefsHelper.saveBrightness(currentBrightness);
        updateOverlayAlpha(currentBrightness / 100.0f);
        updateNotification();
    }

    private void adjustVolume(int delta) {
        currentVolume = Math.max(0, Math.min(100, currentVolume + delta));
        prefsHelper.saveVolume(currentVolume);
        
        Intent intent = new Intent(this, VolumeService.class);
        intent.setAction(VolumeService.ACTION_UPDATE_VOLUME);
        intent.putExtra(VolumeService.EXTRA_VOLUME_REDUCTION, 1.0f - (currentVolume / 100.0f));
        startService(intent);
        
        updateNotification();
    }

    private void updateOverlayAlpha(float brightness) {
        // brightness is 0 to 1, where 1 means standard min brightness, 
        // and lower means darker (more alpha for the black overlay).
        // If brightness is 1.0, alpha is 0.
        // If brightness is 0.0, alpha is 0.8 (don't go to 1.0 or screen will be pitch black).
        float alpha = (1.0f - brightness) * 0.8f;
        if (overlayView != null) {
            overlayView.setAlpha(alpha);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.service_title),
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification getNotification() {
        remoteViews = new android.widget.RemoteViews(getPackageName(), R.layout.notification_controls);
        
        updateRemoteViews();

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setCustomContentView(remoteViews)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void updateRemoteViews() {
        if (remoteViews == null) return;
        
        remoteViews.setTextViewText(R.id.text_brightness_value, currentBrightness + "%");
        remoteViews.setTextViewText(R.id.text_volume_value, currentVolume + "%");

        remoteViews.setOnClickPendingIntent(R.id.btn_brightness_plus, getPendingIntent(ACTION_NOTIF_BRIGHTNESS_PLUS));
        remoteViews.setOnClickPendingIntent(R.id.btn_brightness_minus, getPendingIntent(ACTION_NOTIF_BRIGHTNESS_MINUS));
        remoteViews.setOnClickPendingIntent(R.id.btn_volume_plus, getPendingIntent(ACTION_NOTIF_VOLUME_PLUS));
        remoteViews.setOnClickPendingIntent(R.id.btn_volume_minus, getPendingIntent(ACTION_NOTIF_VOLUME_MINUS));
    }

    private android.app.PendingIntent getPendingIntent(String action) {
        Intent intent = new Intent(this, BrightnessService.class);
        intent.setAction(action);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return android.app.PendingIntent.getService(this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        } else {
            return android.app.PendingIntent.getService(this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    private void updateNotification() {
        updateRemoteViews();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, getNotification());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && overlayView != null) {
            windowManager.removeView(overlayView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
