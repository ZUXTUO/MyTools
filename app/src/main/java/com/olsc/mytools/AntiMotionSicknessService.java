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
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class AntiMotionSicknessService extends Service implements SensorEventListener {

    private WindowManager windowManager;
    private MotionCuesView leftView;
    private MotionCuesView rightView;
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

        setupViews();
    }

    private void setupViews() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        // Each side takes 20% of the screen width
        int sideWidth = (int) (metrics.widthPixels * 0.20f);

        leftView = new MotionCuesView(this, false);
        rightView = new MotionCuesView(this, true);

        WindowManager.LayoutParams baseParams = new WindowManager.LayoutParams(
                sideWidth,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );

        // Left Side
        WindowManager.LayoutParams leftParams = new WindowManager.LayoutParams();
        leftParams.copyFrom(baseParams);
        leftParams.gravity = Gravity.LEFT;
        windowManager.addView(leftView, leftParams);

        // Right Side
        WindowManager.LayoutParams rightParams = new WindowManager.LayoutParams();
        rightParams.copyFrom(baseParams);
        rightParams.gravity = Gravity.RIGHT;
        windowManager.addView(rightView, rightParams);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float ax = -event.values[0];
            float ay = event.values[1];
            if (leftView != null) leftView.setAcceleration(ax, ay);
            if (rightView != null) rightView.setAcceleration(ax, ay);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (windowManager != null) {
            if (leftView != null) windowManager.removeView(leftView);
            if (rightView != null) windowManager.removeView(rightView);
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
        private float accelX, accelY;
        private float filteredAccelX, filteredAccelY;
        private float velX, velY;
        private int dotColor;
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lineBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Dot> dots = new ArrayList<>();
        private final List<WindLine> windLines = new ArrayList<>();
        private long lastUpdateTime;
        private final float DOT_RADIUS = 22f;
        private final int DOTS_PER_SIDE = 12;
        private final int LINES_PER_SIDE = 8;
        private final boolean isRightSide;

        public MotionCuesView(Context context, boolean isRightSide) {
            super(context);
            this.isRightSide = isRightSide;
            com.olsc.mytools.util.PrefsHelper prefs = new com.olsc.mytools.util.PrefsHelper(context);
            dotColor = prefs.getDotColor();

            paint.setColor(dotColor);
            paint.setAlpha(180);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);
            borderPaint.setColor(Color.BLACK);
            borderPaint.setAlpha(200);

            linePaint.setColor(dotColor);
            linePaint.setStrokeWidth(10f);
            linePaint.setStrokeCap(Paint.Cap.ROUND);

            lineBorderPaint.setStyle(Paint.Style.STROKE);
            lineBorderPaint.setStrokeWidth(14f);
            lineBorderPaint.setColor(Color.BLACK);
            lineBorderPaint.setStrokeCap(Paint.Cap.ROUND);
            
            // Initialize dots for this specific side window
            float dotX = isRightSide ? 0.75f : 0.25f;
            for (int i = 0; i < DOTS_PER_SIDE; i++) {
                dots.add(new Dot(dotX, i / (float)DOTS_PER_SIDE));
            }

            // Initialize wind lines within the side window
            for (int i = 0; i < LINES_PER_SIDE; i++) {
                float lineX = (float)Math.random() * 0.8f + 0.1f;
                windLines.add(new WindLine(lineX, i / (float)LINES_PER_SIDE));
            }

            lastUpdateTime = System.currentTimeMillis();
        }

        public void setAcceleration(float x, float y) {
            this.accelX = x;
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

            // Physics for Vertical Dots
            filteredAccelY += (accelY - filteredAccelY) * dt * 8f;
            float targetVelY = (Math.abs(filteredAccelY) > 0.15f) ? filteredAccelY * 1.2f : 0;
            velY += (targetVelY - velY) * dt * 6f;

            // Physics for Horizontal Wind Lines
            filteredAccelX += (accelX - filteredAccelX) * dt * 8f;
            float targetVelX = (Math.abs(filteredAccelX) > 0.15f) ? filteredAccelX * 2.5f : 0;
            velX += (targetVelX - velX) * dt * 6f;

            // Draw Dots
            paint.setAlpha(180);
            for (Dot dot : dots) {
                dot.y += velY * dt;
                if (dot.y < 0) dot.y += 1.0f;
                if (dot.y > 1.0f) dot.y -= 1.0f;

                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(dot.x * w, dot.y * h, DOT_RADIUS, paint);
                canvas.drawCircle(dot.x * w, dot.y * h, DOT_RADIUS, borderPaint);
            }

            // Draw Wind Lines
            int lineAlpha = (int) (Math.min(1.0f, Math.abs(velX) * 2.0f) * 180);
            linePaint.setAlpha(lineAlpha);
            lineBorderPaint.setAlpha((int) (lineAlpha * 0.8f));

            if (lineAlpha > 0) {
                for (WindLine line : windLines) {
                    // Increased speed to compensate for 20% window width (5x)
                    line.x += velX * dt * 5.0f;
                    
                    if (line.x < -0.3f) line.x += 1.6f;
                    if (line.x > 1.3f) line.x -= 1.6f;

                    float startX = line.x * w;
                    float startY = line.y * h;
                    float length = w * line.length;
                    
                    canvas.drawLine(startX, startY, startX + length, startY, lineBorderPaint);
                    canvas.drawLine(startX, startY, startX + length, startY, linePaint);
                }
            }
            
            invalidate();
        }

        private static class Dot {
            float x, y;
            Dot(float x, float y) {
                this.x = x;
                this.y = y;
            }
        }

        private static class WindLine {
            float x, y;
            float length;

            WindLine(float x, float y) {
                this.x = x;
                this.y = y;
                this.length = 0.2f + (float)Math.random() * 0.3f;
            }
        }
    }
}
