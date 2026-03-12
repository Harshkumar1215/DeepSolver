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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * DeepSolverAccessibilityService - "Lens Mode"
 * Scans screen, identifies the question automatically, and displays the answer directly.
 */
public class DeepSolverAccessibilityService extends AccessibilityService implements OverlayService.OnHighlightClickListener {

    private static final String TAG = "DeepSolverLens";
    private static final int FLOATING_WINDOW_HEIGHT = 700;
    // Updated User-Agent to be more modern and robust
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean wasReadingActive = false;
    private View searchView;
    private WindowManager windowManager;

    private class MCQQuestion {
        String questionText;
        List<String> options = new ArrayList<>();
        boolean hasMultipleCorrect = false;
        String questionType; // "single-choice", "multiple-choice", "true-false", etc.
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Log.d(TAG, "Lens Mode Active: Automatic Question Detection");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!OverlayService.isReadingActive) {
            wasReadingActive = false;
            return;
        }

        // Trigger Lens Scan when button is toggled to Active
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
            showToast("Scanning for Question...");
            
            MCQQuestion mcq = parseMCQFromScreen(rootNode);
            
            if (mcq.questionText != null && !mcq.questionText.isEmpty()) {
                String searchQuery = buildMCQSearchQuery(mcq);
                performBackgroundSearch(searchQuery);
            } else {
                showToast("Could not find a question on screen.");
            }
        }
        rootNode.recycle();
    }

    /**
     * Enhanced screen parsing for MCQs
     */
    private MCQQuestion parseMCQFromScreen(AccessibilityNodeInfo rootNode) {
        MCQQuestion mcq = new MCQQuestion();
        List<String> allText = new ArrayList<>();
        collectText(rootNode, allText);
        
        boolean inOptions = false;
        
        for (String text : allText) {
            // Check if this is a question (contains ? or longer than threshold)
            if (text.contains("?") || (text.length() > 30 && !isOption(text))) {
                mcq.questionText = text;
                inOptions = true;
            }
            // Check if this is an option
            else if (inOptions && isOption(text)) {
                mcq.options.add(text.trim());
            }
            // Detect true/false questions
            else if (text.matches("(?i).*true.*false.*") || 
                     text.matches("(?i).*select.*correct.*")) {
                mcq.questionType = "true-false";
            }
        }
        
        return mcq;
    }

    private boolean isOption(String text) {
        return text.matches("^[A-Za-z0-9][\\.\\)]\\s*.+") ||  // A), 1.
               text.matches("^[\\(\\[][A-Za-z0-9][\\)\\]]\\s*.+") || // (a), [1]
               text.matches("^[□○▪☐☑☒]\\s*.+") || // Checkbox/radio symbols
               text.matches("(?i)^(true|false|yes|no)$"); // True/False
    }

    /**
     * Improved Search Query Formation
     */
    private String buildMCQSearchQuery(MCQQuestion mcq) {
        StringBuilder query = new StringBuilder();
        
        // Add question text (clean and truncate)
        String cleanQuestion = mcq.questionText
            .replaceAll("[^a-zA-Z0-9\\s\\?]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        
        // Take first 100 chars for question
        if (cleanQuestion.length() > 100) {
            cleanQuestion = cleanQuestion.substring(0, 100);
        }
        
        query.append(cleanQuestion);
        
        // Add first few options for context
        if (!mcq.options.isEmpty()) {
            query.append(" ");
            for (int i = 0; i < Math.min(2, mcq.options.size()); i++) {
                String opt = mcq.options.get(i).replaceAll("[^a-zA-Z0-9\\s]", " ").trim();
                if (opt.length() > 20) opt = opt.substring(0, 20);
                query.append(opt).append(" ");
            }
        }
        
        return query.toString().trim();
    }

    /**
     * Recursively collects text from nodes.
     */
    private void collectText(AccessibilityNodeInfo node, List<String> textBlocks) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null && text.length() > 5) {
            String cleanText = text.toString().trim();
            if (!cleanText.isEmpty()) {
                textBlocks.add(cleanText);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectText(node.getChild(i), textBlocks);
        }
    }

    /**
     * Required by interface but no longer used for dots.
     */
    @Override
    public void onHighlightClick(String text) {
        performBackgroundSearch(text);
    }

    /**
     * Performs background search and shows results in popup
     */
    private void performBackgroundSearch(String queryText) {
        mainHandler.post(() -> {
            closeFloatingSearch();
            if (!Settings.canDrawOverlays(this)) return;

            // Show loading popup
            showSearchPopup("Solving: " + (queryText.length() > 50 ? queryText.substring(0, 50) + "..." : queryText), true);

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
     * Performs Google search and extracts text results using robust Jsoup selectors
     */
    private String doBackgroundSearch(String query) {
        try {
            // Encode the query correctly
            String searchUrl = "https://www.google.com/search?q=" + Uri.encode(query);
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://www.google.com/")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();
            
            return extractSearchResults(doc);
            
        } catch (IOException e) {
            Log.e(TAG, "Search Error: " + e.getMessage());
            return "Connection Error: Check your internet. " + e.getMessage();
        } catch (Exception e) {
            Log.e(TAG, "General Error: " + e.getMessage());
            return "Unexpected Error: " + e.getMessage();
        }
    }

    /**
     * Extracts search result snippets using multiple possible Google CSS selectors
     */
    private String extractSearchResults(Document doc) {
        StringBuilder results = new StringBuilder();
        
        // 1. Try Featured Snippet (Best for MCQs)
        Element featured = doc.selectFirst("div.LGOv1b, div.kp-blk, div.xpdopen");
        if (featured != null) {
            String snippet = featured.text().trim();
            if (!snippet.isEmpty()) {
                results.append("★ BEST MATCH:\n").append(snippet).append("\n\n---\n\n");
            }
        }

        // 2. Try common mobile/desktop snippet containers
        // Google uses many different classes, so we try multiple common ones
        Elements snippets = doc.select("div.BNeawe.s3v9rd.AP7Wnd, div.VwiC3b, span.hgKElc, div.yD7M6");
        
        int count = 0;
        for (Element snippet : snippets) {
            String text = snippet.text().trim();
            if (text.length() > 30 && !results.toString().contains(text.substring(0, 20))) {
                results.append(text).append("\n\n---\n\n");
                count++;
            }
            if (count >= 3) break; 
        }
        
        // 3. Fallback: Search result titles
        if (results.length() < 50) {
            Elements titles = doc.select("h3, div.vv770c");
            for (Element title : titles) {
                String text = title.text().trim();
                if (!text.isEmpty()) {
                    results.append("• ").append(text).append("\n");
                }
            }
        }
        
        if (results.length() == 0) {
            return "No clear answer found. Google might be blocking the request or the question is too vague.";
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

    private void updateSearchResults(String results) {
        if (searchView != null) {
            try {
                ProgressBar progressBar = searchView.findViewById(R.id.progress_bar);
                TextView tvResults = searchView.findViewById(R.id.tv_search_results);
                
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (tvResults != null) tvResults.setText(results);
                
            } catch (Exception e) {
                Log.e(TAG, "Update Error: " + e.getMessage());
            }
        }
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
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeFloatingSearch();
    }
}
