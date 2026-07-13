package com.kon.myaacapp.domain.service

import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.CombinedTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Gender { MALE, FEMALE }

// OPTIMIZATION: Applied class-level suppression to keep your Service API
// contract intact for upcoming UI components without cluttering the IDE.
@Suppress("unused")
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

    // IMPORTANT: Because this reads .value synchronously, the calling Composable
    // MUST collect 'userGender' as state at the top level to trigger recomposition
    // if the gender changes dynamically while the app is open.
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

        // Regular tiles get added to the strip AND might navigate to a sub-category
        return Pair(true, tile.linkedCategoryId)
    }

    fun getLocalizedLabel(tile: CombinedTile): String {
        return if (userGender.value == Gender.FEMALE) {
            tile.definition.labelFeminine ?: tile.definition.label
        } else {
            tile.definition.label
        }
    }

    // FIX: The 'gender' parameter is now properly evaluated.
    // If a legacy caller explicitly forces a gender string, it respects it.
    // If null, it falls back to the global StateFlow.
    fun getLocalizedLabel(label: String, labelFeminine: String?, gender: String? = null): String {
        val isFemale = when {
            gender != null -> gender.equals("FEMALE", ignoreCase = true)
            else -> userGender.value == Gender.FEMALE
        }

        return if (isFemale) {
            labelFeminine ?: label
        } else {
            label
        }
    }
}