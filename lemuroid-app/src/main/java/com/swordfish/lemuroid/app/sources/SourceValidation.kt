package com.swordfish.lemuroid.app.sources

import java.io.File
import java.net.URI
import java.util.Locale

/** User-facing failures raised while installing, refreshing or loading sources. */
sealed class SourceException(message: String) : Exception(message) {
    class NotAZip(message: String) : SourceException(message)

    class MissingManifest(message: String) : SourceException(message)

    class InvalidManifest(message: String) : SourceException(message)

    class MissingCatalog(message: String) : SourceException(message)

    class InvalidCatalog(message: String) : SourceException(message)

    class UnsafeArchivePath(message: String) : SourceException(message)

    class UnsupportedSystem(message: String) : SourceException(message)

    class ExtractionFailed(message: String) : SourceException(message)

    class DuplicateSource(message: String) : SourceException(message)

    class ArchiveTooLarge(message: String) : SourceException(message)

    class CatalogTooLarge(message: String) : SourceException(message)
}

/**
 * All the checks applied to untrusted source package content. Every rule here exists to keep a
 * source package strictly data-only: no writing outside its own directory, no absolute paths,
 * no shell execution, no absurd archives, no malicious URLs.
 */
object SourceValidation {
    const val MAX_MANIFEST_BYTES = 64 * 1024
    const val MAX_CATALOG_BYTES = 16 * 1024 * 1024
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 512L * 1024 * 1024
    const val MAX_EXTRACTED_ENTRIES = 10_000
    const val MAX_GAMES = 20_000
    const val MAX_NAME_LENGTH = 128
    const val MAX_DESCRIPTION_LENGTH = 20_000

    private val SHA256_RE = Regex("^[0-9a-fA-F]{64}$")
    private val SAFE_FORMAT_RE = Regex("^[a-zA-Z0-9]{1,12}$")
    private val SAFE_ID_RE = Regex("^[A-Za-z0-9._-]{1,64}$")

    fun validateManifest(manifest: SourceManifest): String? {
        if (manifest.name.isBlank()) {
            return "The source has no name."
        }
        if (manifest.name.length > MAX_NAME_LENGTH) {
            return "The source name is too long."
        }
        validateOptionalText(manifest.version, 32)?.let { return it }
        validateOptionalText(manifest.author, MAX_NAME_LENGTH)?.let { return it }
        validateOptionalText(manifest.description, MAX_DESCRIPTION_LENGTH)?.let { return it }
        if (!isSafeRelativeEntry(manifest.catalogFile)) {
            return "The catalog path '${manifest.catalogFile}' is not a safe relative path."
        }
        manifest.sourceUrl?.let {
            if (!isAllowedDownloadUrl(it)) {
                return "The source update URL is not a valid http(s) address."
            }
        }
        return null
    }

    fun validateCatalog(catalog: SourceCatalog): String? {
        if (catalog.games.size > MAX_GAMES) {
            return "The catalog has too many games (max $MAX_GAMES)."
        }
        val ids = HashSet<String>(catalog.games.size)
        catalog.games.forEachIndexed { index, game ->
            validateGame(game)?.let {
                return "Game #${index + 1} (${game.id}): $it"
            }
            if (!ids.add(game.id.lowercase(Locale.US))) {
                return "The catalog contains duplicate game id '${game.id}'."
            }
        }
        return null
    }

    private fun validateGame(game: SourceGame): String? {
        if (!SAFE_ID_RE.matches(game.id)) {
            return "The game id is invalid."
        }
        if (game.title.isBlank() || game.title.length > MAX_NAME_LENGTH) {
            return "The game title is invalid."
        }
        if (game.system.isBlank() || game.system.length > MAX_NAME_LENGTH) {
            return "The game system is missing."
        }
        if (!SAFE_FORMAT_RE.matches(game.format)) {
            return "The game file format '${game.format}' is invalid."
        }
        if (game.size != null && game.size < 0) {
            return "The declared game size must be positive."
        }
        if (!isAllowedDownloadUrl(game.downloadUrl)) {
            return "The download URL is not a valid http(s) address."
        }
        if (game.sha256 != null && !SHA256_RE.matches(game.sha256)) {
            return "The SHA-256 checksum is not valid hexadecimal."
        }
        if (game.cover != null && !isSafeRelativeEntry(game.cover)) {
            return "The cover path '${game.cover}' is not safe."
        }
        if (game.description != null && game.description.length > MAX_DESCRIPTION_LENGTH) {
            return "The game description is too long."
        }
        return null
    }

    private fun validateOptionalText(
        value: String?,
        maxLength: Int,
    ): String? {
        if (value != null && value.length > maxLength) {
            return "A source field is too long."
        }
        return null
    }

    /** True when [entryName] can be safely extracted: relative, no absolute or traversal segments. */
    fun isSafeRelativeEntry(entryName: String): Boolean {
        if (entryName.isBlank()) return false
        if (entryName.length > 512) return false
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/")) return false
        if (Regex("^[a-zA-Z]:/").containsMatchIn(normalized)) return false
        return normalized.split('/').none { it == ".." || it == "." }
    }

    /** True when [url] is a download we are willing to perform: http(s) only. */
    fun isAllowedDownloadUrl(rawUrl: String?): Boolean {
        if (rawUrl == null || rawUrl.length > 2048) return false
        val uri =
            try {
                URI(rawUrl)
            } catch (e: Exception) {
                return false
            }
        val scheme = uri.scheme?.lowercase(Locale.US)
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    /**
     * Turns an arbitrary filename from a catalog into something safe to write to disk:
     * ASCII-safe, no separators, no "..", no leading dots, never blank.
     */
    fun sanitizeFileName(rawName: String): String {
        val cleaned =
            rawName
                .replace(Regex("[^A-Za-z0-9._-]"), "-")
                .replace("..", "_")
                .trim('.', '-', ' ', '\t', '\n')
        return if (cleaned.isBlank()) "game" else cleaned
    }

    /** Guards against zip-slip on the canonical path join. */
    fun isInside(
        base: File,
        candidate: File,
    ): Boolean {
        val baseCanonical = base.canonicalPath
        val candidateCanonical = candidate.canonicalPath
        return candidateCanonical == baseCanonical ||
            candidateCanonical.startsWith(baseCanonical + File.separator)
    }
}
