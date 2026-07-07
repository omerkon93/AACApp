package com.kon.myaacapp

import androidx.room.Entity
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TileDefinition(
    val id: String,

    // 1. Base Text & Speech
    val label: String,            // Display text
    val ttsText: String,          // Phonetic/engine text

    // 2. Gender Localization (Hebrew support based on User's profile)
    val labelFeminine: String? = null,
    val ttsTextFeminine: String? = null,

    // 3. Media (Audio & Visuals)
    val emoji: String? = null,
    val audioUri: String? = null, // Custom recorded voice (Voice Banking)
    val imageUri: String? = null, // Custom photos/symbols (Boardmaker/PCS/Custom)
    val backgroundColorHex: String? = null, // For Fitzgerald Key color coding

    // 4. Grammar
    val partOfSpeech: String? = null,     // e.g., "VERB", "NOUN", "PRONOUN"
    val grammaticalGender: String? = null, // "M" or "F" (for matching adjectives)

    // 5. Navigation & Linking
    val isCategory: Boolean,              // True if it ONLY opens a folder

    val languageCode: String = "he",   // "he", "en", etc.

    // Template fields for initial layout population
    @JsonNames("parentId")
    val defaultParentId: String? = null,
    @JsonNames("cellIndex")
    val defaultCellIndex: Int? = null,
    @JsonNames("linkedCategoryId")
    val defaultLinkedCategoryId: String? = null
)

@Serializable
data class TileLayoutState(
    val tileId: String,
    val parentId: String? = null,   // The folder this tile lives inside
    val linkedCategoryId: String? = null, // Adds word to sentence AND opens this folder
    val cellIndex: Int,           // Fixed position on the grid
    val isQuickFire: Boolean = false, // Speaks instantly, doesn't add to sentence strip
    val isHidden: Boolean = false,    // Hides from UI without deleting
    val clickCount: Int = 0           // Analytics for therapists/parents
)

data class CombinedTile(
    val definition: TileDefinition,
    val layoutState: TileLayoutState
)

fun CombinedTile.toLegacyAACTile(): AACTile {
    return AACTile(
        id = definition.id,
        label = definition.label,
        ttsText = definition.ttsText,
        labelFeminine = definition.labelFeminine,
        ttsTextFeminine = definition.ttsTextFeminine,
        emoji = definition.emoji,
        audioUri = definition.audioUri,
        imageUri = definition.imageUri,
        backgroundColorHex = definition.backgroundColorHex,
        partOfSpeech = definition.partOfSpeech,
        grammaticalGender = definition.grammaticalGender,
        isCategory = definition.isCategory,
        parentId = layoutState.parentId,
        linkedCategoryId = layoutState.linkedCategoryId,
        cellIndex = layoutState.cellIndex,
        sortOrder = 0,
        isQuickFire = layoutState.isQuickFire,
        isHidden = layoutState.isHidden,
        clickCount = layoutState.clickCount,
        languageCode = definition.languageCode
    )
}

// Extension properties for convenience
val CombinedTile.id get() = definition.id
val CombinedTile.label get() = definition.label
val CombinedTile.ttsText get() = definition.ttsText
val CombinedTile.emoji get() = definition.emoji
val CombinedTile.imageUri get() = definition.imageUri
val CombinedTile.audioUri get() = definition.audioUri
val CombinedTile.backgroundColorHex get() = definition.backgroundColorHex
val CombinedTile.isCategory get() = definition.isCategory
val CombinedTile.parentId get() = layoutState.parentId
val CombinedTile.linkedCategoryId get() = layoutState.linkedCategoryId
val CombinedTile.languageCode get() = definition.languageCode
val CombinedTile.cellIndex get() = layoutState.cellIndex
val CombinedTile.isQuickFire get() = layoutState.isQuickFire
val CombinedTile.isHidden get() = layoutState.isHidden
val CombinedTile.clickCount get() = layoutState.clickCount

// Temporary AACTile for backward compatibility during refactoring
@Entity(tableName = "aac_tiles", primaryKeys = ["id", "languageCode"])
@Serializable
data class AACTile(
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
    val isCategory: Boolean,
    val parentId: String? = null,
    val linkedCategoryId: String? = null,
    val cellIndex: Int? = null,
    val sortOrder: Int = 0,
    val isQuickFire: Boolean = false,
    val isHidden: Boolean = false,
    val clickCount: Int = 0,
    val languageCode: String = "he",
)

fun AACTile.toCombinedTile(): CombinedTile {
    return CombinedTile(
        definition = TileDefinition(
            id = id,
            label = label,
            ttsText = ttsText,
            labelFeminine = labelFeminine,
            ttsTextFeminine = ttsTextFeminine,
            emoji = emoji,
            audioUri = audioUri,
            imageUri = imageUri,
            backgroundColorHex = backgroundColorHex,
            partOfSpeech = partOfSpeech,
            grammaticalGender = grammaticalGender,
            isCategory = isCategory,
            languageCode = languageCode,
            defaultParentId = parentId,
            defaultCellIndex = cellIndex,
            defaultLinkedCategoryId = linkedCategoryId
        ),
        layoutState = TileLayoutState(
            tileId = id,
            parentId = parentId,
            linkedCategoryId = linkedCategoryId,
            cellIndex = cellIndex ?: 0,
            isQuickFire = isQuickFire,
            isHidden = isHidden,
            clickCount = clickCount
        )
    )
}

data class AACTileWithPlacement(
    @androidx.room.Embedded val tile: AACTile,
    @androidx.room.ColumnInfo(name = "placed_cellIndex") val placedCellIndex: Int?,
    @androidx.room.ColumnInfo(name = "placed_sortOrder") val placedSortOrder: Int,
    @androidx.room.ColumnInfo(name = "placed_parentId") val placedParentId: String?
)

fun AACTileWithPlacement.toAACTile(): AACTile {
    return tile.copy(
        cellIndex = placedCellIndex,
        sortOrder = placedSortOrder,
        parentId = if (placedParentId == "ROOT_COLLECTION") null else placedParentId
    )
}
