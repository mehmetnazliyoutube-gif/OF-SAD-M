# WebView JavascriptInterface metodlarini koru
-keepclassmembers class com.ofisadim.app.MainActivity$AndroidStepBridge {
    @android.webkit.JavascriptInterface <methods>;
}
