package com.kon.myaacapp

import kotlinx.coroutines.flow.Flow

class AACRepository(private val aacTileDao: AACTileDao) {
    
    fun getTilesByParentId(parentId: String?): Flow<List<AACTile>> {
        return if (parentId == null) {
            aacTileDao.getRootTiles()
        } else {
            aacTileDao.getTilesByParentId(parentId)
        }
    }

    fun getAllTiles(): Flow<List<AACTile>> {
        return aacTileDao.getAllTiles()
    }

    fun getAllCategories(): Flow<List<AACTile>> {
        return aacTileDao.getAllCategories()
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
    }

    suspend fun isEmpty(): Boolean {
        return aacTileDao.getCount() == 0
    }

    suspend fun deleteAllTiles() {
        aacTileDao.deleteAllTiles()
    }
}