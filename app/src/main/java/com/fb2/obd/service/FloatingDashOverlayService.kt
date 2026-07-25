package com.fb2.obd.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fb2.obd.MainActivity
import com.fb2.obd.R
import com.fb2.obd.car.CarDashState
import com.fb2.obd.car.FloatingDashLayout
import com.fb2.obd.car.FloatingDashMetrics
import com.fb2.obd.car.VehicleLiveStore
import kotlin.math.min
import com.fb2.obd.data.ObdLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Floating OBD overlay for Dellson / Android HU:
 * - Collapsed: one draggable circular button
 * - Expanded: same center button + up to 5 satellite circles around it
 * - Vertical swipe pages through remaining metrics (center stays fixed)
 * - Auto-collapses after idle timeout
 *
 * Runs as a **foreground service** so the bubble stays visible over Home /
 * CarPlay after MIN. Broadcasts [ACTION_READY] once the WindowManager view is
 * attached so MainActivity can background itself only after the bubble exists.
 *
 * Requires [android.Manifest.permission.SYSTEM_ALERT_WINDOW].
 */
class FloatingDashOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var collectJob: Job? = null

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var overlayAttached = false

    private var expanded = false
    private var pageIndex = 0
    private var latest: CarDashState = CarDashState()
    private var metrics: List<FloatingDashMetrics.Metric> = emptyList()

    private lateinit var center: TextView
    private val satellites = ArrayList<TextView>(FloatingDashMetrics.PAGE_SIZE)
    private var pageHint: TextView? = null

    private val autoCollapse = Runnable {
        if (expanded) {
            setExpanded(false)
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Floating dash auto-collapsed")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        ensureChannel()
        // Promote BEFORE addView so OEMs don't defer / kill us as we leave the app.
        promoteToForeground()
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "Floating dash overlay created")
        attachOverlay()
        collectJob = scope.launch {
            VehicleLiveStore.dash.collectLatest { state ->
                latest = state
                metrics = FloatingDashMetrics.from(state)
                val pages = FloatingDashMetrics.pageCount(metrics)
                if (pageIndex >= pages) pageIndex = 0
                render()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_COLLAPSE -> setExpanded(false)
            ACTION_EXPAND -> setExpanded(true)
            else -> {
                promoteToForeground()
                if (!overlayAttached) {
                    attachOverlay()
                } else {
                    ensureOnScreen()
                    render()
                }
                notifyReady()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(autoCollapse)
        collectJob?.cancel()
        scope.cancel()
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        overlayAttached = false
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "Floating dash overlay destroyed")
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOverlay() {
        if (overlayAttached && root != null) {
            ensureOnScreen()
            notifyReady()
            return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Floating dash: overlay permission missing")
            stopSelf()
            return
        }

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).roundToInt()

        val collapsed = dp(FloatingDashLayout.COLLAPSED_DP)
        val satSize = dp(FloatingDashLayout.SAT_DP)
        val centerSize = dp(FloatingDashLayout.CENTER_DP)

        val container = FrameLayout(this).apply {
            // Make the hit-target obvious even before first live metric paint.
            setBackgroundColor(Color.TRANSPARENT)
        }

        center = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(centerSize, centerSize).apply {
                gravity = Gravity.CENTER
            }
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            maxLines = 2
            background = circleDrawable(COLOR_ACCENT, fillAlpha = 230)
            elevation = dp(8).toFloat()
            text = "FB2\n…"
        }

        satellites.clear()
        repeat(FloatingDashMetrics.PAGE_SIZE) {
            val sat = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(satSize, satSize)
                visibility = View.GONE
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(dp(6), dp(8), dp(6), dp(8))
                maxLines = 3
                background = circleDrawable(COLOR_ACCENT, fillAlpha = 210)
                elevation = dp(6).toFloat()
            }
            satellites += sat
            container.addView(sat)
        }

        pageHint = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(4)
            }
            visibility = View.GONE
            setTextColor(COLOR_MUTED)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setBackgroundColor(Color.argb(180, 11, 15, 20))
        }
        container.addView(pageHint)
        container.addView(center)

        val lp = WindowManager.LayoutParams(
            collapsed,
            collapsed,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, dp(24))
            y = prefs.getInt(KEY_Y, dp(120))
            // Keep above most system UI chrome where OEMs allow it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        clampToScreen(lp)

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var mode = TouchMode.NONE
        var longPressFired = false

        val longPress = Runnable {
            if (!moved && mode == TouchMode.NONE) {
                longPressFired = true
                openAppAndDismissOverlay()
            }
        }

        fun applyDrag(event: MotionEvent) {
            val dx = (event.rawX - downRawX).roundToInt()
            val dy = (event.rawY - downRawY).roundToInt()
            if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
            lp.x = startX + dx
            lp.y = startY + dy
            clampToScreen(lp)
            windowManager.updateViewLayout(container, lp)
        }

        val touchListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    longPressFired = false
                    mode = TouchMode.NONE
                    bumpAutoCollapse()
                    mainHandler.removeCallbacks(longPress)
                    mainHandler.postDelayed(longPress, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moveDx = event.rawX - downRawX
                    val moveDy = event.rawY - downRawY
                    if (mode == TouchMode.NONE && (abs(moveDx) > dp(8) || abs(moveDy) > dp(8))) {
                        mode = when {
                            expanded && abs(moveDy) > abs(moveDx) * 1.2f -> TouchMode.PAGE
                            else -> TouchMode.DRAG
                        }
                        mainHandler.removeCallbacks(longPress)
                    }
                    when (mode) {
                        TouchMode.DRAG -> applyDrag(event)
                        TouchMode.PAGE -> Unit
                        TouchMode.NONE -> Unit
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPress)
                    val dy = event.rawY - downRawY
                    when {
                        longPressFired -> Unit
                        mode == TouchMode.PAGE && abs(dy) > dp(36) -> {
                            val pages = FloatingDashMetrics.pageCount(metrics)
                            if (dy < 0) pageIndex = (pageIndex + 1) % pages
                            else pageIndex = (pageIndex - 1 + pages) % pages
                            bumpAutoCollapse()
                            render()
                        }
                        !moved && event.actionMasked == MotionEvent.ACTION_UP -> {
                            setExpanded(!expanded)
                        }
                        mode == TouchMode.DRAG -> {
                            prefs.edit().putInt(KEY_X, lp.x).putInt(KEY_Y, lp.y).apply()
                        }
                    }
                    mode = TouchMode.NONE
                    true
                }
                else -> false
            }
        }

        center.setOnTouchListener(touchListener)
        container.setOnTouchListener(touchListener)
        satellites.forEach { it.setOnTouchListener(touchListener) }

        root = container
        params = lp
        val added = runCatching {
            windowManager.addView(container, lp)
            true
        }.onFailure { e ->
            ObdLogger.logDebug(
                ObdLogger.Dir.INFO,
                "Floating dash addView failed: ${e.message}",
            )
            root = null
            params = null
        }.getOrDefault(false)

        if (!added) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        overlayAttached = true
        metrics = FloatingDashMetrics.from(VehicleLiveStore.dash.value)
        latest = VehicleLiveStore.dash.value
        render()
        notifyReady()
        ObdLogger.logDebug(
            ObdLogger.Dir.INFO,
            "Floating dash attached at ${lp.x},${lp.y} size ${lp.width}x${lp.height}",
        )
    }

    /** Short screen edge in dp — used so the expanded ring never fills the HU. */
    private fun shortEdgeDp(): Int {
        val dm = resources.displayMetrics
        val shortPx = min(dm.widthPixels, dm.heightPixels)
        return (shortPx / dm.density).roundToInt().coerceAtLeast(1)
    }

    private fun expandedDp(): Int = FloatingDashLayout.expandedDp(shortEdgeDp())

    private fun radiusDp(): Int =
        FloatingDashLayout.radiusDp(expandedDp(), FloatingDashLayout.SAT_DP)

    private fun render() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).roundToInt()
        val container = root ?: return
        val lp = params ?: return

        val collapsedSize = dp(FloatingDashLayout.COLLAPSED_DP)
        val expandedSize = dp(expandedDp())
        val satSize = dp(FloatingDashLayout.SAT_DP)
        val centerSize = dp(FloatingDashLayout.CENTER_DP)
        val radius = dp(radiusDp())

        if (metrics.isEmpty()) {
            metrics = listOf(FloatingDashMetrics.Metric("Dash", "--", "", null, "WAITING"))
        }
        val page = FloatingDashMetrics.page(metrics, pageIndex)
        val primary = FloatingDashMetrics.collapsedMetric(metrics)
        // Collapsed rim follows coolant (what is shown). Expanded rim = worst of all.
        val rimHealth = if (expanded) {
            FloatingDashMetrics.worstHealth(metrics.mapNotNull { it.health })
        } else {
            primary.health
                ?: FloatingDashMetrics.worstHealth(metrics.mapNotNull { it.health })
        }
        val rim = healthColor(rimHealth)

        center.layoutParams = FrameLayout.LayoutParams(centerSize, centerSize).apply {
            gravity = Gravity.CENTER
        }
        center.text = if (expanded) {
            "FB2\n${pageIndex + 1}/${FloatingDashMetrics.pageCount(metrics)}"
        } else {
            val label = when {
                primary.label.contains("Coolant", ignoreCase = true) -> "COOL"
                else -> primary.label.take(4).uppercase()
            }
            val unit = primary.unit.trim().take(2)
            if (unit.isNotBlank()) {
                "$label\n${primary.value}$unit"
            } else {
                "$label\n${primary.value}"
            }
        }
        center.background = circleDrawable(rim, fillAlpha = 235)
        center.visibility = View.VISIBLE

        if (expanded) {
            lp.width = expandedSize
            lp.height = expandedSize
            pageHint?.visibility = View.VISIBLE
            pageHint?.text = "↕ scroll · tap center to collapse"
            val cx = (expandedSize - satSize) / 2f
            val cy = (expandedSize - satSize) / 2f
            for (i in 0 until FloatingDashMetrics.PAGE_SIZE) {
                val sat = satellites[i]
                if (i < page.size) {
                    val m = page[i]
                    val angleDeg = -90.0 + i * (360.0 / FloatingDashMetrics.PAGE_SIZE)
                    val rad = Math.toRadians(angleDeg)
                    val x = (cx + radius * cos(rad)).roundToInt()
                    val y = (cy + radius * sin(rad)).roundToInt()
                    sat.visibility = View.VISIBLE
                    sat.layoutParams = FrameLayout.LayoutParams(satSize, satSize).apply {
                        leftMargin = x
                        topMargin = y
                    }
                    val unit = m.unit.take(4)
                    sat.text = buildString {
                        append(m.label.take(8).uppercase())
                        append('\n')
                        append(m.value)
                        if (unit.isNotBlank()) {
                            append('\n')
                            append(unit)
                        }
                    }
                    sat.background = circleDrawable(healthColor(m.health), fillAlpha = 215)
                } else {
                    sat.visibility = View.GONE
                }
            }
        } else {
            lp.width = collapsedSize
            lp.height = collapsedSize
            pageHint?.visibility = View.GONE
            satellites.forEach { it.visibility = View.GONE }
        }

        clampToScreen(lp)
        runCatching { windowManager.updateViewLayout(container, lp) }
        container.visibility = View.VISIBLE
        container.requestLayout()
    }

    private fun ensureOnScreen() {
        val lp = params ?: return
        clampToScreen(lp)
        root?.let { runCatching { windowManager.updateViewLayout(it, lp) } }
    }

    private fun clampToScreen(lp: WindowManager.LayoutParams) {
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val dm = resources.displayMetrics
            android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        val maxX = (bounds.width() - lp.width).coerceAtLeast(0)
        val maxY = (bounds.height() - lp.height).coerceAtLeast(0)
        lp.x = lp.x.coerceIn(0, maxX)
        lp.y = lp.y.coerceIn(0, maxY)
    }

    private fun setExpanded(value: Boolean) {
        val lp = params ?: return
        if (expanded == value) {
            if (value) bumpAutoCollapse()
            render()
            return
        }
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).roundToInt()
        val delta = (dp(expandedDp()) - dp(FloatingDashLayout.COLLAPSED_DP)) / 2
        if (value) {
            lp.x -= delta
            lp.y -= delta
        } else {
            lp.x += delta
            lp.y += delta
        }
        clampToScreen(lp)
        expanded = value
        if (expanded) bumpAutoCollapse() else mainHandler.removeCallbacks(autoCollapse)
        render()
    }

    private fun bumpAutoCollapse() {
        mainHandler.removeCallbacks(autoCollapse)
        if (expanded) {
            mainHandler.postDelayed(autoCollapse, AUTO_COLLAPSE_MS)
        }
    }

    private fun openAppAndDismissOverlay() {
        mainHandler.removeCallbacks(autoCollapse)
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(launch)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyReady() {
        val ready = Intent(ACTION_READY).setPackage(packageName)
        sendBroadcast(ready)
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingDashOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FB2 floating Dash")
            .setContentText("Bubble is on — tap to open app, or Close to dismiss")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .addAction(0, "Close", stop)
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
            "Floating Dash",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the floating Dash bubble visible over other apps"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun healthColor(health: String?): Int = when (health) {
        "CRITICAL" -> COLOR_CRIT
        "ELEVATED" -> COLOR_HOT
        "WARN" -> COLOR_WARN
        "COLD" -> COLOR_COLD
        "GOOD" -> COLOR_GOOD
        else -> COLOR_ACCENT
    }

    private fun circleDrawable(color: Int, fillAlpha: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(fillAlpha, 20, 27, 34))
            setStroke((STROKE_DP * resources.displayMetrics.density).roundToInt(), color)
        }

    private enum class TouchMode { NONE, DRAG, PAGE }

    companion object {
        private const val PREFS = "floating_dash"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val CHANNEL_ID = "floating_dash"
        private const val NOTIFICATION_ID = 1002

        private const val AUTO_COLLAPSE_MS = 6_000L
        private const val LONG_PRESS_MS = 520L
        private const val STROKE_DP = 4

        const val ACTION_STOP = "com.fb2.obd.action.STOP_FLOATING_DASH"
        const val ACTION_EXPAND = "com.fb2.obd.action.EXPAND_FLOATING_DASH"
        const val ACTION_COLLAPSE = "com.fb2.obd.action.COLLAPSE_FLOATING_DASH"
        const val ACTION_READY = "com.fb2.obd.action.FLOATING_DASH_READY"

        private val COLOR_ACCENT = Color.parseColor("#00E5FF")
        private val COLOR_GOOD = Color.parseColor("#29D07B")
        private val COLOR_WARN = Color.parseColor("#FFB300")
        private val COLOR_HOT = Color.parseColor("#FF8A3D")
        private val COLOR_CRIT = Color.parseColor("#FF4D4D")
        private val COLOR_COLD = Color.parseColor("#4DA3FF")
        private val COLOR_MUTED = Color.parseColor("#7A8A99")

        fun startOverlay(context: Context) {
            val intent = Intent(context, FloatingDashOverlayService::class.java)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun stop(context: Context) {
            // Prefer explicit stop action so FGS tears down cleanly.
            val intent = Intent(context, FloatingDashOverlayService::class.java).setAction(ACTION_STOP)
            runCatching { context.applicationContext.startService(intent) }
            context.applicationContext.stopService(
                Intent(context, FloatingDashOverlayService::class.java),
            )
        }

        fun isOverlayAllowed(context: Context): Boolean =
            android.provider.Settings.canDrawOverlays(context)
    }
}
