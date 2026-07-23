package com.kon.myaacapp.domain.repository

import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.CreateTileRequest
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.TileUsageEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TileRepository {

    val baseDefinitions: StateFlow<List<TileDefinition>>

    suspend fun reload()

    suspend fun loadAllDefinitions()

    fun getCombinedTiles(
        parentId: String?,
        langCode: String,
    ): Flow<List<CombinedTile>>

    fun getAllDefinitionsAsCombinedTiles(
        langCode: String,
    ): Flow<List<CombinedTile>>

    suspend fun addTile(
        request: CreateTileRequest,
    )

    suspend fun updateTile(
        tile: CombinedTile,
    )

    suspend fun deleteTile(
        tile: CombinedTile,
    )

    suspend fun updateTileIndex(
        tileId: String,
        parentId: String?,
        newIndex: Int,
    )

    suspend fun updateTileVisibility(
        tileId: String,
        parentId: String?,
        isHidden: Boolean,
    )

    suspend fun attachTileToCategory(
        tileId: String,
        parentId: String?,
        langCode: String,
        cellIndex: Int? = null,
    )

    suspend fun removeTileFromCategory(
        tileId: String,
        parentId: String?,
        langCode: String,
    )

    suspend fun swapTilesByIndex(
        parentId: String?,
        fromIndex: Int,
        toIndex: Int,
    )

    suspend fun incrementClickCount(
        id: String,
        parentId: String?,
        langCode: String,
    )

    fun observeUsageEvents(
        startTime: Long,
        endTime: Long,
    ): Flow<List<TileUsageEvent>>
}