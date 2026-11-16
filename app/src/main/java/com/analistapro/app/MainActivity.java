package com.analistapro.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
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
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import android.util.Log;

public class MainActivity extends Activity {
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + ASSET_HOST + "/assets/www/index.html";
    private static final int DARK_BACKGROUND = Color.rgb(8, 13, 19);
    private static final String TAG = "AnalistaPro";
    private static final String CRASH_FILE = "analista_pro_last_crash.txt";
    private static final String DIAG_FILE = "analista_pro_diagnostics.txt";
    private Thread.UncaughtExceptionHandler previousCrashHandler;

    private FrameLayout root;
    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private final AnalistaNativeBridge nativeBridge = new AnalistaNativeBridge(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashHandler();

        // Archive an old crash report without blocking this launch.
        // This prevents a stale diagnostic report from forcing the user to tap
        // "Borrar informe y reintentar" after installing a fixed build.
        String previousCrash = readCrashReport();
        if (previousCrash != null && !previousCrash.trim().isEmpty()) {
            appendDiagnosticLine("Previous launch report (archived automatically):\\n"
                    + previousCrash);
            deleteFile(CRASH_FILE);
        }

        try {
            configureSystemBars();

            root = new FrameLayout(this);
            root.setBackgroundColor(DARK_BACKGROUND);
            root.setClipToPadding(true);

            root.setOnApplyWindowInsetsListener((view, insets) -> {
                applySafeAreaInsets(view, insets);
                return WindowInsets.CONSUMED;
            });

            webView = createWebView();
            root.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            setContentView(root);
            root.requestApplyInsets();

            Log.i(TAG, "Starting local page: " + START_URL);
            webView.loadUrl(START_URL);

        } catch (Throwable error) {
            Log.e(TAG, "Fatal error during app startup", error);
            writeCrashReport("FATAL ERROR DURING STARTUP", error);
            showDiagnosticScreen(formatThrowable("FATAL ERROR DURING STARTUP", error));
        }
    }

