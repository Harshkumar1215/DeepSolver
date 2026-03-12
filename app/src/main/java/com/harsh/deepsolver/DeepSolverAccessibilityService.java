package com.harsh.deepsolver;

import android.animation.ValueAnimator;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * DeepSolverAccessibilityService - Enhanced Version
 * Handles automatic MCQ detection and answer retrieval
 */
public class DeepSolverAccessibilityService extends AccessibilityService {

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
    private SharedPreferences prefs;

    private final List<View> highlightOverlays = new ArrayList<>();
    private int currentHighlightIndex = 0;
    private ValueAnimator pulseAnimator;

    // Cache for recent answers to avoid repeated searches
    private final Map<String, CachedAnswer> answerCache = new HashMap<>();
    private static final int CACHE_SIZE = 20;
    private static final long CACHE_DURATION = 30 * 60 * 1000; // 30 minutes

    private class CachedAnswer {
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

    private class MCQQuestion {
        String questionText = "";
        List<String> options = new ArrayList<>();
        List<String> correctAnswers = new ArrayList<>();
        boolean hasMultipleCorrect = false;
        String questionType = "unknown"; // single-choice, multiple-choice, true-false, fill-blank, math
        int confidenceScore = 0;
    }

    private class AnswerCandidate implements Comparable<AnswerCandidate> {
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

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("DeepSolverPrefs", MODE_PRIVATE);
        Log.d(TAG, "Enhanced Deep Solver Service Connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!OverlayService.isReadingActive) {
            wasReadingActive = false;
            return;
        }

        if (!wasReadingActive && OverlayService.isReadingActive) {
            wasReadingActive = true;
            performEnhancedLensScan();
        }
    }

    /**
     * Main scanning method with enhanced detection
     */
    private void performEnhancedLensScan() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        showToast("🔍 Scanning for MCQ...");

