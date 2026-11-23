package com.example.mealmanager

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.print.PrintManager
import android.print.PrintAttributes
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.OutputStream

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var reportWebView: WebView? = null
    private var activePrintJob: android.print.PrintJob? = null
    private var printInProgress = false
    private val printHandler = Handler(Looper.getMainLooper())
    private val CREATE_BACKUP = 101
    private val OPEN_BACKUP = 102
    private val CREATE_CSV = 103
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
            } else if (requestCode == CREATE_CSV) {
                contentResolver.openOutputStream(uri)?.use { it.write(pendingCsv.toByteArray(Charsets.UTF_8)) }
                web.evaluateJavascript("alert('CSV report exported successfully.')", null)
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
    private var pendingCsv: String = ""

    override fun onBackPressed() {
        if (!::web.isInitialized) {
            super.onBackPressed()
            return
        }
        web.evaluateJavascript(
            "(function(){return typeof window.handleAndroidBack === 'function' ? window.handleAndroidBack() : false;})()"
        ) { result ->
            if (result != "true") {
                super@MainActivity.onBackPressed()
            }
        }
    }
    inner class SheetsBridge {
        @JavascriptInterface fun hapticFeedback(kind: String) {
            runOnUiThread {
                try {
                    val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                    if (!vibrator.hasVibrator()) return@runOnUiThread
                    val duration = when (kind) {
                        "add" -> 30L
                        "update" -> 45L
                        "delete" -> 70L
                        else -> 35L
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration)
                    }
                } catch (_: Exception) { }
            }
        }
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
        @JavascriptInterface fun exportCSV(csv: String, filename: String) {
            runOnUiThread {
                pendingCsv = csv
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, filename)
                }
                startActivityForResult(intent, CREATE_CSV)
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

        @JavascriptInterface fun printReport(html: String) {
            runOnUiThread {
                if (printInProgress) {
                    web.evaluateJavascript("alert('A PDF export is already in progress. Please finish or cancel it first.')", null)
                    return@runOnUiThread
                }
                printInProgress = true
                try {
                    val printView = WebView(this@MainActivity)
                    reportWebView = printView
                    printView.settings.javaScriptEnabled = false
                    printView.settings.domStorageEnabled = false
                    printView.setBackgroundColor(android.graphics.Color.WHITE)
                    var printStarted = false
                    printView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (printStarted) return
                            printStarted = true
                            // Wait until WebView has committed its visual state before handing
                            // the document to Android's print spooler. This avoids intermittent
                            // empty/zero-byte PDF output on slower devices.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                view.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                                    override fun onComplete(requestId: Long) {
                                        view.postDelayed({ startReportPrint(view) }, 350L)
                                    }
                                })
                            } else {
                                view.postDelayed({ startReportPrint(view) }, 700L)
                            }
                        }
                    }
                    printView.loadDataWithBaseURL(
                        "file:///android_asset/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                } catch (e: Exception) {
                    printInProgress = false
                    val msg = org.json.JSONObject.quote(e.message ?: "Could not prepare PDF report")
                    web.evaluateJavascript("alert('PDF export error: '+$msg)", null)
                }
            }
        }

        private fun startReportPrint(view: WebView) {
            if (reportWebView !== view) return
            try {
                val manager = getSystemService(PRINT_SERVICE) as PrintManager
                val adapter = view.createPrintDocumentAdapter("Meal Manager Monthly Report")
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A3.asLandscape())
                    .setResolution(PrintAttributes.Resolution("pdf", "PDF", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                val job = manager.print(
                    "Meal-Manager-${System.currentTimeMillis()}",
                    adapter,
                    attributes
                )
                activePrintJob = job
                watchReportPrintJob(job, view)
            } catch (e: Exception) {
                printInProgress = false
                val msg = org.json.JSONObject.quote(e.message ?: "Could not start PDF export")
                web.evaluateJavascript("alert('PDF export error: '+$msg)", null)
            }
        }

        private fun watchReportPrintJob(job: android.print.PrintJob, view: WebView) {
            printHandler.postDelayed({
                if (activePrintJob !== job) return@postDelayed
                if (job.isCompleted || job.isCancelled || job.isFailed) {
                    activePrintJob = null
                    printInProgress = false
                    // Keep the WebView alive until Android has finished rendering the PDF.
                    view.postDelayed({
                        if (reportWebView === view) {
                            view.destroy()
                            reportWebView = null
                        }
                    }, 1500L)
                } else {
                    watchReportPrintJob(job, view)
                }
            }, 1000L)
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
