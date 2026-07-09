package com.kon.myaacapp

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupService(
    private val context: Context,
    private val repository: AACRepository
) {

    /**
     * Exports the app's internal storage directories to a ZIP file.
     * Includes profiles, tiles (JSONs), and audio/image assets.
     */
    suspend fun exportDatabase(contentResolver: ContentResolver, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                // OPTIMIZATION: Wrap output in a BufferedOutputStream.
                // This batches small disk writes into 64KB chunks before compressing,
                // preventing flash-storage thrashing and drastically speeding up exports.
                ZipOutputStream(outputStream.buffered(65536)).use { zos ->
                    val baseDir = context.filesDir

                    baseDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relativePath = file.relativeTo(baseDir).path.replace("\\", "/")

                        if (relativePath.startsWith("profiles/") ||
                            relativePath.startsWith("tiles/") ||
                            relativePath.startsWith("audio_tiles/") ||
                            relativePath.startsWith("images/")) {

                            try {
                                val entry = ZipEntry(relativePath)
                                zos.putNextEntry(entry)

                                // OPTIMIZATION: Buffered read from the local file system
                                file.inputStream().buffered(65536).use { it.copyTo(zos) }
                                zos.closeEntry()
                            } catch (e: Exception) {
                                Log.e("BackupService", "Failed to add $relativePath to zip", e)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("BackupService", "Export failed", e)
            false
        }
    }

    /**
     * Restores the app's internal storage from a ZIP file.
     * Clears existing directories first, then extracts the ZIP directly.
     */
    suspend fun importDatabase(contentResolver: ContentResolver, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                // Full restore: wipe everything including profiles
                importDatabaseFromStream(inputStream, preserveProfiles = false)
            } ?: false
        } catch (e: Exception) {
            Log.e("BackupService", "Import failed", e)
            false
        }
    }

    suspend fun importFromAssets(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (fileName.endsWith(".zip")) {
                context.assets.open(fileName).use { inputStream ->
                    // Factory reset: wipe dictionary/media, but PRESERVE profiles
                    importDatabaseFromStream(inputStream, preserveProfiles = true)
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("BackupService", "Import from assets failed", e)
            false
        }
    }

    private suspend fun importDatabaseFromStream(inputStream: InputStream, preserveProfiles: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Clear existing data intelligently based on the safety flag
            val directoriesToClear = if (preserveProfiles) {
                listOf("tiles", "audio_tiles", "images")
            } else {
                listOf("profiles", "tiles", "audio_tiles", "images")
            }

            directoriesToClear.forEach { dirName ->
                val dir = File(context.filesDir, dirName)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }

            // 2. Clear SQLite tables
            repository.clearAllStatistics()
            repository.deleteAllTilesFromRoom()

            // OPTIMIZATION: Pre-calculate the root canonical path ONCE.
            // Calling this inside the loop forces the OS to resolve symlinks thousands of times.
            val rootCanonicalPath = context.filesDir.canonicalPath

            // 3. Extract ZIP directly to filesDir with a BufferedInputStream
            ZipInputStream(inputStream.buffered(65536)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val targetFile = File(context.filesDir, entry.name)

                    // Security Check: Zip Path Traversal Protection
                    val entryCanonicalPath = targetFile.canonicalPath
                    if (!entryCanonicalPath.startsWith(rootCanonicalPath)) {
                        throw SecurityException("Zip entry ${entry.name} is outside of the target directory")
                    }

                    if (preserveProfiles && entry.name.startsWith("profiles/")) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        // OPTIMIZATION: Buffered write to the local file system
                        targetFile.outputStream().buffered(65536).use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Log.e("BackupService", "Import from stream failed", e)
            false
        }
    }
}