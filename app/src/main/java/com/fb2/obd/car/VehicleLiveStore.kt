package com.fb2.obd.car

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge between [com.fb2.obd.DashboardViewModel] (phone) and the Android Auto
 * Car App screens. Updated on every live snapshot; car UI collects and invalidates.
 */
object VehicleLiveStore {

    private val _dash = MutableStateFlow(CarDashState())
    val dash: StateFlow<CarDashState> = _dash.asStateFlow()

    @Volatile
    var onToggleLogging: (() -> Unit)? = null

    /** Open phone connect flow / start demo when not live. */
    @Volatile
    var onConnectRequest: (() -> Unit)? = null

    fun publish(state: CarDashState) {
        _dash.value = state
    }
}
