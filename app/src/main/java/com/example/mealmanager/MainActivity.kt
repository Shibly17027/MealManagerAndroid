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
import java.io.OutputStream

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val CREATE_BACKUP = 101
    private val OPEN_BACKUP = 102
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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            if (requestCode == CREATE_BACKUP) {
                contentResolver.openOutputStream(uri)?.use { it.write(pendingBackup.toByteArray(Charsets.UTF_8)) }
                web.evaluateJavascript("alert('Backup exported successfully.')", null)
            } else if (requestCode == OPEN_BACKUP) {
                val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val quoted = org.json.JSONObject.quote(json)
                web.evaluateJavascript("window.restoreFromNative($quoted)", null)
            }
        } catch (e: Exception) {
            val msg = org.json.JSONObject.quote(e.message ?: "File operation failed")
            web.evaluateJavascript("alert('Backup error: '+$msg)", null)
        }
    }
    private var pendingBackup: String = ""

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
    inner class SheetsBridge {
        @JavascriptInterface fun exportBackup(json: String) {
            runOnUiThread {
                pendingBackup = json
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "meal-manager-backup.json")
                }
                startActivityForResult(intent, CREATE_BACKUP)
            }
        }
        @JavascriptInterface fun pickBackup() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                startActivityForResult(intent, OPEN_BACKUP)
            }
        }

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
