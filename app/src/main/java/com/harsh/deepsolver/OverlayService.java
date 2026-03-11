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
 * OverlayService manages the floating button and the visual highlights on the screen.
 * It runs as a foreground service to ensure it remains active while the user 
 * is interacting with other applications.
 */
public class OverlayService extends Service {

    // Static flags to track service state
    public static boolean isRunning = false;
    public static boolean isReadingActive = false;
    
    private WindowManager windowManager;
    private View floatingButton;
    private ImageView floatingIcon;
    private WindowManager.LayoutParams buttonParams;
    
    // List to keep track of green dots currently on the screen
    private final List<View> highlightViews = new ArrayList<>();

    private static OverlayService instance;

    /**
     * Provides access to the current instance of the service for communication 
     * with the Accessibility Service.
     */
    public static OverlayService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        isReadingActive = false;
        instance = this;
        
        // Start as a foreground service with a notification
        startForegroundService();
        
        // Create the floating button UI
        initOverlay();
    }

    /**
     * Configures and starts the foreground service notification.
     */
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
                .setContentText("Overlay service is active")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
    }

    /**
     * Initializes the floating button and sets up the touch listener for 
     * movement and clicking.
     */
    private void initOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Inflate the floating button layout
        floatingButton = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null);
        floatingIcon = floatingButton.findViewById(R.id.iv_floating_icon);

        // Define layout parameters for the overlay window
        buttonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        buttonParams.gravity = Gravity.TOP | Gravity.START;
        buttonParams.x = 100; // Initial X position
        buttonParams.y = 100; // Initial Y position

        // Handle dragging and clicking of the floating button
        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

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
                        // Update position while dragging
                        buttonParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        buttonParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingButton, buttonParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        // Detect tap (if movement was minimal)
                        if (Math.abs(event.getRawX() - initialTouchX) < 10 && Math.abs(event.getRawY() - initialTouchY) < 10) {
                            toggleReading();
                        }
                        return true;
                }
                return false;
            }
        });

        // Add the button to the window manager
        windowManager.addView(floatingButton, buttonParams);
    }

    /**
     * Toggles the "Reading" mode. 
     * Changes border color and clears highlights when turned off.
     */
    private void toggleReading() {
        isReadingActive = !isReadingActive;
        if (isReadingActive) {
            // Orange border when active
            floatingIcon.setBackgroundResource(R.drawable.floating_bg_active);
        } else {
            // White border when inactive
            floatingIcon.setBackgroundResource(R.drawable.floating_bg);
            clearHighlights();
        }
    }

    /**
     * Removes all green highlights from the screen.
     */
    public void clearHighlights() {
        for (View view : highlightViews) {
            windowManager.removeView(view);
        }
        highlightViews.clear();
    }

    /**
     * Dynamically adds a green dot highlight at a specific screen coordinate.
     */
    public void addHighlight(int x, int y, int width, int height) {
        if (!isReadingActive) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;

        View highlight = new View(this);
        highlight.setBackgroundResource(R.drawable.green_dot);
        
        windowManager.addView(highlight, params);
        highlightViews.add(highlight);
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
        // Clean up UI on service stop
        if (floatingButton != null) windowManager.removeView(floatingButton);
        clearHighlights();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
