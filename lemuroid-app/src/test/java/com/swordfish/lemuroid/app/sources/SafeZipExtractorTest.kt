package com.swordfish.lemuroid.app.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SafeZipExtractorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `extracts a plain archive to the destination`() {
        val zip = zipOf("source.json" to """{"name":"Pack"}""", "covers/a.png" to "data")
        val dest = tempFolder.newFolder("dest")

        val summary = SafeZipExtractor.extract(zip, dest)

        assertEquals(2, summary.extractedFiles)
        assertTrue(File(dest, "source.json").isFile)
        assertTrue(File(dest, "covers/a.png").isFile)
    }

    @Test
    fun `rejects a traversal entry that escapes the destination`() {
        val zip = zipOf("../evil.txt" to "boom")
        val dest = tempFolder.newFolder("dest")

        assertThrows<SourceException.UnsafeArchivePath> { SafeZipExtractor.extract(zip, dest) }
        assertFalse(File(tempFolder.root, "evil.txt").exists())
    }

    @Test
    fun `rejects absolute paths`() {
        val zip = zipOf("/etc/evil" to "boom")
        assertThrows<SourceException.UnsafeArchivePath> {
            SafeZipExtractor.extract(zip, tempFolder.newFolder("dest"))
        }
    }

    @Test
    fun `rejects windows drive paths`() {
        val zip = zipOf("C:\\windows\\evil" to "boom")
        assertThrows<SourceException.UnsafeArchivePath> {
            SafeZipExtractor.extract(zip, tempFolder.newFolder("dest"))
        }
    }

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val zipFile = tempFolder.newFile("zip-${System.nanoTime()}.zip")
        ZipOutputStream(zipFile.outputStream()).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return zipFile
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName} to be thrown")
        } catch (e: Throwable) {
            if (e is T) return
            throw e
        }
    }
}
