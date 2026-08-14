package com.fb2.obd.data

import com.fb2.obd.obd.Dtc
import com.fb2.obd.obd.DtcCatalog
import com.fb2.obd.obd.FreezeFrame
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.HondaPidCatalog
import com.fb2.obd.obd.MonitorItem
import com.fb2.obd.obd.ModuleScanResult
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.ReadinessStatus
import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.obd.VehicleInfo
import com.fb2.obd.obd.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Simulated ELM327 feed. Produces a believable driving cycle (accelerate, cruise,
 * decelerate) so the full dashboard can be exercised without a car or adapter —
 * useful for development, demos and UI review in the cloud.
 *
 * [flavour] switches FB2-style intentional n/s (Coolant2/Ambient/LTFT) vs a
 * broader Generic OBD2 support mask for profile QA.
 */
enum class DemoFlavour { FB2, GENERIC }

class DemoObdSource(
    private val gearEstimator: GearEstimator = GearEstimator(),
    private val flavour: DemoFlavour = DemoFlavour.FB2,
) : ObdSource {

    override val name: String = when (flavour) {
        DemoFlavour.FB2 -> "Demo (simulated)"
        DemoFlavour.GENERIC -> "Demo Generic OBD2"
    }
    override val isLive: Boolean = false

    override fun snapshots(): Flow<VehicleSnapshot> = flow {
        var t = 0.0
        var coolant = 40.0 // cold start, warms toward ~92C
        while (true) {
            // Speed follows a smooth accelerate/cruise/brake wave 0..120 km/h.
            val phase = sin(t / 12.0)
            val speed = (60.0 + 60.0 * phase).coerceIn(0.0, 120.0)

            // RPM tracks speed with some load-based variation.
            val accelerating = sin(t / 12.0 + 0.3) - phase > 0
            val baseRpm = 900.0 + speed * 22.0
            val rpm = (baseRpm + (if (accelerating) 700.0 else 0.0)).coerceIn(750.0, 6500.0)

            coolant = (coolant + 0.6).coerceAtMost(92.0)

            val throttle = (10.0 + 40.0 * (0.5 + 0.5 * phase)).coerceIn(0.0, 100.0)
            val load = (15.0 + 55.0 * (0.5 + 0.5 * phase)).coerceIn(0.0, 100.0)
            // Open loop while cold, closed loop once warm (demo).
            val fuelLoop = if (coolant < 70.0) "OPEN LOOP" else "CLOSED LOOP"

            val unsupported = when (flavour) {
                // Mirror a real FB2: Coolant2 / Ambient / LTFT stay n/s until deep search.
                DemoFlavour.FB2 -> setOf(0x67, 0x46, 0x07)
                // Generic demo advertises those SAE PIDs as live.
                DemoFlavour.GENERIC -> emptySet()
            }

            val snapshot = VehicleSnapshot(
                rpm = rpm.roundToInt().toDouble(),
                speedKmh = speed.roundToInt().toDouble(),
                coolantC = coolant.roundToInt().toDouble(),
                coolant2C = if (flavour == DemoFlavour.GENERIC) coolant.roundToInt().toDouble() + 1.0 else null,
                intakeC = 32.0,
                ambientC = if (flavour == DemoFlavour.GENERIC) 28.0 else null,
                engineLoadPct = load,
                throttlePct = throttle,
                timingAdvance = 12.0,
                mafGps = 4.0 + load / 5.0,
                mapKpa = 30.0 + load,
                stftPct = 2.0 * sin(t / 5.0),
                ltftPct = if (flavour == DemoFlavour.GENERIC) 1.5 else null,
                batteryVolts = 14.2 + 0.1 * sin(t / 7.0),
                fuelSystemStatus = fuelLoop,
                gear = null,
                gearSource = GearSource.NONE,
                gearConfidencePct = null,
                unsupportedPids = unsupported,
            ).let { snap ->
                val est = if (flavour == DemoFlavour.FB2) {
                    gearEstimator.estimateDetailed(speed, rpm)
                } else {
                    null
                }
                val withGear = snap.copy(
                    gear = est?.gear,
                    gearSource = if (est != null) GearSource.ESTIMATED else GearSource.NONE,
                    gearConfidencePct = est?.confidencePct,
                )
                val now = System.currentTimeMillis()
                withGear.copy(freshAtMs = SnapshotFreshness.mapForPresentFields(withGear, now))
            }
            emit(snapshot)
            t += 1.0
            // ~1.25 Hz UI feed — 250 ms was thrashing low-RAM HU scroll/swipe.
            delay(800L)
        }
    }

    // Sample codes so the Faults screen is demonstrable without a car.
    private var demoCleared = false

    override suspend fun readStoredDtcs(): List<Dtc> = if (demoCleared) emptyList() else listOf(
        Dtc("P0133", DtcCatalog.describe("P0133")),
        Dtc("P0420", DtcCatalog.describe("P0420")),
    )

    override suspend fun readPendingDtcs(): List<Dtc> = if (demoCleared) emptyList() else listOf(
        Dtc("P0300", DtcCatalog.describe("P0300")),
    )

    override suspend fun readPermanentDtcs(): List<Dtc> =
        if (demoCleared) {
            emptyList()
        } else {
            listOf(Dtc("U0100", DtcCatalog.describe("U0100")))
        }

    override suspend fun clearDtcs(): Boolean {
        demoCleared = true
        return true
    }

    override suspend fun command(raw: String): String? {
        val cmd = raw.trim().uppercase().replace(" ", "")
        return when {
            cmd.startsWith("AT") -> when {
                cmd == "ATRV" -> "14.2V"
                else -> "OK"
            }
            cmd == "0100" -> "41 00 BE 3E B8 11"
            cmd == "0101" -> "41 01 00 07 E5 E5"
            cmd == "0103" -> "41 03 02 00" // closed loop bank 1
            cmd == "0105" -> "41 05 7B"
            cmd == "0107" -> "41 07 80" // LTFT ~0% — deep-search demo hit
            cmd == "010C" -> "41 0C 0B 20"
            cmd == "0142" -> "41 42 36 B0" // ~14.0 V
            cmd == "0146" -> "41 46 4E" // ambient 38°C — deep-search demo hit
            cmd == "0167" -> "41 67 03 7B 78" // coolant sensors — deep-search demo hit
            cmd == "0902" -> "49 02 01 4A 48 4D 46 42 32 31 32 33 34 35 36 37 38 39"
            cmd == "0904" -> "49 04 01 R18A2-DEMO-CAL"
            cmd.startsWith("09") -> "NO DATA"
            cmd.startsWith("22") -> {
                val id = cmd.take(6)
                val v = demoMode22[id] ?: return "NO DATA"
                val pid = id.removePrefix("22")
                val data = if (v >= 256) {
                    "%04X".format(v.toInt().coerceIn(0, 0xFFFF))
                } else {
                    "%02X".format(v.toInt().coerceIn(0, 255))
                }
                // Spaced hex: 62 AABB CC…
                ("62$pid$data").chunked(2).joinToString(" ")
            }
            cmd == "03" || cmd == "07" || cmd == "0A" -> "43 00"
            cmd == "05" || cmd == "06" -> "NO DATA"
            cmd.startsWith("01") -> "41 ${cmd.drop(2)} 00 00"
            else -> "OK"
        }
    }

    override suspend fun readVehicleInfo() = VehicleInfo(
        vin = "JHMFB2123456789",
        calibrationIds = listOf("R18A2-DEMO-CAL"),
        ecuName = "DEMO-ECM",
    )

    override suspend fun readReadiness() = ReadinessStatus(
        milOn = !demoCleared,
        dtcCount = if (demoCleared) 0 else 2,
        monitors = listOf(
            MonitorItem("Misfire", true, true),
            MonitorItem("Fuel system", true, true),
            MonitorItem("Catalyst", true, false),
            MonitorItem("EVAP", true, true),
            MonitorItem("O2 sensor", true, true),
        ),
    )

    override suspend fun readFreezeFrame() = FreezeFrame(
        dtc = if (demoCleared) null else "P0133",
        values = mapOf("RPM" to "850", "Coolant" to "86 °C", "Load" to "22 %"),
    )

    // A few Mode 22 IDs answer in demo so Transmission / Honda / idle UIs are exerciseable.
    private val demoMode22 = mapOf(
        "221101" to 86.0, // ATF temp
        "221201" to 3.0, // gear
        "221202" to 4.0, // range D
        "221206" to 40.0, // TC slip
        "221207" to 1.0, // lock status
        "221208" to 980.0, // line pressure
        "221304" to 2.8, // injector PW
        "221307" to 0.0, // knock
        "221308" to 0.0, // misfire cyl 1
        "221309" to 12.0, // misfire cyl 2 (demo rough-idle signal)
        "22130A" to 0.0,
        "22130B" to 0.0,
        "221310" to 340.0, // fuel pump pressure cand A
        "221314" to 750.0, // target idle
        "221316" to 12.0, // total misfire
    )

    override suspend fun probePids(
        pids: List<PidDefinition>,
        recoverFirst: Boolean,
    ) = pids.map { pid ->
        when {
            pid.request.equals("0103", true) -> {
                // Byte A = 0x02 → CLOSED LOOP
                PidProbeResult(pid, true, 2.0, "41 03 02 00")
            }
            pid.request.startsWith("01") -> {
                val sample = pid.decode(intArrayOf(120, 0, 0, 0)) ?: 1.0
                PidProbeResult(pid, true, sample, "OK")
            }
            demoMode22.containsKey(pid.request) ->
                PidProbeResult(pid, true, demoMode22[pid.request], "62 OK")
            else -> PidProbeResult(pid, false, null, "NO DATA")
        }
    }

    override suspend fun probeHondaModules(): List<ModuleScanResult> {
        return HondaPidCatalog.allPacks.map { pack ->
            val results = probePids(pack.pids)
            ObdLogger.logProbe("Honda:${pack.id}", results)
            val ok = results.filter { it.supported }
            ModuleScanResult(
                module = pack.title,
                profileId = pack.id,
                supportedCount = ok.size,
                totalCount = pack.pids.size,
                samplePids = ok.take(5).map { it.pid.label },
                status = when {
                    ok.isEmpty() -> "No response (demo placeholder)"
                    else -> "${ok.size}/${pack.pids.size} demo PIDs answered"
                },
            )
        }
    }

    override suspend fun readMode05() = listOf(
        com.fb2.obd.obd.O2TestResult("B1S1", "05", "0.45 V", "05 DEMO"),
    )

    override suspend fun readMode06() = listOf(
        com.fb2.obd.obd.Mode06Result("01", "00", "0", "0", "FF", true, "06 01 00 00 00 FF"),
    )

    override suspend fun readPid(pid: PidDefinition): Double? =
        probePids(listOf(pid)).firstOrNull()?.sample
}
