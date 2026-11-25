package com.example.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Restricted JavaScript-to-Android bridge for WebViews in Your App & Artifacts Studio.
 * Exposes ONLY predefined safe Android actions; never evaluates arbitrary scripts.
 */
class ZenvixWebActionBridge(
    context: Context,
    private val onOutcome: (DeviceActionOutcome) -> Unit = {}
) {
    private val actionBridge = DeviceActionBridge(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun openWhatsApp(): String = runAndSerialize { actionBridge.openWhatsApp() }

    @JavascriptInterface
    fun openApp(appName: String): String = runAndSerialize { actionBridge.openApp(appName) }

    @JavascriptInterface
    fun openUrl(url: String): String = runAndSerialize { actionBridge.openUrl(url) }

    @JavascriptInterface
    fun makeCall(phoneNumber: String): String = runAndSerialize { actionBridge.makeCall(phoneNumber) }

    @JavascriptInterface
    fun callContact(contactName: String): String = runAndSerialize { actionBridge.callContact(contactName) }

    @JavascriptInterface
    fun setFlashlight(enabled: Boolean): String = runAndSerialize { actionBridge.setFlashlight(enabled) }

    @JavascriptInterface
    fun openSystemPanel(panelType: String): String = runAndSerialize { actionBridge.openSystemPanel(panelType) }

    @JavascriptInterface
    fun adjustVolume(mode: String): String = runAndSerialize { actionBridge.adjustVolume(mode) }

    @JavascriptInterface
    fun executeDeviceCommand(command: String): String = runAndSerialize {
        actionBridge.executeOfflineSiriCommand(command) ?: DeviceActionOutcome(
            actionName = "executeDeviceCommand",
            executed = false,
            status = DeviceActionStatus.INVALID_INPUT,
            message = "Unrecognized device command."
        )
    }

    @JavascriptInterface
    fun isOnline(): Boolean = actionBridge.isDeviceOnline()

    private fun runAndSerialize(block: () -> DeviceActionOutcome): String {
        var result: DeviceActionOutcome? = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            result = block()
        } else {
            val latch = CountDownLatch(1)
            mainHandler.post {
                try {
                    result = block()
                } finally {
                    latch.countDown()
                }
            }
            latch.await(2, TimeUnit.SECONDS)
        }
        val safeOutcome = result ?: DeviceActionOutcome(
            actionName = "bridge",
            executed = false,
            status = DeviceActionStatus.FAILED,
            message = "Action timed out."
        )
        mainHandler.post { onOutcome(safeOutcome) }
        return safeOutcome.toJson().toString()
    }
}

@SuppressLint("SetJavaScriptEnabled")
fun configureZenvixWebView(
    webView: WebView,
    enableJavaScript: Boolean,
    enableDomStorage: Boolean,
    onOutcome: (DeviceActionOutcome) -> Unit
) {
    val ctx = webView.context
    val actionBridge = DeviceActionBridge(ctx)
    val jsBridge = ZenvixWebActionBridge(ctx, onOutcome)

    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val uri = request?.url ?: return false
            val urlStr = uri.toString()
            val scheme = uri.scheme?.lowercase().orEmpty()
            if (uri.host == "zenvix.local") return false
            return when {
                scheme == "tel" -> {
                    onOutcome(actionBridge.makeCall(uri.schemeSpecificPart.orEmpty()))
                    true
                }
                scheme == "whatsapp" || urlStr.startsWith("https://wa.me/") -> {
                    onOutcome(actionBridge.openWhatsApp())
                    true
                }
                scheme == "https" || scheme == "http" -> {
                    onOutcome(actionBridge.openUrl(urlStr))
                    true
                }
                else -> true
            }
        }
    }

    webView.settings.javaScriptEnabled = enableJavaScript
    webView.settings.domStorageEnabled = enableDomStorage
    webView.settings.allowFileAccess = false
    webView.settings.allowContentAccess = false
    webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE

    if (enableJavaScript) {
        webView.addJavascriptInterface(jsBridge, "ZenvixAndroid")
        webView.addJavascriptInterface(jsBridge, "Android")
    }
}
