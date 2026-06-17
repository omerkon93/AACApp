package com.kon.myaacapp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.kon.myaacapp.ROOT_PARENT_ID

class AACRepository(private val aacTileDao: AACTileDao) {
    
    fun getTilesByParentId(parentId: String?, langCode: String): Flow<List<AACTile>> {
        val effectiveParentId = parentId ?: ROOT_PARENT_ID
        return aacTileDao.getTilesByParentIdWithPlacement(effectiveParentId, langCode)
            .map { list -> list.map { it.toAACTile() } }
    }

    suspend fun attachTileToCategory(tileId: String, parentId: String?, langCode: String, cellIndex: Int? = null) {
        val effectiveParentId = parentId ?: ROOT_PARENT_ID
        aacTileDao.insertPlacement(TilePlacement(tileId, effectiveParentId, langCode, cellIndex))
    }

    suspend fun removeTileFromCategory(tileId: String, parentId: String?, langCode: String) {
        val effectiveParentId = parentId ?: ROOT_PARENT_ID
        aacTileDao.deletePlacement(tileId, effectiveParentId, langCode)
    }

    // Migration helper: Call this to populate tile_placements from legacy parentId columns
    suspend fun migrateLegacyPlacements() {
        val allTiles = aacTileDao.getAllTilesSync()
        allTiles.forEach { tile ->
            val effectiveParentId = tile.parentId ?: ROOT_PARENT_ID
            aacTileDao.insertPlacement(
                TilePlacement(
                    tileId = tile.id,
                    parentId = effectiveParentId,
                    languageCode = tile.languageCode,
                    cellIndex = tile.cellIndex,
                    sortOrder = tile.sortOrder
                )
            )
        }
    }

    fun getAllTiles(langCode: String): Flow<List<AACTile>> {
        return aacTileDao.getAllTiles(langCode)
    }

    fun getEverythingFlow(): Flow<List<AACTile>> {
        return aacTileDao.getEverythingFlow()
    }

    fun getAllCategories(langCode: String): Flow<List<AACTile>> {
        return aacTileDao.getAllCategories(langCode)
    }

    suspend fun getTileById(id: String, langCode: String): AACTile? {
        return aacTileDao.getTileById(id, langCode)
    }

    suspend fun insertTile(tile: AACTile) {
        aacTileDao.insertTile(tile)
        // Also ensure it has a placement in the specified parent
        val effectiveParentId = tile.parentId ?: ROOT_PARENT_ID
        aacTileDao.insertPlacement(
            TilePlacement(
                tileId = tile.id,
                parentId = effectiveParentId,
                languageCode = tile.languageCode,
                cellIndex = tile.cellIndex,
                sortOrder = tile.sortOrder
            )
        )
    }

    suspend fun insertTiles(tiles: List<AACTile>) {
        aacTileDao.insertTiles(tiles)
        // For batch insert, we should also batch insert placements
        val placements = tiles.map { tile ->
            TilePlacement(
                tileId = tile.id,
                parentId = tile.parentId ?: ROOT_PARENT_ID,
                languageCode = tile.languageCode,
                cellIndex = tile.cellIndex,
                sortOrder = tile.sortOrder
            )
        }
        // Need to add insertPlacements to DAO
        placements.forEach { aacTileDao.insertPlacement(it) }
    }

    suspend fun updateTile(tile: AACTile) {
        aacTileDao.updateTile(tile)
        // Also update placement for the context provided in the tile object
        val effectiveParentId = tile.parentId ?: ROOT_PARENT_ID
        aacTileDao.insertPlacement(
            TilePlacement(
                tileId = tile.id,
                parentId = effectiveParentId,
                languageCode = tile.languageCode,
                cellIndex = tile.cellIndex,
                sortOrder = tile.sortOrder
            )
        )
    }

    suspend fun deleteTile(tile: AACTile) {
        aacTileDao.deleteTile(tile)
        // Placements will be deleted by Foreign Key Cascade if set up correctly
        // But let's be explicit just in case or if we want to handle language specifically
        aacTileDao.deleteAllPlacementsForTile(tile.id, tile.languageCode)
    }

    suspend fun incrementClickCount(id: String, langCode: String) {
        aacTileDao.incrementClickCount(id, langCode)
        aacTileDao.insertClickEvent(TileClickEvent(tileId = id))
    }

    fun getClickEventsBetween(startTime: Long, endTime: Long): Flow<List<TileClickEvent>> {
        return aacTileDao.getClickEventsBetween(startTime, endTime)
    }

    fun getAllClickEvents(): Flow<List<TileClickEvent>> {
        return aacTileDao.getAllClickEvents()
    }

    suspend fun clearAllStatistics() {
        aacTileDao.deleteAllClickEvents()
        aacTileDao.resetAllLegacyClickCounts()
    }

    suspend fun isEmpty(): Boolean {
        return aacTileDao.getCount() == 0
    }

    suspend fun deleteAllTiles() {
        aacTileDao.deleteAllTiles()
    }

    suspend fun deleteTilesByLanguage(languageCode: String) {
        aacTileDao.deleteTilesByLanguage(languageCode)
    }

    suspend fun getAllTilesSync(): List<AACTile> {
        return aacTileDao.getAllTilesSync()
    }

    suspend fun getAllTilesWithPlacements(): List<AACTile> {
        return aacTileDao.getAllTilesWithPlacements().map { it.toAACTile() }
    }
}