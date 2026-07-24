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
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.fb2.obd.MainActivity
import com.fb2.obd.car.CarDashState
import com.fb2.obd.car.CarDashTile
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
import kotlin.math.roundToInt

/**
 * System overlay "floating dash" bubble for Dellson / Android HU use:
 * - Collapsed: draggable colored dot that keeps updating from [VehicleLiveStore]
 * - Expanded: swipeable circular list of main-Dash metrics with health colors
 *
 * Requires [android.Manifest.permission.SYSTEM_ALERT_WINDOW] (draw over other apps).
 */
class FloatingDashOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private var expanded = false
    private var metricIndex = 0
    private var latest: CarDashState = CarDashState()
    private var metrics: List<OverlayMetric> = emptyList()

    // Collapsed views
    private lateinit var bubble: TextView
    // Expanded views
    private lateinit var panel: LinearLayout
    private lateinit var labelView: TextView
    private lateinit var valueView: TextView
    private lateinit var statusView: TextView
    private lateinit var indexView: TextView

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
                metrics = buildMetrics(state)
                if (metricIndex >= metrics.size) metricIndex = 0
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
            ACTION_COLLAPSE -> {
                expanded = false
                render()
            }
            ACTION_EXPAND -> {
                expanded = true
                render()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
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

        val container = FrameLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        bubble = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56))
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
            background = circleDrawable(COLOR_ACCENT)
        }

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundRectDrawable(COLOR_SURFACE, dp(16))
            elevation = dp(8).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(220), FrameLayout.LayoutParams.WRAP_CONTENT)
            minimumWidth = dp(200)
        }

        labelView = TextView(this).apply {
            setTextColor(COLOR_MUTED)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        valueView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(2))
        }
        statusView = TextView(this).apply {
            setTextColor(COLOR_GOOD)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        indexView = TextView(this).apply {
            setTextColor(COLOR_MUTED)
            textSize = 10f
            setPadding(0, dp(6), 0, 0)
        }
        val hint = TextView(this).apply {
            text = "swipe · tap bubble to collapse · hold OPEN"
            setTextColor(COLOR_MUTED)
            textSize = 9f
            setPadding(0, dp(4), 0, 0)
        }
        val openBtn = TextView(this).apply {
            text = "OPEN APP"
            setTextColor(COLOR_ACCENT)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, 0)
            setOnClickListener { openAppAndKeepOverlay() }
        }
        val closeBtn = TextView(this).apply {
            text = "CLOSE BUBBLE"
            setTextColor(COLOR_CRIT)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, 0)
            setOnClickListener { stopSelf() }
        }

        panel.addView(labelView)
        panel.addView(valueView)
        panel.addView(statusView)
        panel.addView(indexView)
        panel.addView(hint)
        panel.addView(openBtn)
        panel.addView(closeBtn)

        container.addView(bubble)
        container.addView(panel)

        val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, dp(24))
            y = prefs.getInt(KEY_Y, dp(120))
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var swipeStartX = 0f

        val touchListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    swipeStartX = event.rawX
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).roundToInt()
                    val dy = (event.rawY - downY).roundToInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    if (!expanded || moved) {
                        lp.x = (startX + dx).coerceAtLeast(0)
                        lp.y = (startY + dy).coerceAtLeast(0)
                        windowManager.updateViewLayout(container, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val swipeDx = event.rawX - swipeStartX
                    if (!moved) {
                        if (expanded) {
                            // tap empty → collapse handled on bubble; on panel cycle?
                            // bubble tap toggles
                        } else {
                            expanded = true
                            render()
                        }
                    } else if (expanded && abs(swipeDx) > dp(40) && abs(event.rawY - downY) < dp(40)) {
                        // horizontal swipe cycles metrics
                        if (swipeDx < 0) metricIndex = (metricIndex + 1) % metrics.size.coerceAtLeast(1)
                        else metricIndex = (metricIndex - 1 + metrics.size) % metrics.size.coerceAtLeast(1)
                        render()
                    }
                    prefs.edit().putInt(KEY_X, lp.x).putInt(KEY_Y, lp.y).apply()
                    true
                }
                else -> false
            }
        }

        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).roundToInt()
                    val dy = (event.rawY - downY).roundToInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    lp.x = (startX + dx).coerceAtLeast(0)
                    lp.y = (startY + dy).coerceAtLeast(0)
                    windowManager.updateViewLayout(container, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        expanded = !expanded
                        render()
                    }
                    prefs.edit().putInt(KEY_X, lp.x).putInt(KEY_Y, lp.y).apply()
                    true
                }
                else -> false
            }
        }

        panel.setOnTouchListener(touchListener)

        root = container
        params = lp
        windowManager.addView(container, lp)
        metrics = buildMetrics(VehicleLiveStore.dash.value)
        latest = VehicleLiveStore.dash.value
        render()
    }

    private fun render() {
        if (metrics.isEmpty()) {
            metrics = listOf(OverlayMetric("Dash", "--", "", null, "WAITING"))
        }
        val m = metrics[metricIndex.coerceIn(0, metrics.lastIndex)]
        val color = healthColor(m.health)

        if (expanded) {
            bubble.visibility = View.GONE
            panel.visibility = View.VISIBLE
            labelView.text = m.label.uppercase()
            valueView.text = buildString {
                append(m.value)
                if (m.unit.isNotBlank()) {
                    append(' ')
                    append(m.unit)
                }
            }
            valueView.setTextColor(color)
            statusView.text = m.status ?: m.health ?: "—"
            statusView.setTextColor(color)
            indexView.text = "${metricIndex + 1} / ${metrics.size}  ·  ${latest.statusLine}"
            panel.background = roundRectDrawable(COLOR_SURFACE, (16 * resources.displayMetrics.density).roundToInt())
                .also { (it as GradientDrawable).setStroke((2 * resources.displayMetrics.density).roundToInt(), color) }
        } else {
            panel.visibility = View.GONE
            bubble.visibility = View.VISIBLE
            val shortLabel = m.label.take(4).uppercase()
            bubble.text = "$shortLabel\n${m.value}"
            bubble.background = circleDrawable(color)
        }
    }

    private fun openAppAndKeepOverlay() {
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(launch)
        expanded = false
        render()
    }

    private fun buildMetrics(state: CarDashState): List<OverlayMetric> {
        val hero = listOf(
            OverlayMetric("RPM", state.rpm, "", heroHealth(state), null),
            OverlayMetric("Speed", state.speedKmh, "km/h", heroHealth(state), null),
            OverlayMetric("Gear", state.gear, state.gearBadge, heroHealth(state), null),
        )
        val tiles = state.tiles.map { it.toMetric() }
        return hero + tiles
    }

    private fun CarDashTile.toMetric() = OverlayMetric(label, value, unit, health, status)

    private fun heroHealth(state: CarDashState): String? {
        // Bubble rim uses worst tile health so criticals stand out even on RPM view.
        val order = listOf("CRITICAL", "ELEVATED", "WARN", "COLD", "GOOD", "UNKNOWN")
        return state.tiles.mapNotNull { it.health }.minByOrNull { order.indexOf(it).let { i -> if (i < 0) 99 else i } }
    }

    private fun healthColor(health: String?): Int = when (health) {
        "CRITICAL" -> COLOR_CRIT
        "ELEVATED" -> COLOR_HOT
        "WARN" -> COLOR_WARN
        "COLD" -> COLOR_COLD
        "GOOD" -> COLOR_GOOD
        else -> COLOR_ACCENT
    }

    private fun circleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(230, 20, 27, 34))
            setStroke((3 * resources.displayMetrics.density).roundToInt(), color)
        }

    private fun roundRectDrawable(fill: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(fill)
        }

    private data class OverlayMetric(
        val label: String,
        val value: String,
        val unit: String,
        val health: String?,
        val status: String?,
    )

    companion object {
        private const val PREFS = "floating_dash"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        const val ACTION_STOP = "com.fb2.obd.action.STOP_FLOATING_DASH"
        const val ACTION_EXPAND = "com.fb2.obd.action.EXPAND_FLOATING_DASH"
        const val ACTION_COLLAPSE = "com.fb2.obd.action.COLLAPSE_FLOATING_DASH"

        // Match Theme.kt
        private val COLOR_ACCENT = Color.parseColor("#00E5FF")
        private val COLOR_SURFACE = Color.parseColor("#E6141B22")
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
