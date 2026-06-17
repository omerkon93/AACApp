package com.kon.myaacapp

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Gender { MALE, FEMALE }

class AACTileService(
    private val repository: AACRepository,
    private val settingsRepository: SettingsRepository,
    scope: kotlinx.coroutines.CoroutineScope
) {

    val userGender: StateFlow<Gender> = settingsRepository.userGenderFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = Gender.MALE
        )

    fun setUserGender(gender: Gender, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            settingsRepository.updateUserGender(gender)
        }
    }

    /**
     * Resolves the correct TTS text based on user's gender profile.
     */
    fun getTTSText(tile: AACTile): String {
        return if (userGender.value == Gender.FEMALE) {
            tile.ttsTextFeminine ?: tile.ttsText
        } else {
            tile.ttsText
        }
    }

    /**
     * Handles the complex navigation and state transitions when a tile is pressed.
     * @return Pair of (ShouldAddToStringStrip, NavigateToCategoryId?)
     */
    suspend fun handleTilePress(tile: AACTile): Pair<Boolean, String?> {
        // Analytics
        repository.incrementClickCount(tile.id, tile.languageCode)

        return when {
            tile.isQuickFire -> {
                // Speak immediately, don't add to sentence, no navigation
                Pair(false, null)
            }
            tile.isCategory -> {
                // Only navigate, don't add to sentence
                Pair(false, tile.id)
            }
            tile.linkedCategoryId != null -> {
                // Hybrid: Add to sentence AND navigate to category
                Pair(true, tile.linkedCategoryId)
            }
            else -> {
                // Standard word: Add to sentence, no navigation
                Pair(true, null)
            }
        }
    }
}
