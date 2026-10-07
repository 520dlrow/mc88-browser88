package com.ctoa.browser88;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_PERMISSIONS  = 2001;

    private static final Set<String> AD_HOSTS = new HashSet<>(Arrays.asList(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "google-analytics.com", "googletagservices.com", "googletagmanager.com",
        "adservice.google.com", "adsafeprotected.com", "adnxs.com", "adsrvr.org",
        "amazon-adsystem.com", "outbrain.com", "taboola.com", "criteo.com",
        "criteo.net", "pubmatic.com", "rubiconproject.com", "openx.net",
        "casalemedia.com", "moatads.com", "scorecardresearch.com",
        "quantserve.com", "hotjar.com", "mixpanel.com", "segment.com",
        "segment.io", "connect.facebook.net", "graph.facebook.com",
        "analytics.twitter.com", "static.ads-twitter.com", "bat.bing.com",
        "clarity.ms", "mc.yandex.ru", "yandex.ru"
    ));

    private static final String AD_CSS =
        ".adsbygoogle,.google-ad,.ad-container,.ad-wrapper," +
        "[class*='advert'],[id*='advert'],[class*='sponsored'],[id*='sponsored']," +
        "[class*='banner-ad'],[class*='google-ad']," +
        "iframe[src*='doubleclick'],iframe[src*='googlesyndication']," +
        "iframe[src*='adservice'],iframe[src*='adsystem']," +
        "#google_ads,[data-ad],.ad-slot,.ad-banner,.ad-box," +
        "[class*='taboola'],[class*='outbrain']{display:none!important}";

    private FrameLayout root;
    private WebView contentView;
    private ShellWebView shellView;

    private boolean sheetOpen = false;
    private boolean adBlockEnabled = true;
    private int blockedCount = 0;

    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0e0e10"));

        /* Content WebView — bottom layer, loads real websites */
        contentView = new WebView(this);
        configureWebView(contentView, false);
        contentView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        setupContentClient();
        contentView.loadUrl("file:///android_asset/home.html");
        root.addView(contentView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        /* Shell WebView — top layer, transparent, draws top + dock + sheets */
        shellView = new ShellWebView(this);
        configureWebView(shellView, true);
        shellView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        shellView.loadUrl("file:///android_asset/index.html");
        root.addView(shellView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        requestRuntimePermissions();
    }

    /* ============================================================
       ShellWebView — passes touches through the middle to content
       ============================================================ */
    private class ShellWebView extends WebView {
        public ShellWebView(Activity ctx){ super(ctx); }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev){
            /* If a sheet is open, the shell handles all touches */
            if(sheetOpen) return super.dispatchTouchEvent(ev);

            /* Otherwise, only the top bar and dock areas are interactive */
            float y = ev.getY();
            float h = getHeight();
            float topPx = dp(60);
            float dockPx = dp(64);

            if(y < topPx || y > h - dockPx){
                return super.dispatchTouchEvent(ev);
            }
            /* Middle of the screen — let the content WebView below handle it */
            return false;
        }
    }

    private int dp(int v){
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView wv, boolean transparent){
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        if(transparent){
            wv.setBackgroundColor(Color.TRANSPARENT);
            wv.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
        } else {
            wv.setBackgroundColor(Color.parseColor("#0e0e10"));
        }
    }

    private boolean isAdHost(String host){
        if(host == null) return false;
        String h = host.toLowerCase();
        for(String ad : AD_HOSTS){
            if(h.equals(ad) || h.endsWith("." + ad)) return true;
        }
        return false;
    }

    /* ============================================================
       CONTENT CLIENT
       ============================================================ */
    private void setupContentClient(){

        contentView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(WebView v, int p){
                callShellBool("shellSetProgress", p < 100);
            }

            @Override
            public void onReceivedTitle(WebView v, String title){
                String url = v.getUrl();
                if(url != null && !url.startsWith("file://")){
                    callShellTwo("shellOnPageLoaded",
                        jsStr(url),
                        jsStr(title != null ? title : ""));
                }
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params){
                if(filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE_CHOOSER);
                    return true;
                } catch(Exception e){
                    filePathCallback = null;
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request){
                runOnUiThread(new Runnable(){
                    @Override public void run(){
                        try { request.grant(request.getResources()); }
                        catch(Exception e){ request.deny(); }
                    }
                });
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb){
                cb.invoke(origin, true, false);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm){
                android.util.Log.d("Browser88", cm.message());
                return true;
            }

            @Override
            public boolean onJsAlert(WebView v, String u, String m, final android.webkit.JsResult r){
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(m)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(false).show();
                r.confirm();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String u, String m, final android.webkit.JsResult r){
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(m)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setCancelable(false).show();
                r.confirm();
                return true;
            }
        });

        contentView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req){
                if(adBlockEnabled){
                    Uri u = req.getUrl();
                    if(u != null && isAdHost(u.getHost())){
                        blockedCount++;
                        if(blockedCount % 5 == 0){
                            callShellStr("shellSetBlocked", String.valueOf(blockedCount));
                        }
                        return new WebResourceResponse(
                            "text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                    }
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){
                Uri uri = r.getUrl();
                String scheme = uri.getScheme();
                if(!"http".equals(scheme) && !"https".equals(scheme)
                        && !"file".equals(scheme)){
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return true;
                    } catch(Exception e){ return false; }
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f){
                if(url == null) return;
                if(url.startsWith("file://") && url.contains("home.html")){
                    callShellStr("shellSetUrl", "");
                } else if(!url.startsWith("file://")){
                    callShellStr("shellSetUrl", url);
                }
                updateNavState();
            }

            @Override
            public void onPageFinished(WebView v, String url){
                updateNavState();
                if(adBlockEnabled) injectAdCss();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e){
                if(r.isForMainFrame()){
                    String msg = (Build.VERSION.SDK_INT >= 23)
                        ? e.getDescription().toString() : "load failed";
                    Toast.makeText(MainActivity.this,
                        "Failed: " + msg, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void injectAdCss(){
        String js =
            "(function(){if(document.getElementById('b88-adblock'))return;" +
            "var s=document.createElement('style');s.id='b88-adblock';" +
            "s.textContent=\"" + AD_CSS.replace("\"", "\\\"") + "\";" +
            "document.documentElement.appendChild(s);})();";
        contentView.evaluateJavascript(js, null);
    }

    private void updateNavState(){
        boolean cb = contentView.canGoBack();
        boolean cf = contentView.canGoForward();
        callShellTwo("shellSetNav",
            String.valueOf(cb),
            String.valueOf(cf));
    }

    /* ============================================================
       SHELL CALLBACK HELPERS
       ============================================================ */
    private void callShellStr(String fn, String arg){
        final String f = fn;
        final String a = arg;
        runOnUiThread(new Runnable(){
            @Override public void run(){
                try {
                    shellView.evaluateJavascript(
                        "window." + f + "(" + jsStr(a) + ");", null);
                } catch(Exception ignored){}
            }
        });
    }

    private void callShellBool(String fn, final boolean arg){
        final String f = fn;
        runOnUiThread(new Runnable(){
            @Override public void run(){
                try {
                    shellView.evaluateJavascript(
                        "window." + f + "(" + arg + ");", null);
                } catch(Exception ignored){}
            }
        });
    }

    private void callShellTwo(String fn, String a, String b){
        final String f = fn;
        final String arg1 = a;
        final String arg2 = b;
        runOnUiThread(new Runnable(){
            @Override public void run(){
                try {
                    shellView.evaluateJavascript(
                        "window." + f + "(" + arg1 + "," + arg2 + ");", null);
                } catch(Exception ignored){}
            }
        });
    }

    private String jsStr(String s){
        if(s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "") + "\"";
    }

    /* ============================================================
       BRIDGE — everything the shell and content can call
       ============================================================ */
    public class Bridge {

        @JavascriptInterface
        public String getVersion(){ return "1.0"; }

        @JavascriptInterface
        public void navigate(final String url){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    if(url == null || url.isEmpty()) return;
                    String target = url;
                    if(!url.startsWith("http") && !url.startsWith("file")
                            && !url.startsWith("about")){
                        target = "https://duckduckgo.com/?q=" + Uri.encode(url);
                    }
                    contentView.loadUrl(target);
                }
            });
        }

        @JavascriptInterface
        public void goHome(){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    contentView.loadUrl("file:///android_asset/home.html");
                }
            });
        }

        @JavascriptInterface
        public void goBack(){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    if(contentView.canGoBack()) contentView.goBack();
                }
            });
        }

        @JavascriptInterface
        public void goForward(){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    if(contentView.canGoForward()) contentView.goForward();
                }
            });
        }

        @JavascriptInterface
        public void reload(){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    contentView.reload();
                }
            });
        }

        @JavascriptInterface
        public void setSheetOpen(final boolean open){
            sheetOpen = open;
        }

        @JavascriptInterface
        public void setAdblock(final boolean on){
            adBlockEnabled = on;
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    if(on) injectAdCss();
                    else contentView.evaluateJavascript(
                        "(function(){var e=document.getElementById('b88-adblock');" +
                        "if(e)e.remove();})();", null);
                }
            });
        }

        @JavascriptInterface
        public void setDesktopMode(final boolean on){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    WebSettings s = contentView.getSettings();
                    if(on){
                        s.setUserAgentString(
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    } else {
                        s.setUserAgentString(null);
                    }
                    contentView.reload();
                }
            });
        }

        @JavascriptInterface
        public void clearAllData(){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    contentView.clearCache(true);
                    contentView.clearHistory();
                    contentView.clearFormData();
                    Toast.makeText(MainActivity.this,
                        "Browsing data cleared", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void addPin(final String name, final String url){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    try {
                        SharedPreferences sp = getSharedPreferences("b88.pins", MODE_PRIVATE);
                        String existing = sp.getString("list", "[]");
                        JSONArray arr = new JSONArray(existing);
                        JSONArray next = new JSONArray();
                        JSONObject item = new JSONObject();
                        item.put("name", name != null ? name : "");
                        item.put("url", url != null ? url : "");
                        next.put(item);
                        for(int i = 0; i < arr.length() && i < 20; i++){
                            JSONObject old = arr.optJSONObject(i);
                            if(old == null) continue;
                            String oldUrl = old.optString("url", "");
                            if(!oldUrl.equals(url)) next.put(old);
                        }
                        sp.edit().putString("list", next.toString()).apply();
                        Toast.makeText(MainActivity.this, "Pinned", Toast.LENGTH_SHORT).show();

                        String cur = contentView.getUrl();
                        if(cur != null && cur.contains("home.html")){
                            contentView.reload();
                        }
                    } catch(Exception e){
                        Toast.makeText(MainActivity.this, "Pin failed", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public String getPins(){
            try {
                SharedPreferences sp = getSharedPreferences("b88.pins", MODE_PRIVATE);
                return sp.getString("list", "[]");
            } catch(Exception e){
                return "[]";
            }
        }

        @JavascriptInterface
        public void notify(final String title, final String body){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    Toast.makeText(MainActivity.this,
                        title + ": " + body, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void openExternal(final String url){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch(Exception ignored){}
                }
            });
        }

        @JavascriptInterface
        public void saveAPK(final String b64, final String filename){
            runOnUiThread(new Runnable(){
                @Override public void run(){
                    try {
                        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                        File dir = android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS);
                        if(!dir.exists()) dir.mkdirs();
                        File out = new File(dir, filename);
                        FileOutputStream fos = new FileOutputStream(out);
                        fos.write(bytes);
                        fos.close();
                        Toast.makeText(MainActivity.this,
                            "Saved: " + filename, Toast.LENGTH_LONG).show();
                    } catch(Exception e){
                        Toast.makeText(MainActivity.this,
                            "Save failed", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    /* ============================================================
       FILE CHOOSER RESULT
       ============================================================ */
    @Override
    protected void onActivityResult(int req, int res, Intent data){
        if(req == REQ_FILE_CHOOSER){
            if(filePathCallback != null){
                Uri[] results = null;
                if(res == Activity.RESULT_OK && data != null){
                    if(data.getClipData() != null){
                        int n = data.getClipData().getItemCount();
                        results = new Uri[n];
                        for(int i = 0; i < n; i++)
                            results[i] = data.getClipData().getItemAt(i).getUri();
                    } else if(data.getData() != null){
                        results = new Uri[]{ data.getData() };
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    /* ============================================================
       PERMISSIONS
       ============================================================ */
    private void requestRuntimePermissions(){
        List<String> needed = new ArrayList<>();
        if(Build.VERSION.SDK_INT >= 33){
            if(!has("android.permission.READ_MEDIA_IMAGES")) needed.add("android.permission.READ_MEDIA_IMAGES");
            if(!has("android.permission.READ_MEDIA_VIDEO"))  needed.add("android.permission.READ_MEDIA_VIDEO");
            if(!has("android.permission.READ_MEDIA_AUDIO"))  needed.add("android.permission.READ_MEDIA_AUDIO");
        } else if(Build.VERSION.SDK_INT >= 23){
            if(!has(android.Manifest.permission.READ_EXTERNAL_STORAGE))
                needed.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            if(Build.VERSION.SDK_INT <= 28 && !has(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
                needed.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if(Build.VERSION.SDK_INT >= 23 && !has(android.Manifest.permission.CAMERA)){
            needed.add(android.Manifest.permission.CAMERA);
        }
        if(!needed.isEmpty()){
            requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    private boolean has(String p){
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    /* ============================================================
       BACK BUTTON
       ============================================================ */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(keyCode == KeyEvent.KEYCODE_BACK){
            if(sheetOpen){
                shellView.evaluateJavascript(
                    "(function(){var s=document.getElementById('scrim');" +
                    "if(s)s.click();})();", null);
                return true;
            }
            String url = contentView.getUrl();
            if(url != null && !url.contains("home.html") && contentView.canGoBack()){
                contentView.goBack();
                return true;
            }
            if(url != null && !url.contains("home.html")){
                contentView.loadUrl("file:///android_asset/home.html");
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onPause(){
        super.onPause();
        if(contentView != null) contentView.onPause();
        if(shellView != null) shellView.onPause();
    }
    @Override protected void onResume(){
        super.onResume();
        if(contentView != null) contentView.onResume();
        if(shellView != null) shellView.onResume();
    }
    @Override protected void onDestroy(){
        if(contentView != null){ contentView.destroy(); contentView = null; }
        if(shellView != null){ shellView.destroy(); shellView = null; }
        super.onDestroy();
    }
                               }
