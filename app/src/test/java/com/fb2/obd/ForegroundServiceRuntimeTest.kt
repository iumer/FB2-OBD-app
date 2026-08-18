package com.fb2.obd

import com.fb2.obd.data.FloatingDashPrefs
import com.fb2.obd.service.FloatingDashOverlayService
import com.fb2.obd.service.ObdMonitorForegroundService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Foreground-service lifecycle on a real Android runtime.
 *
 * Both services restart themselves from [android.app.Service.onTaskRemoved],
 * which runs while the app sits in the background after the user swipes it out
 * of recents. On API 31+ a background foreground-service start can be refused
 * with `ForegroundServiceStartNotAllowedException`; if that escapes, the app
 * dies exactly the way 0.1.28 did on connect. Losing keep-alive or the bubble
 * is recoverable, so these paths must swallow the refusal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Fb2App::class)
class ForegroundServiceRuntimeTest {

    @Test
    fun obdMonitor_onTaskRemoved_doesNotThrow() {
        val service = Robolectric.setupService(ObdMonitorForegroundService::class.java)
        service.onTaskRemoved(null)
    }

    @Test
    fun floatingDash_onTaskRemoved_doesNotThrow_whenBubbleEnabled() {
        FloatingDashPrefs.setEnabled(RuntimeEnvironment.getApplication(), true)
        val service = Robolectric.setupService(FloatingDashOverlayService::class.java)
        service.onTaskRemoved(null)
    }

    @Test
    fun floatingDash_onTaskRemoved_doesNotThrow_whenBubbleDisabled() {
        FloatingDashPrefs.setEnabled(RuntimeEnvironment.getApplication(), false)
        val service = Robolectric.setupService(FloatingDashOverlayService::class.java)
        service.onTaskRemoved(null)
    }

    /** Public entry points used from the ViewModel must never surface a start refusal. */
    @Test
    fun startOverlay_doesNotThrow() {
        FloatingDashOverlayService.startOverlay(RuntimeEnvironment.getApplication())
    }
}
