package com.olsc.mytools;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class AntiMotionSicknessService extends Service implements SensorEventListener {

    private WindowManager windowManager;
    private MotionCuesView motionCuesView;
    private SensorManager sensorManager;
    private Sensor linearAccelerationSensor;

    private static final String CHANNEL_ID = "AntiMotionChannel";
    private static final int NOTIF_ID = 3;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, getNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        if (linearAccelerationSensor != null) {
            sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_GAME);
        }

        motionCuesView = new MotionCuesView(this);
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

        windowManager.addView(motionCuesView, params);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            // Y: + is forward acceleration (up), dots should move down (+)
            // X: + is left acceleration, dots should move right (+)
            motionCuesView.setAcceleration(-event.values[0], event.values[1]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (windowManager != null && motionCuesView != null) {
            windowManager.removeView(motionCuesView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.anti_motion_title),
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.anti_motion_title))
                .setContentText(getString(R.string.anti_motion_desc))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private static class MotionCuesView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float accelY;
        private float filteredAccelY;
        private float velY;
        private int dotColor;
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Dot> dots = new ArrayList<>();
        private long lastUpdateTime;
        private final float DOT_RADIUS = 22f;
        private final int DOTS_PER_SIDE = 12;

        public MotionCuesView(Context context) {
            super(context);
            com.olsc.mytools.util.PrefsHelper prefs = new com.olsc.mytools.util.PrefsHelper(context);
            dotColor = prefs.getDotColor();

            paint.setColor(dotColor);
            paint.setAlpha(180); // Higher opacity for visibility

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);
            borderPaint.setColor(Color.BLACK);
            borderPaint.setAlpha(200);
            
            // Initialize dots on the left and right sides
            for (int i = 0; i < DOTS_PER_SIDE; i++) {
                // Left side dots
                dots.add(new Dot(0.05f, i / (float)DOTS_PER_SIDE));
                // Right side dots
                dots.add(new Dot(0.95f, i / (float)DOTS_PER_SIDE));
            }
            lastUpdateTime = System.currentTimeMillis();
        }

        public void setAcceleration(float x, float y) {
            // Ignore X as requested by user
            this.accelY = y;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long now = System.currentTimeMillis();
            float dt = (now - lastUpdateTime) / 1000f;
            if (dt > 0.1f) dt = 0.1f;
            lastUpdateTime = now;

            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            // 1. Smooth the raw input (Low-pass filter)
            filteredAccelY += (accelY - filteredAccelY) * dt * 8f;

            // 2. Define target velocity based on filtered acceleration
            float targetVelY = 0;
            if (Math.abs(filteredAccelY) > 0.15f) { // Lower deadzone for higher sensitivity
                targetVelY = filteredAccelY * 1.2f; // Higher multiplier for speed
            }

            // 3. Smooth the velocity itself (The "fluidity" or "damping" feel)
            // This prevents the dots from stopping/starting too abruptly while avoiding "springing back"
            velY += (targetVelY - velY) * dt * 6f;

            for (Dot dot : dots) {
                // Continuous movement based on current velocity
                dot.y += velY * dt;

                // Infinite Wrap-around (Scrolling effect)
                if (dot.y < 0) dot.y += 1.0f;
                if (dot.y > 1.0f) dot.y -= 1.0f;

                // Draw Dot with Border
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(dot.x * w, dot.y * h, DOT_RADIUS, paint);
                canvas.drawCircle(dot.x * w, dot.y * h, DOT_RADIUS, borderPaint);
            }
            
            invalidate();
        }

        private static class Dot {
            float x, y; // Normalized coordinates 0.0 - 1.0

            Dot(float x, float y) {
                this.x = x;
                this.y = y;
            }
        }
    }
}
