package com.kon.myaacapp

import kotlinx.coroutines.flow.Flow

class AACRepository(private val aacTileDao: AACTileDao) {
    
    fun getTilesByParentId(parentId: String?, langCode: String): Flow<List<AACTile>> {
        return if (parentId == null) {
            aacTileDao.getRootTiles(langCode)
        } else {
            aacTileDao.getTilesByParentId(parentId, langCode)
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

    suspend fun getTileById(id: String): AACTile? {
        return aacTileDao.getTileById(id)
    }

    suspend fun insertTile(tile: AACTile) {
        aacTileDao.insertTile(tile)
    }

    suspend fun insertTiles(tiles: List<AACTile>) {
        aacTileDao.insertTiles(tiles)
    }

    suspend fun updateTile(tile: AACTile) {
        aacTileDao.updateTile(tile)
    }

    suspend fun deleteTile(tile: AACTile) {
        aacTileDao.deleteTile(tile)
    }

    suspend fun incrementClickCount(id: String) {
        aacTileDao.incrementClickCount(id)
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

    suspend fun getAllTilesSync(): List<AACTile> {
        return aacTileDao.getAllTilesSync()
    }
}