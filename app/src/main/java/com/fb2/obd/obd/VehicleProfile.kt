package com.fb2.obd.obd

/**
 * User-selected vehicle behaviour profile.
 *
 * - [FB2]: Honda Civic FB2 — SAE Mode 01 + Honda Mode 22 packs, Transmission,
 *   Honda modules, FB2 health/gear defaults.
 * - [GENERIC_OBD2]: SAE-only fallback for any OBD-II car — no Honda Mode 22,
 *   no Transmission/Honda DIAG rows, wider health defaults, estimated gear off.
 */
enum class VehicleProfile(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val badge: String,
) {
    FB2(
        id = "fb2",
        displayName = "FB2",
        subtitle = "Honda Civic FB2 — enhanced Mode 22 + Transmission",
        badge = "FB2",
    ),
    GENERIC_OBD2(
        id = "generic_obd2",
        displayName = "Generic OBD2",
        subtitle = "Any OBD-II car — SAE Mode 01 / codes only",
        badge = "OBD2",
    );

    val isFb2: Boolean get() = this == FB2
    val isGeneric: Boolean get() = this == GENERIC_OBD2

    companion object {
        val DEFAULT = FB2

        fun fromId(raw: String?): VehicleProfile =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * Profile-scoped feature flags and catalogs. Keep UI / ViewModel free of
 * scattered `if (fb2)` checks — gate through this config.
 */
object VehicleProfileConfig {

    fun pidCatalog(profile: VehicleProfile): List<PidDefinition> = when (profile) {
        VehicleProfile.FB2 -> StandardPidCatalog.all + HondaPidCatalog.allPids
        VehicleProfile.GENERIC_OBD2 -> StandardPidCatalog.all
    }

    /** Swipe page titles on the main dashboard. */
    fun dashPageTitles(profile: VehicleProfile): List<String> = when (profile) {
        VehicleProfile.FB2 -> listOf(
            "Dash", "Custom", "Idle", "Fuel", "Trip", "Trans", "Perf", "G-force", "Health",
        )
        VehicleProfile.GENERIC_OBD2 -> listOf(
            "Dash", "Custom", "Idle", "Fuel", "Trip", "Perf", "G-force", "Health",
        )
    }

    fun showHondaModules(profile: VehicleProfile): Boolean = profile.isFb2

    fun showTransmissionPage(profile: VehicleProfile): Boolean = profile.isFb2

    fun defaultShowEstimatedGear(profile: VehicleProfile): Boolean = profile.isFb2

    fun idleSections(profile: VehicleProfile): List<ColdStartIdleCatalog.Section> =
        ColdStartIdleCatalog.sectionsFor(profile)

    fun fuelExtraPids(profile: VehicleProfile): List<PidDefinition> = when (profile) {
        VehicleProfile.FB2 ->
            HondaPidCatalog.engine.pids.filter {
                it.label.contains("Injector", true) || it.label.contains("Fuel", true)
            }
        VehicleProfile.GENERIC_OBD2 -> emptyList()
    }

    fun deepSearchStrategies(
        profile: VehicleProfile,
        pid: PidDefinition?,
        label: String,
        requestHint: String?,
    ): List<DeepSearchStrategy> {
        val all = DeepSearchKnowledgeBase.strategiesFor(pid, label, requestHint)
        return if (profile.isFb2) all else all.filter { !it.isHondaSpecific }
    }

    fun healthDefaults(profile: VehicleProfile): HealthThresholds = when (profile) {
        VehicleProfile.FB2 -> HealthThresholds()
        VehicleProfile.GENERIC_OBD2 -> HealthThresholds.genericObd2()
    }

    fun diagHubBlurb(profile: VehicleProfile): String = when (profile) {
        VehicleProfile.FB2 ->
            "Read / clear codes, AI analysis, deep scans, VIN, Honda modules, and maintenance. Live sensor pages are on the dashboard swipe tabs."
        VehicleProfile.GENERIC_OBD2 ->
            "Read / clear SAE fault codes, AI analysis, deep scans, VIN, and maintenance. Live sensor pages are on the dashboard swipe tabs."
    }
}
