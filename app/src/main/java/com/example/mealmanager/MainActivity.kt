package com.example.mealmanager

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter

class MainActivity : Activity() {
    private lateinit var web: WebView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
        }
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(SheetsBridge(), "MealBridge")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
    inner class SheetsBridge {
        @JavascriptInterface fun request(method: String, endpoint: String, payload: String): String {
            var conn: HttpURLConnection? = null
            return try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    if (method == "POST") {
                        doOutput = true
                        setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                        OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(payload) }
                    }
                }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
            } catch (e: Exception) {
                "{\"error\":${org.json.JSONObject.quote(e.message ?: "Network error")}}"
            } finally { conn?.disconnect() }
        }
    }
}
