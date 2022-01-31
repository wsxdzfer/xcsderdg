package me.iacn.biliroaming.hook

import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.utils.*
import java.io.BufferedReader
import java.io.InputStreamReader


class WebViewHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val x5WebViewClass by Weak { "com.tencent.smtt.sdk.WebView".findClassOrNull(mClassLoader) }

    private val hookedClient = HashSet<Class<*>>()
    private val hooker: Hooker = { param ->
        try {
            param.args[0].callMethod(
                "evaluateJavascript",
                """(function(){$js})()""".trimMargin(),
                null
            )
        } catch (e: Throwable) {
            Log.e(e)
        }
    }

    private val jsHooker = object : Any() {
        @JavascriptInterface
        fun hook(url: String, text: String): String {
            return this@WebViewHook.hook(url, text)
        }
    }

    private val js by lazy {
        val sb = StringBuilder()
        try {
            WebViewHook::class.java.classLoader?.getResourceAsStream("assets/xhook.js")
                .use { `is` ->
                    val isr = InputStreamReader(`is`)
                    val br = BufferedReader(isr)
                    while (true) {
                        br.readLine()?.also { sb.appendLine(it) } ?: break
                    }
                }
        } catch (e: Exception) {
        }
        sb.appendLine()
        sb.toString()
    }

    override fun startHook() {
        Log.d("startHook: WebView")
        if (BuildConfig.DEBUG)
            WebView.setWebContentsDebuggingEnabled(true)
        WebView::class.java.hookBeforeMethod(
            "setWebViewClient",
            WebViewClient::class.java
        ) { param ->
            val clazz = param.args[0].javaClass
            (param.thisObject as WebView).run {
                addJavascriptInterface(jsHooker, "hooker")
            }
            if (hookedClient.contains(clazz)) return@hookBeforeMethod
            try {
                clazz.getDeclaredMethod(
                    "onPageStarted",
                    WebView::class.java,
                    String::class.java,
                    Bitmap::class.java
                ).hookBeforeMethod(hooker)
                hookedClient.add(clazz)
                Log.d("hook webview $clazz")
            } catch (e: NoSuchMethodException) {
            }
        }
        if (BuildConfig.DEBUG)
            x5WebViewClass?.callStaticMethod("setWebContentsDebuggingEnabled", true)
        x5WebViewClass?.hookBeforeMethod(
            "setWebViewClient",
            "com.tencent.smtt.sdk.WebViewClient"
        ) { param ->
            val clazz = param.args[0].javaClass
            param.thisObject.callMethod("addJavascriptInterface", jsHooker, "hooker")
            if (hookedClient.contains(clazz)) return@hookBeforeMethod
            try {
                clazz.getDeclaredMethod(
                    "onPageStarted",
                    x5WebViewClass,
                    String::class.java,
                    Bitmap::class.java
                ).hookBeforeMethod(hooker)
                hookedClient.add(clazz)
                Log.d("hook webview $clazz")
            } catch (e: NoSuchMethodException) {
            }
        }
    }

    fun hook(url: String, text: String): String {
        return text
    }
}
