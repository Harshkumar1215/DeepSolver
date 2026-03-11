package com.harsh.deepsolver;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
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

import java.util.HashSet;
import java.util.Set;

/**
 * DeepSolverAccessibilityService captures screen text and triggers a
 * floating web search window to find MCQ answers.
 */
public class DeepSolverAccessibilityService extends AccessibilityService {

    private static final String TAG = "DeepSolverAS";
    private static final int MAX_QUERY_LENGTH = 500;
    private static final int FLOATING_WINDOW_HEIGHT = 800;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean wasReadingActive = false;
    private View searchView;
    private WindowManager windowManager;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Log.d(TAG, "Service Connected: Web Search Overlay Mode Active");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!OverlayService.isReadingActive) {
            wasReadingActive = false;
            return;
        }

        // Trigger search if just turned on
        boolean triggeredByToggle = !wasReadingActive && OverlayService.isReadingActive;
        wasReadingActive = true;

        if (triggeredByToggle) {
            performScreenScan();
        }
    }

    private void performScreenScan() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            showToast("No active window found");
            return;
        }

        Set<String> uniqueTexts = new HashSet<>();
        findAllText(rootNode, uniqueTexts);
        rootNode.recycle();

        if (!uniqueTexts.isEmpty()) {
            StringBuilder fullText = new StringBuilder();
            for (String text : uniqueTexts) {
                fullText.append(text).append(" ");
            }

            String queryText = fullText.toString().trim();
            if (!queryText.isEmpty()) {
                showToast("Scanning & Searching...");
                openFloatingSearch(queryText);
            }
        } else {
            showToast("No text found on screen");
        }
    }

    /**
     * Opens a mini-browser window at the bottom of the screen.
     */
    private void openFloatingSearch(String queryText) {
        // Clean query and limit length
        String query = queryText.trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }

        String searchUrl = "https://www.google.com/search?q=" + Uri.encode(query);

        mainHandler.post(() -> {
            // Remove previous search view if exists
            closeFloatingSearch();

            if (!Settings.canDrawOverlays(this)) {
                showToast("Please grant overlay permission");
                return;
            }

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
                
                // Turn off reading mode in the main overlay
                resetReadingState();
            } catch (Exception e) {
                Log.e(TAG, "Error showing search window: " + e.getMessage());
            }
        });
    }

    private void resetReadingState() {
        OverlayService.isReadingActive = false;
        OverlayService service = OverlayService.getInstance();
        if (service != null) {
            service.clearHighlights();
            // We need a way to update the icon back to white border. 
            // In OverlayService, the touch listener handles the border based on isReadingActive toggle.
            // Since we turned it off here, we should notify the service.
            Intent intent = new Intent(this, OverlayService.class);
            // This is just a dummy start to trigger any logic if needed, but since we have static access:
            // The next time the user taps, it will toggle correctly.
        }
    }

    private void closeFloatingSearch() {
        if (searchView != null && windowManager != null) {
            try {
                windowManager.removeView(searchView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing search view: " + e.getMessage());
            }
            searchView = null;
        }
    }

    private void findAllText(AccessibilityNodeInfo node, Set<String> textSet) {
        if (node == null) return;

        if (node.getText() != null && !node.getText().toString().trim().isEmpty()) {
            textSet.add(node.getText().toString().trim());
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            findAllText(node.getChild(i), textSet);
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
