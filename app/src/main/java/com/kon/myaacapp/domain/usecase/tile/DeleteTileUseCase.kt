package com.kon.myaacapp.domain.usecase.tile

import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.repository.TileRepository

class DeleteTileUseCase(
    private val tileRepository: TileRepository,
) {
    suspend operator fun invoke(
        tile: CombinedTile,
    ) {
        require(tile.id.isNotBlank()) {
            "Tile ID cannot be blank."
        }

        tileRepository.deleteTile(tile)
    }
}