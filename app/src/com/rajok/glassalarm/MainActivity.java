package com.rajok.glassalarm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JsResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.Uri;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Full-screen host for the Glass Alarm web app.
 *
 * The page is served from APK assets over an https origin
 * (appassets.androidplatform.net) rather than file:// so that it runs in a
 * secure context: localStorage, wake locks and AudioContext behave exactly as
 * they do in a browser tab.
 *
 * Every failure path is handled instead of being allowed to take the process
 * down: if the https load fails we fall back to file://, if the renderer dies
 * we rebuild the WebView, and if WebView itself is unavailable we say so.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/index.html";
    private static final String FALLBACK_URL = "file:///android_asset/index.html";
    private static final int BG = 0xFF090B1E;

    private FrameLayout container;
    private WebView web;
    private boolean usingFallback = false;
    private boolean rendererLost = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(BG));

        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        applyFullscreen(window);

        container = new FrameLayout(this);
        container.setBackgroundColor(BG);
        setContentView(container);

        buildWebView();
    }

    /** Creates (or recreates) the WebView and loads the app. */
    @SuppressLint({"SetJavaScriptEnabled"})
    private void buildWebView() {
        WebView view;
        try {
            view = new WebView(this);
        } catch (Throwable t) {
            // No usable WebView provider on this device.
            showMessage("This device has no working Android System WebView, so Glass Alarm "
                    + "cannot start.\n\nUpdate \"Android System WebView\" and \"Chrome\" from the "
                    + "Play Store, then open the app again.");
            return;
        }

        view.setBackgroundColor(BG);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);

        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        // The alarm chimes must be able to start on their own.
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        // The page ships its own light/dark themes; never let WebView invert it.
        if (Build.VERSION.SDK_INT >= 29 && Build.VERSION.SDK_INT <= 32) {
            s.setForceDark(WebSettings.FORCE_DARK_OFF);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(false);
        }

        view.setWebViewClient(Build.VERSION.SDK_INT >= 26 ? new AssetClient26() : new AssetClient());
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView v, String url, String message, JsResult result) {
                result.confirm();
                return true;
            }
        });

        web = view;
        usingFallback = false;
        container.removeAllViews();
        container.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        view.loadUrl(START_URL);
    }

    private void showMessage(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFE8ECFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        container.removeAllViews();
        container.addView(tv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** Immersive, edge-to-edge: the mock phone fills the whole display. */
    private void applyFullscreen(Window window) {
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
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyFullscreen(getWindow());
    }

    /**
     * Hardware back walks out of the page the way the UI itself would:
     * stop a ringing alarm, close settings, close the sheet, close the app,
     * then exit.
     */
    @Override
    public void onBackPressed() {
        if (web == null || rendererLost) {
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

    /** Serves assets/ over https://appassets.androidplatform.net/. */
    private class AssetClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            try {
                Uri uri = request.getUrl();
                if (uri == null || !HOST.equals(uri.getHost())) return null;

                String path = uri.getPath();
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                if (path.startsWith("/")) path = path.substring(1);
                if (path.contains("..")) return notFound();

                InputStream in = getAssets().open(path);
                Map<String, String> headers = new HashMap<>();
                headers.put("Cache-Control", "no-store");
                return new WebResourceResponse(mimeOf(path), charsetOf(path), 200, "OK",
                        headers, in);
            } catch (Throwable t) {
                return notFound();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (uri == null) return false;
            // The file:// fallback must be allowed to navigate.
            if ("file".equals(uri.getScheme())) return false;
            // Everything else lives inside the APK; never hand the user to a browser.
            return !HOST.equals(uri.getHost());
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request == null || !request.isForMainFrame()) return;
            loadFallback();
        }
    }

    /**
     * API 26+ only: keeps the process alive if the WebView renderer is killed
     * (which is what turns a heavy page into an app crash on low-memory
     * devices). Kept in its own class so 24/25 never load RenderProcessGoneDetail.
     */
    private class AssetClient26 extends AssetClient {
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            if (web != null) {
                container.removeView(web);
                try {
                    web.destroy();
                } catch (Throwable ignored) {
                }
                web = null;
            }
            rendererLost = true;
            // Rebuild once; if it dies again the fallback path takes over.
            container.post(new Runnable() {
                @Override
                public void run() {
                    rendererLost = false;
                    buildWebView();
                }
            });
            return true; // we handled it — do not let the process die
        }
    }

    /** file:// fallback if the https origin cannot be served on this device. */
    private void loadFallback() {
        if (usingFallback || web == null) {
            showMessage("Glass Alarm could not load its page.\n\nTry reopening the app, or "
                    + "reinstall it.");
            return;
        }
        usingFallback = true;
        WebSettings s = web.getSettings();
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        web.loadUrl(FALLBACK_URL);
    }

    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                new HashMap<String, String>(),
                new ByteArrayInputStream(new byte[0]));
    }

    private static String mimeOf(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "application/javascript";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".otf")) return "font/otf";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".wav")) return "audio/wav";
        if (p.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static String charsetOf(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm") || p.endsWith(".css")
                || p.endsWith(".js") || p.endsWith(".json") || p.endsWith(".svg")) {
            return "utf-8";
        }
        return null;
    }
}
