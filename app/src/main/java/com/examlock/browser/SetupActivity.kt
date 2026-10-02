package com.examlock.browser

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SetupActivity : Activity() {

    private lateinit var urlInput: EditText
    private lateinit var hostInput: EditText
    private lateinit var pinInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val pad = (24 * dp).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)
        root.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this)
        title.text = getString(R.string.setup_title)
        title.textSize = 20f
        root.addView(title)

        urlInput = EditText(this)
        urlInput.hint = "URL ujian, mis: https://ujian.sekolah.id/exam"
        root.addView(urlInput)

        hostInput = EditText(this)
        hostInput.hint = "Domain yang diizinkan, mis: ujian.sekolah.id"
        root.addView(hostInput)

        pinInput = EditText(this)
        pinInput.hint = "PIN keluar darurat (4 digit)"
        pinInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        root.addView(pinInput)

        val requestAdminBtn = Button(this)
        requestAdminBtn.text = "Aktifkan Device Admin"
        requestAdminBtn.setOnClickListener { requestDeviceAdmin() }
        root.addView(requestAdminBtn)

        val saveBtn = Button(this)
        saveBtn.text = "Simpan & Mulai Mode Ujian"
        saveBtn.setOnClickListener { saveAndStart() }
        root.addView(saveBtn)

        setContentView(root)
    }

    private fun requestDeviceAdmin() {
        val adminComponent = ComponentName(this, ExamDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Diperlukan agar mode kunci ujian dapat membatasi kamera, keyguard, " +
                "dan fitur perangkat lain selama ujian berlangsung."
        )
        startActivity(intent)
    }

    private fun saveAndStart() {
        val url = urlInput.text.toString().trim()
        val host = hostInput.text.toString().trim()
        val pin = pinInput.text.toString().trim()

        if (url.isEmpty() || !Patterns.WEB_URL.matcher(url).matches()) {
            Toast.makeText(this, "URL ujian tidak valid.", Toast.LENGTH_SHORT).show()
            return
        }
        if (pin.length != 4) {
            Toast.makeText(this, "PIN harus 4 digit.", Toast.LENGTH_SHORT).show()
            return
        }

        val resolvedHost = host.ifEmpty { Uri.parse(url).host ?: "" }

        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE).edit()
            .putString(MainActivity.KEY_EXAM_URL, url)
            .putString(MainActivity.KEY_ALLOWED_HOST, resolvedHost)
            .putString(MainActivity.KEY_EXIT_PIN, pin)
            .apply()

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
