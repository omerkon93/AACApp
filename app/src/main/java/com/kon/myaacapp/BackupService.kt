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
                ZipOutputStream(outputStream).use { zos ->
                    val baseDir = context.filesDir

                    baseDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        // Normalize separators to forward slashes for cross-platform zip compatibility
                        val relativePath = file.relativeTo(baseDir).path.replace("\\", "/")

                        // Bulletproof path filtering
                        if (relativePath.startsWith("profiles/") ||
                            relativePath.startsWith("tiles/") ||
                            relativePath.startsWith("audio_tiles/") ||
                            relativePath.startsWith("images/")) {

                            try {
                                val entry = ZipEntry(relativePath)
                                zos.putNextEntry(entry)
                                file.inputStream().use { it.copyTo(zos) }
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
                listOf("tiles", "audio_tiles", "images") // Safety ON: Keep profiles!
            } else {
                listOf("profiles", "tiles", "audio_tiles", "images") // Safety OFF: Nuke everything
            }

            directoriesToClear.forEach { dirName ->
                val dir = File(context.filesDir, dirName)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }

            // 2. Clear SQLite tables (pure JSON-first approach)
            repository.clearAllStatistics() // This also clears click events and Room counts
            repository.deleteAllTilesFromRoom() // Ensure Room is clean after restore

            // 3. Extract ZIP directly to filesDir
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val targetFile = File(context.filesDir, entry.name)

                    // Security Check: Zip Path Traversal Protection
                    val canonicalPath = targetFile.canonicalPath
                    if (!canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                        throw SecurityException("Zip entry ${entry.name} is outside of the target directory")
                    }

                    // CRITICAL FIX: If we are preserving profiles, ignore any profile files inside the ZIP!
                    if (preserveProfiles && entry.name.startsWith("profiles/")) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 4. Reload app state will be handled by the caller (ViewModel)
            true
        } catch (e: Exception) {
            Log.e("BackupService", "Import from stream failed", e)
            false
        }
    }
}