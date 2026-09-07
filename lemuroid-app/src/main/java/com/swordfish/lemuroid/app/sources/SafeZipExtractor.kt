package com.swordfish.lemuroid.app.sources

import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Result of a safe zip extraction. */
data class ExtractionSummary(
    val extractedFiles: Int,
    val uncompressedBytes: Long,
)

/**
 * Extracts a ZIP into [destination] applying strict security rules:
 *  - every entry must be a safe relative path (no traversal, no absolute paths);
 *  - canonical output paths must stay inside the destination directory (zip-slip guard);
 *  - total uncompressed size and entry count are capped to defuse zip bombs;
 *  - nothing is ever executed; entries are only ever written as plain files.
 */
object SafeZipExtractor {
    fun extract(zipFile: File, destination: File): ExtractionSummary {
        val destCanonical = destination.absolutePath.let { File(it).canonicalPath }
        destination.mkdirs()

        var extractedFiles = 0
        var uncompressedBytes = 0L

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue

                val name = entry.name
                if (!SourceValidation.isSafeRelativeEntry(name)) {
                    throw SourceException.UnsafeArchivePath("Unsafe archive entry: '$name'")
                }

                val normalizedName = name.replace('\\', '/')
                val target = File(destination, normalizedName)
                if (!SourceValidation.isInside(File(destCanonical), target)) {
                    throw SourceException.UnsafeArchivePath("Archive entry escapes the source directory: '$name'")
                }

                uncompressedBytes += entry.size
                if (uncompressedBytes > SourceValidation.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw SourceException.ArchiveTooLarge("The source package expands beyond the size limit.")
                }
                extractedFiles++
                if (extractedFiles > SourceValidation.MAX_EXTRACTED_ENTRIES) {
                    throw SourceException.ArchiveTooLarge("The source package contains too many files.")
                }

                target.parentFile?.mkdirs()
                try {
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: ZipException) {
                    throw SourceException.ExtractionFailed("Failed to extract '$name'.")
                } catch (e: Exception) {
                    throw SourceException.ExtractionFailed("Failed to extract '$name': ${e.message}")
                }
            }
        }

        return ExtractionSummary(extractedFiles, uncompressedBytes)
    }
}