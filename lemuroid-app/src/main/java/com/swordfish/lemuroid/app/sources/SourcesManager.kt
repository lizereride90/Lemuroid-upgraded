package com.swordfish.lemuroid.app.sources

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** A source plus its live catalog data, ready for the UI. */
data class LoadedSourceCatalog(
    val installed: InstalledSource,
    val manifest: SourceManifest,
    val games: List<SourceGame>,
)

/**
 * Context-aware façade over the source domain logic: enumerates installed sources, installs files
 * from the system picker, refreshes and deletes them, and exposes cover files to the UI.
 */
class SourcesManager(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
    private val registry: SourceRegistry,
    private val installer: SourceInstaller,
    private val okHttpClient: OkHttpClient,
) {
    private val _sources = MutableStateFlow<List<InstalledSource>>(emptyList())

    init {
        reload()
    }

    val sources: StateFlow<List<InstalledSource>> = _sources.asStateFlow()

    val sourcesRoot: File = directoriesManager.getGameSourcesDirectory()

    fun reload() {
        _sources.value = registry.load().sortedBy { it.name }
    }

    fun install(uri: Uri): InstalledSource {
        val tempZip = File(sourcesRoot, "import-${System.currentTimeMillis()}.zip")
        try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use { input ->
                tempZip.outputStream().use { output ->
                    copyStreamCapped(input, output)
                }
            } ?: throw SourceException.NotAZip("Could not open the selected file")

            val installed = installer.install(tempZip, sourcesRoot)
            reload()
            return installed
        } finally {
            tempZip.delete()
        }
    }

    fun installFromDownloadedZip(zipFile: File): InstalledSource {
        val installed = installer.install(zipFile, sourcesRoot)
        reload()
        return installed
    }

    /** Locally required catalog update; also re-downloads the package when sourceUrl is present. */
    suspend fun refresh(id: String): InstalledSource {
        val installed =
            registry.load().firstOrNull { it.id == id }
                ?: throw SourceException.MissingManifest("The source is not installed")
        val refreshed =
            if (installed.sourceUrl != null) {
                val zip = downloadSourceZip(installed.sourceUrl, id)
                try {
                    installer.updateFromZip(installed, zip, sourcesRoot)
                } finally {
                    zip.delete()
                }
            } else {
                installer.refreshLocally(installed, sourcesRoot)
            }
        reload()
        return refreshed
    }

    fun delete(id: String) {
        installer.remove(id)
        val dir = File(sourcesRoot, id)
        if (SourceValidation.isInside(sourcesRoot, dir)) {
            dir.deleteRecursively()
        }
        // Deliberately do NOT touch already downloaded games.
        reload()
    }

    fun loadCatalog(installed: InstalledSource): LoadedSourceCatalog {
        val dir = File(sourcesRoot, installed.id)
        val manifest = SourceCatalogLoader.readManifest(dir)
        val catalog = SourceCatalogLoader.readCatalog(dir, installed.catalogFile)
        return LoadedSourceCatalog(installed, manifest, catalog.games)
    }

    fun readManifest(installed: InstalledSource): SourceManifest {
        val dir = File(sourcesRoot, installed.id)
        return SourceCatalogLoader.readManifest(dir)
    }

    fun findInstalled(id: String): InstalledSource? =
        registry.load().firstOrNull { it.id == id }

    fun coverFile(
        installed: InstalledSource,
        game: SourceGame,
    ): File? {
        val coverPath = game.cover ?: return null
        if (!SourceValidation.isSafeRelativeEntry(coverPath)) return null
        val sourceDir = File(sourcesRoot, installed.id)
        val cover = File(sourceDir, coverPath.replace('\\', '/'))
        return if (SourceValidation.isInside(sourceDir, cover) && cover.isFile) cover else null
    }

    private fun copyStreamCapped(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > SourceValidation.MAX_TOTAL_UNCOMPRESSED_BYTES + 1024 * 1024) {
                throw SourceException.ArchiveTooLarge("The source ZIP is too large.")
            }
            output.write(buffer, 0, read)
        }
    }

    private suspend fun downloadSourceZip(url: String, sourceId: String): File {
        return withContext(Dispatchers.IO) {
            val target = File(sourcesRoot, "refresh-$sourceId.zip")
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SourceException.ExtractionFailed("Update download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw SourceException.ExtractionFailed("Update download returned no body")
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > SourceValidation.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw SourceException.ArchiveTooLarge("The downloaded source package is too large.")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            target
        }
    }
}