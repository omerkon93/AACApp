package com.kon.myaacapp.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonNames

@Immutable
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TileDefinition(
    val id: String,
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

    // Retained for compatibility with existing serialized tile data.
    val isCategory: Boolean = false,

    val type: TileType? = null,
    val languageCode: String = "he",

    // Accepts the legacy JSON field names during import/deserialization.
    @JsonNames("parentId")
    val defaultParentId: String? = null,

    @JsonNames("cellIndex")
    val defaultCellIndex: Int? = null,

    @JsonNames("linkedCategoryId")
    val defaultLinkedCategoryId: String? = null,
) {
    @Transient
    val resolvedType: TileType = type ?: when {
        isCategory -> TileType.FOLDER

        defaultLinkedCategoryId != null -> {
            TileType.CONNECTOR
        }

        else -> TileType.BASIC
    }
}