package com.swordfish.lemuroid.app.sources

import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/** Reads and validates the JSON files of an installed source directory. */
object SourceCatalogLoader {
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = false
        }

    fun readManifest(sourceDir: File): SourceManifest {
        val manifestFile = File(sourceDir, "source.json")
        if (!manifestFile.isFile) {
            throw SourceException.MissingManifest("The source is missing source.json")
        }
        if (manifestFile.length() > SourceValidation.MAX_MANIFEST_BYTES) {
            throw SourceException.InvalidManifest("source.json is too large")
        }
        val manifest =
            runCatching { json.decodeFromString<SourceManifest>(manifestFile.readText()) }
                .getOrElse { throw SourceException.InvalidManifest("source.json is not valid JSON") }

        SourceValidation.validateManifest(manifest)?.let {
            throw SourceException.InvalidManifest(it)
        }
        return manifest
    }

    fun readCatalog(sourceDir: File, catalogFile: String): SourceCatalog {
        val catalog = File(sourceDir, catalogFile)
        if (!catalog.isFile) {
            throw SourceException.MissingCatalog("The source is missing its catalog file '$catalogFile'")
        }
        if (catalog.length() > SourceValidation.MAX_CATALOG_BYTES) {
            throw SourceException.CatalogTooLarge("The catalog file is too large")
        }
        val parsed =
            runCatching { json.decodeFromString<SourceCatalog>(catalog.readText()) }
                .getOrElse { throw SourceException.InvalidCatalog("The catalog is not valid JSON") }

        SourceValidation.validateCatalog(parsed)?.let {
            throw SourceException.InvalidCatalog(it)
        }
        return parsed
    }
}

/**
 * Installs, refreshes and removes game sources. Each source lives in its own
 * `source_NNN` directory under the sources root and never writes anywhere else.
 */
class SourceInstaller(private val registry: SourceRegistry) {
    /** Installs [zipFile] into [sourcesRoot], rejecting duplicates and malformed packages. */
    fun install(zipFile: File, sourcesRoot: File): InstalledSource {
        if (!zipFile.isFile || !isZip(zipFile)) {
            throw SourceException.NotAZip("The selected file is not a valid ZIP archive")
        }

        // Validate the manifest *before* we create anything on disk.
        val manifest = readManifestFromZip(zipFile)
        val catalogFile = manifest.catalogFile
        if (!zipContainsEntry(zipFile, catalogFile)) {
            throw SourceException.MissingCatalog("The ZIP does not contain '$catalogFile'")
        }

        val existing = registry.load()
        val normalizedName = manifest.name.trim()
        val duplicate =
            existing.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }
        if (duplicate != null) {
            throw SourceException.DuplicateSource("A source named '${duplicate.name}' is already installed")
        }

