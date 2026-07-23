package com.kon.myaacapp.domain.usecase.tile

import com.kon.myaacapp.domain.repository.TileRepository

class AttachTileToCategoryUseCase(
    private val tileRepository: TileRepository,
) {
    suspend operator fun invoke(
        tileId: String,
        parentId: String?,
        languageCode: String,
        cellIndex: Int?,
    ) {
        require(tileId.isNotBlank()) {
            "Tile ID cannot be blank."
        }

        tileRepository.attachTileToCategory(
            tileId = tileId,
            parentId = parentId,
            langCode = languageCode,
            cellIndex = cellIndex,
        )
    }
}