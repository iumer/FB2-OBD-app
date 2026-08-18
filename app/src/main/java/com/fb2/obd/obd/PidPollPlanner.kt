package com.fb2.obd.obd

/**
 * Decides which Mode 01 PIDs to request each ELM poll cycle.
 *
 * Cheap clones hang and time out; polling every supported Dash PID every
 * cycle made hero Speed fall behind (last-good freeze while RPM kept
 * updating). RPM + Speed are requested every cycle. Coolant 1 + MAF are also
 * requested every cycle — Coolant is the primary reason this app exists and
 * must not blank from rotation TTL races.
 *
 * Remaining secondary PIDs rotate so each cycle stays short.
 *
 * Pure Kotlin — unit-testable without Android / Bluetooth.
 */
object PidPollPlanner {

    /** Must stay live for driving; never fail-streak skipped. */
    val HERO_NUMBERS: Set<Int> = setOf(
        ObdPid.ENGINE_RPM.number,
        ObdPid.SPEED.number,
    )

    /**
     * Polled every cycle in addition to heroes. Kept small so cycles stay short
     * on cheap ELMs, but Coolant/MAF must not vanish between rotations.
     */
    val ALWAYS_NUMBERS: Set<Int> = HERO_NUMBERS + setOf(
        ObdPid.COOLANT_TEMP.number,
        ObdPid.MAF.number,
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
     * @param secondaryBudget max rotating (non-always) PIDs per cycle
     * @param hold [PollHold.HEROES_ONLY] skips rotating secondaries during deep search.
     */
    fun selectForCycle(
        activePids: List<ObdPid>,
        failStreak: Map<ObdPid, Int>,
        cycle: Int,
        recovering: Boolean,
        secondaryBudget: Int = 4,
        hold: PollHold = PollHold.NONE,
    ): List<ObdPid> {
        if (hold == PollHold.FULL_PAUSE) return emptyList()
        val always = activePids.filter { it.number in ALWAYS_NUMBERS }
        if (hold == PollHold.HEROES_ONLY) {
            return orderAlways(always)
        }
        val secondaryPool = activePids.filter { it.number !in ALWAYS_NUMBERS }

        val eligibleSecondary = secondaryPool.filter { pid ->
            if (recovering && pid.number !in CORE_NUMBERS) return@filter false
            shouldRetrySecondary(pid, failStreak[pid] ?: 0, cycle, recovering)
        }

        val secondaries = rotate(eligibleSecondary, cycle, secondaryBudget.coerceAtLeast(0))

        return orderAlways(always) + secondaries
    }

    private fun orderAlways(always: List<ObdPid>): List<ObdPid> =
        listOfNotNull(
            always.firstOrNull { it == ObdPid.ENGINE_RPM },
            always.firstOrNull { it == ObdPid.SPEED },
            always.firstOrNull { it == ObdPid.COOLANT_TEMP },
            always.firstOrNull { it == ObdPid.MAF },
        ) + always.filter {
            it != ObdPid.ENGINE_RPM && it != ObdPid.SPEED &&
                it != ObdPid.COOLANT_TEMP && it != ObdPid.MAF
        }

    /**
     * Secondary PIDs may be skipped after repeated failures so one flaky PID
     * does not burn every cycle. Always-polled PIDs never use this gate.
     */
    fun shouldRetrySecondary(
        pid: ObdPid,
        streak: Int,
        cycle: Int,
        recovering: Boolean,
    ): Boolean {
        if (pid.number in ALWAYS_NUMBERS) return true
        if (streak >= 2 && cycle % 20 != 0) return false
        if (streak >= 1 && recovering && cycle % 5 != 0) return false
        return true
    }

    /** True when this PID must be requested every cycle regardless of streak. */
    fun isHero(pid: ObdPid): Boolean = pid.number in HERO_NUMBERS

    /** True when this PID is requested every cycle (heroes + Coolant/MAF). */
    fun isAlways(pid: ObdPid): Boolean = pid.number in ALWAYS_NUMBERS

    private fun rotate(eligible: List<ObdPid>, cycle: Int, budget: Int): List<ObdPid> {
        if (eligible.isEmpty() || budget <= 0) return emptyList()
        if (eligible.size <= budget) return eligible
        val start = ((cycle - 1).coerceAtLeast(0) * budget) % eligible.size
        return (0 until budget).map { eligible[(start + it) % eligible.size] }
    }
}
