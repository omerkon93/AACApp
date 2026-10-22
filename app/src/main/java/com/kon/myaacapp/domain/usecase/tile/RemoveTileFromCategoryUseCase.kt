package com.kon.myaacapp.domain.usecase.tile

import com.kon.myaacapp.domain.repository.TileRepository

class RemoveTileFromCategoryUseCase(
    private val tileRepository: TileRepository,
) {
    suspend operator fun invoke(
        tileId: String,
        parentId: String?,
    ) {
        require(tileId.isNotBlank()) {
            "Tile ID cannot be blank."
        }

        tileRepository.removeTileFromCategory(
            tileId = tileId,
            parentId = parentId,
        )
    }
}