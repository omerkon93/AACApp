package com.kon.myaacapp.domain.usecase.tile

import com.kon.myaacapp.domain.model.CreateTileRequest
import com.kon.myaacapp.domain.repository.TileRepository

class AddTileUseCase(
    private val tileRepository: TileRepository,
) {
    suspend operator fun invoke(
        request: CreateTileRequest,
    ) {
        require(request.label.isNotBlank()) {
            "Tile label cannot be blank."
        }

        require(request.ttsText.isNotBlank()) {
            "Tile TTS text cannot be blank."
        }

        require(request.languageCode.isNotBlank()) {
            "Tile language code cannot be blank."
        }

        require(
            request.cellIndex == null ||
                    request.cellIndex >= 0
        ) {
            "Tile cell index cannot be negative."
        }

        tileRepository.addTile(request)
    }
}