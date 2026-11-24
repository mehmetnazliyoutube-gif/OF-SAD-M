package com.ofisadim.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pageLoaded = false

    private val stepUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == StepForegroundService.ACTION_STEPS_UPDATED) {
                val steps = intent.getIntExtra(StepForegroundService.EXTRA_STEPS, 0)
                val sensorType = intent.getStringExtra(StepForegroundService.EXTRA_SENSOR_TYPE)
                    ?: "HARDWARE_STEP_COUNTER"
                pushStepsToWebView(steps, sensorType)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        startStepServiceIfPermitted()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
        }

        webView.addJavascriptInterface(AndroidStepBridge(), "AndroidStepBridge")
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
                    ?: super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageLoaded = true
                pushStepsToWebView(StepStore.getTodaySteps(this@MainActivity), "HARDWARE_STEP_COUNTER")
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        webView.loadUrl("https://appassets.androidplatform.net/assets/ofis_adim.html")
        requestRequiredPermissions()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(StepForegroundService.ACTION_STEPS_UPDATED)
        ContextCompat.registerReceiver(
            this,
            stepUpdateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        startStepServiceIfPermitted()
        if (pageLoaded) {
            pushStepsToWebView(StepStore.getTodaySteps(this), "HARDWARE_STEP_COUNTER")
        }
    }

    override fun onStop() {
        try {
            unregisterReceiver(stepUpdateReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
    }

    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            startStepServiceIfPermitted()
        }
    }

    private fun startStepServiceIfPermitted() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (granted) {
            val intent = Intent(this, StepForegroundService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun pushStepsToWebView(steps: Int, sensorType: String) {
        if (!pageLoaded) return
        runOnUiThread {
            val safeSensor = sensorType.replace("'", "")
            webView.evaluateJavascript(
                "if (window.onNativeStepsUpdate) { window.onNativeStepsUpdate($steps, '$safeSensor'); }",
                null
            )
        }
    }

    private fun isBatteryOptIgnored(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationDialog() {
        try {
            if (!isBatteryOptIgnored()) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    inner class AndroidStepBridge {
        @JavascriptInterface
        fun isNativeApk(): Boolean = true

        @JavascriptInterface
        fun getTodaySteps(): Int = StepStore.getTodaySteps(this@MainActivity)

        @JavascriptInterface
        fun isIgnoringBatteryOptimizations(): Boolean = isBatteryOptIgnored()

        @JavascriptInterface
        fun requestBatteryOptimization() {
            runOnUiThread { openBatteryOptimizationDialog() }
        }
    }
}
