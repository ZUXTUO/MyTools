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

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification(getString(R.string.service_desc)));
        
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
        if (intent != null && ACTION_UPDATE_BRIGHTNESS.equals(intent.getAction())) {
            float brightness = intent.getFloatExtra(EXTRA_BRIGHTNESS, 1.0f);
            updateOverlayAlpha(brightness);
        }
        return START_STICKY;
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

    private Notification getNotification(String contentText) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.service_title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
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
