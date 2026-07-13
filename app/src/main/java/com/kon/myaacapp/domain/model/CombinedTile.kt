package com.kon.myaacapp.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class CombinedTile(
    val definition: TileDefinition,
    val layoutState: TileLayoutState,
) {
    val id: String
        get() = definition.id

    val label: String
        get() = definition.label

    val ttsText: String
        get() = definition.ttsText

    val emoji: String?
        get() = definition.emoji

    val imageUri: String?
        get() = definition.imageUri

    val audioUri: String?
        get() = definition.audioUri

    val backgroundColorHex: String?
        get() = definition.backgroundColorHex

    val tileType: TileType
        get() = definition.resolvedType

    val isCategory: Boolean
        get() = tileType == TileType.FOLDER

    val parentId: String?
        get() = layoutState.parentId

    val linkedCategoryId: String?
        get() = layoutState.linkedCategoryId

    val languageCode: String
        get() = definition.languageCode

    val cellIndex: Int
        get() = layoutState.cellIndex

    val isQuickFire: Boolean
        get() = layoutState.isQuickFire ||
                tileType == TileType.QUICK_FIRE

    val isHidden: Boolean
        get() = layoutState.isHidden

    val clickCount: Int
        get() = layoutState.clickCount
}