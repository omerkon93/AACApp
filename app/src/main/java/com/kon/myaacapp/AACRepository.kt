package com.kon.myaacapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class AACRepository(
    private val aacTileDao: AACTileDao,
    private val context: Context,
    private val profileRepository: ProfileRepository
) {
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true 
        encodeDefaults = true
    }

    val activeProfile: StateFlow<UserProfile?> = profileRepository.activeProfile

    private val _baseDefinitions = MutableStateFlow<List<TileDefinition>>(emptyList())
    val baseDefinitions: StateFlow<List<TileDefinition>> = _baseDefinitions.asStateFlow()

    init {
        // Definitions are loaded lazily when first needed, 
        // but we trigger a load now to ensure bootstrapper can populate layouts
        profileRepository.scope.launch {
            loadAllDefinitions()
            val profile = activeProfile.value
            if (profile != null && profile.layout.isEmpty()) {
                val populatedProfile = populateInitialLayout(profile)
                profileRepository.updateActiveProfile(populatedProfile)
            }
        }
    }

    suspend fun reload() {
        loadAllDefinitions()
        val profile = activeProfile.value
        if (profile != null && profile.layout.isEmpty()) {
            val populatedProfile = populateInitialLayout(profile)
            profileRepository.updateActiveProfile(populatedProfile)
        }
    }

    private suspend fun loadDictionaryFile(file: File): List<TileDefinition> {
        if (!file.exists()) return emptyList()
        return try {
            val jsonString = file.readText().removePrefix("\uFEFF")
            json.decodeFromString<List<TileDefinition>>(jsonString)
        } catch (e: Exception) {
            Log.e("Debug_AAC", "Failed to parse JSON: ${file.name}", e)
            emptyList()
        }
    }

    suspend fun loadAllDefinitions() = withContext(Dispatchers.IO) {
        val tilesDir = File(context.filesDir, "tiles")
        val allMergedDefinitions = mutableListOf<TileDefinition>()

        if (tilesDir.exists()) {
            // Find all language folders (e.g., "he", "en")
            val languageDirs = tilesDir.listFiles { file -> file.isDirectory } ?: emptyArray()

            for (langDir in languageDirs) {
                // 1. Read Factory Defaults
                val defaultFile = File(langDir, "default_dictionary.json")
                val defaultTiles = loadDictionaryFile(defaultFile)

                // 2. Read User Edits (using your existing export file name!)
                val userFile = File(langDir, "export_user_defined.json")
                val userTiles = loadDictionaryFile(userFile)

                // 3. Merge them!
                val mergedLangTiles = mergeDictionaries(defaultTiles, userTiles)
                allMergedDefinitions.addAll(mergedLangTiles)
            }
        }

        try {
            // 4. Also include any legacy tiles from Room (backward compatibility)
            val roomTiles = aacTileDao.getAllTilesSync()
            val roomDefinitions = roomTiles.map { tile ->
                TileDefinition(
                    id = tile.id,
                    label = tile.label,
                    ttsText = tile.ttsText,
                    labelFeminine = tile.labelFeminine,
                    ttsTextFeminine = tile.ttsTextFeminine,
                    emoji = tile.emoji,
                    audioUri = tile.audioUri,
                    imageUri = tile.imageUri,
                    backgroundColorHex = tile.backgroundColorHex,
                    partOfSpeech = tile.partOfSpeech,
                    grammaticalGender = tile.grammaticalGender,
                    isCategory = tile.isCategory,
                    type = when {
                        tile.isCategory -> TileType.FOLDER
                        tile.linkedCategoryId != null -> TileType.CONNECTOR
                        tile.isQuickFire -> TileType.QUICK_FIRE
                        else -> TileType.BASIC
                    },
                    languageCode = tile.languageCode,
                    defaultParentId = tile.parentId,
                    defaultCellIndex = tile.cellIndex,
                    defaultLinkedCategoryId = tile.linkedCategoryId
                )
            }

            // 5. Do one final merge to ensure Room tiles (if any exist) override defaults
            val finalDefinitions = mergeDictionaries(allMergedDefinitions, roomDefinitions)

            _baseDefinitions.value = finalDefinitions
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCombinedTiles(parentId: String?, langCode: String): Flow<List<CombinedTile>> = 
        combine(activeProfile, baseDefinitions) { profile, definitions ->
            if (profile == null || definitions.isEmpty()) {
                return@combine emptyList<CombinedTile>()
            }

            val effectiveParentId = parentId ?: ROOT_PARENT_ID
            val relevantLayouts = profile.layout.filterKeys { it.startsWith("${effectiveParentId}_") }.values

            relevantLayouts.mapNotNull { layout ->
                val def = definitions.find { it.id == layout.tileId && it.languageCode == langCode }
                if (def != null) {
                    CombinedTile(def, layout)
                } else {
                    null
                }
            }.sortedBy { it.layoutState.cellIndex }
        }

    private fun getLayoutKey(parentId: String?, tileId: String): String {
        return "${parentId ?: ROOT_PARENT_ID}_$tileId"
    }

    fun populateInitialLayout(profile: UserProfile): UserProfile {
        val newLayout = profile.layout.toMutableMap()
        baseDefinitions.value.forEach { def ->
            if (def.defaultCellIndex != null) {
                val state = TileLayoutState(
                    tileId = def.id,
                    parentId = def.defaultParentId,
                    linkedCategoryId = def.defaultLinkedCategoryId,
                    cellIndex = def.defaultCellIndex,
                    isQuickFire = false, 
                    isHidden = false,
                    clickCount = 0
                )
                newLayout[getLayoutKey(def.defaultParentId, def.id)] = state
            }
        }
        return profile.copy(layout = newLayout)
    }

    private fun lockInDefaultState(tileId: String, parentId: String?, state: TileLayoutState) {
        val currentProfile = activeProfile.value ?: return
        val key = getLayoutKey(parentId, tileId)
        if (!currentProfile.layout.containsKey(key)) {
            val newLayout = currentProfile.layout.toMutableMap()
            newLayout[key] = state
            profileRepository.scope.launch {
                profileRepository.updateActiveProfile(currentProfile.copy(layout = newLayout))
            }
        }
    }

    suspend fun updateTileIndex(tileId: String, parentId: String?, newIndex: Int) {
        updateLayoutState(tileId, parentId) { it.copy(cellIndex = newIndex) }
    }

    suspend fun updateTileVisibility(tileId: String, parentId: String?, isHidden: Boolean) {
        updateLayoutState(tileId, parentId) { it.copy(isHidden = isHidden) }
    }

    private suspend fun updateLayoutState(tileId: String, parentId: String?, transform: (TileLayoutState) -> TileLayoutState) {
        val currentProfile = activeProfile.value ?: return
        val newLayout = currentProfile.layout.toMutableMap()
        val key = getLayoutKey(parentId, tileId)
        
        val currentState = newLayout[key] ?: run {
            // Find in base data to get initial state
            val def = baseDefinitions.value.find { it.id == tileId }
            TileLayoutState(
                tileId = tileId,
                parentId = parentId,
                linkedCategoryId = def?.defaultLinkedCategoryId,
                cellIndex = def?.defaultCellIndex ?: 0,
                isQuickFire = false,
                isHidden = false,
                clickCount = 0
            )
        }

        newLayout[key] = transform(currentState)
        profileRepository.updateActiveProfile(currentProfile.copy(layout = newLayout))
    }

    suspend fun attachTileToCategory(tileId: String, parentId: String?, langCode: String, cellIndex: Int? = null) {
        val currentProfile = activeProfile.value ?: return
        val def = baseDefinitions.value.find { it.id == tileId && it.languageCode == langCode } ?: return
        
        val nextIndex = cellIndex ?: (getCombinedTiles(parentId, langCode).first().size)
        
        val newState = TileLayoutState(
            tileId = tileId,
            parentId = parentId,
            linkedCategoryId = def.defaultLinkedCategoryId,
            cellIndex = nextIndex,
            isQuickFire = false,
            isHidden = false,
            clickCount = 0
        )
        
        val newLayout = currentProfile.layout.toMutableMap()
        newLayout[getLayoutKey(parentId, tileId)] = newState
        profileRepository.updateActiveProfile(currentProfile.copy(layout = newLayout))
    }

    suspend fun removeTileFromCategory(tileId: String, parentId: String?, langCode: String) {
        val currentProfile = activeProfile.value ?: return
        val newLayout = currentProfile.layout.toMutableMap()
        newLayout.remove(getLayoutKey(parentId, tileId))
        profileRepository.updateActiveProfile(currentProfile.copy(layout = newLayout))
    }

    suspend fun migrateLegacyPlacements() {
        val placements = aacTileDao.getAllPlacements()
        val currentProfile = activeProfile.value ?: return
        val newLayout = currentProfile.layout.toMutableMap()
        
        placements.forEach { p ->
            val key = getLayoutKey(p.parentId, p.tileId)
            if (!newLayout.containsKey(key)) {
                newLayout[key] = TileLayoutState(
                    tileId = p.tileId,
                    parentId = p.parentId,
                    cellIndex = p.cellIndex ?: 0,
                    isQuickFire = false,
                    isHidden = false,
                    clickCount = 0
                )
            }
        }
        profileRepository.updateActiveProfile(currentProfile.copy(layout = newLayout))
    }

    fun getAllDefinitionsAsCombinedTiles(langCode: String): Flow<List<CombinedTile>> = 
        combine(activeProfile, baseDefinitions) { profile, definitions ->
            if (definitions.isEmpty()) return@combine emptyList<CombinedTile>()

            val relevantDefinitions = definitions.filter { it.languageCode == langCode }
            
            relevantDefinitions.map { def ->
                val layout = profile?.layout?.get(def.id) ?: TileLayoutState(
                    tileId = def.id,
                    parentId = def.defaultParentId,
                    linkedCategoryId = def.defaultLinkedCategoryId,
                    cellIndex = def.defaultCellIndex ?: 0,
                    isQuickFire = false,
                    isHidden = false,
                    clickCount = 0
                )
                CombinedTile(def, layout)
            }
        }

    fun getAllCategories(langCode: String): Flow<List<AACTile>> {
        return aacTileDao.getAllCategories(langCode)
    }

    suspend fun getTileById(id: String, langCode: String): AACTile? {
        return aacTileDao.getTileById(id, langCode)
    }

    suspend fun insertTile(tile: AACTile) {
        val explicitType = when {
            tile.isCategory -> TileType.FOLDER
            tile.linkedCategoryId != null -> TileType.CONNECTOR
            tile.isQuickFire -> TileType.QUICK_FIRE
            else -> TileType.BASIC
        }

        val definition = TileDefinition(
            id = tile.id,
            label = tile.label,
            ttsText = tile.ttsText,
            labelFeminine = tile.labelFeminine,
            ttsTextFeminine = tile.ttsTextFeminine,
            emoji = tile.emoji,
            audioUri = tile.audioUri,
            imageUri = tile.imageUri,
            backgroundColorHex = tile.backgroundColorHex,
            partOfSpeech = tile.partOfSpeech,
            grammaticalGender = tile.grammaticalGender,
            isCategory = tile.isCategory,
            type = explicitType,
            languageCode = tile.languageCode,
            defaultParentId = tile.parentId,
            defaultCellIndex = tile.cellIndex,
            defaultLinkedCategoryId = tile.linkedCategoryId
        )

        // 1. Save to JSON Disk
        saveDefinitionToDisk(definition)

        // 2. Save to Legacy Room DB (CRITICAL FOR GRID DISPLAY)
        aacTileDao.insertTile(tile)

        // 3. Update active profile's layout
        val layoutState = TileLayoutState(
            tileId = tile.id,
            parentId = tile.parentId,
            linkedCategoryId = tile.linkedCategoryId,
            cellIndex = tile.cellIndex ?: 0,
            isQuickFire = tile.isQuickFire,
            isHidden = tile.isHidden,
            clickCount = tile.clickCount
        )
        lockInDefaultState(tile.id, tile.parentId, layoutState)

        loadAllDefinitions()
    }

    private suspend fun saveDefinitionToDisk(definition: TileDefinition) = withContext(Dispatchers.IO) {
        val langDir = File(context.filesDir, "tiles/${definition.languageCode}")
        if (!langDir.exists()) langDir.mkdirs()

        val file = File(langDir, "export_user_defined.json")
        val currentDefinitions = if (file.exists()) {
            try {
                // 1. Strip the hidden BOM character so the parser doesn't crash
                val jsonString = file.readText().removePrefix("\uFEFF")
                json.decodeFromString<List<TileDefinition>>(jsonString).toMutableList()
            } catch (e: Exception) {
                // 2. CRITICAL: Never return an empty list here! It will wipe the user's data.
                Log.e("Debug_AAC", "CRITICAL: Failed to parse existing definitions during save. Aborting overwrite!", e)
                return@withContext
            }
        } else {
            mutableListOf()
        }

        currentDefinitions.removeAll { it.id == definition.id }
        currentDefinitions.add(definition)

        file.writeText(json.encodeToString(currentDefinitions))
    }

    suspend fun insertTiles(tiles: List<AACTile>) {
        tiles.forEach { insertTile(it) }
    }

    suspend fun updateTile(tile: AACTile) {
        val explicitType = when {
            tile.isCategory -> TileType.FOLDER
            tile.linkedCategoryId != null -> TileType.CONNECTOR
            tile.isQuickFire -> TileType.QUICK_FIRE
            else -> TileType.BASIC
        }

        val definition = TileDefinition(
            id = tile.id,
            label = tile.label,
            ttsText = tile.ttsText,
            labelFeminine = tile.labelFeminine,
            ttsTextFeminine = tile.ttsTextFeminine,
            emoji = tile.emoji,
            audioUri = tile.audioUri,
            imageUri = tile.imageUri,
            backgroundColorHex = tile.backgroundColorHex,
            partOfSpeech = tile.partOfSpeech,
            grammaticalGender = tile.grammaticalGender,
            isCategory = tile.isCategory,
            type = explicitType,
            languageCode = tile.languageCode,
            defaultParentId = tile.parentId,
            defaultCellIndex = tile.cellIndex,
            defaultLinkedCategoryId = tile.linkedCategoryId
        )

        // 1. Save to JSON Disk
        saveDefinitionToDisk(definition)

        // 2. Update Legacy Room DB (CRITICAL FOR GRID DISPLAY)
        aacTileDao.updateTile(tile)

        // 3. Update Layout in profile
        updateLayoutState(tile.id, tile.parentId) {
            it.copy(
                parentId = tile.parentId,
                linkedCategoryId = tile.linkedCategoryId,
                cellIndex = tile.cellIndex ?: it.cellIndex,
                isQuickFire = tile.isQuickFire,
                isHidden = tile.isHidden
            )
        }
        loadAllDefinitions()
    }

    suspend fun deleteTile(tile: AACTile) {
        // 1. Remove from profile layout
        val currentProfile = activeProfile.value ?: return
        val newLayout = currentProfile.layout.toMutableMap()
        newLayout.remove(getLayoutKey(tile.parentId, tile.id))
        profileRepository.updateActiveProfile(currentProfile.copy(layout = newLayout))

        // 2. Remove from user definitions on disk safely
        val tilesDir = File(context.filesDir, "tiles/${tile.languageCode}")
        val file = File(tilesDir, "export_user_defined.json")
        if (file.exists()) {
            try {
                // Strip the hidden BOM character here too
                val jsonString = file.readText().removePrefix("\uFEFF")
                val currentDefinitions = json.decodeFromString<List<TileDefinition>>(jsonString).toMutableList()

                if (currentDefinitions.removeAll { it.id == tile.id }) {
                    file.writeText(json.encodeToString(currentDefinitions))
                }
            } catch (e: Exception) {
                Log.e("Debug_AAC", "Failed to parse definitions during delete", e)
            }
        }

        // 3. Remove from Room (backward compatibility)
        aacTileDao.deleteTile(tile)

        loadAllDefinitions()
    }

    suspend fun incrementClickCount(id: String, parentId: String?, langCode: String) {
        updateLayoutState(id, parentId) { it.copy(clickCount = it.clickCount + 1) }
        aacTileDao.insertClickEvent(TileClickEvent(tileId = id))
    }

    fun getClickEventsBetween(startTime: Long, endTime: Long): Flow<List<TileClickEvent>> {
        return aacTileDao.getClickEventsBetween(startTime, endTime)
    }

    suspend fun clearAllStatistics() {
        aacTileDao.deleteAllClickEvents()
        aacTileDao.resetAllLegacyClickCounts()
        // Reset click counts in active profile too
        val profile = activeProfile.value ?: return
        val newLayout = profile.layout.mapValues { it.value.copy(clickCount = 0) }
        profileRepository.updateActiveProfile(profile.copy(layout = newLayout))
    }

    suspend fun isEmpty(): Boolean {
        return aacTileDao.getCount() == 0
    }

    suspend fun deleteTilesByLanguage(languageCode: String) {
        aacTileDao.deleteTilesByLanguage(languageCode)
        loadAllDefinitions()
    }

    suspend fun deleteAllTilesFromRoom() {
        aacTileDao.deleteAllTiles()
        aacTileDao.deleteAllPlacements()
    }

    fun getAllTilesSync(): List<AACTile> {
        return aacTileDao.getAllTilesSync()
    }

    suspend fun getAllTilesWithPlacements(): List<AACTile> {
        return aacTileDao.getAllTilesWithPlacements().map { it.toAACTile() }
    }

    suspend fun swapTilesByIndex(parentId: String?, fromIndex: Int, toIndex: Int) {
        val currentProfile = activeProfile.value ?: return
        val newLayout = currentProfile.layout.toMutableMap()

        // Find the keys for the tiles currently occupying these indices
        val itemFromEntry = newLayout.entries.find { it.value.parentId == parentId && it.value.cellIndex == fromIndex }
        val itemToEntry = newLayout.entries.find { it.value.parentId == parentId && it.value.cellIndex == toIndex }

        // Swap their indices
        if (itemFromEntry != null) {
            newLayout[itemFromEntry.key] = itemFromEntry.value.copy(cellIndex = toIndex)
        }
        if (itemToEntry != null) {
            newLayout[itemToEntry.key] = itemToEntry.value.copy(cellIndex = fromIndex)
        }

        // Save the updated layout map to disk and memory
        profileRepository.updateActiveProfile(currentProfile.copy(layout = newLayout))
    }

    suspend fun completeLegacyMigration() = withContext(Dispatchers.IO) {
        // 1. Migrate all old layouts/placements into the active profile
        migrateLegacyPlacements()

        // 2. Migrate old user-created tiles to export_user_defined.json
        val roomTiles = aacTileDao.getAllTilesSync()
        if (roomTiles.isNotEmpty()) {
            roomTiles.forEach { roomTile ->
                // Check if it already exists in the local JSON dictionary to avoid duplicates
                val isAlreadyLocal = baseDefinitions.value.any { it.id == roomTile.id }

                if (!isAlreadyLocal) {
                    val def = TileDefinition(
                        id = roomTile.id,
                        label = roomTile.label,
                        ttsText = roomTile.ttsText,
                        labelFeminine = roomTile.labelFeminine,
                        ttsTextFeminine = roomTile.ttsTextFeminine,
                        emoji = roomTile.emoji,
                        audioUri = roomTile.audioUri,
                        imageUri = roomTile.imageUri,
                        backgroundColorHex = roomTile.backgroundColorHex,
                        partOfSpeech = roomTile.partOfSpeech,
                        grammaticalGender = roomTile.grammaticalGender,
                        isCategory = roomTile.isCategory,
                        languageCode = roomTile.languageCode,
                        defaultParentId = roomTile.parentId,
                        defaultCellIndex = roomTile.cellIndex,
                        defaultLinkedCategoryId = roomTile.linkedCategoryId
                    )
                    saveDefinitionToDisk(def)
                }
            }

            // 3. Nuke the old SQLite database! The refactor is complete.
            deleteAllTilesFromRoom()

            // 4. Reload memory to reflect the newly migrated JSON files
            loadAllDefinitions()
        }
    }

    /**
     * Merges the factory default tiles with the user's custom/modified tiles.
     * User tiles take priority. If an ID matches, the user's version overwrites the default.
     * If the ID is new, it gets added to the list.
     */
    fun mergeDictionaries(
        defaultTiles: List<TileDefinition>,
        userTiles: List<TileDefinition>
    ): List<TileDefinition> {

        // 1. Create a map of the defaults using the Tile ID as the key
        val mergedMap = defaultTiles.associateBy { it.id }.toMutableMap()

        // 2. Loop through the user dictionary.
        // If the ID exists, it overwrites the default. If it's new, it gets added!
        userTiles.forEach { userTile ->
            mergedMap[userTile.id] = userTile
        }

        // 3. Return the unified flat list
        return mergedMap.values.toList()
    }
}
