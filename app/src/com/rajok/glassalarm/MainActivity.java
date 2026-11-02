package com.rajok.glassalarm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Full-screen host for the Glass Alarm web app.
 *
 * Deliberately minimal and hard to break:
 *
 *  - The page is ONE self-contained file in assets (fonts embedded as data
 *    URIs), loaded over file:///android_asset/index.html. No network, no
 *    request interception, no custom scheme, nothing to resolve at runtime.
 *  - setAllowFileAccess(true) is mandatory: for apps targeting API 30+ the
 *    WebView default is false, which blocks file:// loads entirely.
 *  - If anything throws, the exception is shown on screen instead of the app
 *    dying silently, so a crash is diagnosable rather than invisible.
 *  - The screen is kept awake natively (FLAG_KEEP_SCREEN_ON) instead of
 *    relying on the page's Wake Lock API, which needs a secure origin.
 */
public class MainActivity extends Activity {

    private static final String PAGE = "file:///android_asset/index.html";
    private static final int BG = 0xFF090B1E;
    private static final String CRASH_FILE = "last_crash.txt";

    private FrameLayout container;
    private WebView web;
    private int rebuilds = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashHandler();

        try {
            Window window = getWindow();
            window.setBackgroundDrawable(new ColorDrawable(BG));
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    WindowManager.LayoutParams lp = window.getAttributes();
                    lp.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    window.setAttributes(lp);
                }
            } catch (Throwable ignored) {
            }
            applyFullscreen(window);

            container = new FrameLayout(this);
            container.setBackgroundColor(BG);
            setContentView(container);

            String previous = readCrashFile();
            if (previous != null) {
                showReport("The app closed unexpectedly last time", previous, true);
            } else {
                buildWebView();
            }
        } catch (Throwable t) {
            showReport("Glass Alarm could not start", describe(t), false);
        }
    }

    /** Any uncaught exception is written to disk so the next launch can show it. */
    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                writeCrashFile(describe(error));
                if (previous != null) previous.uncaughtException(thread, error);
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void buildWebView() {
        try {
            WebView view = new WebView(this);
            view.setBackgroundColor(BG);
            view.setOverScrollMode(View.OVER_SCROLL_NEVER);
            view.setVerticalScrollBarEnabled(false);
            view.setHorizontalScrollBarEnabled(false);

            WebSettings s = view.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            // MANDATORY for targetSdk 30+: file:// loads are blocked by default.
            s.setAllowFileAccess(true);
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowContentAccess(false);
            s.setJavaScriptCanOpenWindowsAutomatically(false);
            s.setSupportMultipleWindows(false);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setSupportZoom(false);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            s.setTextZoom(100);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            if (Build.VERSION.SDK_INT >= 29 && Build.VERSION.SDK_INT <= 32) {
                s.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
            if (Build.VERSION.SDK_INT >= 33) {
                s.setAlgorithmicDarkeningAllowed(false);
            }

            view.setWebViewClient(Build.VERSION.SDK_INT >= 26
                    ? new PageClient26() : new PageClient());
            view.setWebChromeClient(new WebChromeClient());

            web = view;
            container.removeAllViews();
            container.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            view.loadUrl(PAGE);
        } catch (Throwable t) {
            showReport("Glass Alarm could not open its page", describe(t), false);
        }
    }

    private void applyFullscreen(Window window) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
                window.setStatusBarColor(Color.TRANSPARENT);
                window.setNavigationBarColor(Color.TRANSPARENT);
                android.view.WindowInsetsController c = window.getInsetsController();
                if (c != null) {
                    c.hide(android.view.WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                window.setStatusBarColor(Color.TRANSPARENT);
                window.setNavigationBarColor(Color.TRANSPARENT);
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyFullscreen(getWindow());
    }

    @Override
    public void onBackPressed() {
        if (web == null) {
            finish();
            return;
        }
        String js = "(function(){try{var q=function(s){return document.querySelector(s)};"
                + "var vis=function(e){return e&&!e.hidden};"
                + "if(vis(q('#ring'))){q('#stop').click();return 'stay'}";
        js = js + "if(vis(q('#ssheet'))){q('#setDone').click();return 'stay'}";
        js = js + "if(vis(q('#sheet'))){q('#cancel').click();return 'stay'}";
        js = js + "if(vis(q('#app'))){var c=q('#closeBtn');if(c){c.click();return 'stay'}}";
        js = js + "return 'exit'}catch(e){return 'exit'}})()";
        try {
            web.evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (value == null || value.contains("exit")) finish();
                }
            });
        } catch (Throwable t) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            try {
                container.removeView(web);
                web.destroy();
            } catch (Throwable ignored) {
            }
            web = null;
        }
        super.onDestroy();
    }

    /** Plain, non-API-26 client. */
    private class PageClient extends WebViewClient {
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request != null && !request.isForMainFrame()) return;
            String detail = "Could not load " + PAGE;
            if (Build.VERSION.SDK_INT >= 23 && error != null) {
                detail += "\nError code " + error.getErrorCode() + ": " + error.getDescription();
            }
            showReport("Glass Alarm could not load its page", detail, false);
        }
    }

    /** API 26+: survive the OS killing the WebView renderer. */
    private class PageClient26 extends PageClient {
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            try {
                container.removeView(view);
                view.destroy();
            } catch (Throwable ignored) {
            }
            web = null;
            if (rebuilds++ < 1) {
                container.post(new Runnable() {
                    @Override
                    public void run() {
                        buildWebView();
                    }
                });
            } else {
                String why = "The WebView renderer was killed by the system"
                        + (Build.VERSION.SDK_INT >= 26 && detail != null
                        ? (detail.didCrash() ? " (it crashed)" : " (low memory)") : "");
                showReport("Glass Alarm had to stop rendering", why, true);
            }
            return true; // handled: do not let the process die
        }
    }

    // ---------- diagnostics ----------

    private void showReport(String title, String body, boolean offerRetry) {
        try {
            ScrollView scroll = new ScrollView(this);
            scroll.setBackgroundColor(BG);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(22);
            box.setPadding(pad, pad, pad, pad);

            TextView heading = new TextView(this);
            heading.setText(title);
            heading.setTextColor(0xFFF5F6FF);
            heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            box.addView(heading);

            TextView info = new TextView(this);
            info.setText(body + "\n\n" + environment());
            info.setTextColor(0xFFB9C2E8);
            info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            info.setTypeface(android.graphics.Typeface.MONOSPACE);
            info.setTextIsSelectable(true);
            info.setPadding(0, dp(14), 0, dp(14));
            box.addView(info);

            Button retry = new Button(this);
            retry.setText(offerRetry ? "Open app anyway" : "Try again");
            retry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearCrashFile();
                    rebuilds = 0;
                    if (web != null) {
                        try {
                            container.removeView(web);
                            web.destroy();
                        } catch (Throwable ignored) {
                        }
                        web = null;
                    }
                    buildWebView();
                }
            });
            box.addView(retry);

            scroll.addView(box);
            container.removeAllViews();
            container.addView(scroll, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Throwable ignored) {
            // Nothing left to do; never rethrow from here.
        }
    }

    private String environment() {
        StringBuilder sb = new StringBuilder();
        sb.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("App: ").append(appVersion()).append('\n');
        sb.append("WebView: ").append(webViewVersion()).append('\n');
        return sb.toString();
    }

    private String appVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private String webViewVersion() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                PackageInfo pi = WebView.getCurrentWebViewPackage();
                if (pi != null) return pi.packageName + " " + pi.versionName;
            }
        } catch (Throwable ignored) {
        }
        try {
            PackageInfo pi = getPackageManager().getPackageInfo("com.google.android.webview", 0);
            return pi.packageName + " " + pi.versionName;
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private static String describe(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 6000 ? s.substring(0, 6000) + "\n..." : s;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void writeCrashFile(String text) {
        try (FileOutputStream out = openFileOutput(CRASH_FILE, MODE_PRIVATE)) {
            out.write(text.getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
    }

    private String readCrashFile() {
        File f = new File(getFilesDir(), CRASH_FILE);
        if (!f.exists()) return null;
        try (InputStream in = openFileInput(CRASH_FILE)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            String text = buf.toString("UTF-8");
            return text.isEmpty() ? null : text;
        } catch (Throwable t) {
            return null;
        }
    }

    private void clearCrashFile() {
        try {
            new File(getFilesDir(), CRASH_FILE).delete();
        } catch (Throwable ignored) {
        }
    }
}
