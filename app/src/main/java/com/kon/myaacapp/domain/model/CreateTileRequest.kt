package com.kon.myaacapp.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class CreateTileRequest(
    val id: String? = null,
    val label: String,
    val ttsText: String,

    val labelFeminine: String? = null,
    val ttsTextFeminine: String? = null,

    val emoji: String? = null,
    val audioUri: String? = null,
    val imageUri: String? = null,
    val backgroundColorHex: String? = null,

    val partOfSpeech: String? = null,
    val grammaticalGender: String? = null,

    val tileType: TileType = TileType.BASIC,
    val languageCode: String,

    val parentId: String? = null,
    val linkedCategoryId: String? = null,
    val cellIndex: Int? = null,

    val isHidden: Boolean = false,
)