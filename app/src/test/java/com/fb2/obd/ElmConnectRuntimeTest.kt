package com.fb2.obd

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.ObdSource
import com.fb2.obd.obd.VehicleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android-runtime coverage for the ELM connect path.
 *
 * 0.1.28 crashed instantly on Connect, yet every pure-Kotlin test passed: the
 * failure lived in [DashboardViewModel] plus foreground-service calls, which
 * plain JVM tests never instantiate. These tests drive the real ViewModel on a
 * real Application context so a connect-time crash fails the build instead of
 * the driver.
 *
 * Virtual time is stepped with [advanceTimeBy] only. Demo polling and the
 * ViewModel's upload/voice jobs loop forever, so `advanceUntilIdle()` would
 * never return.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Fb2App::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ElmConnectRuntimeTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    /** Stand-in for [com.fb2.obd.data.Elm327BluetoothSource] without Bluetooth hardware. */
    private class FakeLiveSource(
        private val frames: List<VehicleSnapshot>,
        override val isLive: Boolean = true,
        override val name: String = "ELM327 (Bluetooth)",
    ) : ObdSource {
        override fun snapshots(): Flow<VehicleSnapshot> = flow {
            frames.forEach {
                emit(it)
                delay(50L)
            }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): DashboardViewModel {
        val vm = DashboardViewModel(ApplicationProvider.getApplicationContext<Application>())
        scope.runCurrent()
        return vm
    }

    private fun step(ms: Long) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }

    @Test
    fun viewModelConstructs_onRealAndroidContext() {
        val vm = newViewModel()
        step(500L)
        assertNotNull(vm.uiState.value)
        vm.disconnect()
        step(100L)
    }

    /** The exact 0.1.28 crash path: Demo running, user taps Connect on a live ELM. */
    @Test
    fun demoThenLiveConnect_doesNotCrash_andClearsDemoNumbers() {
        val vm = newViewModel()
        vm.useSource(DemoObdSource())
        step(3_000L)

        vm.useSource(
            FakeLiveSource(
                listOf(
                    VehicleSnapshot(batteryVolts = 14.1),
                    VehicleSnapshot(rpm = 820.0, speedKmh = 0.0, coolantC = 76.0, batteryVolts = 14.1),
                ),
            ),
        )
        step(500L)

        val state = vm.uiState.value
        assertEquals(ConnectionState.CONNECTED, state.connection)
        assertTrue("live ELM must be marked live", state.sourceIsLive)
        assertEquals(820.0, state.snapshot.rpm!!, 0.001)
        assertEquals(76.0, state.snapshot.coolantC!!, 0.001)
        vm.disconnect()
        step(100L)
    }

    /** Demo speed/RPM must never survive into the first live frame (I70). */
    @Test
    fun firstLiveFrame_doesNotInheritDemoHeroes() {
        val vm = newViewModel()
        vm.useSource(DemoObdSource())
        step(4_000L)
        assertNotNull("demo should populate RPM first", vm.uiState.value.snapshot.rpm)

        // First live frame is ATRV-only, exactly like a real ELM handshake.
        vm.useSource(FakeLiveSource(listOf(VehicleSnapshot(batteryVolts = 14.2))))
        step(200L)

        val snap = vm.uiState.value.snapshot
        assertNull("Demo RPM must not leak into live session", snap.rpm)
        assertNull("Demo speed must not leak into live session", snap.speedKmh)
        assertEquals(14.2, snap.batteryVolts!!, 0.001)
        vm.disconnect()
        step(100L)
    }

    /** Mid-session ATRV-only frames must not blank heroes (I69). */
    @Test
    fun midSessionAtrvOnlyFrame_keepsHeroesLive() {
        val vm = newViewModel()
        vm.useSource(
            FakeLiveSource(
                listOf(
                    VehicleSnapshot(rpm = 900.0, speedKmh = 42.0, coolantC = 88.0, batteryVolts = 14.0),
                    VehicleSnapshot(batteryVolts = 14.3),
                ),
            ),
        )
        step(500L)

        val snap = vm.uiState.value.snapshot
        assertEquals(900.0, snap.rpm!!, 0.001)
        assertEquals(42.0, snap.speedKmh!!, 0.001)
        assertEquals(88.0, snap.coolantC!!, 0.001)
        assertEquals(14.3, snap.batteryVolts!!, 0.001)
        vm.disconnect()
        step(100L)
    }

    @Test
    fun connectThenDisconnect_leavesNoLiveState() {
        val vm = newViewModel()
        vm.useSource(FakeLiveSource(listOf(VehicleSnapshot(rpm = 1000.0, batteryVolts = 14.0))))
        step(300L)
        assertEquals(ConnectionState.CONNECTED, vm.uiState.value.connection)

        vm.disconnect()
        step(200L)
        assertEquals(ConnectionState.DISCONNECTED, vm.uiState.value.connection)
        assertNull(vm.uiState.value.snapshot.rpm)
    }
}
