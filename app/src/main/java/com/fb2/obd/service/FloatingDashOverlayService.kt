package com.fb2.obd.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.fb2.obd.MainActivity
import com.fb2.obd.car.CarDashState
import com.fb2.obd.car.FloatingDashMetrics
import com.fb2.obd.car.VehicleLiveStore
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
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_COLLAPSE -> setExpanded(false)
            ACTION_EXPAND -> setExpanded(true)
        }
        // Do not restart after swipe-away / process kill — MIN is intentional only
        // while the user wants the bubble; Exit must fully dismiss it.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(autoCollapse)
        collectJob?.cancel()
        scope.cancel()
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "Floating dash overlay destroyed")
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOverlay() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).roundToInt()

        val collapsed = dp(COLLAPSED_DP)
        val satSize = dp(SAT_DP)
        val centerSize = dp(CENTER_DP)

        val container = FrameLayout(this)

        center = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(centerSize, centerSize)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
            background = circleDrawable(COLOR_ACCENT, fillAlpha = 230)
            elevation = dp(6).toFloat()
        }

        repeat(FloatingDashMetrics.PAGE_SIZE) {
            val sat = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(satSize, satSize)
                visibility = View.GONE
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(dp(4), dp(6), dp(4), dp(6))
                background = circleDrawable(COLOR_ACCENT, fillAlpha = 210)
                elevation = dp(4).toFloat()
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
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setBackgroundColor(Color.argb(160, 11, 15, 20))
        }
        container.addView(pageHint)
        container.addView(center) // on top for taps

        val lp = WindowManager.LayoutParams(
            collapsed,
            collapsed,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, dp(24))
            y = prefs.getInt(KEY_Y, dp(120))
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var mode = TouchMode.NONE
        var longPressFired = false

        // OnTouchListener consumes events, so long-press must be scheduled here
        // (View.setOnLongClickListener will not fire).
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
            lp.x = (startX + dx).coerceAtLeast(0)
            lp.y = (startY + dy).coerceAtLeast(0)
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
                        TouchMode.PAGE -> { /* wait for UP */ }
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
                            // Tap center / ring: toggle expand/collapse
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
        windowManager.addView(container, lp)
        metrics = FloatingDashMetrics.from(VehicleLiveStore.dash.value)
        latest = VehicleLiveStore.dash.value
        render()
    }

    private fun render() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).roundToInt()
        val container = root ?: return
        val lp = params ?: return

        val collapsed = dp(COLLAPSED_DP)
        val expandedSize = dp(EXPANDED_DP)
        val satSize = dp(SAT_DP)
        val centerSize = dp(CENTER_DP)
        val radius = dp(RADIUS_DP)

        if (metrics.isEmpty()) {
            metrics = listOf(FloatingDashMetrics.Metric("Dash", "--", "", null, "WAITING"))
        }
        val page = FloatingDashMetrics.page(metrics, pageIndex)
        val worst = FloatingDashMetrics.worstHealth(metrics.mapNotNull { it.health })
        val rim = healthColor(worst)

        // Center always visible
        center.layoutParams = FrameLayout.LayoutParams(centerSize, centerSize).apply {
            gravity = Gravity.CENTER
        }
        center.text = if (expanded) {
            "FB2\n${pageIndex + 1}/${FloatingDashMetrics.pageCount(metrics)}"
        } else {
            val first = page.firstOrNull() ?: metrics.first()
            "${first.label.take(4).uppercase()}\n${first.value}"
        }
        center.background = circleDrawable(rim, fillAlpha = 235)

        if (expanded) {
            lp.width = expandedSize
            lp.height = expandedSize
            pageHint?.visibility = View.VISIBLE
            pageHint?.text = "↕ scroll · tap center to collapse"
            // Place satellites in a pentagon around center
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
                        append(m.label.take(7).uppercase())
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
            lp.width = collapsed
            lp.height = collapsed
            pageHint?.visibility = View.GONE
            satellites.forEach { it.visibility = View.GONE }
        }

        runCatching { windowManager.updateViewLayout(container, lp) }
        container.requestLayout()
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
        val delta = (dp(EXPANDED_DP) - dp(COLLAPSED_DP)) / 2
        if (value) {
            lp.x = (lp.x - delta).coerceAtLeast(0)
            lp.y = (lp.y - delta).coerceAtLeast(0)
        } else {
            lp.x += delta
            lp.y += delta
        }
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
        // Long-press means "I'm back in the full app" — remove the bubble.
        stopSelf()
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
            setStroke((3 * resources.displayMetrics.density).roundToInt(), color)
        }

    private enum class TouchMode { NONE, DRAG, PAGE }

    companion object {
        private const val PREFS = "floating_dash"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        private const val COLLAPSED_DP = 56
        private const val CENTER_DP = 56
        private const val SAT_DP = 68
        private const val EXPANDED_DP = 280
        private const val RADIUS_DP = 96
        private const val AUTO_COLLAPSE_MS = 6_000L
        private const val LONG_PRESS_MS = 520L

        const val ACTION_STOP = "com.fb2.obd.action.STOP_FLOATING_DASH"
        const val ACTION_EXPAND = "com.fb2.obd.action.EXPAND_FLOATING_DASH"
        const val ACTION_COLLAPSE = "com.fb2.obd.action.COLLAPSE_FLOATING_DASH"

        private val COLOR_ACCENT = Color.parseColor("#00E5FF")
        private val COLOR_GOOD = Color.parseColor("#29D07B")
        private val COLOR_WARN = Color.parseColor("#FFB300")
        private val COLOR_HOT = Color.parseColor("#FF8A3D")
        private val COLOR_CRIT = Color.parseColor("#FF4D4D")
        private val COLOR_COLD = Color.parseColor("#4DA3FF")
        private val COLOR_MUTED = Color.parseColor("#7A8A99")

        fun startOverlay(context: Context) {
            context.applicationContext.startService(
                Intent(context, FloatingDashOverlayService::class.java),
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context, FloatingDashOverlayService::class.java),
            )
        }

        fun isOverlayAllowed(context: Context): Boolean =
            android.provider.Settings.canDrawOverlays(context)
    }
}
