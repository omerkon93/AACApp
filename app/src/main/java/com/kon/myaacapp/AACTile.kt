package com.kon.myaacapp

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonNames

@Serializable
enum class TileType {
    BASIC,
    FOLDER,
    CONNECTOR,
    QUICK_FIRE
}

// OPTIMIZATION: @Immutable explicitly tells the Compose Compiler that all fields are final.
// This prevents expensive recursive equality checks during UI recomposition, guaranteeing O(1) diffing.
@Immutable
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TileDefinition(
    val id: String,

    // 1. Base Text & Speech
    val label: String,
    val ttsText: String,

    // 2. Gender Localization
    val labelFeminine: String? = null,
    val ttsTextFeminine: String? = null,

    // 3. Media (Audio & Visuals)
    val emoji: String? = null,
    val audioUri: String? = null,
    val imageUri: String? = null,
    val backgroundColorHex: String? = null,

    // 4. Grammar
    val partOfSpeech: String? = null,
    val grammaticalGender: String? = null,

    // 5. Navigation & Linking (Legacy support)
    val isCategory: Boolean = false,

    // 6. Explicit Type (NEW)
    val type: TileType? = null,

    val languageCode: String = "he",

    // Template fields for initial layout population
    @JsonNames("parentId")
    val defaultParentId: String? = null,
    @JsonNames("cellIndex")
    val defaultCellIndex: Int? = null,
    @JsonNames("linkedCategoryId")
    val defaultLinkedCategoryId: String? = null
) {
    @Transient
    val resolvedType: TileType = type ?: when {
        isCategory -> TileType.FOLDER
        defaultLinkedCategoryId != null -> TileType.CONNECTOR
        else -> TileType.BASIC
    }
}

// OPTIMIZATION: Marked @Immutable for O(1) Compose diffing
@Immutable
@Serializable
data class TileLayoutState(
    val tileId: String,
    val parentId: String? = null,
    val linkedCategoryId: String? = null,
    val cellIndex: Int,
    val isQuickFire: Boolean = false,
    val isHidden: Boolean = false,
    val clickCount: Int = 0
)

// OPTIMIZATION: Marked @Immutable for O(1) Compose diffing.
// When this is passed to your Grid item, Compose will never recompose it unnecessarily.
@Immutable
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
        isCategory = definition.resolvedType == TileType.FOLDER,
        isQuickFire = layoutState.isQuickFire || definition.resolvedType == TileType.QUICK_FIRE,
        parentId = layoutState.parentId,
        linkedCategoryId = layoutState.linkedCategoryId,
        cellIndex = layoutState.cellIndex,
        sortOrder = 0,
        isHidden = layoutState.isHidden,
        clickCount = layoutState.clickCount,
        languageCode = definition.languageCode
    )
}

val CombinedTile.id get() = definition.id
val CombinedTile.label get() = definition.label
val CombinedTile.ttsText get() = definition.ttsText
val CombinedTile.emoji get() = definition.emoji
val CombinedTile.imageUri get() = definition.imageUri
val CombinedTile.audioUri get() = definition.audioUri
val CombinedTile.backgroundColorHex get() = definition.backgroundColorHex
val CombinedTile.isCategory get() = definition.resolvedType == TileType.FOLDER

// FIX: Suppressed unused warning to maintain your exact API contract
@Suppress("unused")
val CombinedTile.tileType get() = definition.resolvedType

val CombinedTile.parentId get() = layoutState.parentId
val CombinedTile.linkedCategoryId get() = layoutState.linkedCategoryId
val CombinedTile.languageCode get() = definition.languageCode
val CombinedTile.cellIndex get() = layoutState.cellIndex

// FIX: Suppressed unused warning to maintain your exact API contract
@Suppress("unused")
val CombinedTile.isQuickFire get() = layoutState.isQuickFire || definition.resolvedType == TileType.QUICK_FIRE

val CombinedTile.isHidden get() = layoutState.isHidden
val CombinedTile.clickCount get() = layoutState.clickCount

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
    val mappedType = when {
        isCategory -> TileType.FOLDER
        linkedCategoryId != null -> TileType.CONNECTOR
        isQuickFire -> TileType.QUICK_FIRE
        else -> TileType.BASIC
    }

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
            type = mappedType,
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