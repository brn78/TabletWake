package com.tabletwake

import android.content.Context

object AppPreferences {
    private const val FILE_NAME = "tablet_wake_preferences"
    private const val KEY_MODE = "detection_mode"
    private const val KEY_SENSITIVITY = "camera_sensitivity"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    private const val KEY_SCREEN_OFF_PAUSE_SECONDS = "screen_off_pause_seconds"
    private const val KEY_AUTOMATIC_UPDATES = "automatic_updates"
    private const val KEY_LAST_WAKE = "last_wake"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_UPDATE_DOWNLOAD_ID = "update_download_id"
    private const val KEY_UPDATE_DOWNLOAD_PATH = "update_download_path"
    private const val KEY_UPDATE_SHA256 = "update_sha256"

    fun mode(context: Context): DetectionMode = DetectionMode.fromStoredValue(
        preferences(context).getString(KEY_MODE, DetectionMode.CAMERA.storedValue)
    )

    fun setMode(context: Context, mode: DetectionMode) {
        preferences(context).edit().putString(KEY_MODE, mode.storedValue).apply()
    }

    fun sensitivity(context: Context): Int =
        preferences(context).getInt(KEY_SENSITIVITY, 65).coerceIn(10, 100)

    fun setSensitivity(context: Context, value: Int) {
        preferences(context).edit().putInt(KEY_SENSITIVITY, value.coerceIn(10, 100)).apply()
    }

    fun monitoringEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_MONITORING_ENABLED, false)

    fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
    }

    fun screenOffPauseSeconds(context: Context): Int =
        preferences(context).getInt(KEY_SCREEN_OFF_PAUSE_SECONDS, 30).coerceIn(0, 120)

    fun setScreenOffPauseSeconds(context: Context, seconds: Int) {
        preferences(context).edit()
            .putInt(KEY_SCREEN_OFF_PAUSE_SECONDS, seconds.coerceIn(0, 120))
            .apply()
    }

    fun automaticUpdates(context: Context): Boolean =
        preferences(context).getBoolean(KEY_AUTOMATIC_UPDATES, true)

    fun setAutomaticUpdates(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_AUTOMATIC_UPDATES, enabled).apply()
    }

    fun lastWake(context: Context): Long = preferences(context).getLong(KEY_LAST_WAKE, 0L)

    fun setLastWake(context: Context, timestamp: Long) {
        preferences(context).edit().putLong(KEY_LAST_WAKE, timestamp).apply()
    }

    fun lastError(context: Context): String? = preferences(context).getString(KEY_LAST_ERROR, null)

    fun setLastError(context: Context, message: String?) {
        preferences(context).edit().apply {
            if (message == null) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, message)
        }.apply()
    }

    fun updateDownloadId(context: Context): Long =
        preferences(context).getLong(KEY_UPDATE_DOWNLOAD_ID, -1L)

    fun setUpdateDownload(
        context: Context,
        downloadId: Long,
        path: String,
        sha256: String?
    ) {
        preferences(context).edit()
            .putLong(KEY_UPDATE_DOWNLOAD_ID, downloadId)
            .putString(KEY_UPDATE_DOWNLOAD_PATH, path)
            .putString(KEY_UPDATE_SHA256, sha256)
            .apply()
    }

    fun updateDownloadPath(context: Context): String? =
        preferences(context).getString(KEY_UPDATE_DOWNLOAD_PATH, null)

    fun updateSha256(context: Context): String? =
        preferences(context).getString(KEY_UPDATE_SHA256, null)

    fun clearUpdateDownload(context: Context) {
        preferences(context).edit()
            .remove(KEY_UPDATE_DOWNLOAD_ID)
            .remove(KEY_UPDATE_DOWNLOAD_PATH)
            .remove(KEY_UPDATE_SHA256)
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
