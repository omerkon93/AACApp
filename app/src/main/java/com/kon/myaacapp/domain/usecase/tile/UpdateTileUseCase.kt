package com.kon.myaacapp.domain.usecase.tile

import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.repository.TileRepository

class UpdateTileUseCase(
    private val tileRepository: TileRepository,
) {
    suspend operator fun invoke(
        tile: CombinedTile,
    ) {
        require(tile.id.isNotBlank()) {
            "Tile ID cannot be blank."
        }

        require(tile.definition.label.isNotBlank()) {
            "Tile label cannot be blank."
        }

        require(tile.definition.ttsText.isNotBlank()) {
            "Tile TTS text cannot be blank."
        }

        tileRepository.updateTile(tile)
    }
}