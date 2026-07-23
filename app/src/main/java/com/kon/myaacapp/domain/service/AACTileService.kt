package com.kon.myaacapp.domain.service

import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.CombinedTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Gender { MALE, FEMALE }

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

    fun getTTSText(tile: CombinedTile): String {
        return if (userGender.value == Gender.FEMALE) {
            tile.definition.ttsTextFeminine ?: tile.definition.ttsText
        } else {
            tile.definition.ttsText
        }
    }

    fun handleTilePress(tile: CombinedTile): Pair<Boolean, String?> {
        if (tile.isCategory) {
            return Pair(false, tile.id)
        }

        if (tile.layoutState.isQuickFire) {
            return Pair(false, tile.linkedCategoryId)
        }

        return Pair(true, tile.linkedCategoryId)
    }

    fun getLocalizedLabel(tile: CombinedTile): String {
        return if (userGender.value == Gender.FEMALE) {
            tile.definition.labelFeminine ?: tile.definition.label
        } else {
            tile.definition.label
        }
    }

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