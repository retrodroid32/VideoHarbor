package com.example.videoharbor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Browses public webpages and saves directly addressable HTML5 video files.
 * It does NOT bypass DRM, extract MediaSource/blob data, or decrypt streaming media.
 */
public final class MainActivity extends Activity {
    private static final int DARK = Color.rgb(10, 18, 32);
    private static final int SURFACE = Color.rgb(19, 32, 50);
    private static final int SURFACE_LIGHT = Color.rgb(28, 46, 65);
    private static final int MINT = Color.rgb(39, 219, 192);
    private static final int WHITE = Color.rgb(235, 245, 255);
    private static final int MUTED = Color.rgb(155, 177, 195);
    private static final int PERMISSION_STORAGE = 37;
    private static final int MAX_CANDIDATES = 70;

    private FrameLayout screen;
    private LinearLayout appLayout;
    private WebView browser;
    private EditText address;
    private TextView resultCount;
    private TextView pageStatus;
    private ProgressBar progress;
    private TextView backButton;
    private TextView forwardButton;
    private FrameLayout fullScreenVideo;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private String detectorScript;
    private String currentPage = "";
    private String currentPageTitle = "";
    private DownloadJob pendingPermissionDownload;
    private final LinkedHashMap<String, VideoCandidate> videos = new LinkedHashMap<>();

    private static final class DownloadJob {
        final VideoCandidate video;
        final String proposedName;
        final String userAgent;
        DownloadJob(VideoCandidate video, String proposedName, String userAgent) {
            this.video = video;
            this.proposedName = proposedName;
            this.userAgent = userAgent;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        detectorScript = readDetectorScript();
        buildUi();
        configureBrowser();
        readSharedUrl(getIntent());
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView pill(String text, boolean emphasis) {
        TextView result = text(text, 13, emphasis ? DARK : WHITE, true);
        result.setGravity(Gravity.CENTER);
        result.setPadding(dp(14), dp(10), dp(14), dp(10));
        result.setBackground(rounded(emphasis ? MINT : SURFACE_LIGHT, 12));
        result.setClickable(true);
        result.setFocusable(true);
        return result;
    }

    private LinearLayout horizontal(int spacing) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(0, dp(spacing), 0, dp(spacing));
        return layout;
    }

    private void buildUi() {
        screen = new FrameLayout(this);
        screen.setBackgroundColor(DARK);
        appLayout = new LinearLayout(this);
        appLayout.setOrientation(LinearLayout.VERTICAL);
        appLayout.setBackgroundColor(DARK);
        screen.addView(appLayout, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);

        // API 35's enforced edge-to-edge requires explicitly respecting system bars.
        if (Build.VERSION.SDK_INT >= 35) {
            screen.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets system = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(system.left, system.top, system.right, system.bottom);
                return insets;
            });
        }

