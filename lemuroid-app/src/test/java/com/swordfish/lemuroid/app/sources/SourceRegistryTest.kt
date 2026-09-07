package com.swordfish.lemuroid.app.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SourceRegistryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `round trips save load update and remove`() {
        val registryFile = File(tempFolder.newFolder(), "sources.json")
        val registry = SourceRegistry(registryFile)
        val now = System.currentTimeMillis()
        val source = InstalledSource(id = "source_001", name = "Pack", installedAt = now, lastUpdated = now)

        assertTrue(registry.load().isEmpty())
        registry.update(source)
        assertEquals(listOf(source), registry.load())

        val updated = source.copy(gameCount = 5, lastUpdated = now + 10)
        registry.update(updated)
        assertEquals(listOf(updated), registry.load())

        registry.remove("source_001")
        assertTrue(registry.load().isEmpty())
    }

    @Test
    fun `ignores corrupt registry files`() {
        val registryFile = tempFolder.newFile("sources.json")
        registryFile.writeText("{not valid json")
        val registry = SourceRegistry(registryFile)
        assertTrue(registry.load().isEmpty())
    }

    @Test
    fun `drops entries with empty names or unsafe ids`() {
        val registryFile = File(tempFolder.newFolder(), "sources.json")
        val registry = SourceRegistry(registryFile)
        val now = System.currentTimeMillis()
        val good = InstalledSource(id = "source_001", name = "Pack", installedAt = now, lastUpdated = now)
        val blankName = good.copy(name = "   ", id = "source_002")
        val unsafeId = good.copy(id = "../evil", name = "Evil")

        registry.save(listOf(good, blankName, unsafeId))
        assertEquals(listOf(good), registry.load())
    }
}
