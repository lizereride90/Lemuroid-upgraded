package com.swordfish.lemuroid.app.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceValidationTest {
    @Test
    fun `accepts a valid manifest`() {
        val manifest = SourceManifest(name = "Homebrew Collection", version = "1.0")
        assertNull(SourceValidation.validateManifest(manifest))
    }

    @Test
    fun `rejects blank or oversized names`() {
        assertTrue(SourceValidation.validateManifest(SourceManifest(name = "  ")) != null)
        assertTrue(SourceValidation.validateManifest(SourceManifest(name = "x".repeat(200))) != null)
    }

    @Test
    fun `rejects unsafe catalog path`() {
        val manifest = SourceManifest(name = "Bad", catalogFile = "../games.json")
        assertTrue(SourceValidation.validateManifest(manifest) != null)
    }

    @Test
    fun `rejects non http catalog urls`() {
        assertTrue(
            SourceValidation.validateManifest(
                SourceManifest(name = "Bad", sourceUrl = "ftp://example.com/source.zip"),
            ) != null,
        )
        assertNull(
            SourceValidation.validateManifest(
                SourceManifest(name = "Good", sourceUrl = "https://example.com/source.zip"),
            ),
        )
    }

    @Test
    fun `validates catalog games and duplicate ids`() {
        val good =
            SourceCatalog(
                games =
                    listOf(
                        SourceGame(
                            id = "game-1",
                            title = "Test Game",
                            system = "PlayStation 2",
                            format = "iso",
                            size = 1024,
                            downloadUrl = "https://example.com/game.iso",
                        ),
                    ),
            )
        assertNull(SourceValidation.validateCatalog(good))

        val badFormat = good.games[0].copy(format = "with spaces")
        assertTrue(SourceValidation.validateCatalog(SourceCatalog(games = listOf(badFormat))) != null)

        val badUrl = good.games[0].copy(downloadUrl = "javascript:alert(1)")
        assertTrue(SourceValidation.validateCatalog(SourceCatalog(games = listOf(badUrl))) != null)

        val badSha = good.games[0].copy(sha256 = "not-hex")
        assertTrue(SourceValidation.validateCatalog(SourceCatalog(games = listOf(badSha))) != null)

        val duplicate = SourceCatalog(games = listOf(good.games[0], good.games[0].copy(title = "Other")))
        assertTrue(SourceValidation.validateCatalog(duplicate) != null)
    }

    @Test
    fun `isSafeRelativeEntry rejects traversal and absolute paths`() {
        assertTrue(SourceValidation.isSafeRelativeEntry("games.json"))
        assertTrue(SourceValidation.isSafeRelativeEntry("covers/a/b.png"))
        assertFalse(SourceValidation.isSafeRelativeEntry("../secret"))
        assertFalse(SourceValidation.isSafeRelativeEntry("/etc/passwd"))
        assertFalse(SourceValidation.isSafeRelativeEntry("a/../../b"))
        assertFalse(SourceValidation.isSafeRelativeEntry("C:/windows/evil"))
        assertFalse(SourceValidation.isSafeRelativeEntry("a\\..\\b"))
    }

    @Test
    fun `isInside guards canonical boundaries`() {
        val util = java.io.File.createTempFile("check", ".tmp")
        val base = util.parentFile!!
        assertTrue(SourceValidation.isInside(base, java.io.File(base, "sub/file")))
        assertTrue(SourceValidation.isInside(base, base))
        assertFalse(SourceValidation.isInside(base, java.io.File(base.parentFile, "z-never-exists-1234")))
    }

    @Test
    fun `sanitizeFileName strips hostile characters`() {
        assertEquals("game-1", SourceValidation.sanitizeFileName("game-1"))
        assertEquals("my-game", SourceValidation.sanitizeFileName("my game"))
        assertEquals("_", SourceValidation.sanitizeFileName(".."))
        assertEquals("game", SourceValidation.sanitizeFileName(""))
        val clean = SourceValidation.sanitizeFileName("../../etc/passwd")
        assertFalse(clean.contains(".."))
        assertFalse(clean.contains("/"))
    }
}
