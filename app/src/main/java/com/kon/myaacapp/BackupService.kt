package com.kon.myaacapp

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupService(private val context: Context, private val repository: AACRepository) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportDatabase(contentResolver: ContentResolver, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val allTiles = repository.getAllTiles().first()
            val jsonString = json.encodeToString(allTiles)
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonString)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importDatabase(contentResolver: ContentResolver, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = InputStreamReader(inputStream).use { it.readText() }
                val tiles = json.decodeFromString<List<AACTile>>(jsonString)
                
                // Atomically update database
                repository.deleteAllTiles()
                repository.insertTiles(tiles)
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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
