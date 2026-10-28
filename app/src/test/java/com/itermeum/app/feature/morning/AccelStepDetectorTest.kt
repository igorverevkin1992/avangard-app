package com.itermeum.app.feature.morning

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccelStepDetectorTest {

    private val g = 9.81f

    /** [seconds] of samples at 50 Hz: gravity on z plus [amp] m/s² swinging at [hz] steps per second. */
    private fun run(d: AccelStepDetector, seconds: Double, hz: Double, amp: Double) {
        val n = (seconds * 50).toInt()
        for (i in 0 until n) {
            val t = i / 50.0
            d.onSample(0.3f, 0.2f, g + (amp * sin(2 * PI * hz * t)).toFloat(), (t * 1e9).toLong())
        }
    }

    @Test fun `walking at two steps a second counts about two steps a second`() {
        val d = AccelStepDetector()
        run(d, 10.0, 2.0, 4.0)
        assertTrue("got ${d.steps}", d.steps in 18..21)
    }

    @Test fun `a phone lying still counts nothing`() {
        val d = AccelStepDetector()
        run(d, 10.0, 2.0, 0.2)
        assertEquals(0, d.steps)
    }

    @Test fun `vibration faster than a step is not counted as many steps`() {
        val d = AccelStepDetector()
        run(d, 2.0, 20.0, 5.0)
        assertTrue("got ${d.steps}", d.steps <= 8)
    }
}
