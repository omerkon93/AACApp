package com.kon.myaacapp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Gender { MALE, FEMALE }

class AACTileService(private val repository: AACRepository) {

    private val _userGender = MutableStateFlow(Gender.MALE)
    val userGender: StateFlow<Gender> = _userGender.asStateFlow()

    fun setUserGender(gender: Gender) {
        _userGender.value = gender
    }

    /**
     * Resolves the correct display label based on user's gender profile.
     */
    fun getDisplayLabel(tile: AACTile): String {
        return if (_userGender.value == Gender.FEMALE) {
            tile.labelFeminine ?: tile.label
        } else {
            tile.label
        }
    }

    /**
     * Resolves the correct TTS text based on user's gender profile.
     */
    fun getTTSText(tile: AACTile): String {
        return if (_userGender.value == Gender.FEMALE) {
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
        repository.incrementClickCount(tile.id)

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
