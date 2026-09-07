package com.swordfish.lemuroid.app.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemResolverTest {
    @Test
    fun `resolves dbnames and enum names`() {
        assertEquals("ps2", SystemResolver.resolve("ps2")?.dbname)
        assertEquals("psp", SystemResolver.resolve("PSP")?.dbname)
        assertEquals("NES", SystemResolver.resolve("nes")?.dbname)
    }

    @Test
    fun `resolves common aliases`() {
        assertEquals("psx", SystemResolver.resolve("PS1")?.dbname)
        assertEquals("psx", SystemResolver.resolve("PlayStation")?.dbname)
        assertEquals("ps2", SystemResolver.resolve("PlayStation 2")?.dbname)
        assertEquals("psp", SystemResolver.resolve("PlayStation Portable")?.dbname)
        assertEquals("gb", SystemResolver.resolve("Game Boy")?.dbname)
        assertEquals("gba", SystemResolver.resolve("Game Boy Advance")?.dbname)
        assertEquals("nds", SystemResolver.resolve("Nintendo DS")?.dbname)
        assertEquals("3ds", SystemResolver.resolve("3DS")?.dbname)
        assertEquals("md", SystemResolver.resolve("Mega Drive")?.dbname)
    }

    @Test
    fun `loosely matches on contained dbname`() {
        assertEquals("ps2", SystemResolver.resolve("My PS2 Collection")?.dbname)
    }

    @Test
    fun `returns null for unknown systems`() {
        assertNull(SystemResolver.resolve("Vectrex 3000"))
        assertNull(SystemResolver.resolve(""))
    }

    @Test
    fun `allSystems covers every SystemID and has a directory name`() {
        val all = SystemResolver.allSystems()
        assertTrue(all.isNotEmpty())
        all.forEach { resolved ->
            assertNotNull(resolved.dbname)
            assertEquals(resolved.dbname, resolved.directoryName)
            assertSame(resolved.dbname, resolved.toGameSystem().id.dbname)
        }
    }
}
