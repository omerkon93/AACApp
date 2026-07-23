package com.kon.myaacapp.domain.usecase.tile

import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.repository.TileRepository
import kotlinx.coroutines.flow.Flow

class ObserveAllTilesUseCase(
    private val tileRepository: TileRepository,
) {
    operator fun invoke(
        languageCode: String,
    ): Flow<List<CombinedTile>> {
        return tileRepository.getAllDefinitionsAsCombinedTiles(
            langCode = languageCode,
        )
    }
}