        val targetDir = allocateDirectory(sourcesRoot, existing)
        try {
            SafeZipExtractor.extract(zipFile, targetDir)

            // Re-validate the extracted copies so a partial/broken extract is rejected too.
            val extractedManifest = SourceCatalogLoader.readManifest(targetDir)
            val catalog = SourceCatalogLoader.readCatalog(targetDir, extractedManifest.catalogFile)

            val now = System.currentTimeMillis()
            val installed =
                InstalledSource(
                    id = targetDir.name,
                    name = extractedManifest.name.trim(),
                    version = extractedManifest.version,
                    author = extractedManifest.author,
                    description = extractedManifest.description,
                    catalogFile = extractedManifest.catalogFile,
                    sourceUrl = extractedManifest.sourceUrl,
                    installedAt = now,
                    lastUpdated = now,
                    gameCount = catalog.games.size,
                )
            registry.update(installed)
            return installed
        } catch (e: Throwable) {
            targetDir.deleteRecursively()
            throw e
        }
    }

    /** Re-validates the stored files of an installed source and refreshes its registry entry. */
    fun refreshLocally(source: InstalledSource, sourcesRoot: File): InstalledSource {
        val targetDir = sourceDirectory(sourcesRoot, source.id)
        if (!targetDir.isDirectory) {
            throw SourceException.MissingManifest("The source directory is gone")
        }
        val manifest = SourceCatalogLoader.readManifest(targetDir)
        val catalog = SourceCatalogLoader.readCatalog(targetDir, manifest.catalogFile)

        val now = System.currentTimeMillis()
        val refreshed = source.copy(lastUpdated = now, gameCount = catalog.games.size)
        registry.update(refreshed)
        return refreshed
    }

    /** Replaces the content of an installed source from a freshly downloaded ZIP. */
    fun updateFromZip(source: InstalledSource, zipFile: File, sourcesRoot: File): InstalledSource {
        if (!zipFile.isFile || !isZip(zipFile)) {
            throw SourceException.NotAZip("The downloaded update is not a valid ZIP archive")
        }

        val targetDir = sourceDirectory(sourcesRoot, source.id)
        if (!targetDir.isDirectory) {
            throw SourceException.MissingManifest("The source directory is gone")
        }

        val stagingDir = File(sourcesRoot, "${source.id}.tmp")
        stagingDir.mkdirs()
        try {
            SafeZipExtractor.extract(zipFile, stagingDir)
            val manifest = SourceCatalogLoader.readManifest(stagingDir)
            val catalog = SourceCatalogLoader.readCatalog(stagingDir, manifest.catalogFile)
            val now = System.currentTimeMillis()

            if (manifest.name.equals(source.name, ignoreCase = true).not()) {
                throw SourceException.InvalidManifest(
                    "The update changes the source name ('${source.name}' -> '${manifest.name}')",
                )
            }

            // Swap content without touching the directory identity.
            targetDir.listFiles()?.forEach { it.deleteRecursively() }
            stagingDir.listFiles()?.forEach { it.copyRecursively(targetDir, overwrite = true) }

            val refreshed =
                source.copy(
                    name = manifest.name.trim(),
                    version = manifest.version,
                    author = manifest.author,
                    description = manifest.description,
                    catalogFile = manifest.catalogFile,
                    sourceUrl = manifest.sourceUrl,
                    lastUpdated = now,
                    gameCount = catalog.games.size,
                )
            registry.update(refreshed)
            return refreshed
        } catch (e: Throwable) {
            throw e
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    fun remove(id: String) {
        registry.remove(id)
    }

    private fun sourceDirectory(sourcesRoot: File, id: String): File {
        val dir = File(sourcesRoot, id)
        return if (SourceValidation.isInside(sourcesRoot, dir)) dir else File(sourcesRoot, "")
    }

    private fun allocateDirectory(sourcesRoot: File, existing: List<InstalledSource>): File {
        sourcesRoot.mkdirs()
        val usedIds = existing.mapTo(HashSet()) { it.id }
        var index = 1
        while (true) {
            val candidateName = String.format(Locale.US, "source_%03d", index)
            val candidate = File(sourcesRoot, candidateName)
            if (!usedIds.contains(candidateName) && !candidate.exists()) {
                candidate.mkdirs()
                return candidate
            }
            index++
        }
    }

    private fun isZip(file: File): Boolean {
        return runCatching {
            java.nio.file.Files.newInputStream(file.toPath()).use { input ->
                val magic = ByteArray(4)
                val read = input.read(magic)
                read >= 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() &&
                    (magic[2] == 0x03.toByte() || magic[2] == 0x05.toByte() || magic[2] == 0x07.toByte())
            }
        }.getOrDefault(false)
    }

    private fun readManifestFromZip(zipFile: File): SourceManifest {
        java.util.zip.ZipFile(zipFile).use { zip ->
            val entry =
                zip.getEntry("source.json")
                    ?: throw SourceException.MissingManifest("The ZIP has no source.json")
            if (entry.size > SourceValidation.MAX_MANIFEST_BYTES) {
                throw SourceException.InvalidManifest("source.json is too large")
            }
            val manifest =
                zip.getInputStream(entry).use { it.bufferedReader().readText() }
            val decoded =
                runCatching { SourceCatalogLoader.json.decodeFromString<SourceManifest>(manifest) }
                    .getOrElse { throw SourceException.InvalidManifest("source.json is not valid JSON") }
            SourceValidation.validateManifest(decoded)?.let {
                throw SourceException.InvalidManifest(it)
            }
            return decoded
        }
    }

    private fun zipContainsEntry(zipFile: File, name: String): Boolean {
        return runCatching {
            java.util.zip.ZipFile(zipFile).use { zip ->
                zip.getEntry(name) != null
            }
        }.getOrDefault(false)
    }
}