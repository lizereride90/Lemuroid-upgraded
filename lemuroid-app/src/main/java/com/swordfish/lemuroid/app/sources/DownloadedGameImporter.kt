package com.swordfish.lemuroid.app.sources

import android.content.Context
import androidx.core.net.toUri
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Moves a fully downloaded catalog game into `Games/<system>/`, seeds its library row (title,
 * local cover art) and triggers a library re-index so it shows up in the normal Lemuroid library.
 */
class DownloadedGameImporter(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
    private val retrogradeDb: RetrogradeDatabase,
) {
    suspend fun import(
        source: InstalledSource,
        game: SourceGame,
        partFile: File,
    ): Int {
        return withContext(Dispatchers.IO) {
            val system =
                SystemResolver.resolve(game.system)
                    ?: throw SourceException.UnsupportedSystem(
                        "Unsupported system '${game.system}' for game '${game.title}'",
                    )

            val destDir = File(directoriesManager.getDownloadedGamesDirectory(), system.directoryName)
            destDir.mkdirs()

            val safeBase = SourceValidation.sanitizeFileName(game.title.take(100))
            val finalName = "$safeBase.${game.format.lowercase()}"
            val finalFile = File(destDir, finalName)

            if (finalFile.exists()) {
                finalFile.delete()
            }
            val moved = partFile.renameTo(finalFile)
            if (!moved) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            val coverUrl = coverForGame(source, game)?.let { it.toUri().toString() }
            val fileUri = finalFile.toUri().toString()

            // Replace a previously downloaded row for the same file so re-downloads never collide.
            retrogradeDb.gameDao().selectByFileUri(fileUri)?.let { stale ->
                retrogradeDb.gameDao().delete(listOf(stale))
            }

            val row =
                Game(
                    fileName = finalFile.name,
                    fileUri = fileUri,
                    title = game.title,
                    systemId = system.dbname,
                    developer = null,
                    coverFrontUrl = coverUrl,
                    lastIndexedAt = System.currentTimeMillis(),
                )
            val ids = retrogradeDb.gameDao().insert(listOf(row))

            LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)

            ids.first().toInt()
        }
    }

    fun coverForGame(
        source: InstalledSource,
        game: SourceGame,
    ): File? {
        val coverPath = game.cover ?: return null
        if (!SourceValidation.isSafeRelativeEntry(coverPath)) return null
        val sourceDir = File(directoriesManager.getGameSourcesDirectory(), source.id)
        val cover = File(sourceDir, coverPath.replace('\\', '/'))
        return if (SourceValidation.isInside(sourceDir, cover) && cover.isFile) cover else null
    }
}