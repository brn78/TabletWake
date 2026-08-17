package com.tabletwake

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

data class UpdateResult(
    val available: Boolean,
    val versionName: String? = null,
    val message: String
)

object AppUpdater {
    const val REPOSITORY_URL = "https://github.com/brn78/TabletWake"
    private const val VERSION_MANIFEST_URL =
        "https://raw.githubusercontent.com/brn78/TabletWake/main/version.json"
    private const val UPDATE_JOB_ID = 78
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1_000L
    private val executor = Executors.newSingleThreadExecutor()

    fun configurePeriodicChecks(context: Context): Boolean = runCatching {
        val appContext = context.applicationContext
        val scheduler = appContext.getSystemService(JobScheduler::class.java)
        if (!AppPreferences.automaticUpdates(appContext)) {
            scheduler.cancel(UPDATE_JOB_ID)
            return@runCatching true
        }
        val job = JobInfo.Builder(
            UPDATE_JOB_ID,
            ComponentName(appContext, UpdateJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(CHECK_INTERVAL_MS)
            .setPersisted(true)
            .build()
        scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }.getOrDefault(false)

    fun check(
        context: Context,
        automaticDownload: Boolean,
        callback: (UpdateResult) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching {
                val manifest = readManifest()
                val currentCode = PackageInfoCompat.getLongVersionCode(
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                )
                if (manifest.versionCode <= currentCode) {
                    UpdateResult(false, manifest.versionName, "Tablet Wake è aggiornato.")
                } else if (automaticDownload) {
                    enqueueDownload(appContext, manifest)
                    UpdateResult(
                        true,
                        manifest.versionName,
                        "Aggiornamento ${manifest.versionName} in download."
                    )
                } else {
                    UpdateResult(
                        true,
                        manifest.versionName,
                        "È disponibile Tablet Wake ${manifest.versionName}."
                    )
                }
            }.getOrElse {
                UpdateResult(false, message = "Controllo non riuscito: ${it.message ?: "errore di rete"}")
            }
            callback(result)
        }
    }

    private fun readManifest(): UpdateManifest {
        val connection = URL("$VERSION_MANIFEST_URL?ts=${System.currentTimeMillis()}")
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "TabletWake-Android")
            check(connection.responseCode in 200..299) {
                "server ${connection.responseCode}"
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val apkUrl = json.getString("apkUrl")
            check(Uri.parse(apkUrl).scheme == "https") { "indirizzo APK non sicuro" }
            UpdateManifest(
                versionCode = json.getLong("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = apkUrl,
                sha256 = json.optString("sha256").takeIf { it.length == 64 }
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun enqueueDownload(context: Context, manifest: UpdateManifest) {
        if (AppPreferences.updateDownloadId(context) >= 0L) return
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("archivio download non disponibile")
        val fileName = "TabletWake-${manifest.versionName.replace(Regex("[^0-9A-Za-z._-]"), "_")}.apk"
        val destination = File(directory, fileName)
        if (destination.exists() && !destination.delete()) {
            error("impossibile sostituire il download precedente")
        }

        val request = DownloadManager.Request(Uri.parse(manifest.apkUrl))
            .setTitle("Tablet Wake ${manifest.versionName}")
            .setDescription("Download aggiornamento")
            .setMimeType(APK_MIME_TYPE)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
        val id = context.getSystemService(DownloadManager::class.java).enqueue(request)
        AppPreferences.setUpdateDownload(context, id, destination.absolutePath, manifest.sha256)
    }

    private data class UpdateManifest(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val sha256: String?
    )

    internal const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}

class UpdateJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        AppUpdater.check(this, automaticDownload = true) {
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val expectedId = AppPreferences.updateDownloadId(context)
        if (expectedId < 0L || intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != expectedId) {
            return
        }

        val manager = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(expectedId)
        manager.query(query).use { cursor ->
            if (!cursor.moveToFirst() || cursor.status() != DownloadManager.STATUS_SUCCESSFUL) {
                AppPreferences.clearUpdateDownload(context)
                return
            }
        }

        val path = AppPreferences.updateDownloadPath(context) ?: return
        val apk = File(path)
        val expectedHash = AppPreferences.updateSha256(context)
        if (!apk.isFile || expectedHash != null && sha256(apk) != expectedHash.lowercase()) {
            apk.delete()
            AppPreferences.clearUpdateDownload(context)
            showFailure(context)
            return
        }

        showInstallNotification(context, apk)
        AppPreferences.clearUpdateDownload(context)
    }

    private fun Cursor.status(): Int =
        getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun showInstallNotification(context: Context, apk: File) {
        val manager = context.getSystemService(NotificationManager::class.java)
        createChannel(manager)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, AppUpdater.APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            78,
            installIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Aggiornamento pronto")
            .setContentText("Tocca per installare la nuova versione di Tablet Wake.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(UPDATE_NOTIFICATION_ID, notification) }
    }

    private fun showFailure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        createChannel(manager)
        val notification = Notification.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Aggiornamento non valido")
            .setContentText("Il controllo di integrità dell’APK non è riuscito.")
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(UPDATE_NOTIFICATION_ID, notification) }
    }

    private fun createChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL_ID,
                "Aggiornamenti Tablet Wake",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Download e installazione degli aggiornamenti"
            }
        )
    }

    companion object {
        private const val UPDATE_CHANNEL_ID = "app_updates"
        private const val UPDATE_NOTIFICATION_ID = 78
    }
}
