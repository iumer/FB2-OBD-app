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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.fb2.obd.DashboardViewModel
import com.fb2.obd.Fb2App
import com.fb2.obd.MainActivity
import com.fb2.obd.R
import com.fb2.obd.data.ObdLogger

/**
 * Foreground service that keeps the process (and CPU via a partial wake lock)
 * alive while a real ELM327 session is active.
 *
 * Does **not** own the OBD poll loop — [DashboardViewModel] (process-scoped via
 * [Fb2App]) collects snapshots. This service holds the FGS notification + wake
 * lock and reconnects after OEM process death (Nakamichi RAM reclaim).
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
        // Sticky restart (intent == null) or explicit keep-alive after swipe-away.
        val reconnect = intent == null || intent.getBooleanExtra(EXTRA_RECONNECT, false)
        if (reconnect) {
            Handler(Looper.getMainLooper()).post {
                (application as? Fb2App)?.let { app ->
                    ViewModelProvider(app as ViewModelStoreOwner)[DashboardViewModel::class.java]
                        .reconnectLastElmIfIdle()
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "ObdMonitor FGS onTaskRemoved — restarting keep-alive")
        val restart = Intent(applicationContext, ObdMonitorForegroundService::class.java).apply {
            putExtra(EXTRA_STATUS, STATUS_LIVE)
            putExtra(EXTRA_RECONNECT, true)
        }
        // Swiping the app from recents leaves us in the background, where a
        // restart can be refused (ForegroundServiceStartNotAllowedException).
        // Losing keep-alive is acceptable; crashing the process is not.
        runCatching {
            ContextCompat.startForegroundService(applicationContext, restart)
        }.onFailure { e ->
            ObdLogger.logDebug(
                ObdLogger.Dir.INFO,
                "ObdMonitor onTaskRemoved restart refused: ${e.message}",
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "ObdMonitor FGS destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(status: String) {
        val notification = buildNotification(status)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { e ->
            ObdLogger.logDebug(
                ObdLogger.Dir.INFO,
                "ObdMonitor FGS start failed: ${e.message}",
            )
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
            .setContentText("Live ELM logging — leave this notification on")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(NOTIFICATION_GROUP)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ELM monitor",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Keeps FB2 Diag logging while the ELM327 is connected"
            setShowBadge(false)
            setGroup(NOTIFICATION_GROUP)
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
        private const val NOTIFICATION_GROUP = "fb2_diag_session"

        const val EXTRA_STATUS = "status"
        const val EXTRA_RECONNECT = "reconnect"

        const val STATUS_CONNECTED = "ELM connected"
        const val STATUS_LIVE = "LIVE"
        const val STATUS_RETRY = "RETRY…"

        fun start(context: Context, status: String = STATUS_CONNECTED) {
            val intent = Intent(context, ObdMonitorForegroundService::class.java).apply {
                putExtra(EXTRA_STATUS, status)
            }
            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }.onFailure { e ->
                ObdLogger.logDebug(
                    ObdLogger.Dir.INFO,
                    "ObdMonitor startForegroundService failed: ${e.message}",
                )
            }
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
