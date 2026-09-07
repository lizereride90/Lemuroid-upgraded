package com.swordfish.lemuroid.app.sources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models for the Game Sources catalog format.
 *
 * A source package is a ZIP file that must contain a `source.json` manifest at its root plus
 * the catalog file referenced by the manifest (default `games.json`). Covers, icons and other
 * assets referenced from the catalog are resolved relative to the source root.
 */
@Serializable
data class SourceManifest(
    val name: String,
    val version: String? = null,
    val author: String? = null,
    val description: String? = null,
    @SerialName("catalog") val catalogFile: String = "games.json",
    /** Optional URL of a newer copy of the same ZIP, used by "Refresh source". */
    @SerialName("sourceUrl") val sourceUrl: String? = null,
)

@Serializable
data class SourceCatalog(
    val games: List<SourceGame> = emptyList(),
)

/**
 * A catalog game entry. Only [id], [title], [system] and [format] are required; every other field
 * is optional and must pass the validation in [SourceValidation] before any download is attempted.
 */
@Serializable
data class SourceGame(
    val id: String,
    val title: String,
    val system: String,
    val format: String,
    val size: Long? = null,
    val cover: String? = null,
    val description: String? = null,
    val downloadUrl: String? = null,
    val sha256: String? = null,
    val version: String? = null,
)

/** Persisted metadata for an installed source, stored in the Sources registry file. */
@Serializable
data class InstalledSource(
    val id: String,
    val name: String,
    val version: String? = null,
    val author: String? = null,
    val description: String? = null,
    val catalogFile: String = "games.json",
    val sourceUrl: String? = null,
    val installedAt: Long,
    val lastUpdated: Long,
    val gameCount: Int = 0,
)
