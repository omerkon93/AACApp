package com.kon.myaacapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Gender { MALE, FEMALE }

class AACTileService(
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope
) {
    val userGender: StateFlow<Gender> = settingsRepository.userGenderFlow
        .stateIn(scope, SharingStarted.Eagerly, Gender.MALE)

    fun setUserGender(gender: Gender, scope: CoroutineScope) {
        scope.launch {
            settingsRepository.updateUserGender(gender)
        }
    }

    fun getTTSText(tile: CombinedTile): String {
        return if (userGender.value == Gender.FEMALE) {
            tile.definition.ttsTextFeminine ?: tile.definition.ttsText
        } else {
            tile.definition.ttsText
        }
    }

    fun handleTilePress(tile: CombinedTile): Pair<Boolean, String?> {
        // Categories do NOT get added to the sentence strip
        if (tile.isCategory) {
            return Pair(false, tile.id)
        }

        // Quick-fires do NOT get added to the sentence strip
        if (tile.layoutState.isQuickFire) {
            return Pair(false, tile.linkedCategoryId)
        }

        // Regular tiles get added to the strip AND might navigate to a sub-category (like "Eat" -> "Food Items")
        return Pair(true, tile.linkedCategoryId)
    }

    // Helper for UI labels (not TTS)
    fun getLocalizedLabel(tile: CombinedTile): String {
        return if (userGender.value == Gender.FEMALE) {
            tile.definition.labelFeminine ?: tile.definition.label
        } else {
            tile.definition.label
        }
    }

    // Legacy support
    fun getLocalizedLabel(label: String, labelFeminine: String?, gender: String? = null): String {
        return if (userGender.value == Gender.FEMALE) {
            labelFeminine ?: label
        } else {
            label
        }
    }
}
