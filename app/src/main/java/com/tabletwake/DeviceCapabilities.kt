package com.tabletwake

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager

data class DeviceCapabilities(
    val hasFrontCamera: Boolean,
    val proximitySensor: Sensor?
) {
    companion object {
        fun read(context: Context): DeviceCapabilities {
            val sensors = context.getSystemService(SensorManager::class.java)
            val wakeUpProximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            val anyProximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            return DeviceCapabilities(
                hasFrontCamera = context.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_CAMERA_FRONT
                ),
                proximitySensor = wakeUpProximity ?: anyProximity
            )
        }
    }
}
