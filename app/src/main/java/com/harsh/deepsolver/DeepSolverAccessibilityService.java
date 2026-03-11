package com.harsh.deepsolver;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * DeepSolverAccessibilityService - "Lens Mode"
 * Scans screen, places interactive dots on text, and allows targeted searching.
 */
public class DeepSolverAccessibilityService extends AccessibilityService implements OverlayService.OnHighlightClickListener {

    private static final String TAG = "DeepSolverLens";
    private static final int FLOATING_WINDOW_HEIGHT = 900;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean wasReadingActive = false;
    private View searchView;
    private WindowManager windowManager;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Log.d(TAG, "Lens Mode Active: Tap dots to search");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!OverlayService.isReadingActive) {
            wasReadingActive = false;
            return;
        }

        // Trigger Lens Scan when button is toggled to Orange
        if (!wasReadingActive && OverlayService.isReadingActive) {
            wasReadingActive = true;
            performLensScan();
        }
    }

    private void performLensScan() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        OverlayService service = OverlayService.getInstance();
        if (service != null) {
            service.clearHighlights();
            showToast("Lens Scanning...");
            scanAndAddDots(rootNode, service);
        }
        rootNode.recycle();
    }

    /**
     * Recursively finds text and places interactive dots.
     */
    private void scanAndAddDots(AccessibilityNodeInfo node, OverlayService service) {
        if (node == null) return;

        if (node.getText() != null && !node.getText().toString().trim().isEmpty()) {
            Rect rect = new Rect();
            node.getBoundsInScreen(rect);
            
            // Add a clickable Lens dot at the text location
            String textToSearch = node.getText().toString().trim();
            service.addLensDot(rect.centerX(), rect.centerY(), textToSearch, this);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            scanAndAddDots(node.getChild(i), service);
        }
    }

    /**
     * Triggered when a Lens Dot is clicked.
     */
    @Override
    public void onHighlightClick(String text) {
        showToast("Searching: " + (text.length() > 20 ? text.substring(0, 20) + "..." : text));
        openFloatingSearch(text);
    }

    private void openFloatingSearch(String queryText) {
        String searchUrl = "https://www.google.com/search?q=" + Uri.encode(queryText);

        mainHandler.post(() -> {
            closeFloatingSearch();

            if (!Settings.canDrawOverlays(this)) return;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    FLOATING_WINDOW_HEIGHT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.BOTTOM;

            try {
                searchView = LayoutInflater.from(this).inflate(R.layout.layout_floating_search, null);
                WebView miniBrowser = searchView.findViewById(R.id.mini_browser);
                ImageButton btnClose = searchView.findViewById(R.id.btn_close_search);

                WebSettings webSettings = miniBrowser.getSettings();
                webSettings.setJavaScriptEnabled(true);
                webSettings.setDomStorageEnabled(true);
                miniBrowser.setWebViewClient(new WebViewClient());
                miniBrowser.loadUrl(searchUrl);

                btnClose.setOnClickListener(v -> closeFloatingSearch());

                windowManager.addView(searchView, params);
            } catch (Exception e) {
                Log.e(TAG, "Search Error: " + e.getMessage());
            }
        });
    }

    private void closeFloatingSearch() {
        if (searchView != null && windowManager != null) {
            try {
                windowManager.removeView(searchView);
            } catch (Exception ignored) {}
            searchView = null;
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onInterrupt() {
        closeFloatingSearch();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeFloatingSearch();
    }
}
