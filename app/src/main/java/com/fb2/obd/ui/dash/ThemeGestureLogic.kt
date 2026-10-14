package com.fb2.obd.ui.dash

/**
 * Pure gesture state machine for Dash metric interactions.
 * Shared by Classic tiles and OptA/B/C themes.
 *
 * Contract:
 * - double-tap → remap
 * - triple-tap → ignored (deep search removed — use full-screen sensor picker)
 * - long-press → threshold editor when available
 */
object ThemeGestureLogic {
    const val TAP_WINDOW_MS = 520L
    const val REMAP_CONFIRM_DELAY_MS = 280L

    enum class TapAction { NONE, SCHEDULE_REMAP }

    data class TapResult(
        val taps: Int,
        val action: TapAction,
        /** When [action] is [TapAction.SCHEDULE_REMAP], fire remap only if taps still == 2 after delay. */
        val confirmRemapAfterMs: Long? = null,
    )

    fun onTap(
        previousTaps: Int,
        lastTapMs: Long,
        nowMs: Long,
        hasRemap: Boolean,
    ): TapResult {
        val taps = if (nowMs - lastTapMs < TAP_WINDOW_MS) previousTaps + 1 else 1
        return when {
            taps >= 3 -> TapResult(taps = 0, action = TapAction.NONE)
            taps == 2 && hasRemap -> TapResult(
                taps = taps,
                action = TapAction.SCHEDULE_REMAP,
                confirmRemapAfterMs = REMAP_CONFIRM_DELAY_MS,
            )
            else -> TapResult(taps = taps, action = TapAction.NONE)
        }
    }

    enum class HoldAction { EDIT_THRESHOLDS, NONE }

    fun onHold(hasEditThresholds: Boolean): HoldAction = when {
        hasEditThresholds -> HoldAction.EDIT_THRESHOLDS
        else -> HoldAction.NONE
    }

    /** After [REMAP_CONFIRM_DELAY_MS], remap only if a third tap did not arrive. */
    fun confirmRemap(currentTaps: Int): Boolean = currentTaps == 2
}
