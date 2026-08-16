package com.tabletwake

import kotlin.math.abs

class MotionDetector(sensitivity: Int) {
    private val changedPixelRatio = 0.22f - sensitivity.coerceIn(10, 100) * 0.00165f
    private var baseline: FloatArray? = null
    private var consecutiveMotionFrames = 0

    fun analyze(frame: IntArray): Boolean {
        val background = baseline
        if (background == null || background.size != frame.size) {
            baseline = FloatArray(frame.size) { frame[it].toFloat() }
            return false
        }

        var globalShift = 0f
        frame.indices.forEach { globalShift += frame[it] - background[it] }
        globalShift /= frame.size

        var changedPixels = 0
        frame.indices.forEach {
            val localDifference = abs(frame[it] - background[it] - globalShift)
            if (localDifference > PIXEL_DIFFERENCE_THRESHOLD) changedPixels++
        }

        val motion = changedPixels.toFloat() / frame.size >= changedPixelRatio
        consecutiveMotionFrames = if (motion) consecutiveMotionFrames + 1 else 0

        val adaptation = if (motion) 0.02f else 0.07f
        frame.indices.forEach {
            background[it] = background[it] * (1f - adaptation) + frame[it] * adaptation
        }

        if (consecutiveMotionFrames < REQUIRED_CONSECUTIVE_FRAMES) return false
        consecutiveMotionFrames = 0
        frame.indices.forEach { background[it] = frame[it].toFloat() }
        return true
    }

    fun reset() {
        baseline = null
        consecutiveMotionFrames = 0
    }

    companion object {
        private const val PIXEL_DIFFERENCE_THRESHOLD = 18f
        private const val REQUIRED_CONSECUTIVE_FRAMES = 2
    }
}
