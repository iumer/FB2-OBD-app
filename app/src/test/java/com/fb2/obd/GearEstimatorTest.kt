package com.fb2.obd

import com.fb2.obd.obd.GearEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GearEstimatorTest {

    private val estimator = GearEstimator()

    @Test
    fun stopped_returnsNull() {
        assertNull(estimator.estimate(speedKmh = 2.0, rpm = 800.0))
    }

    @Test
    fun zeroRpm_returnsNull() {
        assertNull(estimator.estimate(speedKmh = 50.0, rpm = 0.0))
    }

    @Test
    fun highway_lowRpm_isTopGear() {
        // 100 km/h @ ~1900 rpm -> 5th
        assertEquals(5, estimator.estimate(speedKmh = 100.0, rpm = 1900.0))
    }

    @Test
    fun highway_higherRpm_isFourth() {
        // 100 km/h @ ~2820 rpm -> 4th
        assertEquals(4, estimator.estimate(speedKmh = 100.0, rpm = 2820.0))
    }

    @Test
    fun cityCruise_isThird() {
        // 50 km/h @ ~2000 rpm -> 3rd
        assertEquals(3, estimator.estimate(speedKmh = 50.0, rpm = 2000.0))
    }

    @Test
    fun cityAccel_isSecond() {
        // 50 km/h @ ~3100 rpm -> 2nd
        assertEquals(2, estimator.estimate(speedKmh = 50.0, rpm = 3100.0))
    }
}
