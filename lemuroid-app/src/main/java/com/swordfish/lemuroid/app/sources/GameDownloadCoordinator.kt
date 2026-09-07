package com.swordfish.lemuroid.app.sources

import android.content.Context
import android.content.Intent
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

sealed interface GameCatalogDownloadState {
    data class Active(
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val speedBytesPerSecond: Double,
        val etaSeconds: Long?,
    ) : GameCatalogDownloadState

    data class Paused(
        val totalBytes: Long,
    ) : GameCatalogDownloadState

    data class WaitingRetry(
        val attempt: Int,
        val delayMillis: Long,
        val error: String,
    ) : GameCatalogDownloadState

    data class Completed(
        val gameId: Int,
        val filePath: String,
    ) : GameCatalogDownloadState

    data class Failed(
        val error: String,
        val retryable: Boolean,
    ) : GameCatalogDownloadState

    data object Cancelled : GameCatalogDownloadState
}

/**
 * App-scoped owner of [SourceGame] downloads. Downloads survive leaving the screen, stream to
 * disk, support a foreground service for background progress, and hand the finished file over to
 * [DownloadedGameImporter].
 */
class GameDownloadCoordinator(
    private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val directoriesManager: DirectoriesManager,
    private val downloadedGameImporter: DownloadedGameImporter,
) {
    private val downloader = GameFileDownloader(okHttpClient)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states =
        MutableStateFlow<Map<String, GameCatalogDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, GameCatalogDownloadState>> = _states.asStateFlow()

    private val controls = ConcurrentHashMap<String, DownloadControl>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val resumeSignals = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val titles = ConcurrentHashMap<String, String>()

    fun key(
        sourceId: String,
        gameId: String,
    ): String = "$sourceId/$gameId"

    fun titleFor(key: String): String? = titles[key]

    fun start(
        source: InstalledSource,
        game: SourceGame,
    ) {
        val key = key(source.id, game.id)
        if (controls.containsKey(key)) return

        val control = DownloadControl()
        controls[key] = control
        titles[key] = game.title
        _states.value = _states.value + (key to GameCatalogDownloadState.Active(0, game.size, 0.0, null))

        val job =
            scope.launch {
                runDownload(key, source, game, control)
            }
        jobs[key] = job
        startServiceForDownload()
    }

    private fun startServiceForDownload() {
        kotlin.runCatching {
            val intent = Intent(appContext, GameSourceDownloadService::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startService(intent)
        }
    }

    private suspend fun runDownload(
        key: String,
        source: InstalledSource,
        game: SourceGame,
        control: DownloadControl,
    ) {
        val url =
            game.downloadUrl
                ?: run {
                    fail(key, "The catalog has no download URL for '${game.title}'", false)
                    return
                }
        if (!SourceValidation.isAllowedDownloadUrl(url)) {
            fail(key, "The download URL for '${game.title}' is not allowed", false)
            return
        }

        val system =
            SystemResolver.resolve(game.system)
                ?: run {
                    fail(key, "Unsupported system '${game.system}'", false)
                    return
                }

        val destDir = File(directoriesManager.getDownloadedGamesDirectory(), system.directoryName)
        destDir.mkdirs()
        val safeBase = SourceValidation.sanitizeFileName(game.title)
        val finalName = "$safeBase.${game.format.lowercase()}"
        val partFile = File(destDir, "$finalName.part")
        val finalFile = File(destDir, finalName)

        try {
            while (true) {
                val outcome =
                    downloader.download(
                        url = url,
                        partFile = partFile,
                        expectedSize = game.size,
                        expectedSha256 = game.sha256,
                        maxAttempts = 4,
                        shouldPause = { control.paused },
                        onProgress = {
                            setState(
                                key,
                                GameCatalogDownloadState.Active(
                                    it.downloadedBytes,
                                    it.totalBytes,
                                    it.speedBytesPerSecond,
                                    it.etaSeconds,
                                ),
                            )
                        },
                        onRetry = { attempt, delayMillis, error ->
                            setState(
                                key,
                                GameCatalogDownloadState.WaitingRetry(
                                    attempt,
                                    delayMillis,
                                    error,
                                ),
                            )
                        },
                    )

                when (outcome) {
                    is GameDownloadOutcome.Complete -> {
                        val gameId =
                            downloadedGameImporter.import(source, game, partFile)
                        val completed = GameCatalogDownloadState.Completed(gameId, finalFile.path)
                        _states.value = _states.value + (key to completed)
                        controls.remove(key)
                        jobs.remove(key)
                        resumeSignals.remove(key)
                        return
                    }
                    is GameDownloadOutcome.Paused -> {
                        val total = outcome.totalBytes
                        _states.value = _states.value + (key to GameCatalogDownloadState.Paused(total))
                        awaitResume(key)
                    }
                    is GameDownloadOutcome.Failed -> {
                        partFile.delete()
                        fail(key, outcome.reason, outcome.retryable)
                        return
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                partFile.delete()
                fail(key, e.message ?: "download failed", true)
            } else {
                partFile.delete()
                _states.value = _states.value + (key to GameCatalogDownloadState.Cancelled)
                controls.remove(key)
                jobs.remove(key)
                resumeSignals.remove(key)
            }
        }
    }

    private fun setState(
        key: String,
        state: GameCatalogDownloadState,
    ) {
        _states.value = _states.value + (key to state)
    }

    private fun fail(
        key: String,
        error: String,
        retryable: Boolean,
    ) {
        _states.value = _states.value + (key to GameCatalogDownloadState.Failed(error, retryable))
        controls.remove(key)
        jobs.remove(key)
        resumeSignals.remove(key)
    }

    private suspend fun awaitResume(key: String) {
        val deferred = CompletableDeferred<Unit>()
        resumeSignals[key] = deferred
        try {
            deferred.await()
        } finally {
            resumeSignals.remove(key)
        }
    }

    fun pause(key: String) {
        controls[key]?.paused = true
    }

    fun resume(key: String) {
        val control = controls[key] ?: return
        control.paused = false
        resumeSignals[key]?.complete(Unit)
    }

    fun cancel(key: String) {
        controls[key]?.paused = true
        jobs[key]?.cancel()
    }

    fun isActive(key: String): Boolean {
        return jobs.containsKey(key) && controls.containsKey(key)
    }

    fun activeCount(): Int = jobs.values.count { it.isActive }

    fun firstActiveKey(): String? = jobs.keys.firstOrNull { isActive(it) }

    /** Convenience for the notification: (downloaded, total, fraction). */
    fun progressFor(key: String): Triple<Long, Long?, Float> {
        val state = _states.value[key]
        if (state is GameCatalogDownloadState.Active) {
            val total = state.totalBytes
            val fraction =
                if (total != null && total > 0) {
                    (state.downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            return Triple(state.downloadedBytes, total, fraction)
        }
        return Triple(0L, null, 0f)
    }

    private class DownloadControl {
        @Volatile
        var paused: Boolean = false
    }
}
