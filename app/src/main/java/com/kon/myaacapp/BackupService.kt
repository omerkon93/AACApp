package com.kon.myaacapp

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupService(private val context: Context, private val repository: AACRepository) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportDatabase(contentResolver: ContentResolver, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val allTiles = repository.getEverythingFlow().first()
            val jsonString = json.encodeToString(allTiles)
            
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    // 1. Write tiles.json
                    val jsonEntry = ZipEntry("tiles.json")
                    zos.putNextEntry(jsonEntry)
                    zos.write(jsonString.toByteArray())
                    zos.closeEntry()

                    // 2. Write media files
                    allTiles.forEach { tile ->
                        tile.audioUri?.let { path ->
                            if (isInternalPath(path)) {
                                addFileToZip(zos, File(path), "audio/${File(path).name}")
                            }
                        }
                        tile.imageUri?.let { path ->
                            if (isInternalPath(path)) {
                                addFileToZip(zos, File(path), "images/${File(path).name}")
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun isInternalPath(path: String): Boolean {
        return path.startsWith(context.filesDir.absolutePath)
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, zipPath: String) {
        if (!file.exists()) return
        try {
            val entry = ZipEntry(zipPath)
            zos.putNextEntry(entry)
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun importDatabase(contentResolver: ContentResolver, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                importDatabaseFromStream(inputStream)
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importFromAssets(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.assets.open(fileName).use { inputStream ->
                importDatabaseFromStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun importDatabaseFromStream(inputStream: java.io.InputStream): Boolean {
        var tilesJson: String? = null
        
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                // Handle both / and \ as separators
                val normalizedName = entry.name.replace('\\', '/')
                val fileName = File(normalizedName).name
                
                when {
                    normalizedName == "tiles.json" -> {
                        tilesJson = zis.bufferedReader().readText()
                    }
                    normalizedName.startsWith("audio/") -> {
                        extractFile(zis, File(context.filesDir, "audio_tiles/$fileName"))
                    }
                    normalizedName.startsWith("images/") -> {
                        extractFile(zis, File(context.filesDir, "image_tiles/$fileName"))
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        tilesJson?.let { jsonStr ->
            val importedTiles = try {
                json.decodeFromString<List<AACTile>>(jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            
            // Remap URIs to current device paths
            val remappedTiles = importedTiles.map { tile ->
                var updatedTile = tile
                tile.audioUri?.let { oldPath ->
                    val fileName = File(oldPath).name
                    val newPath = File(context.filesDir, "audio_tiles/$fileName").absolutePath
                    updatedTile = updatedTile.copy(audioUri = newPath)
                }
                tile.imageUri?.let { oldPath ->
                    val fileName = File(oldPath).name
                    val newPath = File(context.filesDir, "image_tiles/$fileName").absolutePath
                    updatedTile = updatedTile.copy(imageUri = newPath)
                }
                updatedTile
            }

            repository.deleteAllTiles()
            repository.insertTiles(remappedTiles)
            return true
        }
        return false
    }

    private fun extractFile(zis: ZipInputStream, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        targetFile.outputStream().use { zis.copyTo(it) }
    }

    suspend fun loadTilesFromAssets(fileName: String): List<AACTile>? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)
            val jsonString = reader.use { it.readText() }
            json.decodeFromString<List<AACTile>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}