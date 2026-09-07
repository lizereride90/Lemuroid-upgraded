package com.swordfish.lemuroid.app.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SourceInstallerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sourcesRoot: File
    private lateinit var registry: SourceRegistry
    private lateinit var installer: SourceInstaller

    @Before
    fun setUp() {
        sourcesRoot = tempFolder.newFolder("sources")
        val registryFile = File(sourcesRoot, "sources.json")
        registry = SourceRegistry(registryFile)
        installer = SourceInstaller(registry)
    }

    @Test
    fun `installs a valid package and creates a source_001 directory`() {
        val zip =
            buildSourceZip(
                manifest = """{"name":"Homebrew 64","version":"1.0","catalog":"games.json"}""".trimIndent(),
                catalog =
                    """
                    {
                      "games": [
                        {
                          "id": "g1",
                          "title": "Test",
                          "system": "PS2",
                          "format": "iso",
                          "size": 1024,
                          "downloadUrl": "https://example.com/g.iso"
                        }
                      ]
                    }
                    """.trimIndent(),
            )

        val installed = installer.install(zip, sourcesRoot)

        assertEquals("source_001", installed.id)
        assertEquals("Homebrew 64", installed.name)
        assertEquals(1, installed.gameCount)
        assertTrue(File(sourcesRoot, "source_001/source.json").isFile)
        assertTrue(File(sourcesRoot, "source_001/games.json").isFile)
        assertEquals(installed, registry.load().single())
    }

    @Test
    fun `rejects a package without source json`() {
        val zip = buildSourceZip(manifest = null, catalog = null)
        assertThrows<SourceException.MissingManifest> { installer.install(zip, sourcesRoot) }
    }

    @Test
    fun `rejects malformed source json`() {
        val zip = buildSourceZip(manifest = "{not json", catalog = null)
        assertThrows<SourceException.InvalidManifest> { installer.install(zip, sourcesRoot) }
    }

    @Test
    fun `rejects a missing catalog file`() {
        val zip =
            buildSourceZip(
                manifest = """{"name":"No Catalog"}""".trimIndent(),
                catalog = null,
                includeCatalog = false,
            )
        assertThrows<SourceException.MissingCatalog> { installer.install(zip, sourcesRoot) }
    }

    @Test
    fun `rejects duplicate source names case-insensitively`() {
        installer.install(
            buildSourceZip(manifest = """{"name":"Retro Pack"}""".trimIndent(), catalog = null),
            sourcesRoot,
        )
        val dup =
            buildSourceZip(manifest = """{"name":"retro pack"}""".trimIndent(), catalog = null)
        assertThrows<SourceException.DuplicateSource> { installer.install(dup, sourcesRoot) }
    }

    @Test
    fun `allocates unique directories for multiple sources`() {
        installer.install(
            buildSourceZip(manifest = """{"name":"Pack A"}""".trimIndent(), catalog = null),
            sourcesRoot,
        )
        installer.install(
            buildSourceZip(manifest = """{"name":"Pack B"}""".trimIndent(), catalog = null),
            sourcesRoot,
        )
        assertEquals(listOf("source_001", "source_002"), registry.load().map { it.id }.sorted())
    }

    @Test
    fun `remove deletes only the registry entry`() {
        installer.install(
            buildSourceZip(manifest = """{"name":"Pack A"}""".trimIndent(), catalog = null),
            sourcesRoot,
        )
        installer.remove("source_001")
        assertTrue(registry.load().isEmpty())
    }

    private fun buildSourceZip(manifest: String?, catalog: String?, includeCatalog: Boolean = true): File {
        val zipFile = tempFolder.newFile("source-${System.nanoTime()}.zip")
        ZipOutputStream(zipFile.outputStream()).use { out ->
            manifest?.let {
                out.putNextEntry(ZipEntry("source.json"))
                out.write(it.toByteArray())
                out.closeEntry()
            }
            val catalogContent = catalog ?: """{"games":[]}"""
            if (includeCatalog) {
                out.putNextEntry(ZipEntry("games.json"))
                out.write(catalogContent.toByteArray())
                out.closeEntry()
            }
        }
        return zipFile
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName} to be thrown")
        } catch (expected: T) {
            // expected
        }
    }
}