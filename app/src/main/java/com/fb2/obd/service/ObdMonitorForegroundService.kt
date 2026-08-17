package com.fb2.obd.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fb2.obd.MainActivity
import com.fb2.obd.R
import com.fb2.obd.data.ObdLogger

/**
 * Minimal foreground service that keeps the process (and CPU via a partial wake
 * lock) alive while a real ELM327 session is active, so [com.fb2.obd.data.VoiceAlerter]
 * can still speak with the screen off / app backgrounded.
 *
 * Does **not** own the OBD poll loop — [com.fb2.obd.DashboardViewModel] continues
 * collecting snapshots; this service only holds the FGS notification + wake lock.
 */
class ObdMonitorForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        acquireWakeLock()
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "ObdMonitor FGS created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val status = intent?.getStringExtra(EXTRA_STATUS) ?: STATUS_CONNECTED
        promoteToForeground(status)
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "ObdMonitor FGS destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(status: String) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(status: String): Notification {
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FB2 Diag — $status")
            .setContentText("Monitoring ELM327 · voice alerts active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ELM monitor",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps FB2 Diag alive while the ELM327 is connected"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "fb2obd:elm_monitor",
        ).also {
            it.setReferenceCounted(false)
            it.acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "obd_monitor"
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_STATUS = "status"

        const val STATUS_CONNECTED = "ELM connected"
        const val STATUS_LIVE = "LIVE"
        const val STATUS_RETRY = "RETRY…"

        fun start(context: Context, status: String = STATUS_CONNECTED) {
            val intent = Intent(context, ObdMonitorForegroundService::class.java).apply {
                putExtra(EXTRA_STATUS, status)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun updateStatus(context: Context, status: String) {
            start(context, status)
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context, ObdMonitorForegroundService::class.java),
            )
        }
    }
}
