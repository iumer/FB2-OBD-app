package com.fb2.obd.obd

/**
 * Canonical builder credit. Keep this in sync with [LICENSE], README, and
 * `res/values/strings.xml` — forks should retain the name.
 */
object AppAttribution {
    const val AUTHOR_NAME = "iUmer Farooq"
    const val AUTHOR_GITHUB = "iumer"
    const val PROJECT_URL = "https://github.com/iumer/FB2-OBD-app"
    const val BUILT_BY = "Built by $AUTHOR_NAME"
    const val COPYRIGHT = "Copyright (c) 2026 $AUTHOR_NAME"
    const val SHORT_CREDIT = "FB2 Diag — $BUILT_BY"

    /** Comment lines for session CSV / AI report headers. */
    fun logHeaderLines(): List<String> = listOf(
        "# $BUILT_BY",
        "# $PROJECT_URL",
        "# $COPYRIGHT",
    )
}
