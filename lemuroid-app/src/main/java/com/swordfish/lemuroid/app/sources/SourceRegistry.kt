package com.swordfish.lemuroid.app.sources

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class SourceRegistryData(val sources: List<InstalledSource> = emptyList())

/**
 * File-backed registry of installed game sources. Data-only, survives restarts, and avoids
 * coupling the source feature to the (versioned) Room database used for game libraries.
 */
class SourceRegistry(private val registryFile: File) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val lock = Any()

    fun load(): List<InstalledSource> {
        synchronized(lock) {
            if (!registryFile.exists()) return emptyList()
            return runCatching {
                json.decodeFromString<SourceRegistryData>(registryFile.readText())
                    .sources
                    .mapNotNull { candidate ->
                        val safeId =
                            runCatching { SourceValidation.sanitizeFileName(candidate.id) }
                                .getOrNull()
                                ?: return@mapNotNull null
                        if (!candidate.name.isBlank() && safeId == candidate.id) candidate else null
                    }
            }.getOrDefault(emptyList())
        }
    }

    fun save(sources: List<InstalledSource>) {
        synchronized(lock) {
            val parent = registryFile.parentFile
            parent?.mkdirs()
            val temp = File(parent, registryFile.name + ".tmp")
            temp.writeText(json.encodeToString(SourceRegistryData(sources)))
            if (!temp.renameTo(registryFile)) {
                // Fallback for filesystems where rename across the same dir is flaky.
                temp.copyTo(registryFile, overwrite = true)
                temp.delete()
            }
        }
    }

    fun update(source: InstalledSource): List<InstalledSource> {
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.id == source.id }
        if (index >= 0) {
            current[index] = source
        } else {
            current.add(source)
        }
        save(current)
        return current
    }

    fun remove(id: String) {
        save(load().filterNot { it.id == id })
    }
}