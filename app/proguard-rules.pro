# Analista Pro: keep the JavaScript bridge methods exposed to WebView.
-keepclassmembers class com.analistapro.app.MainActivity$AnalistaNativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}
