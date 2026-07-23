package com.kon.myaacapp.domain.usecase.tile

import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.repository.TileRepository

class UpdateTileAudioUseCase(
    private val tileRepository: TileRepository,
) {
    suspend operator fun invoke(
        tile: CombinedTile,
        audioUri: String?,
    ) {
        require(tile.id.isNotBlank()) {
            "Tile ID cannot be blank."
        }

        val updatedTile = tile.copy(
            definition = tile.definition.copy(
                audioUri = audioUri,
            ),
        )

        tileRepository.updateTile(updatedTile)
    }
}