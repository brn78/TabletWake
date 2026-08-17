package com.tabletwake

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var capabilities: DeviceCapabilities
    private lateinit var statusDot: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var cameraMode: RadioButton
    private lateinit var proximityMode: RadioButton
    private lateinit var modeGroup: RadioGroup
    private lateinit var capabilitiesText: TextView
    private lateinit var sensitivityCard: LinearLayout
    private lateinit var sensitivityTitle: TextView
    private lateinit var sensitivity: SeekBar
    private lateinit var screenPauseTitle: TextView
    private lateinit var screenPause: SeekBar
    private lateinit var screenTimeoutText: TextView
    private lateinit var automaticUpdates: Switch
    private lateinit var updateStatus: TextView
    private lateinit var startStopButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var pendingStart = false

    private val refreshStatus = object : Runnable {
        override fun run() {
            renderStatus()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        configureInsets()
        bindViews()
        capabilities = DeviceCapabilities.read(this)
        configureCapabilities()
        configureSettings()
        configureActions()
        renderStatus()
        window.decorView.post { configureAutomaticUpdatesSafely() }
    }

    override fun onStart() {
        super.onStart()
        handler.post(refreshStatus)
    }

    override fun onResume() {
        super.onResume()
        renderScreenTimeout()
        resumePersistentMonitoring()
    }

    override fun onStop() {
        handler.removeCallbacks(refreshStatus)
        super.onStop()
    }

    private fun configureInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun bindViews() {
        statusDot = findViewById(R.id.statusDot)
        statusTitle = findViewById(R.id.statusTitle)
        statusDetail = findViewById(R.id.statusDetail)
        cameraMode = findViewById(R.id.cameraMode)
        proximityMode = findViewById(R.id.proximityMode)
        modeGroup = findViewById(R.id.modeGroup)
        capabilitiesText = findViewById(R.id.capabilitiesText)
        sensitivityCard = findViewById(R.id.sensitivityCard)
        sensitivityTitle = findViewById(R.id.sensitivityTitle)
        sensitivity = findViewById(R.id.sensitivity)
        screenPauseTitle = findViewById(R.id.screenPauseTitle)
        screenPause = findViewById(R.id.screenPause)
        screenTimeoutText = findViewById(R.id.screenTimeoutText)
        automaticUpdates = findViewById(R.id.automaticUpdates)
        updateStatus = findViewById(R.id.updateStatus)
        startStopButton = findViewById(R.id.startStopButton)
    }

    private fun configureCapabilities() {
        cameraMode.isEnabled = capabilities.hasFrontCamera
        proximityMode.isEnabled = capabilities.proximitySensor != null

        val cameraLabel = getString(
            if (capabilities.hasFrontCamera) R.string.camera_available else R.string.camera_unavailable
        )
        val proximityLabel = capabilities.proximitySensor?.let {
            val wakeUp = if (it.isWakeUpSensor) getString(R.string.proximity_wake_up) else ""
            getString(R.string.proximity_available, formatRange(it.maximumRange), wakeUp)
        } ?: getString(R.string.camera_unavailable)
        capabilitiesText.text = getString(
            R.string.capabilities_format,
            cameraLabel,
            proximityLabel
        )

        val savedMode = AppPreferences.mode(this)
        val usableMode = when {
            savedMode == DetectionMode.CAMERA && capabilities.hasFrontCamera -> savedMode
            savedMode == DetectionMode.PROXIMITY && capabilities.proximitySensor != null -> savedMode
            capabilities.hasFrontCamera -> DetectionMode.CAMERA
            else -> DetectionMode.PROXIMITY
        }
        selectMode(usableMode)
        AppPreferences.setMode(this, usableMode)
    }

    private fun configureSettings() {
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.proximityMode) {
                DetectionMode.PROXIMITY
            } else {
                DetectionMode.CAMERA
            }
            AppPreferences.setMode(this, mode)
            sensitivityCard.visibility = if (mode == DetectionMode.CAMERA) View.VISIBLE else View.GONE
        }

        sensitivity.progress = AppPreferences.sensitivity(this)
        sensitivityTitle.text = getString(R.string.sensitivity_format, sensitivity.progress)
        sensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sensitivityTitle.text = getString(R.string.sensitivity_format, progress)
                if (fromUser) AppPreferences.setSensitivity(this@MainActivity, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        sensitivityCard.visibility =
            if (selectedMode() == DetectionMode.CAMERA) View.VISIBLE else View.GONE

        screenPause.progress = AppPreferences.screenOffPauseSeconds(this)
        screenPauseTitle.text = formatScreenPause(screenPause.progress)
        screenPause.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                screenPauseTitle.text = formatScreenPause(progress)
                if (fromUser) AppPreferences.setScreenOffPauseSeconds(this@MainActivity, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        automaticUpdates.isChecked = AppPreferences.automaticUpdates(this)
        automaticUpdates.setOnCheckedChangeListener { _, enabled ->
            AppPreferences.setAutomaticUpdates(this, enabled)
            configureAutomaticUpdatesSafely()
        }
        updateStatus.setText(
            if (automaticUpdates.isChecked) R.string.updates_enabled else R.string.updates_disabled
        )
    }

    private fun configureActions() {
        startStopButton.setOnClickListener {
            if (WakeMonitorService.isRunning) {
                WakeMonitorService.stop(this)
            } else {
                requestPermissionsAndStart()
            }
            handler.postDelayed({ renderStatus() }, 250L)
        }

        findViewById<Button>(R.id.testButton).setOnClickListener {
            Toast.makeText(
                this,
                getString(R.string.test_instructions),
                Toast.LENGTH_LONG
            ).show()
            handler.postDelayed({
                if (!WakeController.wakeScreen(applicationContext)) {
                    runOnUiThread {
                        Toast.makeText(this, R.string.screen_already_on, Toast.LENGTH_SHORT).show()
                    }
                }
            }, 5_000L)
        }

        findViewById<Button>(R.id.batteryButton).setOnClickListener {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }

        findViewById<Button>(R.id.displaySettingsButton).setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
                .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }

        findViewById<Button>(R.id.updateButton).setOnClickListener {
            updateStatus.setText(R.string.updates_checking)
            AppUpdater.check(this, automaticDownload = true) { result ->
                runOnUiThread {
                    updateStatus.text = result.message
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        findViewById<TextView>(R.id.repositoryLink).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdater.REPOSITORY_URL)))
        }

        findViewById<TextView>(R.id.copyrightText).setOnClickListener {
            val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:b.leonardi78@gmail.com"))
                .putExtra(Intent.EXTRA_SUBJECT, "Tablet Wake")
            runCatching { startActivity(email) }.onFailure {
                Toast.makeText(this, R.string.email_app_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissionsAndStart() {
        val requested = mutableListOf<String>()
        if (selectedMode() == DetectionMode.CAMERA &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            requested += Manifest.permission.CAMERA
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requested += Manifest.permission.POST_NOTIFICATIONS
        }

        if (requested.isEmpty()) {
            WakeMonitorService.start(this)
        } else {
            pendingStart = true
            requestPermissions(requested.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST || !pendingStart) return
        pendingStart = false

        val cameraReady = selectedMode() != DetectionMode.CAMERA ||
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (cameraReady) {
            WakeMonitorService.start(this)
        } else {
            Toast.makeText(
                this,
                getString(R.string.camera_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun renderStatus() {
        val running = WakeMonitorService.isRunning
        val controlsEnabled = !running
        modeGroup.isEnabled = controlsEnabled
        cameraMode.isEnabled = controlsEnabled && capabilities.hasFrontCamera
        proximityMode.isEnabled = controlsEnabled && capabilities.proximitySensor != null
        sensitivity.isEnabled = controlsEnabled
        screenPause.isEnabled = controlsEnabled
        startStopButton.setText(if (running) R.string.stop_detection else R.string.start_detection)

        if (running) {
            statusDot.setTextColor(getColor(R.color.success))
            statusTitle.setText(R.string.status_running)
            val modeLabel = when (WakeMonitorService.activeMode) {
                DetectionMode.CAMERA -> getString(R.string.status_camera_waiting)
                DetectionMode.PROXIMITY -> getString(R.string.status_proximity_waiting)
                null -> getString(R.string.status_starting)
            }
            statusDetail.text = appendLastWake(modeLabel)
            return
        }

        val error = AppPreferences.lastError(this)
        statusDot.setTextColor(getColor(if (error == null) R.color.text_secondary else R.color.warning))
        statusTitle.setText(
            if (error == null) R.string.status_stopped else R.string.status_check_configuration
        )
        statusDetail.text = error ?: appendLastWake(getString(R.string.status_choose_mode))
    }

    private fun renderScreenTimeout() {
        val milliseconds = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        }.getOrDefault(-1)
        screenTimeoutText.text = if (milliseconds > 0) {
            getString(R.string.screen_timeout_current, formatDuration(milliseconds / 1_000))
        } else {
            getString(R.string.screen_timeout_unknown)
        }
    }

    private fun configureAutomaticUpdatesSafely() {
        val enabled = AppPreferences.automaticUpdates(this)
        val configured = AppUpdater.configurePeriodicChecks(applicationContext)
        updateStatus.setText(
            when {
                !enabled -> R.string.updates_disabled
                configured -> R.string.updates_enabled
                else -> R.string.updates_scheduler_unavailable
            }
        )
    }

    private fun resumePersistentMonitoring() {
        if (!AppPreferences.monitoringEnabled(this) || WakeMonitorService.isRunning) return
        val mode = AppPreferences.mode(this)
        if (mode == DetectionMode.CAMERA &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { WakeMonitorService.start(this, persist = false) }
    }

    private fun formatDuration(totalSeconds: Int): String {
        if (totalSeconds < 60) return resources.getQuantityString(
            R.plurals.seconds_count,
            totalSeconds,
            totalSeconds
        )
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val minuteText = resources.getQuantityString(R.plurals.minutes_count, minutes, minutes)
        return if (seconds == 0) minuteText else "$minuteText e $seconds s"
    }

    private fun formatScreenPause(seconds: Int): String = getString(
        R.string.screen_pause_title_format,
        resources.getQuantityString(R.plurals.seconds_count, seconds, seconds)
    )

    private fun appendLastWake(prefix: String): String {
        val timestamp = AppPreferences.lastWake(this)
        if (timestamp == 0L) return prefix
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
        return getString(R.string.status_last_wake, prefix, time)
    }

    private fun selectedMode(): DetectionMode =
        if (modeGroup.checkedRadioButtonId == R.id.proximityMode) {
            DetectionMode.PROXIMITY
        } else {
            DetectionMode.CAMERA
        }

    private fun selectMode(mode: DetectionMode) {
        modeGroup.check(if (mode == DetectionMode.CAMERA) R.id.cameraMode else R.id.proximityMode)
    }

    private fun formatRange(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

    companion object {
        private const val PERMISSION_REQUEST = 100
    }
}
