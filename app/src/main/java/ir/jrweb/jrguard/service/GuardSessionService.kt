package ir.jrweb.jrguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import ir.jrweb.jrguard.R
import ir.jrweb.jrguard.data.GuardStorage
import ir.jrweb.jrguard.data.UnlockSession
import java.util.UUID

class GuardSessionService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var unlockTimeMs: Long = 0L
    private lateinit var sessionId: String

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        unlockTimeMs = intent?.getLongExtra("unlock_time", System.currentTimeMillis())
            ?: System.currentTimeMillis()
        sessionId = UUID.randomUUID().toString()

        val notification = buildNotification()
        startForeground(1042, notification)

        // ثبت اولیه جلسه بلافاصله پس از باز شدن قفل
        val initialSession = UnlockSession(
            id = sessionId,
            timestampMs = unlockTimeMs,
            formattedTime = GuardStorage.formatTimestamp(unlockTimeMs),
            photoPath = null,
            colleagueTag = "بررسی‌نشده",
            visits = emptyList()
        )
        GuardStorage.saveSession(this, initialSession)

        // به‌روزرسانی لیست برنامه‌های بازدیدشده پس از ۲۰ ثانیه و سپس ۶۰ ثانیه
        handler.postDelayed({ flushVisits() }, 20_000L)
        handler.postDelayed({
            flushVisits()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, 60_000L)

        return START_NOT_STICKY
    }

    private fun flushVisits() {
        val now = System.currentTimeMillis()
        val visits = GuardStorage.collectRecentAppVisits(this, unlockTimeMs - 3000L, now)
        val existing = GuardStorage.loadSessions(this).find { it.id == sessionId }
        val updated = UnlockSession(
            id = sessionId,
            timestampMs = unlockTimeMs,
            formattedTime = GuardStorage.formatTimestamp(unlockTimeMs),
            photoPath = existing?.photoPath,
            colleagueTag = existing?.colleagueTag ?: "بررسی‌نشده",
            visits = visits
        )
        GuardStorage.saveSession(this, updated)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "jrguard_channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_desc))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jrguard_channel",
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
