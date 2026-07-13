package com.kon.myaacapp.domain.model

import com.kon.myaacapp.data.local.entity.AACTile

/*
 * Used by previews and legacy AACTile-based code while the project transitions
 * toward the separated definition/layout architecture.
 */
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
            defaultLinkedCategoryId = linkedCategoryId,
        ),
        layoutState = TileLayoutState(
            tileId = id,
            parentId = parentId,
            linkedCategoryId = linkedCategoryId,
            cellIndex = cellIndex ?: 0,
            isQuickFire = isQuickFire,
            isHidden = isHidden,
            clickCount = clickCount,
        ),
    )
}

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
        isCategory = tileType == TileType.FOLDER,
        parentId = layoutState.parentId,
        linkedCategoryId = layoutState.linkedCategoryId,
        cellIndex = layoutState.cellIndex,
        sortOrder = 0,
        isQuickFire = isQuickFire,
        isHidden = layoutState.isHidden,
        clickCount = layoutState.clickCount,
        languageCode = definition.languageCode,
    )
}