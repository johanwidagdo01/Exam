package com.examlock.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var prefs: SharedPreferences

    private var violationCount = 0
    private var examUrl: String = ""
    private var allowedHost: String = ""

    companion object {
        private const val TAG = "ExamLock"
        const val PREFS_NAME = "exam_lock_prefs"
        const val KEY_EXAM_URL = "exam_url"
        const val KEY_ALLOWED_HOST = "allowed_host"
        const val KEY_EXIT_PIN = "exit_pin"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, ExamDeviceAdminReceiver::class.java)

        examUrl = prefs.getString(KEY_EXAM_URL, "") ?: ""
        allowedHost = prefs.getString(KEY_ALLOWED_HOST, "") ?: ""

        if (examUrl.isEmpty()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        applySecureWindowFlags()
        setupImmersiveMode()
        setContentView(buildWebViewLayout())
        setupWebView()
        enableDeviceOwnerRestrictionsIfAvailable()
        enterLockTaskMode()

        startService(Intent(this, KioskWatchdogService::class.java))
    }

    private fun applySecureWindowFlags() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
    }

    private fun buildWebViewLayout(): View {
        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return webView
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.saveFormData = false
        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE

        WebView.setWebContentsDebuggingEnabled(false)

        webView.isLongClickable = false
        webView.setOnLongClickListener { true }
        webView.isHapticFeedbackEnabled = false

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?
            ): Boolean = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                val host = request?.url?.host ?: return true
                return if (isHostAllowed(host)) {
                    false
                } else {
                    logViolation("Percobaan navigasi keluar domain ujian: $host")
                    true
                }
            }
        }

        webView.loadUrl(examUrl)
    }

    private fun isHostAllowed(host: String): Boolean {
        if (allowedHost.isEmpty()) return true
        return host == allowedHost || host.endsWith(".$allowedHost")
    }

    private fun enableDeviceOwnerRestrictionsIfAvailable() {
        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var features = DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                features = features or DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                dpm.setLockTaskFeatures(adminComponent, features)
            }

            dpm.setCameraDisabled(adminComponent, true)

            Log.i(TAG, "Device Owner terdeteksi: proteksi lockdown penuh diaktifkan.")
        } else {
            Log.w(
                TAG,
                "Aplikasi belum menjadi Device Owner. Hanya screen-pinning standar " +
                    "yang akan aktif (proteksi terbatas). Lihat README.md."
            )
        }
    }

    private fun enterLockTaskMode() {
        try {
            startLockTask()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai lock task mode: ${e.message}")
            Toast.makeText(
                this,
                "Gagal mengaktifkan mode terkunci. Hubungi proktor ujian.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onBackPressed() {
        logViolation("Tombol Back ditekan (diabaikan).")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU -> {
                logViolation("Percobaan menekan tombol sistem (code=$keyCode).")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        logViolation("Terdeteksi upaya berpindah aplikasi (onUserLeaveHint).")
    }

    override fun onPause() {
        super.onPause()
        val i = Intent(KioskWatchdogService.ACTION_ACTIVITY_PAUSED)
        i.setPackage(packageName)
        sendBroadcast(i)
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
    }

    private fun logViolation(message: String) {
        violationCount++
        Log.w(TAG, "[Pelanggaran #$violationCount] $message")
    }

    private fun showExitDialog() {
        val input = EditText(this)
        input.hint = "Masukkan PIN proktor"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()
        container.setPadding(pad, pad, pad, pad)
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Keluar dari Mode Ujian")
            .setView(container)
            .setPositiveButton("Keluar") { _, _ ->
                val correctPin = prefs.getString(KEY_EXIT_PIN, "0000")
                if (input.text.toString() == correctPin) {
                    stopLockTaskSafely()
                    finish()
                } else {
                    Toast.makeText(this, "PIN salah.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun stopLockTaskSafely() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            Log.e(TAG, "stopLockTask gagal: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, KioskWatchdogService::class.java))
    }
}
