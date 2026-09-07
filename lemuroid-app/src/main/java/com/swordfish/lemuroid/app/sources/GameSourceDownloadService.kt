package com.swordfish.lemuroid.app.sources

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationManagerCompat
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import dagger.android.DaggerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps catalog downloads running and visible while the user leaves the app. Started when a
 * download begins and stopped once no download is active anymore.
 */
class GameSourceDownloadService : DaggerService() {
    @Inject
    lateinit var coordinator: GameDownloadCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        stateJob =
            scope.launch {
                while (true) {
                    delay(500)
                    updateNotification()
                }
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        updateNotification()
        return START_STICKY
    }

    private fun updateNotification() {
        val count = coordinator.activeCount()
        if (count == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val key = coordinator.firstActiveKey()
        val title = coordinator.titleFor(key)
        val (downloaded, total, fraction) = coordinator.progressFor(key)
        val notification =
            NotificationsManager(applicationContext)
                .gameSourceDownloadNotification(count, title, fraction)

        if (count > 0 && !foregroundStarted) {
            startForeground(
                NotificationsManager.GAME_SOURCE_DOWNLOAD_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            foregroundStarted = true
        } else {
            NotificationManagerCompat.from(this)
                .notify(NotificationsManager.GAME_SOURCE_DOWNLOAD_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        stateJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private var foregroundStarted = false
}
