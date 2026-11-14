package com.tabletwake

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WakeMonitorService : Service(), LifecycleOwner, SensorEventListener {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeSensor: Sensor? = null
    private var monitoringMode: DetectionMode? = null
    @Volatile
    private var lastWakeElapsed = 0L
    @Volatile
    private var pauseUntilElapsed = 0L
    private var generation = 0
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            pauseUntilElapsed = SystemClock.elapsedRealtime() +
                AppPreferences.screenOffPauseSeconds(this@WakeMonitorService) * 1_000L
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        sensorManager = getSystemService(SensorManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        cameraExecutor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AppPreferences.setMonitoringEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        val mode = AppPreferences.mode(this)
        val hasCameraPermission = mode != DetectionMode.CAMERA || hasCameraPermission()
        RestartNotifier.cancel(this)
        startInForeground(mode, hasCameraPermission)
        if (!lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        startMonitoring(mode)
        return if (mode == DetectionMode.PROXIMITY) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        generation++
        unregisterReceiver(screenStateReceiver)
        sensorManager.unregisterListener(this)
        cameraProvider?.unbindAll()
        cameraExecutor.shutdownNow()
        activeSensor = null
        monitoringMode = null
        isRunning = false
        activeMode = null
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun startMonitoring(mode: DetectionMode) {
        generation++
        sensorManager.unregisterListener(this)
        cameraProvider?.unbindAll()
        activeSensor = null
        monitoringMode = mode
        isRunning = true
        activeMode = mode
        AppPreferences.setLastError(this, null)

        when (mode) {
            DetectionMode.PROXIMITY -> startProximityMonitoring()
            DetectionMode.CAMERA -> startCameraMonitoring(generation)
        }
    }

    private fun startProximityMonitoring() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensor == null) {
            fail(getString(R.string.error_no_proximity_sensor))
            return
        }

        activeSensor = sensor
        if (!sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            fail(getString(R.string.error_proximity_start_failed))
        }
    }

    private fun startCameraMonitoring(expectedGeneration: Int) {
        if (!hasCameraPermission()) {
            fail(getString(R.string.error_camera_permission_missing))
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (expectedGeneration != generation || !isRunning) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(320, 240),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(
                    cameraExecutor,
                    MotionFrameAnalyzer(
                        sensitivity = AppPreferences.sensitivity(this),
                        shouldAnalyze = { !powerManager.isInteractive },
                        onMotion = { attemptWake() }
                    )
                )

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
            } catch (error: Exception) {
                fail(
                    getString(
                        R.string.error_camera_unavailable,
                        error.message ?: getString(R.string.error_unknown)
                    )
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
        val distance = event.values.firstOrNull() ?: return
        if (distance < event.sensor.maximumRange) attemptWake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Synchronized
    private fun attemptWake() {
        val now = SystemClock.elapsedRealtime()
        if (now < pauseUntilElapsed) return
        if (now - lastWakeElapsed < WAKE_COOLDOWN_MS) return
        if (WakeController.wakeScreen(this)) {
            lastWakeElapsed = now
            AppPreferences.setLastWake(this, System.currentTimeMillis())
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startInForeground(mode: DetectionMode, hasCameraPermission: Boolean) {
        val notification = buildNotification(mode)
        val useCameraFgs = mode == DetectionMode.CAMERA && hasCameraPermission
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val type = if (useCameraFgs) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIFICATION_ID, notification, type)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && useCameraFgs ->
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )

            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(mode: DetectionMode): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, WakeMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val modeLabel = when (mode) {
            DetectionMode.CAMERA -> getString(R.string.service_mode_camera)
            DetectionMode.PROXIMITY -> getString(R.string.service_mode_proximity)
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(modeLabel)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.service_action_stop),
                    stopIntent
                ).build()
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun fail(message: String) {
        AppPreferences.setLastError(this, message)
        isRunning = false
        activeMode = null
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "wake_monitor"
        private const val NOTIFICATION_ID = 41
        private const val WAKE_COOLDOWN_MS = 20_000L
        internal const val ACTION_START = "com.tabletwake.action.START"
        private const val ACTION_STOP = "com.tabletwake.action.STOP"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var activeMode: DetectionMode? = null
            private set

        fun start(context: Context, persist: Boolean = true) {
            if (persist) AppPreferences.setMonitoringEnabled(context, true)
            val intent = Intent(context, WakeMonitorService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, persist: Boolean = true) {
            if (persist) AppPreferences.setMonitoringEnabled(context, false)
            context.stopService(Intent(context, WakeMonitorService::class.java))
        }
    }
}