    private void installCrashHandler() {
        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            writeCrashReport("UNCAUGHT EXCEPTION on thread " + thread.getName(), error);
            Log.e(TAG, "Uncaught exception", error);

            if (previousCrashHandler != null) {
                previousCrashHandler.uncaughtException(thread, error);
            }
        });
    }

    private String formatThrowable(String heading, Throwable error) {
        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        printer.println(heading);
        printer.println("Android SDK: " + android.os.Build.VERSION.SDK_INT);
        printer.println("Device: " + android.os.Build.MANUFACTURER + " "
                + android.os.Build.MODEL);
        printer.println("App URL: " + START_URL);
        error.printStackTrace(printer);
        printer.flush();
        return writer.toString();
    }

    private synchronized void writeCrashReport(String heading, Throwable error) {
        try (FileOutputStream output = openFileOutput(CRASH_FILE, MODE_PRIVATE)) {
            String report = formatThrowable(heading, error);
            output.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception fileError) {
            Log.e(TAG, "Could not save crash report", fileError);
        }
    }

    private synchronized void appendDiagnosticLine(String line) {
        try (FileOutputStream output = openFileOutput(DIAG_FILE, MODE_APPEND)) {
            output.write((line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception fileError) {
            Log.e(TAG, "Could not append diagnostic line", fileError);
        }
    }

    private synchronized String readCrashReport() {
        try (FileInputStream input = openFileInput(CRASH_FILE);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private synchronized String readDiagnosticLog() {
        try (FileInputStream input = openFileInput(DIAG_FILE);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        } catch (Exception ignored) {
            return "(sin registros adicionales)";
        }
    }

    private synchronized void clearCrashReport() {
        try {
            deleteFile(CRASH_FILE);
            deleteFile(DIAG_FILE);
        } catch (Exception ignored) {
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showDiagnosticScreen(String message) {
        runOnUiThread(() -> {
            try {
                if (webView != null) {
                    webView.stopLoading();
                    webView.setWebViewClient(new WebViewClient());
                    ViewParent parent = webView.getParent();
                    if (parent instanceof ViewGroup) {
                        ((ViewGroup) parent).removeView(webView);
                    }
                    webView.destroy();
                    webView = null;
                }

                LinearLayout page = new LinearLayout(this);
                page.setOrientation(LinearLayout.VERTICAL);
                page.setPadding(dp(20), dp(24), dp(20), dp(24));
                page.setBackgroundColor(DARK_BACKGROUND);

                TextView title = new TextView(this);
                title.setText("ANALISTA PRO — INFORME DE DIAGNÓSTICO");
                title.setTextColor(Color.WHITE);
                title.setTextSize(18);
                title.setPadding(0, 0, 0, dp(14));
                page.addView(title);

                TextView details = new TextView(this);
                details.setText(message);
                details.setTextColor(Color.rgb(220, 232, 242));
                details.setTextSize(12);
                details.setTextIsSelectable(true);

                ScrollView scroll = new ScrollView(this);
                scroll.addView(details);
                page.addView(scroll, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                Button copy = new Button(this);
                copy.setText("COPIAR INFORME");
                copy.setOnClickListener(v -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                ClipData.newPlainText("Analista Pro crash report", message));
                        Toast.makeText(this, "Informe copiado", Toast.LENGTH_SHORT).show();
                    }
                });
                page.addView(copy);

                Button retry = new Button(this);
                retry.setText("BORRAR INFORME Y REINTENTAR");
                retry.setOnClickListener(v -> {
                    clearCrashReport();
                    recreate();
                });
                page.addView(retry);

                setContentView(page);

            } catch (Throwable screenError) {
                Log.e(TAG, "Could not display diagnostic screen", screenError);
            }
        });
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            // Configure edge-to-edge before content is laid out.
            // Do NOT call Window.getInsetsController() here: on some HONOR
            // firmware the DecorView controller is not created yet and that
            // call throws a NullPointerException during Activity.onCreate().
            window.setDecorFitsSystemWindows(false);
        }

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
    }

    private void applySafeAreaInsets(View target, WindowInsets insets) {
        int safeTypes = WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout()
                | WindowInsets.Type.systemGestures();

        Insets safe = insets.getInsets(safeTypes);
        target.setPadding(safe.left, safe.top, safe.right, safe.bottom);

        // Clip-to-padding is configured on root (FrameLayout),
        // not on View, because setClipToPadding belongs to ViewGroup.

        notifyWebViewportChanged();
    }

    private void notifyWebViewportChanged() {
        if (webView == null) return;

        webView.post(() -> {
            if (webView != null && webView.getVisibility() == View.VISIBLE) {
                webView.evaluateJavascript(
                        "window.dispatchEvent(new Event('resize'));",
                        null);
            }
        });
    }

    private void showSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                View decorView = getWindow().getDecorView();
                if (decorView == null) return;

                // The decor view is created by setContentView(). Post the
                // request so the WindowInsetsController is available on OEM
                // implementations that initialize it lazily.
                decorView.post(() -> {
                    try {
                        WindowInsetsController controller =
                                decorView.getWindowInsetsController();
                        if (controller != null) {
                            controller.show(WindowInsets.Type.systemBars());
                        }
                    } catch (RuntimeException error) {
                        Log.w(TAG, "Could not show system bars", error);
                    }
                });
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not access decor view to show system bars", error);
            }
        }
    }

    private WebView createWebView() {
        WebView.setWebContentsDebuggingEnabled(true);
        WebView view = new WebView(this);
        view.setBackgroundColor(DARK_BACKGROUND);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.setWebViewClient(new LocalOnlyWebViewClient());
        view.setWebChromeClient(new AppWebChromeClient());
        view.addJavascriptInterface(nativeBridge, "AnalistaNative");

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(true);
        }

        return view;
    }

    private final class LocalOnlyWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(
                WebView view, WebResourceRequest request) {

            Uri uri = request.getUrl();

            if (uri != null && ASSET_HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/assets/")) {

                String assetPath = uri.getPath().substring("/assets/".length());

                try {
                    InputStream stream = getAssets().open(assetPath);
                    String extension = MimeTypeMap.getFileExtensionFromUrl(assetPath);
                    String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                            extension == null ? "" : extension.toLowerCase());

                    if (mime == null) mime = "application/octet-stream";

                    String encoding = (mime.startsWith("text/")
                            || mime.contains("javascript")
                            || mime.contains("json")
                            || mime.contains("xml")) ? "UTF-8" : null;

                    Map<String, String> headers = Collections.singletonMap(
                            "Cache-Control", "no-cache, no-store, must-revalidate");

                    return new WebResourceResponse(
                            mime, encoding, 200, "OK", headers, stream);

                } catch (IOException exception) {
                    String line = "ASSET NOT FOUND: " + assetPath + " — " + exception;
                    Log.e(TAG, line, exception);
                    appendDiagnosticLine(line);
                    return diagnosticResponse(404, "Not Found", line);
                }
            }

            String blocked = "BLOCKED NON-LOCAL REQUEST: "
                    + (uri == null ? "null" : uri.toString());
            Log.w(TAG, blocked);
            appendDiagnosticLine(blocked);
            return diagnosticResponse(403, "Offline Only", blocked);
        }

        @Override
        public boolean shouldOverrideUrlLoading(
                WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            boolean blocked = uri == null
                    || !ASSET_HOST.equalsIgnoreCase(uri.getHost());

            if (blocked) {
                appendDiagnosticLine("NAVIGATION BLOCKED: " + uri);
            }
            return blocked;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            Log.i(TAG, "Page started: " + url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Log.i(TAG, "Page finished: " + url);
        }

        @Override
        public void onReceivedError(
                WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);

            if (request.isForMainFrame()) {
                String message = "MAIN PAGE LOAD ERROR\nURL: " + request.getUrl()
                        + "\nCode: " + error.getErrorCode()
                        + "\nDescription: " + error.getDescription();
                appendDiagnosticLine(message);
                showDiagnosticScreen(message);
            }
        }

        @Override
        public void onReceivedHttpError(
                WebView view, WebResourceRequest request, WebResourceResponse response) {
            super.onReceivedHttpError(view, request, response);

            if (request.isForMainFrame()) {
                String message = "MAIN PAGE HTTP ERROR\nURL: " + request.getUrl()
                        + "\nStatus: " + response.getStatusCode()
                        + "\nReason: " + response.getReasonPhrase();
                appendDiagnosticLine(message);
                showDiagnosticScreen(message);
            }
        }

        @Override
        public boolean onRenderProcessGone(
                WebView view, RenderProcessGoneDetail detail) {
            String message = "WEBVIEW RENDERER PROCESS TERMINATED\n"
                    + "Did crash: " + detail.didCrash() + "\n"
                    + "Renderer priority at exit: " + detail.rendererPriorityAtExit()
                    + "\nAndroid SDK: " + android.os.Build.VERSION.SDK_INT
                    + "\nDevice: " + android.os.Build.MANUFACTURER + " "
                    + android.os.Build.MODEL;
            appendDiagnosticLine(message);
            Log.e(TAG, message);
            showDiagnosticScreen(message);
            return true;
        }

        private WebResourceResponse diagnosticResponse(
                int code, String reason, String detail) {
            byte[] bytes = detail.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    code,
                    reason,
                    Collections.emptyMap(),
                    new ByteArrayInputStream(bytes));
        }
    }

    private final class AppWebChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            String message = "WEB CONSOLE " + consoleMessage.messageLevel()
                    + ": " + consoleMessage.message()
                    + " (" + consoleMessage.sourceId() + ":"
                    + consoleMessage.lineNumber() + ")";
            Log.e(TAG, message);
            if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                appendDiagnosticLine(message);
            }
            return true;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }

            customView = view;
            customViewCallback = callback;
            webView.setVisibility(View.GONE);

            root.addView(customView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            showSystemBars();
            root.requestApplyInsets();
        }

        @Override
        public void onHideCustomView() {
            exitCustomView();
        }
    }

    private void exitCustomView() {
        if (customView == null) return;

        root.removeView(customView);
        customView = null;
        webView.setVisibility(View.VISIBLE);

        showSystemBars();
        root.requestApplyInsets();

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }

        webView.requestApplyInsets();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (root != null) {
            root.requestApplyInsets();
            root.requestLayout();
        }

        if (webView != null) {
            webView.requestLayout();

            webView.post(() -> webView.evaluateJavascript(
                    "window.dispatchEvent(new Event('resize'));"
                            + "window.dispatchEvent(new Event('orientationchange'));",
                    null));
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitCustomView();
            return;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        nativeBridge.closeAll();

        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeJavascriptInterface("AnalistaNative");
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webView);
            }
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    private static class PendingSave {
        final Uri uri;
        final OutputStream stream;

        PendingSave(Uri uri, OutputStream stream) {
            this.uri = uri;
            this.stream = stream;
        }
    }

    public static class AnalistaNativeBridge {
        private final MainActivity activity;
        private final Map<String, PendingSave> pending =
                new ConcurrentHashMap<>();

        AnalistaNativeBridge(MainActivity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public String beginSave(String requestedName, String mimeType) {
            try {
                String mime = (mimeType == null || mimeType.trim().isEmpty())
                        ? "application/octet-stream"
                        : mimeType.trim().toLowerCase();

                String name = sanitizeName(requestedName);
                Uri collection;
                String folder;

                if (mime.startsWith("video/")) {
                    collection = MediaStore.Video.Media.getContentUri(
                            MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    folder = Environment.DIRECTORY_MOVIES + "/Analista Pro";

                    if (!name.toLowerCase().endsWith(".mp4")) {
                        name += ".mp4";
                    }

                } else if (mime.startsWith("image/")) {
                    collection = MediaStore.Images.Media.getContentUri(
                            MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    folder = Environment.DIRECTORY_PICTURES + "/Analista Pro";

                    if (!name.toLowerCase().endsWith(".jpg")
                            && !name.toLowerCase().endsWith(".jpeg")
                            && !name.toLowerCase().endsWith(".png")) {
                        name += ".jpg";
                    }

                } else {
                    collection = MediaStore.Downloads.getContentUri(
                            MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    folder = Environment.DIRECTORY_DOWNLOADS + "/Analista Pro";
                }

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri uri = activity.getContentResolver().insert(collection, values);
                if (uri == null) return "";

                OutputStream stream =
                        activity.getContentResolver().openOutputStream(uri, "w");

                if (stream == null) {
                    activity.getContentResolver().delete(uri, null, null);
                    return "";
                }

                String id = UUID.randomUUID().toString();
                pending.put(id, new PendingSave(uri, stream));

                return id;

            } catch (Exception exception) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean appendSaveChunk(String id, String base64Chunk) {
            PendingSave save = pending.get(id);

            if (save == null || base64Chunk == null) return false;

            try {
                byte[] bytes = Base64.decode(base64Chunk, Base64.DEFAULT);

                synchronized (save) {
                    save.stream.write(bytes);
                }

                return true;

            } catch (Exception exception) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean finishSave(String id) {
            PendingSave save = pending.remove(id);
            if (save == null) return false;

            try {
                synchronized (save) {
                    save.stream.flush();
                    save.stream.close();
                }

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);

                activity.getContentResolver().update(
                        save.uri, values, null, null);

                activity.runOnUiThread(() ->
                        Toast.makeText(
                                activity,
                                "Archivo guardado en Analista Pro",
                                Toast.LENGTH_LONG).show());

                return true;

            } catch (Exception exception) {
                try {
                    activity.getContentResolver().delete(
                            save.uri, null, null);
                } catch (Exception ignored) {
                }

                return false;
            }
        }

        @JavascriptInterface
        public void cancelSave(String id) {
            PendingSave save = pending.remove(id);
            if (save == null) return;

            try {
                save.stream.close();
            } catch (Exception ignored) {
            }

            try {
                activity.getContentResolver().delete(
                        save.uri, null, null);
            } catch (Exception ignored) {
            }
        }

        void closeAll() {
            for (String id : pending.keySet()) {
                cancelSave(id);
            }
        }

        private static String sanitizeName(String input) {
            String name = input == null ? "Analista_Pro" : input.trim();

            name = name.replaceAll("[\\\\/:*?\"<>|]", "_")
                    .replaceAll("\\.\\.", "_");

            if (name.isEmpty()) {
                name = "Analista_Pro";
            }

            return name.length() > 100
                    ? name.substring(0, 100)
                    : name;
        }
    }
}
