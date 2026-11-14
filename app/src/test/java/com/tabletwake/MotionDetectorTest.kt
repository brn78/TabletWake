package com.tabletwake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionDetectorTest {
    @Test
    fun uniformLightChangeDoesNotTriggerMotion() {
        val detector = MotionDetector(65)
        assertFalse(detector.analyze(IntArray(100) { 80 }))
        assertFalse(detector.analyze(IntArray(100) { 125 }))
        assertFalse(detector.analyze(IntArray(100) { 125 }))
    }

    @Test
    fun structuredChangeTriggersAfterTwoFrames() {
        val detector = MotionDetector(65)
        val baseline = IntArray(100) { 80 }
        val movement = IntArray(100) { if (it < 20) 160 else 80 }

        assertFalse(detector.analyze(baseline))
        assertFalse(detector.analyze(movement))
        assertTrue(detector.analyze(movement))
    }

    @Test
    fun resetRequiresANewBaseline() {
        val detector = MotionDetector(100)
        detector.analyze(IntArray(100) { 50 })
        detector.reset()
        assertFalse(detector.analyze(IntArray(100) { if (it < 30) 200 else 50 }))
    }

    @Test
    fun emptyFrameReturnsFalseWithoutError() {
        val detector = MotionDetector(65)
        assertFalse(detector.analyze(IntArray(0)))
    }

    @Test
    fun calmFrameResetsConsecutiveMotionCounter() {
        val detector = MotionDetector(65)
        val baseline = IntArray(100) { 80 }
        val movement = IntArray(100) { if (it < 20) 160 else 80 }

        assertFalse(detector.analyze(baseline))
        assertFalse(detector.analyze(movement))
        assertFalse(detector.analyze(baseline))
        assertFalse(detector.analyze(movement))
        assertTrue(detector.analyze(movement))
    }

    @Test
    fun sensitivityClampsToMinimumAndMaximumThresholds() {
        val lowSensitivity = MotionDetector(0)
        val highSensitivity = MotionDetector(150)
        val baseline = IntArray(100) { 80 }
        val smallChange = IntArray(100) { if (it < 8) 160 else 80 }

        assertFalse(lowSensitivity.analyze(baseline))
        assertFalse(lowSensitivity.analyze(smallChange))
        assertFalse(lowSensitivity.analyze(smallChange))

        assertFalse(highSensitivity.analyze(baseline))
        assertFalse(highSensitivity.analyze(smallChange))
        assertTrue(highSensitivity.analyze(smallChange))
    }
}
