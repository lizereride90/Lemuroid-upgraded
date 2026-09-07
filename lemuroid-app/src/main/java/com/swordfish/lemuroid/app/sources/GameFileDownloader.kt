package com.swordfish.lemuroid.app.sources

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSink
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface GameDownloadOutcome {
    data class Complete(val totalBytes: Long) : GameDownloadOutcome

    /** The downloader stopped because [GameFileDownloader.download]'s `shouldPause` was true. */
    data class Paused(val totalBytes: Long) : GameDownloadOutcome

    data class Failed(val reason: String, val retryable: Boolean) : GameDownloadOutcome
}

data class GameDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speedBytesPerSecond: Double,
    val etaSeconds: Long?,
)

/**
 * Streaming HTTP(S) downloader. Never buffers a whole file in memory: bytes are streamed straight
 * to disk, optional Range-based resume is attempted from the existing part file, transient failures
 * are retried with backoff, and optional size/SHA-256 checks are verified before success.
 */
class GameFileDownloader(private val client: OkHttpClient) {
    companion object {
        private const val CHUNK_SIZE = 64L * 1024
        private const val PROGRESS_INTERVAL_MS = 200L
        private const val MAX_BACKOFF_MS = 30_000L
        private val RETRYABLE_STATUS = setOf(408, 429, 500, 502, 503, 504)
    }

    suspend fun download(
        url: String,
        partFile: File,
        expectedSize: Long? = null,
        expectedSha256: String? = null,
        maxAttempts: Int = 3,
        shouldPause: () -> Boolean = { false },
        onProgress: (GameDownloadProgress) -> Unit = {},
        onRetry: (attempt: Int, delayMillis: Long, error: String) -> Unit = { _, _, _ -> },
    ): GameDownloadOutcome {
        partFile.parentFile?.mkdirs()

        var resumeOffset = if (partFile.exists()) partFile.length() else 0L

        // Already complete from a previous run.
        if (expectedSize != null && resumeOffset >= expectedSize) {
            resumeOffset = expectedSize
            return finish(partFile, resumeOffset, expectedSize, expectedSha256)
        }

        repeat(maxAttempts) { attempt ->
            currentCoroutineContext().ensureActive()
            val outcome =
                try {
                    downloadOnce(url, partFile, resumeOffset, expectedSize, shouldPause, onProgress)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        throw e
                    }
                    val retryable = isRetryableError(e)
                    if (retryable && attempt < maxAttempts - 1) {
                        val backoff = backoffMillis(attempt)
                        onRetry(attempt + 1, backoff, e.message ?: "network error")
                        delay(backoff)
                        return@repeat
                    }
                    return GameDownloadOutcome.Failed(e.message ?: "download failed", retryable)
                }

            when (outcome) {
                is GameDownloadOutcome.Complete ->
                    return finish(partFile, outcome.totalBytes, expectedSize, expectedSha256)
                is GameDownloadOutcome.Paused -> return outcome
                is GameDownloadOutcome.Failed -> {
                    if (!outcome.retryable || attempt >= maxAttempts - 1) {
                        return outcome
                    }
                    val backoff = backoffMillis(attempt)
                    onRetry(attempt + 1, backoff, outcome.reason)
                    delay(backoff)
                }
            }
        }

