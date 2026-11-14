package com.tabletwake

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class MotionFrameAnalyzer(
    sensitivity: Int,
    private val shouldAnalyze: () -> Boolean,
    private val onMotion: () -> Unit
) : ImageAnalysis.Analyzer {
    private val detector = MotionDetector(sensitivity)
    private val frame = IntArray(GRID_WIDTH * GRID_HEIGHT)
    private var lastAnalysisAt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (!shouldAnalyze()) {
                detector.reset()
                return
            }
            if (now - lastAnalysisAt < ANALYSIS_INTERVAL_MS) return
            if (image.width <= 0 || image.height <= 0) return
            lastAnalysisAt = now

            val yPlane = image.planes.firstOrNull() ?: return
            val buffer = yPlane.buffer
            val limit = buffer.limit()

            for (gridY in 0 until GRID_HEIGHT) {
                val sourceY = gridY * image.height / GRID_HEIGHT
                val rowOffset = gridY * GRID_WIDTH
                val sourceRowOffset = sourceY * yPlane.rowStride
                for (gridX in 0 until GRID_WIDTH) {
                    val sourceX = gridX * image.width / GRID_WIDTH
                    val index = sourceRowOffset + sourceX * yPlane.pixelStride
                    frame[rowOffset + gridX] = if (index in 0 until limit) {
                        buffer.get(index).toInt() and 0xFF
                    } else {
                        0
                    }
                }
            }

            if (detector.analyze(frame)) onMotion()
        } finally {
            image.close()
        }
    }

    companion object {
        private const val GRID_WIDTH = 40
        private const val GRID_HEIGHT = 30
        private const val ANALYSIS_INTERVAL_MS = 500L
    }
}
