package com.fb2.obd.obd

/**
 * Torque-style picker scan: Mode 01 only, one request at a time, advertised
 * PIDs first. Honda Mode 22 placeholders need ATSH and time out on cheap
 * clones — blasting them in batches of 6 holds the ELM mutex long enough
 * that Dash heroes TTL-blank (RPM/Speed/Coolant `--`) and remaining Mode 01
 * PIDs get marked SKIPPED / Readable shrinks.
 */
object PickerScanPlanner {

    /** After a timeout / NO DATA, give Dash enough time to send RPM/Speed/Coolant. */
    const val YIELD_AFTER_MISS_MS = 700L

    /** After a fast hit, still yield so heroes are not starved, but keep scan moving. */
    const val YIELD_AFTER_HIT_MS = 350L

    /** @deprecated same as [YIELD_AFTER_MISS_MS] */
    const val YIELD_TO_DASH_MS = YIELD_AFTER_MISS_MS

    private val SUPPORT_BITMASK_REQUESTS = setOf("0100", "0120", "0140", "0160", "0180", "01A0")

    fun yieldMs(hit: Boolean): Long = if (hit) YIELD_AFTER_HIT_MS else YIELD_AFTER_MISS_MS

    fun requestsToProbe(
        catalog: List<PidDefinition>,
        alreadyLiveIds: Set<String>,
        advertised: Set<Int> = emptySet(),
    ): List<PidDefinition> {
        val live = alreadyLiveIds.map { it.uppercase() }.toSet()
        val liveReqs = catalog
            .filter { it.id.uppercase() in live }
            .map { it.request.uppercase() }
            .toSet()
        return catalog
            .filter { pid ->
                val req = pid.request.uppercase()
                req.startsWith("01") &&
                    pid.id.uppercase() !in live &&
                    req !in liveReqs &&
                    req !in SUPPORT_BITMASK_REQUESTS
            }
            .distinctBy { it.request.uppercase() }
            .sortedBy { pid ->
                val n = pid.mode01Number
                when {
                    n != null && n in advertised -> 0
                    else -> 1
                }
            }
    }
}
