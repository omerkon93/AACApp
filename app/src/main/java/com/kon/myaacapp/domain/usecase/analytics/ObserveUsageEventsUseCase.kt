package com.kon.myaacapp.domain.usecase.analytics

import com.kon.myaacapp.domain.model.TileUsageEvent
import com.kon.myaacapp.domain.repository.TileRepository
import kotlinx.coroutines.flow.Flow

class ObserveUsageEventsUseCase(
    private val tileRepository: TileRepository,
) {
    operator fun invoke(
        startTime: Long,
        endTime: Long,
    ): Flow<List<TileUsageEvent>> {
        return tileRepository.observeUsageEvents(
            startTime = startTime,
            endTime = endTime,
        )
    }
}