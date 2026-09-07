package com.swordfish.lemuroid.app.sources

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GameFileDownloaderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var downloader: GameFileDownloader
    private lateinit var partFile: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        downloader = GameFileDownloader(client)
        val dir = tempFolder.newFolder("downloads")
        partFile = File(dir, "game.bin.part")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `downloads a small file completely`() {
        runBlocking {
            val payload = "hello world".repeat(10).toByteArray()
            server.enqueue(MockResponse().setBody(String(payload)))

            val outcome = downloader.download(server.url("/game").toString(), partFile)

            assertTrue(outcome is GameDownloadOutcome.Complete)
            assertEquals(payload.size.toLong(), (outcome as GameDownloadOutcome.Complete).totalBytes)
            assertEquals(payload.size.toLong(), partFile.length())
            assertEquals(String(payload), partFile.readText())
        }
    }

    @Test
    fun `resumes from an existing range-prefixed part file`() {
        runBlocking {
            val payload = "0123456789".repeat(50).toByteArray()
            partFile.writeBytes(payload.copyOfRange(0, 100))
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 100-499/500")
                    .setBody(String(payload.copyOfRange(100, 500))),
            )

            val outcome = downloader.download(server.url("/game").toString(), partFile)

            assertTrue(outcome is GameDownloadOutcome.Complete)
            assertEquals(payload.size.toLong(), partFile.length())
        }
    }

    @Test
    fun `retries transient failures then succeeds`() {
        runBlocking {
            val payload = "retry me".toByteArray()
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setBody(String(payload)))

            val outcome = downloader.download(server.url("/game").toString(), partFile, maxAttempts = 3)

            assertTrue(outcome is GameDownloadOutcome.Complete)
            assertEquals(String(payload), partFile.readText())
        }
    }

    @Test
    fun `fails permanently on non retryable http errors`() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(404))

            val outcome = downloader.download(server.url("/game").toString(), partFile, maxAttempts = 3)

            assertTrue(outcome is GameDownloadOutcome.Failed)
            assertTrue((outcome as GameDownloadOutcome.Failed).reason.contains("404"))
        }
    }

    @Test
    fun `detects size mismatch and cleans up`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("short"))

            val outcome = downloader.download(server.url("/game").toString(), partFile, expectedSize = 999)

            assertTrue(outcome is GameDownloadOutcome.Failed)
            assertTrue((outcome as GameDownloadOutcome.Failed).reason.contains("size mismatch"))
            assertTrue(!partFile.exists() || partFile.length() == 0L)
        }
    }

    @Test
    fun `verifies sha256 checksum`() {
        runBlocking {
            val payload = "sha-256 content".toByteArray()
            server.enqueue(MockResponse().setBody(String(payload)))
            val expected = sha256Of(payload)

            val good = downloader.download(server.url("/game").toString(), partFile, expectedSha256 = expected)
            assertTrue(good is GameDownloadOutcome.Complete)

            server.enqueue(MockResponse().setBody(String(payload)))
            val bad = downloader.download(server.url("/game").toString(), partFile, expectedSha256 = "0".repeat(64))
            assertTrue(bad is GameDownloadOutcome.Failed)
        }
    }

    @Test
    fun `pauses and preserves the partial part file`() {
        runBlocking {
            partFile.writeBytes("partial".toByteArray())
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 7-99/100")
                    .setBody("rest of the content"),
            )

            val outcome =
                downloader.download(
                    url = server.url("/game").toString(),
                    partFile = partFile,
                    shouldPause = { true },
                )

            assertTrue(outcome is GameDownloadOutcome.Paused)
            assertEquals("partial".length.toLong(), partFile.length())
        }
    }

    private fun sha256Of(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
