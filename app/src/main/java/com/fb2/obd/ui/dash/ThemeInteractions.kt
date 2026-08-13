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
 * Classic Dash gesture contract:
 * - double-tap → remap / change value
 * - triple-tap → deep search
 * - long-press → threshold editor
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
            taps = if (now - lastTapMs < 520L) taps + 1 else 1
            lastTapMs = now
            pendingRemap?.cancel()
            when {
                taps >= 3 -> {
                    taps = 0
                    onDeepSearch?.invoke()
                }
                taps == 2 && onRemap != null -> {
                    pendingRemap = scope.launch {
                        delay(280)
                        if (taps == 2) {
                            taps = 0
                            onRemap()
                        }
                    }
                }
            }
        },
        onLongClick = {
            // Prefer deep-search on hold when available (user request); else thresholds.
            when {
                onDeepSearch != null -> onDeepSearch()
                onEditThresholds != null -> onEditThresholds()
            }
        },
    )
}

fun DashThemeMetric.interaction(
    onRemapBase: (String) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
    onEdit: (EditableMetric) -> Unit,
): Triple<() -> Unit, () -> Unit, (() -> Unit)?> = Triple(
    { onRemapBase(label) },
    { onDeepSearch(label, pidRequest) },
    editMetric?.let { e -> { onEdit(e) } },
)
