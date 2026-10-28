package com.itermeum.app.feature.morning

/**
 * A plain pedometer on the accelerometer: the magnitude without gravity
 * (slow low-pass), one step per peak above a threshold, at least 250 ms apart.
 * Shaking the phone in the hand also counts — the reverse code after the
 * steps is what proves the person is awake.
 */
internal class AccelStepDetector(
    private val threshold: Float = 1.6f,
    private val minGapNanos: Long = 250_000_000L,
) {
    var steps = 0
        private set
    private var gravity = Float.NaN
    private var above = false
    private var lastStepAt = 0L

    fun onSample(x: Float, y: Float, z: Float, timestampNanos: Long) {
        val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
        gravity = if (gravity.isNaN()) magnitude else gravity * 0.9f + magnitude * 0.1f
        val dynamic = magnitude - gravity
        if (!above && dynamic > threshold) {
            above = true
            if (timestampNanos - lastStepAt >= minGapNanos) { steps++; lastStepAt = timestampNanos }
        } else if (above && dynamic < threshold / 2) {
            above = false
        }
    }
}