        executorService.execute(() -> {
            MCQQuestion mcq = parseEnhancedMCQ(rootNode);
            rootNode.recycle();

            if (mcq.questionText.isEmpty()) {
                showToast("❌ No question detected");
                return;
            }

            // Show detected question in popup
            mainHandler.post(() -> {
                showSearchPopup("📝 Question detected!\n\n" + mcq.questionText, true);
            });

            // Check cache first
            String cacheKey = generateCacheKey(mcq);
            CachedAnswer cached = answerCache.get(cacheKey);
            if (cached != null && cached.isValid()) {
                mainHandler.post(() -> {
                    displayAnswerWithOptions(cached.answer, mcq);
                });
                return;
            }

            // Perform multi-engine search
            String answer = searchAllEngines(mcq);

            // Cache the answer
            if (answer != null && !answer.isEmpty()) {
                if (answerCache.size() >= CACHE_SIZE) {
                    // Remove oldest entry
                    String oldestKey = Collections.min(answerCache.entrySet(),
                            (e1, e2) -> Long.compare(e1.getValue().timestamp, e2.getValue().timestamp)).getKey();
                    answerCache.remove(oldestKey);
                }
                answerCache.put(cacheKey, new CachedAnswer(answer));
            }

            String finalAnswer = (answer != null && !answer.isEmpty()) ? answer : "No answer found";
            mainHandler.post(() -> {
                displayAnswerWithOptions(finalAnswer, mcq);
            });
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
                Pattern.compile("^[A-Z][\\.\\)]\\s*(.+)$"),           // A), A.
                Pattern.compile("^[a-z][\\.\\)]\\s*(.+)$"),           // a), a.
                Pattern.compile("^[0-9][\\.\\)]\\s*(.+)$"),           // 1), 1.
                Pattern.compile("^[0-9]{2}[\\.\\)]\\s*(.+)$"),        // 10), 10.
                Pattern.compile("^[\\(\\[]?[A-Za-z0-9][\\)\\]]?\\s*(.+)$"), // (a), [1]
                Pattern.compile("^[○▪□○●]\\s*(.+)$"),                  // Symbol options
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
                        String cleaned = cleanOptionText(text, pattern);
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

        // Validate and add options
        if (tempOptions.size() >= 2) {
            mcq.options = tempOptions;
            mcq.confidenceScore = 100;
        } else if (tempOptions.size() == 1) {
            mcq.options = tempOptions;
            mcq.confidenceScore = 50;
        }

        return mcq;
    }

    /**
     * Clean option text by removing prefixes
     */
    private String cleanOptionText(String text, Pattern pattern) {
        return pattern.matcher(text).replaceAll("$1").trim();
    }

    /**
     * Search across multiple engines for better results
     */
    private String searchAllEngines(MCQQuestion mcq) {
        List<AnswerCandidate> allCandidates = new ArrayList<>();

        // Build search queries
        List<String> queries = buildSearchQueries(mcq);

        for (String query : queries) {
            for (String engine : SEARCH_ENGINES) {
                try {
                    String url = String.format(engine, URLEncoder.encode(query, "UTF-8"));
                    List<AnswerCandidate> candidates = searchWithEngine(url, mcq);
                    allCandidates.addAll(candidates);

                    // Small delay to avoid rate limiting
                    Thread.sleep(random.nextInt(1000) + 500);
                } catch (Exception e) {
                    Log.e(TAG, "Search error: " + e.getMessage());
                }
            }
        }

        // Sort and return best answer
        if (!allCandidates.isEmpty()) {
            Collections.sort(allCandidates);
            AnswerCandidate best = allCandidates.get(0);

            // Format answer with confidence
            StringBuilder formattedAnswer = new StringBuilder();
            formattedAnswer.append("🎯 Best Answer (").append((int)(best.score * 100)).append("% confidence)\n");
            formattedAnswer.append("Source: ").append(best.source).append("\n\n");
            formattedAnswer.append(best.text);

            if (best.matchedOption != null && !mcq.options.isEmpty()) {
                formattedAnswer.append("\n\n✅ Matched Option: ").append(best.matchedOption);
            }

            return formattedAnswer.toString();
        }

        return null;
    }

    /**
     * Build multiple search queries for better coverage
     */
    private List<String> buildSearchQueries(MCQQuestion mcq) {
        List<String> queries = new ArrayList<>();
        String baseQuestion = mcq.questionText.replaceAll("[^a-zA-Z0-9\\s]", " ").trim();

        // Query 1: Direct question
        queries.add(baseQuestion);

        // Query 2: Question + "answer"
        queries.add(baseQuestion + " answer");

        // Query 3: Question + "correct answer"
        queries.add(baseQuestion + " correct answer");

        // Query 4: Question + "solution"
        if (mcq.questionType.equals("math")) {
            queries.add(baseQuestion + " solution step by step");
        }

        // Query 5: Question + first few options
        if (!mcq.options.isEmpty()) {
            String optionsStr = String.join(" ", mcq.options.subList(0, Math.min(2, mcq.options.size())));
            queries.add(baseQuestion + " " + optionsStr);
        }

        return queries;
    }

    /**
     * Search with specific engine and parse results
     */
    private List<AnswerCandidate> searchWithEngine(String url, MCQQuestion mcq) throws IOException {
        List<AnswerCandidate> candidates = new ArrayList<>();

        String userAgent = USER_AGENTS[random.nextInt(USER_AGENTS.length)];

        Document doc = Jsoup.connect(url)
                .userAgent(userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Accept-Encoding", "gzip, deflate")
                .header("DNT", "1")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .timeout(15000)
                .followRedirects(true)
                .get();

        String source = url.contains("google") ? "Google" :
                (url.contains("bing") ? "Bing" : "DuckDuckGo");

        // Try to find featured snippets/knowledge panels first
        candidates.addAll(extractFeaturedSnippets(doc, mcq, source));

        // Try to find answer from known educational sites
        candidates.addAll(extractFromKnownSites(doc, mcq, source));

        // Try regular search results
        candidates.addAll(extractSearchSnippets(doc, mcq, source));

        return candidates;
    }

    /**
     * Extract featured snippets (highest priority)
     */
    private List<AnswerCandidate> extractFeaturedSnippets(Document doc, MCQQuestion mcq, String source) {
        List<AnswerCandidate> candidates = new ArrayList<>();

        // Google featured snippet selectors
        String[] featuredSelectors = {
                "div[class*='LGOv1b']", "div[class*='kp-blk']", "div[class*='xpdopen']",
                "div.BNeawe.s3v9rd.AP7Wnd", "div.hgKElc", "div.yD7M6",
                "div[class*='IZ6rdc']", "div[class*='Z0LcW']", "div[class*='sXLaOe']"
        };

        for (String selector : featuredSelectors) {
            Elements elements = doc.select(selector);
            for (Element elem : elements) {
                String text = elem.text();
                if (text.length() > 20 && text.length() < 1000) {
                    double score = calculateAnswerScore(text, mcq);
                    if (score > 0.6) {
                        candidates.add(new AnswerCandidate(text, source + " (Featured)", score));
                    }
                }
            }
        }

        return candidates;
    }

    /**
     * Extract from known educational/answer sites
     */
    private List<AnswerCandidate> extractFromKnownSites(Document doc, MCQQuestion mcq, String source) {
        List<AnswerCandidate> candidates = new ArrayList<>();

        for (Map.Entry<String, String> site : ANSWER_SITES.entrySet()) {
            if (doc.location().contains(site.getKey())) {
                Elements answers = doc.select(site.getValue());
                for (Element answer : answers) {
                    String text = answer.text();
                    double score = calculateAnswerScore(text, mcq) + 0.2; // Bonus for known site
                    candidates.add(new AnswerCandidate(text, site.getKey(), score));
                }
            }
        }

        return candidates;
    }

    /**
     * Extract from regular search result snippets
     */
    private List<AnswerCandidate> extractSearchSnippets(Document doc, MCQQuestion mcq, String source) {
        List<AnswerCandidate> candidates = new ArrayList<>();

        // Common search result selectors
        String[] snippetSelectors = {
                "div.VwiC3b", "span.hgKElc", "div.yD7M6",
                "div.BNeawe.s3v9rd", "div[class*='g'] div[class*='st']",
                "div[class*='result'] div[class*='snippet']"
        };

        for (String selector : snippetSelectors) {
            Elements snippets = doc.select(selector);
            for (Element snippet : snippets) {
                String text = snippet.text();
                if (text.length() > 30 && text.length() < 500) {
                    double score = calculateAnswerScore(text, mcq);
                    if (score > 0.3) {
                        candidates.add(new AnswerCandidate(text, source + " (Search)", score));
                    }
                }
            }
        }

        return candidates;
    }

    /**
     * Calculate how well a text matches the MCQ
     */
    private double calculateAnswerScore(String text, MCQQuestion mcq) {
        double score = 0.0;
        String lowerText = text.toLowerCase();
        String lowerQuestion = mcq.questionText.toLowerCase();

        // Check if answer contains parts of the question
        String[] questionWords = lowerQuestion.split("\\s+");
        int matchedWords = 0;
        for (String word : questionWords) {
            if (word.length() > 3 && lowerText.contains(word)) {
                matchedWords++;
            }
        }
        score += (double) matchedWords / questionWords.length * 0.3;

        // Check if answer contains any options
        if (!mcq.options.isEmpty()) {
            double maxOptionScore = 0;
            for (String option : mcq.options) {
                String lowerOption = option.toLowerCase();
                if (lowerText.contains(lowerOption)) {
                    // Check if option is mentioned as correct
                    if (lowerText.contains("correct") || lowerText.contains("answer") ||
                            lowerText.contains("right") || lowerText.contains("true")) {
                        maxOptionScore = Math.max(maxOptionScore, 1.0);
                    } else {
                        maxOptionScore = Math.max(maxOptionScore, 0.7);
                    }
                }
            }
            score += maxOptionScore * 0.5;
        }

        // Bonus for containing answer indicators
        String[] answerIndicators = {"answer", "correct", "solution", "therefore", "thus", "hence"};
        for (String indicator : answerIndicators) {
            if (lowerText.contains(indicator)) {
                score += 0.1;
                break;
            }
        }

        // Penalty for very short answers
        if (text.length() < 20) {
            score *= 0.5;
        }

        return Math.min(score, 1.0);
    }

    /**
     * Generate cache key for an MCQ
     */
    private String generateCacheKey(MCQQuestion mcq) {
        return mcq.questionText.substring(0, Math.min(50, mcq.questionText.length())) +
                "|" + String.join("|", mcq.options);
    }

    /**
     * Display answer with options highlighted if matched
     */
    private void displayAnswerWithOptions(String answer, MCQQuestion mcq) {
        closeFloatingSearch();

        if (!Settings.canDrawOverlays(this)) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                FLOATING_WINDOW_HEIGHT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM;

        try {
            searchView = LayoutInflater.from(this).inflate(R.layout.layout_search_results, null);

            TextView tvQuestion = searchView.findViewById(R.id.tv_detected_question);
            TextView tvOptions = searchView.findViewById(R.id.tv_detected_options);
            TextView tvAnswer = searchView.findViewById(R.id.tv_search_results);
            TextView tvConfidence = searchView.findViewById(R.id.tv_confidence);
            ImageButton btnClose = searchView.findViewById(R.id.btn_close_results);
            ProgressBar progressBar = searchView.findViewById(R.id.progress_bar);

            // Show detected question
            if (tvQuestion != null) {
                tvQuestion.setText("📝 Question: " + mcq.questionText);
                tvQuestion.setVisibility(View.VISIBLE);
            }

            // Show detected options
            if (tvOptions != null && !mcq.options.isEmpty()) {
                StringBuilder optionsText = new StringBuilder("🔘 Options detected:\n");
                for (int i = 0; i < mcq.options.size(); i++) {
                    optionsText.append("   ").append((char)('A' + i)).append(") ")
                            .append(mcq.options.get(i)).append("\n");
                }
                tvOptions.setText(optionsText.toString());
                tvOptions.setVisibility(View.VISIBLE);
            }

            // Show answer
            if (tvAnswer != null) {
                tvAnswer.setText(answer);
            }

            // Hide progress bar
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }

            btnClose.setOnClickListener(v -> closeFloatingSearch());

            windowManager.addView(searchView, params);

        } catch (Exception e) {
            Log.e(TAG, "Display error: " + e.getMessage());
        }
    }

    /**
     * Show search popup with progress
     */
    private void showSearchPopup(String message, boolean showProgress) {
        closeFloatingSearch();

        if (!Settings.canDrawOverlays(this)) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                FLOATING_WINDOW_HEIGHT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM;

        try {
            searchView = LayoutInflater.from(this).inflate(R.layout.layout_search_results, null);

            TextView tvResults = searchView.findViewById(R.id.tv_search_results);
            ImageButton btnClose = searchView.findViewById(R.id.btn_close_results);
            ProgressBar progressBar = searchView.findViewById(R.id.progress_bar);

            tvResults.setText(message);
            progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);

            btnClose.setOnClickListener(v -> closeFloatingSearch());

            windowManager.addView(searchView, params);

        } catch (Exception e) {
            Log.e(TAG, "Popup error: " + e.getMessage());
        }
    }

    /**
     * Collect all text from node hierarchy
     */
    private void collectAllText(AccessibilityNodeInfo node, List<String> textList) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null && text.length() > 2) {
            String cleanText = text.toString().trim();
            if (!cleanText.isEmpty() && !textList.contains(cleanText)) {
                textList.add(cleanText);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectAllText(node.getChild(i), textList);
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
        executorService.shutdown();
    }
}
