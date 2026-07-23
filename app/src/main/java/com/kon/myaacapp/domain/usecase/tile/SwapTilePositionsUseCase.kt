package com.kon.myaacapp.domain.usecase.tile

import com.kon.myaacapp.domain.repository.TileRepository

class SwapTilePositionsUseCase(
    private val tileRepository: TileRepository,
) {
    suspend operator fun invoke(
        parentId: String?,
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex == toIndex) {
            return
        }

        tileRepository.swapTilesByIndex(
            parentId = parentId,
            fromIndex = fromIndex,
            toIndex = toIndex,
        )
    }
}