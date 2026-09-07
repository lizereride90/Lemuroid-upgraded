package com.swordfish.lemuroid.app.sources

import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import java.util.Locale

/** A catalog "system" string resolved to Lemuroid's internal system id and a label for display. */
data class ResolvedSystem(val dbname: String) {
    /** Folder name under the Games root, matching Lemuroid's scanner expectations. */
    val directoryName: String = dbname

    fun toGameSystem(): GameSystem = GameSystem.findById(dbname)
}

/**
 * Maps arbitrary catalog system names (display names, aliases, dbnames) onto Lemuroid's
 * [SystemID] set without ever throwing on unknown systems, so a source can never crash the app.
 */
object SystemResolver {
    private val aliases: Map<String, String> =
        buildMap {
            put("ps1", "psx")
            put("ps", "psx")
            put("playstation", "psx")
            put("playstation 1", "psx")
            put("playstation 2", "ps2")
            put("playstation portable", "psp")
            put("nintendo", "nes")
            put("nintendo entertainment system", "nes")
            put("super nintendo", "snes")
            put("super nintendo entertainment system", "snes")
            put("super famicom", "snes")
            put("gameboy", "gb")
            put("game boy", "gb")
            put("gameboy color", "gbc")
            put("game boy color", "gbc")
            put("gameboy advance", "gba")
            put("game boy advance", "gba")
            put("nintendo 64", "n64")
            put("nintendo ds", "nds")
            put("nintendo 3ds", "3ds")
            put("3ds", "3ds")
            put("mega drive", "md")
            put("megadrive", "md")
            put("genesis", "md")
            put("sega genesis", "md")
            put("master system", "sms")
            put("sega master system", "sms")
            put("sega cd", "scd")
            put("mega cd", "scd")
            put("game gear", "gg")
            put("wonderswan", "ws")
            put("wonderswan color", "wsc")
            put("neo geo pocket", "ngp")
            put("neo geo pocket color", "ngc")
            put("pc engine", "pce")
            put("turbografx 16", "pce")
            put("turbo grafx 16", "pce")
            put("atari 2600", "atari2600")
            put("atari 7800", "atari7800")
            put("arcade", "fbneo")
            put("mame", "mame2003plus")
            put("dos", "dos")
            put("nec pc engine", "pce")
        }

    fun resolve(systemName: String): ResolvedSystem? {
        val raw = systemName.trim()
        if (raw.isEmpty()) return null

        SystemID.values().firstOrNull { it.dbname.equals(raw, ignoreCase = true) }?.let {
            return ResolvedSystem(it.dbname)
        }
        SystemID.values().firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let {
            return ResolvedSystem(it.dbname)
        }
        GameSystem.all().firstOrNull { it.libretroFullName.equals(raw, ignoreCase = true) }?.let {
            return ResolvedSystem(it.id.dbname)
        }

        val alias = aliases[raw.lowercase(Locale.US)]
        if (alias != null) {
            return SystemID.values().firstOrNull { it.dbname == alias }?.let { ResolvedSystem(it.dbname) }
        }

        // Very loose fallback: a folder-ish name that contains a known dbname (e.g. "my-ps2-games").
        val lower = raw.lowercase(Locale.US)
        SystemID.values()
            .sortedByDescending { it.dbname.length }
            .firstOrNull { lower.contains(it.dbname) }
            ?.let { return ResolvedSystem(it.dbname) }

        return null
    }

    fun allSystems(): List<ResolvedSystem> = SystemID.values().sortedBy { it.dbname }.map { ResolvedSystem(it.dbname) }
}
