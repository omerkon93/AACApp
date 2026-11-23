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

        val cleanAudioUri = audioUri?.trim()?.takeIf { it.isNotEmpty() }
        val updatedTile = tile.copy(
            definition = tile.definition.copy(
                audioUri = cleanAudioUri,
                isCustomAudio = cleanAudioUri != null,
            ),
        )

        tileRepository.updateTile(updatedTile)
    }
}