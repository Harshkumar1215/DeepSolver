package com.harsh.deepsolver;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * DeepSolverAccessibilityService - Enhanced Version
 * Handles automatic MCQ detection and answer retrieval with visual scanning effects.
 */
public class DeepSolverAccessibilityService extends AccessibilityService implements OverlayService.OnHighlightClickListener {

    private static final String TAG = "DeepSolverEnhanced";
    private static final int FLOATING_WINDOW_HEIGHT = 700;

    // Rotating User Agents to avoid blocking
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1"
    };

    // Multiple search engines for redundancy
    private static final String[] SEARCH_ENGINES = {
            "https://www.google.com/search?q=%s",
            "https://www.bing.com/search?q=%s",
            "https://duckduckgo.com/html/?q=%s"
    };

    // Common answer sites for direct parsing
    private static final Map<String, String> ANSWER_SITES = new HashMap<String, String>() {{
        put("brainly.com", "div.js-answer-content, div.answers-container");
        put("quora.com", "div.q-text.qu-bold, div.qu-borderRadius--small");
        put("stackoverflow.com", "div.accepted-answer, div.answer");
        put("answers.com", "div.answer-content");
        put("toppr.com", "div.answer-block");
        put("byjus.com", "div.answer-text");
        put("vedantu.com", "div.answer-content");
    }};

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Random random = new Random();

    private boolean wasReadingActive = false;
    private View searchView;
    private WindowManager windowManager;

    // Visual highlights management
    private final List<View> highlightOverlays = new ArrayList<>();
    private ValueAnimator pulseAnimator;

    // Cache for recent answers to avoid repeated searches
    private final Map<String, CachedAnswer> answerCache = new HashMap<>();
    private static final int CACHE_SIZE = 20;
    private static final long CACHE_DURATION = 30 * 60 * 1000; // 30 minutes

    private static class CachedAnswer {
        String answer;
        long timestamp;

        CachedAnswer(String answer) {
            this.answer = answer;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid() {
            return System.currentTimeMillis() - timestamp < CACHE_DURATION;
        }
    }

    private static class MCQQuestion {
        String questionText = "";
        List<String> options = new ArrayList<>();
        boolean hasMultipleCorrect = false;
        String questionType = "unknown"; // single-choice, multiple-choice, true-false, fill-blank, math
    }

    private static class AnswerCandidate implements Comparable<AnswerCandidate> {
        String text;
        String source;
        double score;
        String matchedOption;

        AnswerCandidate(String text, String source, double score) {
            this.text = text;
            this.source = source;
            this.score = score;
        }

        @Override
        public int compareTo(AnswerCandidate other) {
            return Double.compare(other.score, this.score);
        }
    }

    private static class TextNodeInfo {
        Rect bounds;
        String text;

        TextNodeInfo(Rect bounds, String text) {
            this.bounds = bounds;
            this.text = text;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Log.d(TAG, "Enhanced Deep Solver Service Connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!OverlayService.isReadingActive) {
            wasReadingActive = false;
            return;
        }

        if (!wasReadingActive) {
            wasReadingActive = true;
            performEnhancedLensScan();
        }
    }

    /**
     * Main scanning method with enhanced detection and visual effects
     */
    private void performEnhancedLensScan() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // Create scanning effect
        createScanningEffect(rootNode);

        executorService.execute(() -> {
            MCQQuestion mcq = parseEnhancedMCQ(rootNode);
            rootNode.recycle();

            if (mcq.questionText.isEmpty()) {
                mainHandler.post(() -> {
                    showToast("❌ No question detected");
                    clearScanHighlights();
                });
                return;
            }

            // Small delay to let the scanning effect be visible
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

            // Show detected question in popup
            mainHandler.post(() -> showSearchPopup("📝 Question detected!\n\n" + mcq.questionText, true));

            // Check cache first
            String cacheKey = generateCacheKey(mcq);
            CachedAnswer cached = answerCache.get(cacheKey);
            if (cached != null && cached.isValid()) {
                mainHandler.post(() -> displayAnswerWithOptions(cached.answer, mcq));
                return;
            }

            // Perform multi-engine search
            String answer = searchAllEngines(mcq);

            // Cache the answer
            if (answer != null && !answer.isEmpty()) {
                if (answerCache.size() >= CACHE_SIZE) {
                    // Remove oldest entry
                    String oldestKey = Collections.min(answerCache.entrySet(),
                            Comparator.comparingLong(e -> e.getValue().timestamp)).getKey();
                    answerCache.remove(oldestKey);
                }
                answerCache.put(cacheKey, new CachedAnswer(answer));
            }

            String finalAnswer = (answer != null && !answer.isEmpty()) ? answer : "No answer found";
            mainHandler.post(() -> displayAnswerWithOptions(finalAnswer, mcq));
        });
    }

    /**
     * Enhanced MCQ parsing with better option detection
     */
    private MCQQuestion parseEnhancedMCQ(AccessibilityNodeInfo rootNode) {
        MCQQuestion mcq = new MCQQuestion();
        List<String> allText = new ArrayList<>();
        collectAllText(rootNode, allText);

        // Common option patterns
        Pattern[] optionPatterns = {
                Pattern.compile("^[A-Z][.\\)]\\s*(.+)$"),           // A), A.
                Pattern.compile("^[a-z][.\\)]\\s*(.+)$"),           // a), a.
                Pattern.compile("^[0-9][.\\)]\\s*(.+)$"),           // 1), 1.
                Pattern.compile("^[0-9]{2}[.\\)]\\s*(.+)$"),        // 10), 10.
                Pattern.compile("^[([]?[A-Za-z0-9][)\\]]?\\s*(.+)$"), // (a), [1]
                Pattern.compile("^[○▪□●]\\s*(.+)$"),                  // Symbol options
                Pattern.compile("^(True|False|Yes|No)$", Pattern.CASE_INSENSITIVE)
        };

        // Question indicators
        String[] questionIndicators = {
                "?", "which", "what", "who", "where", "when", "why", "how",
                "choose", "select", "identify", "find", "solve", "calculate",
                "determine", "evaluate", "explain", "describe", "define"
        };

        List<String> tempOptions = new ArrayList<>();
        boolean foundQuestion = false;

        for (String text : allText) {
            String lowerText = text.toLowerCase();

            // Check if this is a question
            if (!foundQuestion) {
                boolean isQuestion = text.contains("?");
                if (!isQuestion) {
                    for (String indicator : questionIndicators) {
                        if (lowerText.startsWith(indicator) || lowerText.contains(" " + indicator)) {
                            isQuestion = true;
                            break;
                        }
                    }
                }

                if (isQuestion || text.length() > 50) {
                    mcq.questionText = text;
                    foundQuestion = true;
                    continue;
                }
            }

            // Check if this is an option (only after finding question)
            if (foundQuestion) {
                for (Pattern pattern : optionPatterns) {
                    if (pattern.matcher(text).matches()) {
                        String cleaned = pattern.matcher(text).replaceAll("$1").trim();
                        if (!tempOptions.contains(cleaned) && cleaned.length() > 1) {
                            tempOptions.add(cleaned);
                        }
                        break;
                    }
                }
            }

            // Detect question type
            if (lowerText.contains("select all") || lowerText.contains("multiple answers") ||
                    lowerText.contains("choose all") || lowerText.contains("more than one")) {
                mcq.hasMultipleCorrect = true;
                mcq.questionType = "multiple-choice";
            } else if (lowerText.contains("true") && lowerText.contains("false")) {
                mcq.questionType = "true-false";
            } else if (text.contains("____") || text.contains("....") || text.contains("___")) {
                mcq.questionType = "fill-blank";
            } else if (text.matches(".*[+\\-*/=√^π∫∑].*") || text.matches(".*[0-9].*[xX÷].*")) {
                mcq.questionType = "math";
            }
        }

        if (!tempOptions.isEmpty()) {
            mcq.options = tempOptions;
        }

        return mcq;
    }

    /**
     * Creates a subtle scanning effect on text elements
     */
    private void createScanningEffect(AccessibilityNodeInfo rootNode) {
        clearScanHighlights();
        List<TextNodeInfo> textNodes = new ArrayList<>();
        collectTextNodesWithBounds(rootNode, textNodes);
        if (textNodes.isEmpty()) return;
        for (TextNodeInfo nodeInfo : textNodes) {
            createTextHighlight(nodeInfo.bounds);
        }
        startScanAnimation(textNodes.size());
    }

    private void collectTextNodesWithBounds(AccessibilityNodeInfo node, List<TextNodeInfo> textNodes) {
        if (node == null) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 2 && node.isVisibleToUser()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.width() > 20 && bounds.height() > 20) {
                textNodes.add(new TextNodeInfo(bounds, text.toString()));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectTextNodesWithBounds(node.getChild(i), textNodes);
        }
    }

    private void createTextHighlight(Rect bounds) {
        if (windowManager == null) return;
        FrameLayout highlightView = new FrameLayout(this);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(8f);
        drawable.setColor(Color.parseColor("#3300BFFF"));
        drawable.setStroke(2, Color.parseColor("#8000BFFF"));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            highlightView.setElevation(4f);
            highlightView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 8f);
                }
            });
            highlightView.setClipToOutline(true);
        }
        highlightView.setBackground(drawable);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                bounds.width() + 20, bounds.height() + 10,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = bounds.left - 10;
        params.y = bounds.top - 5;

        try {
            windowManager.addView(highlightView, params);
            highlightOverlays.add(highlightView);
        } catch (Exception e) {
            Log.e(TAG, "Highlight add error: " + e.getMessage());
        }
    }

    private void startScanAnimation(int totalHighlights) {
        if (highlightOverlays.isEmpty()) return;
        if (pulseAnimator != null && pulseAnimator.isRunning()) pulseAnimator.cancel();
        pulseAnimator = ValueAnimator.ofFloat(0, totalHighlights);
        pulseAnimator.setDuration(totalHighlights * 200L);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            int currentIndex = (int) (float) animation.getAnimatedValue();
            for (int i = 0; i < highlightOverlays.size(); i++) {
                View overlay = highlightOverlays.get(i);
                if (overlay != null) {
                    float distance = Math.abs(i - currentIndex);
                    overlay.setAlpha(distance < 1 ? 0.8f : (distance < 3 ? 0.3f : 0.1f));
                }
            }
        });
        pulseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                for (View overlay : highlightOverlays) {
                    overlay.animate().alpha(0f).setDuration(300).withEndAction(() -> clearScanHighlights()).start();
                }
            }
        });
        pulseAnimator.start();
    }

    private void clearScanHighlights() {
        for (View overlay : highlightOverlays) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
        }
        highlightOverlays.clear();
        if (pulseAnimator != null && pulseAnimator.isRunning()) pulseAnimator.cancel();
    }

    /**
     * Search across multiple engines for better results
     */
    private String searchAllEngines(MCQQuestion mcq) {
        List<AnswerCandidate> allCandidates = new ArrayList<>();
        List<String> queries = buildSearchQueries(mcq);

        for (String query : queries) {
            for (String engine : SEARCH_ENGINES) {
                try {
                    String url = String.format(engine, URLEncoder.encode(query, StandardCharsets.UTF_8.toString()));
                    allCandidates.addAll(searchWithEngine(url, mcq));
                    Thread.sleep(random.nextInt(500) + 200);
                } catch (Exception e) {
                    Log.e(TAG, "Search error: " + e.getMessage());
                }
            }
        }

        if (!allCandidates.isEmpty()) {
            Collections.sort(allCandidates);
            AnswerCandidate best = allCandidates.get(0);
            StringBuilder formattedAnswer = new StringBuilder();
            formattedAnswer.append("🎯 Best Answer (").append((int)(best.score * 100)).append("% confidence)\n");
            formattedAnswer.append("Source: ").append(best.source).append("\n\n").append(best.text);
            if (best.matchedOption != null && !mcq.options.isEmpty()) {
                formattedAnswer.append("\n\n✅ Matched Option: ").append(best.matchedOption);
            }
            return formattedAnswer.toString();
        }
        return null;
    }

    private List<String> buildSearchQueries(MCQQuestion mcq) {
        List<String> queries = new ArrayList<>();
        String baseQuestion = mcq.questionText.replaceAll("[^a-zA-Z0-9\\s]", " ").trim();
        queries.add(baseQuestion);
        queries.add(baseQuestion + " correct answer");
        if (mcq.questionType.equals("math")) queries.add(baseQuestion + " solution step by step");
        if (!mcq.options.isEmpty()) {
            String optionsStr = String.join(" ", mcq.options.subList(0, Math.min(2, mcq.options.size())));
            queries.add(baseQuestion + " " + optionsStr);
        }
        return queries;
    }

    private List<AnswerCandidate> searchWithEngine(String url, MCQQuestion mcq) throws IOException {
        List<AnswerCandidate> candidates = new ArrayList<>();
        String userAgent = USER_AGENTS[random.nextInt(USER_AGENTS.length)];
        Document doc = Jsoup.connect(url).userAgent(userAgent).timeout(15000).followRedirects(true).get();
        String source = url.contains("google") ? "Google" : (url.contains("bing") ? "Bing" : "DuckDuckGo");
        candidates.addAll(extractFeaturedSnippets(doc, mcq, source));
        candidates.addAll(extractFromKnownSites(doc, mcq));
        candidates.addAll(extractSearchSnippets(doc, mcq, source));
        return candidates;
    }

    private List<AnswerCandidate> extractFeaturedSnippets(Document doc, MCQQuestion mcq, String source) {
        List<AnswerCandidate> candidates = new ArrayList<>();
        String[] featuredSelectors = {"div[class*='LGOv1b']", "div[class*='kp-blk']", "div[class*='xpdopen']", "div.BNeawe.s3v9rd.AP7Wnd", "div.hgKElc", "div.yD7M6"};
        for (String selector : featuredSelectors) {
            Elements elements = doc.select(selector);
            for (Element elem : elements) {
                String text = elem.text();
                if (text.length() > 20 && text.length() < 1000) {
                    double score = calculateAnswerScore(text, mcq);
                    if (score > 0.6) candidates.add(new AnswerCandidate(text, source + " (Featured)", score));
                }
            }
        }
        return candidates;
    }

    private List<AnswerCandidate> extractFromKnownSites(Document doc, MCQQuestion mcq) {
        List<AnswerCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, String> site : ANSWER_SITES.entrySet()) {
            if (doc.location().contains(site.getKey())) {
                Elements answers = doc.select(site.getValue());
                for (Element answer : answers) {
                    double score = calculateAnswerScore(answer.text(), mcq) + 0.2;
                    candidates.add(new AnswerCandidate(answer.text(), site.getKey(), score));
                }
            }
        }
        return candidates;
    }

    private List<AnswerCandidate> extractSearchSnippets(Document doc, MCQQuestion mcq, String source) {
        List<AnswerCandidate> candidates = new ArrayList<>();
        String[] snippetSelectors = {"div.VwiC3b", "span.hgKElc", "div.yD7M6", "div.BNeawe.s3v9rd"};
        for (String selector : snippetSelectors) {
            Elements snippets = doc.select(selector);
            for (Element snippet : snippets) {
                String text = snippet.text();
                if (text.length() > 30 && text.length() < 500) {
                    double score = calculateAnswerScore(text, mcq);
                    if (score > 0.3) candidates.add(new AnswerCandidate(text, source + " (Search)", score));
                }
            }
        }
        return candidates;
    }

    private double calculateAnswerScore(String text, MCQQuestion mcq) {
        double score = 0.0;
        String lowerText = text.toLowerCase();
        String lowerQuestion = mcq.questionText.toLowerCase();
        String[] questionWords = lowerQuestion.split("\\s+");
        int matchedWords = 0;
        for (String word : questionWords) if (word.length() > 3 && lowerText.contains(word)) matchedWords++;
        score += (double) matchedWords / questionWords.length * 0.3;
        if (!mcq.options.isEmpty()) {
            double maxOptionScore = 0;
            for (String option : mcq.options) {
                if (lowerText.contains(option.toLowerCase())) {
                    maxOptionScore = Math.max(maxOptionScore, (lowerText.contains("correct") || lowerText.contains("answer")) ? 1.0 : 0.7);
                }
            }
            score += maxOptionScore * 0.5;
        }
        return Math.min(score, 1.0);
    }

    private String generateCacheKey(MCQQuestion mcq) {
        return mcq.questionText.substring(0, Math.min(50, mcq.questionText.length())) + "|" + String.join("|", mcq.options);
    }

    private void displayAnswerWithOptions(String answer, MCQQuestion mcq) {
        closeFloatingSearch();
        if (!Settings.canDrawOverlays(this)) return;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, FLOATING_WINDOW_HEIGHT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM;
        try {
            searchView = LayoutInflater.from(this).inflate(R.layout.layout_search_results, null);
            TextView tvQuestion = searchView.findViewById(R.id.tv_detected_question);
            TextView tvOptions = searchView.findViewById(R.id.tv_detected_options);
            TextView tvAnswer = searchView.findViewById(R.id.tv_search_results);
            if (tvQuestion != null) {
                tvQuestion.setText(mcq.questionText);
                tvQuestion.setVisibility(View.VISIBLE);
            }
            if (tvOptions != null && !mcq.options.isEmpty()) {
                tvOptions.setText(String.join(", ", mcq.options));
                tvOptions.setVisibility(View.VISIBLE);
            }
            if (tvAnswer != null) tvAnswer.setText(answer);
            ProgressBar progressBar = searchView.findViewById(R.id.progress_bar);
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            searchView.findViewById(R.id.btn_close_results).setOnClickListener(v -> closeFloatingSearch());
            windowManager.addView(searchView, params);
        } catch (Exception e) { Log.e(TAG, "Display error: " + e.getMessage()); }
    }

    private void showSearchPopup(String message, boolean showProgress) {
        closeFloatingSearch();
        if (!Settings.canDrawOverlays(this)) return;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, FLOATING_WINDOW_HEIGHT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM;
        try {
            searchView = LayoutInflater.from(this).inflate(R.layout.layout_search_results, null);
            TextView tvResults = searchView.findViewById(R.id.tv_search_results);
            ProgressBar progressBar = searchView.findViewById(R.id.progress_bar);
            tvResults.setText(message);
            if (progressBar != null) progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            searchView.findViewById(R.id.btn_close_results).setOnClickListener(v -> closeFloatingSearch());
            windowManager.addView(searchView, params);
        } catch (Exception e) { Log.e(TAG, "Popup error: " + e.getMessage()); }
    }

    private void collectAllText(AccessibilityNodeInfo node, List<String> textList) {
        if (node == null) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 2) {
            String cleanText = text.toString().trim();
            if (!cleanText.isEmpty() && !textList.contains(cleanText)) textList.add(cleanText);
        }
        for (int i = 0; i < node.getChildCount(); i++) collectAllText(node.getChild(i), textList);
    }

    private void closeFloatingSearch() {
        if (searchView != null && windowManager != null) {
            try { windowManager.removeView(searchView); } catch (Exception ignored) {}
            searchView = null;
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    @Override public void onHighlightClick(String text) { performEnhancedLensScan(); }
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        super.onDestroy();
        closeFloatingSearch();
        clearScanHighlights();
        executorService.shutdown();
    }
}
