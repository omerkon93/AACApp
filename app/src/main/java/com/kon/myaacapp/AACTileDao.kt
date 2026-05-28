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

    @Query("SELECT * FROM aac_tiles WHERE id = :id")
    fun getTileByIdFlow(id: String): Flow<AACTile?>

    @Query("SELECT * FROM aac_tiles WHERE parentId = :parentId ORDER BY cellIndex ASC, sortOrder ASC")
    fun getTilesByParentId(parentId: String?): Flow<List<AACTile>>

    @Query("SELECT * FROM aac_tiles WHERE parentId IS NULL ORDER BY cellIndex ASC, sortOrder ASC")
    fun getRootTiles(): Flow<List<AACTile>>

    @Query("UPDATE aac_tiles SET clickCount = clickCount + 1 WHERE id = :id")
    suspend fun incrementClickCount(id: String)

    @Query("SELECT * FROM aac_tiles")
    fun getAllTiles(): Flow<List<AACTile>>

    @Query("SELECT * FROM aac_tiles WHERE isCategory = 1")
    fun getAllCategories(): Flow<List<AACTile>>

    @Query("SELECT COUNT(*) FROM aac_tiles")
    suspend fun getCount(): Int

    @Query("DELETE FROM aac_tiles")
    suspend fun deleteAllTiles()
}