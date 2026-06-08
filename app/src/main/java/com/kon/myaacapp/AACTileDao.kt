package com.kon.myaacapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AACTileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTile(tile: AACTile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTiles(tiles: List<AACTile>)

    @Update
    suspend fun updateTile(tile: AACTile)

    @Delete
    suspend fun deleteTile(tile: AACTile)

    @Query("SELECT * FROM aac_tiles WHERE id = :id")
    suspend fun getTileById(id: String): AACTile?

    @Query("SELECT * FROM aac_tiles WHERE parentId = :parentId AND languageCode = :langCode ORDER BY cellIndex ASC, sortOrder ASC")
    fun getTilesByParentId(parentId: String?, langCode: String): Flow<List<AACTile>>

    @Query("SELECT * FROM aac_tiles WHERE parentId IS NULL AND languageCode = :langCode ORDER BY cellIndex ASC, sortOrder ASC")
    fun getRootTiles(langCode: String): Flow<List<AACTile>>

    @Query("UPDATE aac_tiles SET clickCount = clickCount + 1 WHERE id = :id")
    suspend fun incrementClickCount(id: String)

    @Query("SELECT * FROM aac_tiles")
    fun getAllTilesSync(): List<AACTile>

    @Query("SELECT * FROM aac_tiles")
    fun getEverythingFlow(): Flow<List<AACTile>>

    @Query("SELECT * FROM aac_tiles WHERE languageCode = :langCode")
    fun getAllTiles(langCode: String): Flow<List<AACTile>>

    @Query("SELECT * FROM aac_tiles WHERE isCategory = 1 AND languageCode = :langCode")
    fun getAllCategories(langCode: String): Flow<List<AACTile>>

    @Query("SELECT COUNT(*) FROM aac_tiles")
    suspend fun getCount(): Int

    @Query("DELETE FROM aac_tiles")
    suspend fun deleteAllTiles()

    // --- TileClickEvent Operations ---

    @Insert
    suspend fun insertClickEvent(event: TileClickEvent)

    @Query("SELECT * FROM tile_click_events WHERE timestamp BETWEEN :startTime AND :endTime")
    fun getClickEventsBetween(startTime: Long, endTime: Long): Flow<List<TileClickEvent>>

    @Query("SELECT * FROM tile_click_events")
    fun getAllClickEvents(): Flow<List<TileClickEvent>>
}