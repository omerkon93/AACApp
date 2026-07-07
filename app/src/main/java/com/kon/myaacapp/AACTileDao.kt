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

    @Query("SELECT * FROM aac_tiles WHERE id = :id AND languageCode = :langCode")
    suspend fun getTileById(id: String, langCode: String): AACTile?

    @Transaction
    @Query("""
        SELECT aac_tiles.*, 
               tile_placements.cellIndex AS placed_cellIndex, 
               tile_placements.sortOrder AS placed_sortOrder, 
               tile_placements.parentId AS placed_parentId 
        FROM aac_tiles 
        JOIN tile_placements ON aac_tiles.id = tile_placements.tileId AND aac_tiles.languageCode = tile_placements.languageCode
        WHERE tile_placements.parentId = :parentId AND aac_tiles.languageCode = :langCode AND aac_tiles.isHidden = 0 
        ORDER BY tile_placements.cellIndex ASC, tile_placements.sortOrder ASC
    """)
    fun getTilesByParentIdWithPlacement(parentId: String, langCode: String): Flow<List<AACTileWithPlacement>>

    @Query("SELECT * FROM aac_tiles WHERE parentId = :parentId AND languageCode = :langCode AND isHidden = 0 ORDER BY cellIndex ASC, sortOrder ASC")
    fun getTilesByParentIdLegacy(parentId: String?, langCode: String): Flow<List<AACTile>>

    @Query("SELECT * FROM aac_tiles WHERE parentId IS NULL AND languageCode = :langCode AND isHidden = 0 ORDER BY cellIndex ASC, sortOrder ASC")
    fun getRootTilesLegacy(langCode: String): Flow<List<AACTile>>

    // --- TilePlacement Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlacement(placement: TilePlacement)

    @Query("DELETE FROM tile_placements WHERE tileId = :tileId AND parentId = :parentId AND languageCode = :langCode")
    suspend fun deletePlacement(tileId: String, parentId: String, langCode: String)

    @Query("DELETE FROM tile_placements WHERE tileId = :tileId AND languageCode = :langCode")
    suspend fun deleteAllPlacementsForTile(tileId: String, langCode: String)

    @Query("SELECT * FROM tile_placements")
    suspend fun getAllPlacements(): List<TilePlacement>

    @Transaction
    @Query("""
        SELECT aac_tiles.*, 
               tile_placements.cellIndex AS placed_cellIndex, 
               tile_placements.sortOrder AS placed_sortOrder, 
               tile_placements.parentId AS placed_parentId
        FROM aac_tiles 
        JOIN tile_placements ON aac_tiles.id = tile_placements.tileId AND aac_tiles.languageCode = tile_placements.languageCode
    """)
    suspend fun getAllTilesWithPlacements(): List<AACTileWithPlacement>

    @Query("UPDATE aac_tiles SET clickCount = clickCount + 1 WHERE id = :id AND languageCode = :langCode")
    suspend fun incrementClickCount(id: String, langCode: String)

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

    @Query("DELETE FROM tile_placements")
    suspend fun deleteAllPlacements()

    @Query("DELETE FROM aac_tiles WHERE languageCode = :languageCode")
    suspend fun deleteTilesByLanguage(languageCode: String)

    // --- TileClickEvent Operations ---

    @Insert
    suspend fun insertClickEvent(event: TileClickEvent)

    @Query("SELECT * FROM tile_click_events WHERE timestamp BETWEEN :startTime AND :endTime")
    fun getClickEventsBetween(startTime: Long, endTime: Long): Flow<List<TileClickEvent>>

    @Query("SELECT * FROM tile_click_events")
    fun getAllClickEvents(): Flow<List<TileClickEvent>>

    @Query("DELETE FROM tile_click_events")
    suspend fun deleteAllClickEvents()

    @Query("UPDATE aac_tiles SET clickCount = 0")
    suspend fun resetAllLegacyClickCounts()
}