        LinearLayout titleRow = horizontal(0);
        titleRow.setPadding(dp(16), dp(12), dp(16), dp(7));
        TextView symbol = text("▶", 21, MINT, true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(rounded(SURFACE, 12));
        titleRow.addView(symbol, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(11), 0, 0, 0);
        titles.addView(text("VideoHarbor", 20, WHITE, true));
        titles.addView(text("Find and save webpage videos", 11, MUTED, false));
        titleRow.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        appLayout.addView(titleRow);

        LinearLayout addressRow = horizontal(0);
        addressRow.setPadding(dp(12), dp(7), dp(12), dp(7));
        address = new EditText(this);
        address.setTextSize(14);
        address.setSingleLine(true);
        address.setTextColor(WHITE);
        address.setHintTextColor(MUTED);
        address.setHint("Paste a webpage URL");
        address.setPadding(dp(12), 0, dp(8), 0);
        address.setSelectAllOnFocus(true);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setBackground(rounded(SURFACE, 12));
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(47), 1);
        addressRow.addView(address, addressParams);
        TextView go = pill("GO", true);
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(-2, dp(47));
        goParams.leftMargin = dp(7);
        addressRow.addView(go, goParams);
        go.setOnClickListener(v -> navigate());
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate();
                return true;
            }
            return false;
        });
        appLayout.addView(addressRow);

        LinearLayout actionRow = horizontal(2);
        actionRow.setPadding(dp(12), dp(3), dp(12), dp(10));
        backButton = pill("‹", false);
        forwardButton = pill("›", false);
        TextView refresh = pill("↻", false);
        TextView scan = pill("Find videos", true);
        TextView downloads = pill("↓", false);
        actionRow.addView(backButton, equalControl(0.7f));
        actionRow.addView(forwardButton, equalControl(0.7f));
        actionRow.addView(refresh, equalControl(0.7f));
        actionRow.addView(scan, equalControl(2.3f));
        actionRow.addView(downloads, equalControl(0.7f));
        backButton.setContentDescription("Go back");
        forwardButton.setContentDescription("Go forward");
        refresh.setContentDescription("Refresh page");
        downloads.setContentDescription("Open downloads");
        backButton.setOnClickListener(v -> { if (browser.canGoBack()) browser.goBack(); });
        forwardButton.setOnClickListener(v -> { if (browser.canGoForward()) browser.goForward(); });
        refresh.setOnClickListener(v -> browser.reload());
        scan.setOnClickListener(v -> scanPage(true));
        downloads.setOnClickListener(v -> openDownloads());
        appLayout.addView(actionRow);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(MINT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE));
        progress.setVisibility(View.GONE);
        appLayout.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        appLayout.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = horizontal(0);
        footer.setPadding(dp(15), dp(10), dp(15), dp(12));
        footer.setBackgroundColor(SURFACE);
        LinearLayout footerLabels = new LinearLayout(this);
        footerLabels.setOrientation(LinearLayout.VERTICAL);
        resultCount = text("0 videos found", 13, WHITE, true);
        pageStatus = text("Paste a URL to begin", 11, MUTED, false);
        footerLabels.addView(resultCount);
        footerLabels.addView(pageStatus);
        footer.addView(footerLabels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView show = pill("Show videos", false);
        footer.addView(show);
        show.setOnClickListener(v -> showVideos());
        appLayout.addView(footer);
    }

    private LinearLayout.LayoutParams equalControl(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), weight);
        params.leftMargin = dp(3);
        params.rightMargin = dp(3);
        return params;
    }

    private void configureBrowser() {
        WebSettings s = browser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSafeBrowsingEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);

        browser.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (VideoUrlUtils.isVideoFile(url) || VideoUrlUtils.isStream(url)) {
                    offerVideo(new VideoCandidate(url, "Linked video", "", view.getUrl()), null, null);
                    return true;
                }
                if (!url.startsWith("https://")) {
                    toast("Only HTTPS pages are supported.");
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (VideoUrlUtils.isVideoFile(url) || VideoUrlUtils.isStream(url)) {
                    runOnUiThread(() -> registerVideo(url, "Video loaded by page", "", currentPage));
                }
                return null; // do not alter website traffic
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                currentPage = url;
                currentPageTitle = "";
                videos.clear();
                updateCount();
                address.setText(url);
                pageStatus.setText("Loading page…");
                updateControls();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                currentPage = url;
                if (!url.contains("appassets.androidplatform.net")) {
                    pageStatus.setText("Tap Find videos after the player loads");
                }
                updateControls();
                // Some players populate <video> only after their scripts execute.
                view.postDelayed(() -> scanPage(false), 700);
                view.postDelayed(() -> scanPage(false), 2500);
                view.postDelayed(() -> scanPage(false), 5500);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) pageStatus.setText("Page could not be loaded");
            }
        });

        browser.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                currentPageTitle = title == null ? "" : title;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullScreenVideo != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customViewCallback = callback;
                fullScreenVideo = new FrameLayout(MainActivity.this);
                fullScreenVideo.setBackgroundColor(Color.BLACK);
                fullScreenVideo.addView(view, new FrameLayout.LayoutParams(-1, -1));
                screen.addView(fullScreenVideo, new FrameLayout.LayoutParams(-1, -1));
                appLayout.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                closeFullScreen();
            }
        });

        browser.setDownloadListener((url, ua, disposition, mime, length) -> {
            String guessed = URLUtil.guessFileName(url, disposition, mime);
            offerVideo(new VideoCandidate(url, "Webpage download", mime, browser.getUrl()), guessed, ua);
        });

        showWelcome();
    }

    private String readDetectorScript() {
        StringBuilder script = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                getAssets().open("video_detector.js"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) script.append(line).append('\n');
        } catch (Exception e) {
            return "(function(){return '[]';})()";
        }
        return script.toString();
    }

    private void showWelcome() {
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{background:#0b1220;color:#eaf5ff;font-family:sans-serif;margin:0;padding:30px 25px}"
                + "h1{font-size:27px;margin-top:45px}p{color:#adc1d0;line-height:1.6;font-size:15px}"
                + ".tag{color:#27dbc0;font-size:13px;font-weight:bold}.step{background:#17253a;border-radius:14px;padding:15px;margin-top:12px}"
                + "b{color:#fff}</style></head><body><div class='tag'>YOUR VIDEO BROWSER</div>"
                + "<h1>Save videos from webpages.</h1><p>Find directly accessible video files and download them to your phone.</p>"
                + "<div class='step'><b>1.</b> Paste a webpage address at the top.</div>"
                + "<div class='step'><b>2.</b> Open or play the video, then tap <b>Find videos</b>.</div>"
                + "<div class='step'><b>3.</b> Select a result and tap <b>Download</b>.</div>"
                + "<p>Supports direct MP4, WebM and other common video files. DRM, blob streams, HLS and DASH are not supported.</p>"
                + "</body></html>";
        browser.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null);
    }

    private void navigate() {
        String raw = address.getText().toString().trim();
        if (raw.isEmpty()) return;
        String url;
        if (raw.startsWith("https://")) {
            url = raw;
        } else if (raw.startsWith("http://")) {
            toast("Use an HTTPS address; insecure HTTP is disabled.");
            return;
        } else if (raw.contains(" ") || !raw.contains(".")) {
            url = "https://www.google.com/search?q=" + Uri.encode(raw);
        } else {
            url = "https://" + raw;
        }
        if (!VideoUrlUtils.isHttpsUrl(url)) {
            toast("Enter a valid HTTPS webpage address.");
            return;
        }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(address.getWindowToken(), 0);
        address.clearFocus();
        if (VideoUrlUtils.isVideoFile(url) || VideoUrlUtils.isStream(url)) {
            offerVideo(new VideoCandidate(url, "Direct video link", "", currentPage), null, null);
        } else {
            browser.loadUrl(url);
        }
    }

    private void scanPage(boolean openResults) {
        String scannedPage = browser.getUrl();
        if (scannedPage == null || !scannedPage.startsWith("https://")
                || scannedPage.contains("appassets.androidplatform.net")) {
            if (openResults) showVideos();
            return;
        }
        browser.evaluateJavascript(detectorScript, jsonValue -> {
            if (!scannedPage.equals(browser.getUrl())) return;
            try {
                Object decoded = new JSONTokener(jsonValue).nextValue();
                JSONArray found = new JSONArray(decoded.toString());
                for (int i = 0; i < found.length(); i++) {
                    JSONObject item = found.optJSONObject(i);
                    if (item == null) continue;
                    registerVideo(item.optString("url"), item.optString("label", currentPageTitle),
                            item.optString("mime"), scannedPage);
                }
            } catch (Exception ignored) {
                // A page without a readable DOM may still expose direct network media URLs.
            }
            if (openResults) showVideos();
        });
    }

    private void registerVideo(String url, String label, String mime, String fromPage) {
        if (!VideoUrlUtils.isHttpsUrl(url) || videos.size() >= MAX_CANDIDATES) return;
        if (videos.containsKey(url)) return;
        videos.put(url, new VideoCandidate(url, label, mime, fromPage));
        updateCount();
    }

    private void updateCount() {
        int n = videos.size();
        if (resultCount != null) resultCount.setText(n + (n == 1 ? " video found" : " videos found"));
        if (n > 0 && pageStatus != null) pageStatus.setText("Tap Show videos to download");
    }

    private void updateControls() {
        backButton.setAlpha(browser.canGoBack() ? 1f : 0.35f);
        forwardButton.setAlpha(browser.canGoForward() ? 1f : 0.35f);
    }

    private void showVideos() {
        if (videos.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No video files detected")
                    .setMessage("Play the video on the webpage and tap Find videos again. "
                            + "Some sites use protected streams, blob URLs or separate players that this app cannot download.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(6), dp(12), dp(6));
        scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Videos on this page (" + videos.size() + ")")
                .setView(scroll)
                .setNegativeButton("Close", null)
                .create();

        ArrayList<VideoCandidate> choices = new ArrayList<>(videos.values());
        for (int i = 0; i < choices.size(); i++) {
            VideoCandidate candidate = choices.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(12), dp(11), dp(12), dp(11));
            item.setBackground(rounded(i % 2 == 0 ? 0xFFF2F6F8 : 0xFFE6EEF1, 10));
            TextView heading = text(candidate.label, 14, 0xFF0D2030, true);
            heading.setMaxLines(2);
            item.addView(heading);
            TextView details = text(VideoUrlUtils.hostOf(candidate.url)
                    + (candidate.isStream() ? " • Streaming playlist" : " • Direct file"),
                    12, 0xFF576F7D, false);
            item.addView(details);
            TextView button = text(candidate.isStream() ? "Why can't I save this?" : "Download  ↓",
                    13, 0xFF007765, true);
            button.setPadding(0, dp(9), 0, dp(5));
            button.setOnClickListener(v -> {
                dialog.dismiss();
                offerVideo(candidate, null, null);
            });
            item.addView(button);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
            itemParams.bottomMargin = dp(7);
            list.addView(item, itemParams);
        }
        dialog.show();
    }

    private void offerVideo(VideoCandidate video, String suggestedName, String userAgent) {
        if (!VideoUrlUtils.isHttpsUrl(video.url)) {
            new AlertDialog.Builder(this).setTitle("Unsupported link")
                    .setMessage("Only publicly addressable HTTPS video files can be downloaded.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (video.isStream()) {
            new AlertDialog.Builder(this).setTitle("Streaming video")
                    .setMessage("This link is an HLS/DASH playlist rather than a video file. "
                            + "This version does not download or assemble segmented streams, "
                            + "DRM-protected media, or blob videos.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String mime = video.mimeType;
        String name = suggestedName;
        if (name == null || name.trim().isEmpty()) {
            name = URLUtil.guessFileName(video.url, null, mime.isEmpty() ? "video/mp4" : mime);
        }
        DownloadJob job = new DownloadJob(video, name, userAgent);
        new AlertDialog.Builder(this)
                .setTitle("Download this video?")
                .setMessage("Source: " + VideoUrlUtils.hostOf(video.url)
                        + "\n\nFile: " + VideoUrlUtils.safeFileName(name, mime, System.currentTimeMillis())
                        + "\n\nSave location: Downloads/VideoHarbor")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Download", (d, which) -> checkPermissionAndDownload(job))
                .show();
    }

    private void checkPermissionAndDownload(DownloadJob job) {
        if (Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionDownload = job;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_STORAGE);
            return;
        }
        startDownload(job);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_STORAGE || pendingPermissionDownload == null) return;
        DownloadJob job = pendingPermissionDownload;
        pendingPermissionDownload = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startDownload(job);
        } else {
            toast("Storage permission is required on Android 8 and 9.");
        }
    }

    private void startDownload(DownloadJob job) {
        try {
            String mime = job.video.mimeType.isEmpty()
                    ? guessMime(job.video.url) : job.video.mimeType;
            String fileName = VideoUrlUtils.safeFileName(job.proposedName, mime, System.currentTimeMillis());
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(job.video.url));
            request.setTitle(fileName);
            request.setDescription("Saved with VideoHarbor");
            request.setMimeType(mime);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    "VideoHarbor/" + fileName);
            String agent = job.userAgent;
            if (agent == null || agent.isEmpty()) agent = browser.getSettings().getUserAgentString();
            request.addRequestHeader("User-Agent", agent);
            String cookies = CookieManager.getInstance().getCookie(job.video.url);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            if (VideoUrlUtils.isHttpsUrl(job.video.sourcePage)) {
                request.addRequestHeader("Referer", job.video.sourcePage);
            }
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Download service unavailable");
            manager.enqueue(request);
            pageStatus.setText("Download queued: " + fileName);
            new AlertDialog.Builder(this)
                    .setTitle("Download started")
                    .setMessage("Saved to Downloads/VideoHarbor when finished. "
                            + "Check the Android download notification for progress and any errors.")
                    .setNegativeButton("Close", null)
                    .setPositiveButton("Open downloads", (d, w) -> openDownloads())
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Unable to start download")
                    .setMessage(e.getMessage() == null ? "The link is not supported." : e.getMessage())
                    .setPositiveButton("OK", null).show();
        }
    }

    private String guessMime(String url) {
        String path = VideoUrlUtils.pathOf(url);
        if (path.endsWith(".webm")) return "video/webm";
        if (path.endsWith(".mov")) return "video/quicktime";
        if (path.endsWith(".mkv")) return "video/x-matroska";
        if (path.endsWith(".ts")) return "video/mp2t";
        if (path.endsWith(".3gp")) return "video/3gpp";
        return "video/mp4";
    }

    private void openDownloads() {
        try {
            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (ActivityNotFoundException e) {
            toast("Open Files > Downloads > VideoHarbor to find saved videos.");
        }
    }

    private void readSharedUrl(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.isEmpty()) {
                address.setText(shared.trim());
                navigate();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readSharedUrl(intent);
    }

    private void closeFullScreen() {
        if (fullScreenVideo == null) return;
        screen.removeView(fullScreenVideo);
        fullScreenVideo.removeAllViews();
        fullScreenVideo = null;
        appLayout.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void handleBack() {
        if (fullScreenVideo != null) {
            closeFullScreen();
        } else if (browser.canGoBack()) {
            browser.goBack();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onDestroy() {
        closeFullScreen();
        if (browser != null) {
            browser.stopLoading();
            browser.setWebChromeClient(null);
            browser.setWebViewClient(null);
            appLayout.removeView(browser);
            browser.destroy();
        }
        super.onDestroy();
    }
}
