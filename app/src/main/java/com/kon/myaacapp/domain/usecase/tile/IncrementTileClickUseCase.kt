package com.kon.myaacapp.domain.usecase.tile

import com.kon.myaacapp.domain.repository.TileRepository

class IncrementTileClickUseCase(
    private val tileRepository: TileRepository,
) {
    suspend operator fun invoke(
        tileId: String,
        parentId: String?,
        languageCode: String,
    ) {
        if (tileId.isBlank()) {
            return
        }

        tileRepository.incrementClickCount(
            id = tileId,
            parentId = parentId,
            langCode = languageCode,
        )
    }
}