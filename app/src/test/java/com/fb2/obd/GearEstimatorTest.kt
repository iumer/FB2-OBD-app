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

    // Expected RPMs below use the FB2 5AT ratios (2.666/1.534/1.022/0.721/0.525,
    // final 4.44) and a 195/65 R15 rolling circumference.

    @Test
    fun highway_lowRpm_isTopGear() {
        // 100 km/h @ ~1950 rpm -> 5th
        assertEquals(5, estimator.estimate(speedKmh = 100.0, rpm = 1950.0))
    }

    @Test
    fun highway_higherRpm_isFourth() {
        // 100 km/h @ ~2680 rpm -> 4th
        assertEquals(4, estimator.estimate(speedKmh = 100.0, rpm = 2680.0))
    }

    @Test
    fun cityCruise_isThird() {
        // 50 km/h @ ~1900 rpm -> 3rd
        assertEquals(3, estimator.estimate(speedKmh = 50.0, rpm = 1900.0))
    }

    @Test
    fun cityAccel_isSecond() {
        // 50 km/h @ ~2850 rpm -> 2nd
        assertEquals(2, estimator.estimate(speedKmh = 50.0, rpm = 2850.0))
    }
}
