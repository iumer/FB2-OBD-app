package com.fb2.obd.ui.dash

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fb2.obd.obd.EditableMetric
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Classic + Opt theme Dash gesture contract (see [ThemeGestureLogic]):
 * - double-tap → remap / change value
 * - triple-tap → deep search
 * - long-press → threshold editor when available, else deep search
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.themeMetricGestures(
    onRemap: (() -> Unit)? = null,
    onDeepSearch: (() -> Unit)? = null,
    onEditThresholds: (() -> Unit)? = null,
): Modifier {
    var taps by remember { mutableIntStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    var pendingRemap by remember { mutableStateOf<Job?>(null) }

    return this.combinedClickable(
        onClick = {
            val now = System.currentTimeMillis()
            val result = ThemeGestureLogic.onTap(
                previousTaps = taps,
                lastTapMs = lastTapMs,
                nowMs = now,
                hasRemap = onRemap != null,
            )
            taps = result.taps
            lastTapMs = now
            pendingRemap?.cancel()
            when (result.action) {
                ThemeGestureLogic.TapAction.DEEP_SEARCH -> onDeepSearch?.invoke()
                ThemeGestureLogic.TapAction.SCHEDULE_REMAP -> {
                    val delayMs = result.confirmRemapAfterMs ?: ThemeGestureLogic.REMAP_CONFIRM_DELAY_MS
                    pendingRemap = scope.launch {
                        delay(delayMs)
                        if (ThemeGestureLogic.confirmRemap(taps)) {
                            taps = 0
                            onRemap?.invoke()
                        }
                    }
                }
                ThemeGestureLogic.TapAction.NONE -> Unit
            }
        },
        onLongClick = {
            when (ThemeGestureLogic.onHold(onDeepSearch != null, onEditThresholds != null)) {
                ThemeGestureLogic.HoldAction.DEEP_SEARCH -> onDeepSearch?.invoke()
                ThemeGestureLogic.HoldAction.EDIT_THRESHOLDS -> onEditThresholds?.invoke()
                ThemeGestureLogic.HoldAction.NONE -> Unit
            }
        },
    )
}

fun DashThemeMetric.interaction(
    onRemapBase: (String) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
    onEdit: (EditableMetric) -> Unit,
): Triple<() -> Unit, () -> Unit, (() -> Unit)?> {
    val remapKey = remapBaseLabel ?: label
    return Triple(
        { onRemapBase(remapKey) },
        { onDeepSearch(label, pidRequest) },
        editMetric?.let { e -> { onEdit(e) } },
    )
}
