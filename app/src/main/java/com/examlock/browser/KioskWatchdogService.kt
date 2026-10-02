package com.examlock.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class KioskWatchdogService : Service() {

    companion object {
        const val ACTION_ACTIVITY_PAUSED = "com.examlock.browser.ACTIVITY_PAUSED"
        private const val CHANNEL_ID = "exam_lock_watchdog"
        private const val NOTIF_ID = 1001
    }

    private val handler = Handler(Looper.getMainLooper())

    private val pauseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_ACTIVITY_PAUSED) {
                handler.postDelayed({ bringExamActivityToFront() }, 400)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(pauseReceiver, IntentFilter(ACTION_ACTIVITY_PAUSED), RECEIVER_NOT_EXPORTED_COMPAT)
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun bringExamActivityToFront() {
        val i = Intent(this, MainActivity::class.java)
        i.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        startActivity(i)
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mode Ujian Aktif",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mode Ujian Aktif")
            .setContentText("Perangkat terkunci pada aplikasi ujian.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pauseReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private val RECEIVER_NOT_EXPORTED_COMPAT: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
