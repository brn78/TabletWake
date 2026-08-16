package com.tabletwake

enum class DetectionMode(val storedValue: String) {
    CAMERA("camera"),
    PROXIMITY("proximity");

    companion object {
        fun fromStoredValue(value: String?): DetectionMode =
            entries.firstOrNull { it.storedValue == value } ?: CAMERA
    }
}
