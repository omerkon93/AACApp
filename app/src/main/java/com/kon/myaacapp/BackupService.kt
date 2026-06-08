package com.kon.myaacapp

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
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
            
            // Group tiles by languageCode
            val tilesByLanguage = allTiles.groupBy { it.languageCode }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    val processedZipPaths = HashSet<String>()

                    tilesByLanguage.forEach { (langCode, langTiles) ->
                        // 1. Group tiles by parentId for JSON files
                        val groupedByParent = langTiles.groupBy { it.parentId }
                        
                        groupedByParent.forEach { (parentId, tiles) ->
                            val fileName = if (parentId == null) {
                                "export_00_core.json"
                            } else {
                                "export_${parentId.replace("[^a-zA-Z0-9]".toRegex(), "_")}.json"
                            }
                            
                            val zipPath = "$langCode/tiles/$fileName"
                            if (processedZipPaths.add(zipPath)) {
                                val jsonString = json.encodeToString(tiles)
                                val jsonEntry = ZipEntry(zipPath)
                                zos.putNextEntry(jsonEntry)
                                zos.write(jsonString.toByteArray())
                                zos.closeEntry()
                            }
                        }

                        // 2. Write media files for this language
                        langTiles.forEach { tile ->
                            tile.audioUri?.let { path ->
                                if (isInternalPath(path)) {
                                    val fileName = File(path).name
                                    val zipPath = "$langCode/audio/$fileName"
                                    if (processedZipPaths.add(zipPath)) {
                                        addFileToZip(zos, File(path), zipPath)
                                    }
                                }
                            }
                            tile.imageUri?.let { path ->
                                if (isInternalPath(path)) {
                                    val fileName = File(path).name
                                    val zipPath = "$langCode/images/$fileName"
                                    if (processedZipPaths.add(zipPath)) {
                                        addFileToZip(zos, File(path), zipPath)
                                    }
                                }
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
            if (fileName.endsWith(".zip")) {
                context.assets.open(fileName).use { inputStream ->
                    importDatabaseFromStream(inputStream)
                }
            } else {
                importTilesFromJsonAssets("seed")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importTilesFromJsonAssets(directory: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val allTiles = mutableListOf<AACTile>()
            scanAssetsRecursively(directory, allTiles)

            if (allTiles.isNotEmpty()) {
                repository.deleteAllTiles()
                repository.insertTiles(allTiles)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun scanAssetsRecursively(path: String, outList: MutableList<AACTile>) {
        val assetManager = context.assets
        val list = assetManager.list(path) ?: return

        for (item in list) {
            val fullPath = if (path.isEmpty()) item else "$path/$item"
            if (item.endsWith(".json")) {
                try {
                    val jsonString = assetManager.open(fullPath).bufferedReader().use { it.readText() }
                    val tiles = json.decodeFromString<List<AACTile>>(jsonString)
                    outList.addAll(tiles)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // If it doesn't have an extension, assume it's a directory and recurse
                if (!item.contains(".")) {
                    scanAssetsRecursively(fullPath, outList)
                }
            }
        }
    }

    private suspend fun importDatabaseFromStream(inputStream: java.io.InputStream): Boolean {
        val allImportedTiles = mutableListOf<AACTile>()
        
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val normalizedName = entry.name.replace('\\', '/')
                val fileName = File(normalizedName).name
                
                when {
                    normalizedName.endsWith(".json") -> {
                        val jsonStr = zis.bufferedReader().readText()
                        try {
                            val tiles = json.decodeFromString<List<AACTile>>(jsonStr)
                            allImportedTiles.addAll(tiles)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    normalizedName.contains("/audio/") -> {
                        extractFile(zis, File(context.filesDir, "audio_tiles/$fileName"))
                    }
                    normalizedName.contains("/images/") -> {
                        extractFile(zis, File(context.filesDir, "image_tiles/$fileName"))
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (allImportedTiles.isNotEmpty()) {
            // Remap URIs to current device paths
            val remappedTiles = allImportedTiles.map { tile ->
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
