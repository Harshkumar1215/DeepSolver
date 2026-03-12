package com.harsh.deepsolver;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
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
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

/**
 * DeepSolverAccessibilityService - "Lens Mode"
 * Scans screen, places interactive dots on text, and allows targeted searching.
 * Supports both WebView search and background text search.
 */
public class DeepSolverAccessibilityService extends AccessibilityService implements OverlayService.OnHighlightClickListener {

    private static final String TAG = "DeepSolverLens";
    private static final int FLOATING_WINDOW_HEIGHT = 600;
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36";

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
        String displayText = text.length() > 30 ? text.substring(0, 30) + "..." : text;
        showToast("Searching: " + displayText);
        performBackgroundSearch(text);
    }

    /**
     * Performs background search and shows results in popup (no WebView)
     */
    private void performBackgroundSearch(String queryText) {
        mainHandler.post(() -> {
            closeFloatingSearch();
            if (!Settings.canDrawOverlays(this)) return;

            // Show loading popup first
            showSearchPopup("Searching for: " + queryText, true);

            // Perform search in background thread
            new Thread(() -> {
                String searchResults = doBackgroundSearch(queryText);
                
                mainHandler.post(() -> {
                    updateSearchResults(searchResults);
                });
            }).start();
        });
    }

    /**
     * Performs Google search and extracts text results using Jsoup
     */
    private String doBackgroundSearch(String query) {
        try {
            String searchUrl = "https://www.google.com/search?q=" + Uri.encode(query);
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get();
            
            return extractSearchResults(doc);
            
        } catch (IOException e) {
            Log.e(TAG, "Search Error: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Extracts search result titles and snippets from HTML Document
     */
    private String extractSearchResults(Document doc) {
        StringBuilder results = new StringBuilder();
        
        // Try to find snippets (similar to the Python implementation)
        // Google uses various classes, 'BNeawe s3v9rd AP7Wnd' is common for mobile snippets
        Elements snippets = doc.select("div.BNeawe.s3v9rd.AP7Wnd");
        
        if (!snippets.isEmpty()) {
            int count = 0;
            for (Element snippet : snippets) {
                String text = snippet.text().trim();
                if (text.length() > 20) {
                    results.append(text).append("\n\n---\n\n");
                    count++;
                }
                if (count >= 3) break; // Show top 3 results
            }
        }
        
        if (results.length() == 0) {
            // Fallback to titles if no snippets found
            Elements titles = doc.select("h3");
            int count = 0;
            for (Element title : titles) {
                String text = title.text().trim();
                if (!text.isEmpty()) {
                    results.append(count + 1).append(". ").append(text).append("\n\n");
                    count++;
                }
                if (count >= 5) break;
            }
        }
        
        if (results.length() == 0) {
            results.append("No clear answer found. Try selecting more specific text.");
        }
        
        return results.toString();
    }

    /**
     * Shows floating search popup with results
     */
    private void showSearchPopup(String message, boolean showProgress) {
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
            searchView = LayoutInflater.from(this).inflate(R.layout.layout_search_results, null);
            
            TextView tvResults = searchView.findViewById(R.id.tv_search_results);
            ImageButton btnClose = searchView.findViewById(R.id.btn_close_results);
            ProgressBar progressBar = searchView.findViewById(R.id.progress_bar);
            
            tvResults.setText(message);
            
            if (showProgress) {
                progressBar.setVisibility(View.VISIBLE);
            }
            
            btnClose.setOnClickListener(v -> closeFloatingSearch());
            
            windowManager.addView(searchView, params);
        } catch (Exception e) {
            Log.e(TAG, "Popup Error: " + e.getMessage());
        }
    }

    /**
     * Updates search results in the popup
     */
    private void updateSearchResults(String results) {
        if (searchView != null) {
            try {
                ProgressBar progressBar = searchView.findViewById(R.id.progress_bar);
                TextView tvResults = searchView.findViewById(R.id.tv_search_results);
                
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                
                if (tvResults != null) {
                    tvResults.setText(results);
                }
            } catch (Exception e) {
                Log.e(TAG, "Update Error: " + e.getMessage());
            }
        }
    }

    /**
     * Opens WebView search as fallback
     */
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