        return GameDownloadOutcome.Failed("download failed after $maxAttempts attempts", retryable = true)
    }

    private suspend fun downloadOnce(
        url: String,
        partFile: File,
        resumeOffset: Long,
        expectedSize: Long?,
        shouldPause: () -> Boolean,
        onProgress: (GameDownloadProgress) -> Unit,
    ): GameDownloadOutcome {
        val requestBuilder =
            Request.Builder().url(url).header("Accept-Encoding", "identity")
        if (resumeOffset > 0) {
            requestBuilder.header("Range", "bytes=$resumeOffset-")
        }
        val request = requestBuilder.build()

        val call = client.newCall(request)
        val response = executeCall(call)
        response.use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 416 && expectedSize != null && resumeOffset >= expectedSize) {
                    return GameDownloadOutcome.Complete(resumeOffset)
                }
                return GameDownloadOutcome.Failed("HTTP ${resp.code} ${resp.message}", resp.code in RETRYABLE_STATUS)
            }

            // Server ignored our Range header: restart from byte zero.
            var offset = resumeOffset
            if (resumeOffset > 0 && resp.code == 200) {
                offset = 0
                partFile.delete()
            }

            val body = resp.body ?: return GameDownloadOutcome.Failed("empty response body", retryable = true)

            val contentLength = body.contentLength().takeIf { it >= 0 }
            val totalBytes =
                expectedSize
                    ?: contentLength?.let { offset + it }
                    ?: parseTotalFromContentRange(resp)
                        ?.let { total -> total.coerceAtLeast(offset) }

            var downloaded = offset
            var lastEmit = System.currentTimeMillis()
            var lastEmitBytes = downloaded
            var speed = 0.0

            val sink: BufferedSink = partFile.sink(append = offset > 0).buffer()
            try {
                val source = body.source()
                while (true) {
                    if (shouldPause()) {
                        sink.flush()
                        return GameDownloadOutcome.Paused(downloaded)
                    }
                    currentCoroutineContext().ensureActive()
                    val buffer = okio.Buffer()
                    val read = source.read(buffer, CHUNK_SIZE)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    downloaded += read

                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= PROGRESS_INTERVAL_MS) {
                        val elapsedSeconds = (now - lastEmit).coerceAtLeast(1) / 1000.0
                        speed = (downloaded - lastEmitBytes) / elapsedSeconds
                        lastEmit = now
                        lastEmitBytes = downloaded
                        val remaining = totalBytes?.minus(downloaded) ?: 0L
                        val eta =
                            if (speed > 0 && totalBytes != null) {
                                (remaining / speed).toLong()
                            } else {
                                null
                            }
                        onProgress(
                            GameDownloadProgress(
                                downloadedBytes = downloaded,
                                totalBytes = totalBytes,
                                speedBytesPerSecond = speed,
                                etaSeconds = eta,
                            ),
                        )
                    }
                }
                sink.flush()
            } finally {
                sink.close()
                body.close()
            }

            onProgress(
                GameDownloadProgress(
                    downloadedBytes = downloaded,
                    totalBytes = totalBytes,
                    speedBytesPerSecond = speed,
                    etaSeconds = 0,
                ),
            )
            return GameDownloadOutcome.Complete(downloaded)
        }
    }

    private fun finish(
        partFile: File,
        downloadedBytes: Long,
        expectedSize: Long?,
        expectedSha256: String?,
    ): GameDownloadOutcome {
        if (expectedSize != null && downloadedBytes != expectedSize) {
            partFile.delete()
            return GameDownloadOutcome.Failed(
                "size mismatch: expected $expectedSize bytes, got $downloadedBytes",
                retryable = false,
            )
        }
        if (expectedSha256 != null) {
            val actual = sha256(partFile)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                partFile.delete()
                return GameDownloadOutcome.Failed("SHA-256 mismatch", retryable = false)
            }
        }
        return GameDownloadOutcome.Complete(downloadedBytes)
    }

    private suspend fun executeCall(call: Call): Response {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                call.cancel()
            }
            try {
                val response = call.execute()
                if (continuation.isCancelled) {
                    response.close()
                    throw kotlinx.coroutines.CancellationException("Download cancelled")
                }
                continuation.resume(response)
            } catch (e: Throwable) {
                val exception =
                    if (continuation.isCancelled) {
                        kotlinx.coroutines.CancellationException("Download cancelled", e)
                    } else {
                        e
                    }
                continuation.resumeWithException(exception)
            }
        }
    }

    private fun isRetryableError(e: Exception): Boolean {
        return e is java.io.IOException
    }

    private fun backoffMillis(attempt: Int): Long {
        val exp = 1L shl attempt.coerceAtMost(6) // 1,2,4,...64 (0-indexed attempt)
        return (exp * 1_000L).coerceAtMost(MAX_BACKOFF_MS)
    }

    private fun parseTotalFromContentRange(response: Response): Long? {
        val range = response.header("Content-Range") ?: return null
        val total = range.substringAfter('/', "").toLongOrNull() ?: return null
        return total.takeIf { it >= 0 }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
