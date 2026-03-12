package com.harsh.deepsolver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * OverlayService manages the floating Lens button and interactive text dots.
 */
public class OverlayService extends Service {

    public static boolean isRunning = false;
    public static boolean isReadingActive = false;
    
    private WindowManager windowManager;
    private View floatingButton;
    private ImageView floatingIcon;
    private WindowManager.LayoutParams buttonParams;
    
    private final List<View> highlightViews = new ArrayList<>();
    private static OverlayService instance;

    /**
     * Interface to communicate dot clicks back to the Accessibility Service.
     */
    public interface OnHighlightClickListener {
        void onHighlightClick(String text);
    }

    public static OverlayService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        isReadingActive = false;
        instance = this;
        startForegroundService();
        initOverlay();
    }

    private void startForegroundService() {
        String channelId = "DeepSolverService";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "Deep Solver Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Deep Solver")
                .setContentText("Lens Mode is ready")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
    }

    private void initOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingButton = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null);
        floatingIcon = floatingButton.findViewById(R.id.iv_floating_icon);

        buttonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        buttonParams.gravity = Gravity.TOP | Gravity.START;
        buttonParams.x = 100;
        buttonParams.y = 100;

        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = buttonParams.x;
                        initialY = buttonParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        buttonParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        buttonParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingButton, buttonParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        if (Math.abs(event.getRawX() - initialTouchX) < 10 && Math.abs(event.getRawY() - initialTouchY) < 10) {
                            toggleReading();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingButton, buttonParams);
    }

    private void toggleReading() {
        isReadingActive = !isReadingActive;
        if (isReadingActive) {
            floatingIcon.setBackgroundResource(R.drawable.floating_bg_active);
        } else {
            floatingIcon.setBackgroundResource(R.drawable.floating_bg);
            clearHighlights();
        }
    }

    public void clearHighlights() {
        for (View view : highlightViews) {
            try {
                windowManager.removeView(view);
            } catch (Exception ignored) {}
        }
        highlightViews.clear();
    }

    /**
     * Adds an interactive selection dot at text coordinates.
     * (Deprecated: Logic moved to automatic detection)
     */
    public void addLensDot(int x, int y, final String text, final OnHighlightClickListener listener) {
        // Dot logic removed as requested for direct display
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        isReadingActive = false;
        instance = null;
        if (floatingButton != null) windowManager.removeView(floatingButton);
        clearHighlights();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
