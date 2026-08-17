package com.fb2.obd.obd

/** Where the displayed gear came from. */
enum class GearSource {
    /** No gear available (stopped, unknown, or no data). */
    NONE,

    /** Estimated from wheel speed + engine RPM (approximate). */
    ESTIMATED,

    /** Read from the ECU/TCM (PID 0xA4 actual gear ratio). */
    ECU,
}
