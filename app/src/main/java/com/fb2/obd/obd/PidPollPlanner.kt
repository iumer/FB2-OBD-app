package com.fb2.obd.obd

/**
 * Decides which Mode 01 PIDs to request each ELM poll cycle.
 *
 * Cheap clones hang and time out; polling every supported Dash PID every
 * cycle made hero Speed fall behind (last-good freeze while RPM kept
 * updating). Heroes (RPM + Speed) are requested every cycle and are never
 * skipped for fail-streak. Secondary PIDs rotate so each cycle stays short.
 *
 * Pure Kotlin — unit-testable without Android / Bluetooth.
 */
object PidPollPlanner {

    /** Must stay live for driving; never fail-streak skipped. */
    val HERO_NUMBERS: Set<Int> = setOf(
        ObdPid.ENGINE_RPM.number,
        ObdPid.SPEED.number,
    )

    /** Prefer these while the bus is recovering from UNABLE / dead cycles. */
    val CORE_NUMBERS: Set<Int> = setOf(
        ObdPid.ENGINE_RPM.number,
        ObdPid.SPEED.number,
        ObdPid.COOLANT_TEMP.number,
        ObdPid.MAF.number,
        ObdPid.INTAKE_MAP.number,
        ObdPid.THROTTLE.number,
        ObdPid.STFT_B1.number,
        ObdPid.FUEL_SYSTEM_STATUS.number,
        ObdPid.ENGINE_LOAD.number,
    )

    /**
     * @param activePids full poll list for this car
     * @param failStreak consecutive failures per PID
     * @param cycle 1-based cycle counter
     * @param recovering true after bus-lost / dead cycle
     * @param secondaryBudget max non-hero PIDs per cycle (keeps Dash snappy)
     */
    fun selectForCycle(
        activePids: List<ObdPid>,
        failStreak: Map<ObdPid, Int>,
        cycle: Int,
        recovering: Boolean,
        secondaryBudget: Int = 4,
    ): List<ObdPid> {
        val heroes = activePids.filter { it.number in HERO_NUMBERS }
        val secondaryPool = activePids.filter { it.number !in HERO_NUMBERS }

        val eligibleSecondary = secondaryPool.filter { pid ->
            if (recovering && pid.number !in CORE_NUMBERS) return@filter false
            shouldRetrySecondary(pid, failStreak[pid] ?: 0, cycle, recovering)
        }

        val secondaries = rotate(eligibleSecondary, cycle, secondaryBudget.coerceAtLeast(0))

        // Stable order: RPM, Speed, then this cycle's secondaries (catalog order).
        val heroOrdered = listOfNotNull(
            heroes.firstOrNull { it == ObdPid.ENGINE_RPM },
            heroes.firstOrNull { it == ObdPid.SPEED },
        ) + heroes.filter { it != ObdPid.ENGINE_RPM && it != ObdPid.SPEED }

        return heroOrdered + secondaries
    }

    /**
     * Secondary PIDs may be skipped after repeated failures so one flaky PID
     * does not burn every cycle. Heroes never use this gate.
     */
    fun shouldRetrySecondary(
        pid: ObdPid,
        streak: Int,
        cycle: Int,
        recovering: Boolean,
    ): Boolean {
        if (pid.number in HERO_NUMBERS) return true
        if (streak >= 2 && cycle % 20 != 0) return false
        if (streak >= 1 && recovering && cycle % 5 != 0) return false
        return true
    }

    /** True when this PID must be requested every cycle regardless of streak. */
    fun isHero(pid: ObdPid): Boolean = pid.number in HERO_NUMBERS

    private fun rotate(eligible: List<ObdPid>, cycle: Int, budget: Int): List<ObdPid> {
        if (eligible.isEmpty() || budget <= 0) return emptyList()
        if (eligible.size <= budget) return eligible
        val start = ((cycle - 1).coerceAtLeast(0) * budget) % eligible.size
        return (0 until budget).map { eligible[(start + it) % eligible.size] }
    }
}
