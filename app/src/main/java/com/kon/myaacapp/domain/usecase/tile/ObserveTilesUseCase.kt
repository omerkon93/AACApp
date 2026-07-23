package com.kon.myaacapp.domain.usecase.tile

import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.repository.TileRepository
import kotlinx.coroutines.flow.Flow

class ObserveTilesUseCase(
    private val tileRepository: TileRepository,
) {
    operator fun invoke(
        parentId: String?,
        languageCode: String,
    ): Flow<List<CombinedTile>> {
        return tileRepository.getCombinedTiles(
            parentId = parentId,
            langCode = languageCode,
        )
    }
}