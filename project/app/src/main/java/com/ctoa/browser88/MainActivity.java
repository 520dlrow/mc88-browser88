package com.ctoa.browser88;
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
import android.view.Gravity;
import android.view.KeyEvent;
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
import android.widget.LinearLayout;
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

    /* Ad blocker host list. Add domains here if you find leaks. */
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
    private LinearLayout column;
    private WebView topShell;
    private WebView contentView;
    private WebView dockShell;

    private boolean dockExpanded = false;
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

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.parseColor("#0e0e10"));

        /* Top bar shell — the URL field and menu button */
        topShell = new WebView(this);
        configureWebView(topShell, false);
        topShell.addJavascriptInterface(new Bridge(), "AndroidBridge");
        topShell.loadUrl("file:///android_asset/index.html");

        /* Content view — web pages load here */
        contentView = new WebView(this);
        configureWebView(contentView, false);
        contentView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        setupContentClient();
        contentView.loadUrl("file:///android_asset/home.html");

        /* Bottom dock — transparent so the content shows through when expanded */
        dockShell = new WebView(this);
        configureWebView(dockShell, true);
        dockShell.addJavascriptInterface(new Bridge(), "AndroidBridge");
        dockShell.loadUrl("file:///android_asset/dock.html");

        column.addView(topShell, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        column.addView(contentView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View spacer = new View(this);
        column.addView(spacer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        root.addView(column, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        dockLp.gravity = Gravity.BOTTOM;
        root.addView(dockShell, dockLp);

        setContentView(root);

        requestRuntimePermissions();
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

        /* Remove WebView bounce and scrollbars so the shell feels like an app */
        wv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wv.setVerticalScrollBarEnabled(false);
        wv.setHorizontalScrollBarEnabled(false);
        wv.setScrollbarFadingEnabled(true);
        wv.setNestedScrollingEnabled(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
       CONTENT CLIENT — web pages load here
       ============================================================ */
    private void setupContentClient(){

        contentView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(WebView v, int p){
                callTopBool("shellSetProgress", p < 100);
            }

            @Override
            public void onReceivedTitle(WebView v, String title){
                String url = v.getUrl();
                if(url != null && !url.startsWith("file://")){
                    dockCall("dockOnPageLoaded",
                        jsStr(url),
                        jsStr(title != null ? title : ""));
                }
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params){
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE_CHOOSER);
                    return true;
                } catch (Exception e){
                    filePathCallback = null;
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request){
                runOnUiThread(() -> {
                    try { request.grant(request.getResources()); }
                    catch (Exception e) { request.deny(); }
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
                    .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm())
                    .setCancelable(false).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String u, String m, final android.webkit.JsResult r){
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(m)
                    .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm())
                    .setNegativeButton(android.R.string.cancel, (d, w) -> r.cancel())
                    .setCancelable(false).show();
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
                            dockCall("dockSetBlocked", String.valueOf(blockedCount));
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
                if (!"http".equals(scheme) && !"https".equals(scheme)
                        && !"file".equals(scheme)){
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return true;
                    } catch (Exception e){ return false; }
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f){
                if(url == null) return;
                if(url.startsWith("file://") && url.contains("home.html")){
                    callTopStr("shellSetUrl", "");
                } else if(!url.startsWith("file://")){
                    callTopStr("shellSetUrl", url);
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
        dockCall("dockSetNavState",
            String.valueOf(cb),
            String.valueOf(cf));
    }

    /* ============================================================
       SHELL CALLBACK HELPERS
       ============================================================ */
    private void callTopStr(String fn, String arg){
        runOnUiThread(() -> {
            try {
                topShell.evaluateJavascript(
                    "window." + fn + "(" + jsStr(arg) + ");", null);
            } catch (Exception ignored){}
        });
    }

    private void callTopBool(String fn, boolean arg){
        runOnUiThread(() -> {
            try {
                topShell.evaluateJavascript(
                    "window." + fn + "(" + arg + ");", null);
            } catch (Exception ignored){}
        });
    }

    private void dockCall(String fn, String... args){
        runOnUiThread(() -> {
            try {
                StringBuilder sb = new StringBuilder("window.");
                sb.append(fn).append("(");
                for(int i = 0; i < args.length; i++){
                    if(i > 0) sb.append(",");
                    sb.append(args[i]);
                }
                sb.append(");");
                dockShell.evaluateJavascript(sb.toString(), null);
            } catch (Exception ignored){}
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
       DOCK EXPAND / COLLAPSE
       ============================================================ */
    private void expandDock(){
        if(dockExpanded) return;
        runOnUiThread(() -> {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
            dockShell.setLayoutParams(lp);
            dockShell.bringToFront();
            dockExpanded = true;
        });
    }

    private void collapseDock(){
        if(!dockExpanded) return;
        runOnUiThread(() -> {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
            lp.gravity = Gravity.BOTTOM;
            dockShell.setLayoutParams(lp);
            dockExpanded = false;
        });
    }

    /* ============================================================
       BRIDGE — everything the shell can call
       ============================================================ */
    public class Bridge {

        @JavascriptInterface
        public String getVersion(){ return "1.0"; }

        @JavascriptInterface
        public void navigate(final String url){
            runOnUiThread(() -> {
                if(url == null || url.isEmpty()) return;
                String target = url;
                if(!url.startsWith("http") && !url.startsWith("file")
                        && !url.startsWith("about")){
                    target = "https://duckduckgo.com/?q=" + Uri.encode(url);
                }
                contentView.loadUrl(target);
            });
        }

        @JavascriptInterface
        public void goHome(){
            runOnUiThread(() -> contentView.loadUrl("file:///android_asset/home.html"));
        }

        @JavascriptInterface
        public void goBack(){
            runOnUiThread(() -> { if(contentView.canGoBack()) contentView.goBack(); });
        }

        @JavascriptInterface
        public void goForward(){
            runOnUiThread(() -> { if(contentView.canGoForward()) contentView.goForward(); });
        }

        @JavascriptInterface
        public void reload(){
            runOnUiThread(() -> contentView.reload());
        }

        @JavascriptInterface
        public void toggleMenu(){
            runOnUiThread(() -> {
                expandDock();
                dockShell.postDelayed(() -> dockCall("dockOpenMenu"), 80);
            });
        }

        @JavascriptInterface
        public void expandDock(){ MainActivity.this.expandDock(); }

        @JavascriptInterface
        public void collapseDock(){ MainActivity.this.collapseDock(); }

        @JavascriptInterface
        public void setAdblock(final boolean on){
            adBlockEnabled = on;
            runOnUiThread(() -> {
                if(on) injectAdCss();
                else contentView.evaluateJavascript(
                    "(function(){var e=document.getElementById('b88-adblock');" +
                    "if(e)e.remove();})();", null);
            });
        }

        @JavascriptInterface
        public void setDesktopMode(final boolean on){
            runOnUiThread(() -> {
                WebSettings s = contentView.getSettings();
                if(on){
                    s.setUserAgentString(
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                } else {
                    s.setUserAgentString(null);
                }
                contentView.reload();
            });
        }

        @JavascriptInterface
        public void clearAllData(){
            runOnUiThread(() -> {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                contentView.clearCache(true);
                contentView.clearHistory();
                contentView.clearFormData();
                Toast.makeText(MainActivity.this,
                    "Browsing data cleared", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void addPin(final String name, final String url){
            runOnUiThread(() -> {
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
                } catch (Exception e){
                    Toast.makeText(MainActivity.this, "Pin failed", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public String getPins(){
            try {
                SharedPreferences sp = getSharedPreferences("b88.pins", MODE_PRIVATE);
                return sp.getString("list", "[]");
            } catch (Exception e){
                return "[]";
            }
        }

        @JavascriptInterface
        public void notify(final String title, final String body){
            runOnUiThread(() -> Toast.makeText(
                MainActivity.this, title + ": " + body, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void openExternal(final String url){
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored){}
            });
        }

        @JavascriptInterface
        public void saveAPK(final String b64, final String filename){
            runOnUiThread(() -> {
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
                } catch (Exception e){
                    Toast.makeText(MainActivity.this,
                        "Save failed", Toast.LENGTH_SHORT).show();
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

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants){
        super.onRequestPermissionsResult(req, perms, grants);
    }

    /* ============================================================
       BACK BUTTON
       ============================================================ */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(keyCode == KeyEvent.KEYCODE_BACK){
            if(dockExpanded){
                dockShell.evaluateJavascript(
                    "(function(){var s=document.getElementById('scrim');" +
                    "if(s&&s.classList.contains('on'))s.click();})();", null);
                collapseDock();
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

    @Override
    protected void onPause(){
        super.onPause();
        if(topShell != null) topShell.onPause();
        if(contentView != null) contentView.onPause();
        if(dockShell != null) dockShell.onPause();
    }

    @Override
    protected void onResume(){
        super.onResume();
        if(topShell != null) topShell.onResume();
        if(contentView != null) contentView.onResume();
        if(dockShell != null) dockShell.onResume();
    }

    @Override
    protected void onDestroy(){
        if(topShell != null){ topShell.destroy(); topShell = null; }
        if(contentView != null){ contentView.destroy(); contentView = null; }
        if(dockShell != null){ dockShell.destroy(); dockShell = null; }
        super.onDestroy();
    }
                                   }
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
import android.view.Gravity;
import android.view.KeyEvent;
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
import android.widget.LinearLayout;
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

    /* Ad blocker host list. Add domains here if you find leaks. */
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
    private LinearLayout column;
    private WebView topShell;
    private WebView contentView;
    private WebView dockShell;

    private boolean dockExpanded = false;
    private boolean adBlockEnabled = true;
    private int blockedCount = 0;

    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0e0e10"));

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.parseColor("#0e0e10"));

        /* Top bar shell */
        topShell = new WebView(this);
        configureWebView(topShell, false);
        topShell.addJavascriptInterface(new Bridge(), "AndroidBridge");
        topShell.loadUrl("file:///android_asset/index.html");

        /* Content view — starts on the local home page */
        contentView = new WebView(this);
        configureWebView(contentView, false);
        contentView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        setupContentClient();
        contentView.loadUrl("file:///android_asset/home.html");

        /* Bottom dock shell — transparent so the content behind shows through */
        dockShell = new WebView(this);
        configureWebView(dockShell, true);
        dockShell.addJavascriptInterface(new Bridge(), "AndroidBridge");
        dockShell.loadUrl("file:///android_asset/dock.html");

        column.addView(topShell, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        column.addView(contentView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View spacer = new View(this);
        column.addView(spacer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        root.addView(column, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        dockLp.gravity = Gravity.BOTTOM;
        root.addView(dockShell, dockLp);

        setContentView(root);

        requestRuntimePermissions();
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
       CONTENT CLIENT — web pages load here
       ============================================================ */
    private void setupContentClient(){

        contentView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(WebView v, int p){
                callTopBool("shellSetProgress", p < 100);
            }

            @Override
            public void onReceivedTitle(WebView v, String title){
                String url = v.getUrl();
                if(url != null && !url.startsWith("file://")){
                    dockCall("dockOnPageLoaded",
                        jsStr(url),
                        jsStr(title != null ? title : ""));
                }
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params){
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE_CHOOSER);
                    return true;
                } catch (Exception e){
                    filePathCallback = null;
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request){
                runOnUiThread(() -> {
                    try { request.grant(request.getResources()); }
                    catch (Exception e) { request.deny(); }
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
                    .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm())
                    .setCancelable(false).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String u, String m, final android.webkit.JsResult r){
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(m)
                    .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm())
                    .setNegativeButton(android.R.string.cancel, (d, w) -> r.cancel())
                    .setCancelable(false).show();
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
                            dockCall("dockSetBlocked", String.valueOf(blockedCount));
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
                if (!"http".equals(scheme) && !"https".equals(scheme)
                        && !"file".equals(scheme)){
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return true;
                    } catch (Exception e){ return false; }
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f){
                if(url == null) return;
                if(url.startsWith("file://") && url.contains("home.html")){
                    callTopStr("shellSetUrl", "");
                } else if(!url.startsWith("file://")){
                    callTopStr("shellSetUrl", url);
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
        dockCall("dockSetNavState",
            String.valueOf(cb),
            String.valueOf(cf));
    }

    /* ============================================================
       SHELL CALLBACK HELPERS
       ============================================================ */
    private void callTopStr(String fn, String arg){
        runOnUiThread(() -> {
            try {
                topShell.evaluateJavascript(
                    "window." + fn + "(" + jsStr(arg) + ");", null);
            } catch (Exception ignored){}
        });
    }

    private void callTopBool(String fn, boolean arg){
        runOnUiThread(() -> {
            try {
                topShell.evaluateJavascript(
                    "window." + fn + "(" + arg + ");", null);
            } catch (Exception ignored){}
        });
    }

    private void dockCall(String fn, String... args){
        runOnUiThread(() -> {
            try {
                StringBuilder sb = new StringBuilder("window.");
                sb.append(fn).append("(");
                for(int i = 0; i < args.length; i++){
                    if(i > 0) sb.append(",");
                    sb.append(args[i]);
                }
                sb.append(");");
                dockShell.evaluateJavascript(sb.toString(), null);
            } catch (Exception ignored){}
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
       DOCK EXPAND / COLLAPSE
       ============================================================ */
    private void expandDock(){
        if(dockExpanded) return;
        runOnUiThread(() -> {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
            dockShell.setLayoutParams(lp);
            dockShell.bringToFront();
            dockExpanded = true;
        });
    }

    private void collapseDock(){
        if(!dockExpanded) return;
        runOnUiThread(() -> {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
            lp.gravity = Gravity.BOTTOM;
            dockShell.setLayoutParams(lp);
            dockExpanded = false;
        });
    }

    /* ============================================================
       BRIDGE
       ============================================================ */
    public class Bridge {

        @JavascriptInterface
        public String getVersion(){ return "1.0"; }

        @JavascriptInterface
        public void navigate(final String url){
            runOnUiThread(() -> {
                if(url == null || url.isEmpty()) return;
                String target = url;
                if(!url.startsWith("http") && !url.startsWith("file")
                        && !url.startsWith("about")){
                    target = "https://duckduckgo.com/?q=" + Uri.encode(url);
                }
                contentView.loadUrl(target);
            });
        }

        @JavascriptInterface
        public void goHome(){
            runOnUiThread(() -> contentView.loadUrl("file:///android_asset/home.html"));
        }

        @JavascriptInterface
        public void goBack(){
            runOnUiThread(() -> { if(contentView.canGoBack()) contentView.goBack(); });
        }

        @JavascriptInterface
        public void goForward(){
            runOnUiThread(() -> { if(contentView.canGoForward()) contentView.goForward(); });
        }

        @JavascriptInterface
        public void reload(){
            runOnUiThread(() -> contentView.reload());
        }

        @JavascriptInterface
        public void toggleMenu(){
            runOnUiThread(() -> {
                expandDock();
                dockShell.postDelayed(() -> dockCall("dockOpenMenu"), 80);
            });
        }

        @JavascriptInterface
        public void expandDock(){ MainActivity.this.expandDock(); }

        @JavascriptInterface
        public void collapseDock(){ MainActivity.this.collapseDock(); }

        @JavascriptInterface
        public void setAdblock(final boolean on){
            adBlockEnabled = on;
            runOnUiThread(() -> {
                if(on) injectAdCss();
                else contentView.evaluateJavascript(
                    "(function(){var e=document.getElementById('b88-adblock');" +
                    "if(e)e.remove();})();", null);
            });
        }

        @JavascriptInterface
        public void setDesktopMode(final boolean on){
            runOnUiThread(() -> {
                WebSettings s = contentView.getSettings();
                if(on){
                    s.setUserAgentString(
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                } else {
                    s.setUserAgentString(null);
                }
                contentView.reload();
            });
        }

        @JavascriptInterface
        public void clearAllData(){
            runOnUiThread(() -> {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                contentView.clearCache(true);
                contentView.clearHistory();
                contentView.clearFormData();
                Toast.makeText(MainActivity.this,
                    "Browsing data cleared", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void addPin(final String name, final String url){
            runOnUiThread(() -> {
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
                } catch (Exception e){
                    Toast.makeText(MainActivity.this, "Pin failed", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public String getPins(){
            try {
                SharedPreferences sp = getSharedPreferences("b88.pins", MODE_PRIVATE);
                return sp.getString("list", "[]");
            } catch (Exception e){
                return "[]";
            }
        }

        @JavascriptInterface
        public void notify(final String title, final String body){
            runOnUiThread(() -> Toast.makeText(
                MainActivity.this, title + ": " + body, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void openExternal(final String url){
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored){}
            });
        }

        @JavascriptInterface
        public void saveAPK(final String b64, final String filename){
            runOnUiThread(() -> {
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
                } catch (Exception e){
                    Toast.makeText(MainActivity.this,
                        "Save failed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /* ============================================================
       FILE CHOOSER
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
            if(dockExpanded){
                dockShell.evaluateJavascript(
                    "(function(){var s=document.getElementById('scrim');" +
                    "if(s&&s.classList.contains('on'))s.click();})();", null);
                collapseDock();
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
        if(topShell != null) topShell.onPause();
        if(contentView != null) contentView.onPause();
        if(dockShell != null) dockShell.onPause();
    }
    @Override protected void onResume(){
        super.onResume();
        if(topShell != null) topShell.onResume();
        if(contentView != null) contentView.onResume();
        if(dockShell != null) dockShell.onResume();
    }
    @Override protected void onDestroy(){
        if(topShell != null){ topShell.destroy(); topShell = null; }
        if(contentView != null){ contentView.destroy(); contentView = null; }
        if(dockShell != null){ dockShell.destroy(); dockShell = null; }
        super.onDestroy();
    }
           }
