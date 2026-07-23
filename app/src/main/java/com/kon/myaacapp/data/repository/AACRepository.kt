package com.kon.myaacapp.data.repository

import android.content.Context
import android.util.Log
import com.kon.myaacapp.data.local.dao.AACTileDao
import com.kon.myaacapp.data.local.entity.AACTile
import com.kon.myaacapp.data.local.entity.ROOT_PARENT_ID
import com.kon.myaacapp.data.local.entity.TileClickEvent
import com.kon.myaacapp.data.local.entity.toAACTile
import com.kon.myaacapp.data.mapper.toDomain
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.CreateTileRequest
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.model.TileUsageEvent
import com.kon.myaacapp.domain.model.UserProfile
import com.kon.myaacapp.domain.repository.TileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class AACRepository(
    private val aacTileDao: AACTileDao,
    private val context: Context,
    private val profileRepository: ProfileRepository,
) : TileRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    val activeProfile: StateFlow<UserProfile?> =
        profileRepository.activeProfile

    private val _baseDefinitions =
        MutableStateFlow<List<TileDefinition>>(emptyList())

    override val baseDefinitions: StateFlow<List<TileDefinition>> =
        _baseDefinitions.asStateFlow()

    private val diskMutex = Mutex()
    private val profileMutex = Mutex()

    override suspend fun reload() {
        loadAllDefinitions()
    }

    private fun loadDictionaryFile(file: File): List<TileDefinition> {
        if (!file.exists()) return emptyList()
        return try {
            val jsonString = file.readText().removePrefix("\uFEFF")
            json.decodeFromString<List<TileDefinition>>(jsonString)
        } catch (e: Exception) {
            Log.e("Debug_AAC", "Failed to parse JSON: ${file.name}", e)
            emptyList()
        }
    }

    override suspend fun loadAllDefinitions(): Unit =
        withContext(Dispatchers.IO) {
        val tilesDir = File(
            context.filesDir,
            "tiles",
        )

        val fileDefinitions = mutableListOf<TileDefinition>()

        diskMutex.withLock {
            val languageDirs = tilesDir.listFiles { file ->
                file.isDirectory
            }.orEmpty()

            languageDirs.forEach { languageDirectory ->
                val defaultDefinitions = loadDictionaryFile(
                    File(
                        languageDirectory,
                        "default_dictionary.json",
                    )
                )

                val userDefinitions = loadDictionaryFile(
                    File(
                        languageDirectory,
                        "export_user_defined.json",
                    )
                )

                /*
                 * User definitions override defaults only when both the tile ID
                 * and language code match.
                 */
                fileDefinitions += mergeDictionaries(
                    defaultTiles = defaultDefinitions,
                    userTiles = userDefinitions,
                )
            }
        }

        try {
            val roomDefinitions = aacTileDao
                .getAllTilesSync()
                .map { tile ->
                    TileDefinition(
                        id = tile.id,
                        label = tile.label,
                        ttsText = tile.ttsText,
                        labelFeminine = tile.labelFeminine,
                        ttsTextFeminine = tile.ttsTextFeminine,
                        emoji = tile.emoji,
                        audioUri = tile.audioUri,
                        imageUri = tile.imageUri,
                        backgroundColorHex =
                            tile.backgroundColorHex,
                        partOfSpeech = tile.partOfSpeech,
                        grammaticalGender =
                            tile.grammaticalGender,
                        isCategory = tile.isCategory,
                        type = when {
                            tile.isCategory -> {
                                TileType.FOLDER
                            }

                            tile.linkedCategoryId != null -> {
                                TileType.CONNECTOR
                            }

                            tile.isQuickFire -> {
                                TileType.QUICK_FIRE
                            }

                            else -> {
                                TileType.BASIC
                            }
                        },
                        languageCode = tile.languageCode,
                        defaultParentId = tile.parentId,
                        defaultCellIndex = tile.cellIndex,
                        defaultLinkedCategoryId =
                            tile.linkedCategoryId,
                    )
                }

            _baseDefinitions.value = mergeDictionaries(
                defaultTiles = fileDefinitions,
                userTiles = roomDefinitions,
            )

        } catch (error: Exception) {
            Log.e(
                "AACRepository",
                "Failed to load tile definitions",
                error,
            )

            Unit
        }
    }

    override fun getCombinedTiles(
        parentId: String?,
        langCode: String,
    ): Flow<List<CombinedTile>> =
        combine(activeProfile, baseDefinitions) { profile, definitions ->
            if (profile == null || definitions.isEmpty()) {
                return@combine emptyList()
            }

            // FIX: Normalize root/home comparison. If parentId is null (Home screen query),
            // accept items where parentId is null, "home", or ROOT_PARENT_ID.
            // This ensures default tiles with parentId = "home" render correctly on startup.
            val relevantLayouts = profile.layout.values.filter { layout ->
                if (parentId == null) {
                    layout.parentId == null || layout.parentId == "home" || layout.parentId == ROOT_PARENT_ID
                } else {
                    layout.parentId == parentId
                }
            }

            val defMap = definitions
                .filter { it.languageCode == langCode }
                .associateBy { it.id }

            relevantLayouts.mapNotNull { layout ->
                val def = defMap[layout.tileId]
                if (def != null) CombinedTile(def, layout) else null
            }.sortedBy { it.cellIndex }
        }

    private fun getLayoutKey(parentId: String?, tileId: String): String {
        return "${parentId ?: ROOT_PARENT_ID}_$tileId"
    }

    fun populateInitialLayout(
        profile: UserProfile,
        languageCode: String? = null,
    ): UserProfile {
        val newLayout = profile.layout.toMutableMap()

        val definitions = if (languageCode == null) {
            baseDefinitions.value
        } else {
            baseDefinitions.value.filter { definition ->
                definition.languageCode == languageCode
            }
        }

        definitions.forEach { definition ->
            val defaultCellIndex =
                definition.defaultCellIndex ?: return@forEach

            val key = getLayoutKey(
                parentId = definition.defaultParentId,
                tileId = definition.id,
            )

            if (!newLayout.containsKey(key)) {
                newLayout[key] = TileLayoutState(
                    tileId = definition.id,
                    parentId = definition.defaultParentId,
                    linkedCategoryId =
                        definition.defaultLinkedCategoryId,
                    cellIndex = defaultCellIndex,
                    isQuickFire = false,
                    isHidden = false,
                    clickCount = 0,
                )
            }
        }

        return profile.copy(
            activeLanguageCode =
                languageCode ?: profile.activeLanguageCode,
            layout = newLayout,
        )
    }

    private fun lockInDefaultState(tileId: String, parentId: String?, state: TileLayoutState) {
        profileRepository.scope.launch {
            profileMutex.withLock {
                val currentProfile = activeProfile.value ?: return@withLock
                val key = getLayoutKey(parentId, tileId)
                if (!currentProfile.layout.containsKey(key)) {
                    val newLayout = currentProfile.layout.toMutableMap()
                    newLayout[key] = state
                    profileRepository.updateActiveProfile(currentProfile.copy(layout = newLayout))
                }
            }
        }
    }

    suspend fun prepareLanguage(
        languageCode: String,
    ): Boolean {
        loadAllDefinitions()

        val languageDefinitions = baseDefinitions.value.filter {
                definition ->
            definition.languageCode == languageCode
        }

        if (languageDefinitions.isEmpty()) {
            Log.e(
                "AACRepository",
                "No tile definitions found for language: " +
                        languageCode
            )

            return false
        }

        val profile = activeProfile.value
            ?: activeProfile.first { it != null }
            ?: return false

        /*
         * Hebrew and English definitions share the same logical tile IDs and
         * therefore use the same profile layout. Only update the profile's active
         * language; do not rebuild placements from definition defaults.
         */
        profileRepository.updateActiveProfile(
            profile.copy(
                activeLanguageCode = languageCode,
            )
        )

        return true
    }

    override suspend fun updateTileIndex(
        tileId: String,
        parentId: String?,
        newIndex: Int,
    ) {
        updateLayoutState(tileId, parentId) { it.copy(cellIndex = newIndex) }
    }

    override suspend fun updateTileVisibility(
        tileId: String,
        parentId: String?,
        isHidden: Boolean,
    ) {
        updateLayoutState(tileId, parentId) { it.copy(isHidden = isHidden) }
    }

    private suspend fun updateLayoutState(tileId: String, parentId: String?, transform: (TileLayoutState) -> TileLayoutState) {
        profileMutex.withLock {
            val currentProfile = activeProfile.value ?: return
            val newLayout = currentProfile.layout.toMutableMap()
            val key = getLayoutKey(parentId, tileId)

            val currentState = newLayout[key] ?: run {
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
    }

    override suspend fun attachTileToCategory(
        tileId: String,
        parentId: String?,
        langCode: String,
        cellIndex: Int?,
    ) {
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

    @Suppress("UNUSED_PARAMETER")
    override suspend fun removeTileFromCategory(
        tileId: String,
        parentId: String?,
        langCode: String,
    ) {
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

    override fun getAllDefinitionsAsCombinedTiles(
        langCode: String,
    ): Flow<List<CombinedTile>> =
        combine(activeProfile, baseDefinitions) { profile, definitions ->
            if (definitions.isEmpty()) {
                return@combine emptyList()
            }

            val relevantDefinitions = definitions.filter { it.languageCode == langCode }

            relevantDefinitions.map { def ->
                val key = getLayoutKey(def.defaultParentId, def.id)
                val layout = profile?.layout?.get(key) ?: TileLayoutState(
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

    @Suppress("unused")
    fun getAllCategories(langCode: String): Flow<List<AACTile>> = aacTileDao.getAllCategories(langCode)

    suspend fun getTileById(id: String, langCode: String): AACTile? = aacTileDao.getTileById(id, langCode)

    override suspend fun addTile(
        request: CreateTileRequest,
    ) {
        val nextIndex = request.cellIndex
            ?: getCombinedTiles(
                parentId = request.parentId,
                langCode = request.languageCode,
            ).first().size

        val tileId = request.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: java.util.UUID.randomUUID().toString()

        val databaseTile = AACTile(
            id = tileId,
            label = request.label.trim(),
            ttsText = request.ttsText.trim(),
            labelFeminine = request.labelFeminine,
            ttsTextFeminine = request.ttsTextFeminine,
            emoji = request.emoji,
            audioUri = request.audioUri,
            imageUri = request.imageUri,
            backgroundColorHex = request.backgroundColorHex,
            partOfSpeech = request.partOfSpeech,
            grammaticalGender = request.grammaticalGender,
            isCategory = request.tileType == TileType.FOLDER,
            languageCode = request.languageCode,
            parentId = request.parentId,
            linkedCategoryId = when (request.tileType) {
                TileType.FOLDER,
                TileType.CONNECTOR -> request.linkedCategoryId

                TileType.BASIC,
                TileType.QUICK_FIRE -> null
            },
            cellIndex = nextIndex,
            isQuickFire =
                request.tileType == TileType.QUICK_FIRE,
            isHidden = request.isHidden,
        )

        insertTile(databaseTile)
    }

    suspend fun insertTile(tile: AACTile) {
        val definition = createDefinitionFromTile(tile)

        withContext(Dispatchers.IO) {
            saveDefinitionToDisk(definition)
            aacTileDao.insertTile(tile)
        }

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

        _baseDefinitions.value =
            _baseDefinitions.value.filterNot { existing ->
                existing.id == definition.id &&
                        existing.languageCode == definition.languageCode
            } + definition
    }

    private suspend fun saveDefinitionToDisk(definition: TileDefinition) =
        withContext(Dispatchers.IO) {
            val langDir = File(context.filesDir, "tiles/${definition.languageCode}")
            if (!langDir.exists()) langDir.mkdirs()

            val file = File(langDir, "export_user_defined.json")

            diskMutex.withLock {
                val currentDefinitions = if (file.exists()) {
                    try {
                        val jsonString = file.readText().removePrefix("\uFEFF")
                        json.decodeFromString<List<TileDefinition>>(jsonString).toMutableList()
                    } catch (e: Exception) {
                        Log.e(
                            "Debug_AAC",
                            "CRITICAL: Failed to parse during save. Aborting overwrite!",
                            e
                        )
                        return@withContext
                    }
                } else {
                    mutableListOf()
                }

                currentDefinitions.removeAll { it.id == definition.id }
                currentDefinitions.add(definition)

                file.writeText(json.encodeToString(currentDefinitions))
            }
        }

    @Suppress("unused")
    suspend fun insertTiles(tiles: List<AACTile>) {
        tiles.forEach { insertTile(it) }
    }

    suspend fun updateTile(tile: AACTile) {
        val definition = createDefinitionFromTile(tile)

        withContext(Dispatchers.IO) {
            saveDefinitionToDisk(definition)
            aacTileDao.updateTile(tile)
        }

        updateLayoutState(tile.id, tile.parentId) {
            it.copy(
                parentId = tile.parentId,
                linkedCategoryId = tile.linkedCategoryId,
                cellIndex = tile.cellIndex ?: it.cellIndex,
                isQuickFire = tile.isQuickFire,
                isHidden = tile.isHidden
            )
        }

        _baseDefinitions.value =
            _baseDefinitions.value.filterNot { existing ->
                existing.id == definition.id &&
                        existing.languageCode == definition.languageCode
            } + definition
    }

    override suspend fun updateTile(
        tile: CombinedTile,
    ) {
        val definition = tile.definition
        val layoutState = tile.layoutState

        val databaseTile = AACTile(
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
            languageCode = definition.languageCode,
            parentId = layoutState.parentId,
            linkedCategoryId = layoutState.linkedCategoryId,
            cellIndex = layoutState.cellIndex,
            isQuickFire = layoutState.isQuickFire ||
                    definition.resolvedType == TileType.QUICK_FIRE,
            isHidden = layoutState.isHidden,
            clickCount = layoutState.clickCount,
        )

        updateTile(databaseTile)
    }

    override suspend fun removeAllAudio() {
        val tiles = withContext(Dispatchers.IO) {
            aacTileDao.getAllTilesSync()
        }

        tiles.forEach { tile ->
            if (tile.audioUri != null) {
                updateTile(
                    tile.copy(
                        audioUri = null,
                    )
                )
            }
        }
    }

    suspend fun deleteTile(tile: AACTile) {
        val currentProfile = activeProfile.value ?: return
        val newLayout = currentProfile.layout.toMutableMap()
        newLayout.remove(getLayoutKey(tile.parentId, tile.id))
        profileRepository.updateActiveProfile(currentProfile.copy(layout = newLayout))

        withContext(Dispatchers.IO) {
            val tilesDir = File(context.filesDir, "tiles/${tile.languageCode}")
            val file = File(tilesDir, "export_user_defined.json")
            if (file.exists()) {
                diskMutex.withLock {
                    try {
                        val jsonString = file.readText().removePrefix("\uFEFF")
                        val currentDefinitions =
                            json.decodeFromString<List<TileDefinition>>(jsonString).toMutableList()

                        if (currentDefinitions.removeAll { it.id == tile.id }) {
                            file.writeText(json.encodeToString(currentDefinitions))
                        }
                    } catch (e: Exception) {
                        Log.e("Debug_AAC", "Failed to parse definitions during delete", e)
                    }
                }
            }
            aacTileDao.deleteTile(tile)
        }

        _baseDefinitions.value =
            _baseDefinitions.value.filterNot { definition ->
                definition.id == tile.id &&
                        definition.languageCode == tile.languageCode
            }
    }

    override suspend fun deleteTile(
        tile: CombinedTile,
    ) {
        val definition = tile.definition
        val layoutState = tile.layoutState

        val databaseTile = AACTile(
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
            isCategory =
                definition.resolvedType == TileType.FOLDER,
            languageCode = definition.languageCode,
            parentId = layoutState.parentId,
            linkedCategoryId = layoutState.linkedCategoryId,
            cellIndex = layoutState.cellIndex,
            isQuickFire =
                layoutState.isQuickFire ||
                        definition.resolvedType == TileType.QUICK_FIRE,
            isHidden = layoutState.isHidden,
            clickCount = layoutState.clickCount,
        )

        deleteTile(databaseTile)
    }

    private fun createDefinitionFromTile(tile: AACTile): TileDefinition {
        return TileDefinition(
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

    @Suppress("UNUSED_PARAMETER")
    override suspend fun incrementClickCount(
        id: String,
        parentId: String?,
        langCode: String,
    ) {
        val currentProfile =
            activeProfile.value ?: return

        updateLayoutState(
            tileId = id,
            parentId = parentId,
        ) { currentState ->
            currentState.copy(
                clickCount = currentState.clickCount + 1
            )
        }

        aacTileDao.insertClickEvent(
            TileClickEvent(
                tileId = id,
                profileId = currentProfile.profileId,
            )
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUsageEvents(
        startTime: Long,
        endTime: Long,
    ): Flow<List<TileUsageEvent>> {
        return activeProfile.flatMapLatest { profile ->
            if (profile == null) {
                flowOf(emptyList())
            } else {
                aacTileDao.getClickEventsBetween(
                    profileId = profile.profileId,
                    startTime = startTime,
                    endTime = endTime,
                ).map { events ->
                    events.map { event ->
                        event.toDomain()
                    }
                }
            }
        }
    }

    suspend fun clearAllStatistics() {
        val profile = activeProfile.value ?: return

        aacTileDao.deleteClickEventsForProfile(
            profileId = profile.profileId,
        )

        val clearedLayout = profile.layout.mapValues {
                (_, layoutState) ->
            layoutState.copy(clickCount = 0)
        }

        profileRepository.updateActiveProfile(
            profile.copy(layout = clearedLayout)
        )
    }

    @Suppress("unused")
    suspend fun isEmpty(): Boolean = aacTileDao.getCount() == 0

    @Suppress("unused")
    suspend fun deleteTilesByLanguage(languageCode: String) {
        aacTileDao.deleteTilesByLanguage(languageCode)
        loadAllDefinitions()
    }

    suspend fun deleteAllTilesFromRoom() {
        aacTileDao.deleteAllTiles()
        aacTileDao.deleteAllPlacements()
    }

    fun getAllTilesSync(): List<AACTile> = aacTileDao.getAllTilesSync()

    @Suppress("unused")
    suspend fun getAllTilesWithPlacements(): List<AACTile> {
        return aacTileDao.getAllTilesWithPlacements().map { it.toAACTile() }
    }

    override suspend fun swapTilesByIndex(
        parentId: String?,
        fromIndex: Int,
        toIndex: Int,
    ) {
        profileMutex.withLock {
            val currentProfile = activeProfile.value ?: return@withLock
            val newLayout = currentProfile.layout.toMutableMap()

            val itemFromEntry = newLayout.entries.find { it.value.parentId == parentId && it.value.cellIndex == fromIndex }
            val itemToEntry = newLayout.entries.find { it.value.parentId == parentId && it.value.cellIndex == toIndex }

            if (itemFromEntry != null) {
                newLayout[itemFromEntry.key] = itemFromEntry.value.copy(cellIndex = toIndex)
            }
            if (itemToEntry != null) {
                newLayout[itemToEntry.key] = itemToEntry.value.copy(cellIndex = fromIndex)
            }

            profileRepository.updateActiveProfile(currentProfile.copy(layout = newLayout))
        }
    }

    suspend fun deleteProfileAnalytics(
        profileId: String,
    ) {
        aacTileDao.deleteClickEventsForProfile(
            profileId = profileId,
        )
    }

    suspend fun completeLegacyMigration() = withContext(Dispatchers.IO) {
        loadAllDefinitions()
        migrateLegacyPlacements()

        val roomTiles = aacTileDao.getAllTilesSync()

        if (roomTiles.isEmpty()) {
            return@withContext
        }

        roomTiles.forEach { roomTile ->
            /*
             * IDs can be identical across languages, so both the tile ID and
             * language code must match.
             */
            val isAlreadyLocal = baseDefinitions.value.any { definition ->
                definition.id == roomTile.id &&
                        definition.languageCode == roomTile.languageCode
            }

            if (!isAlreadyLocal) {
                val definition = createDefinitionFromTile(roomTile)
                saveDefinitionToDisk(definition)
            }
        }

        deleteAllTilesFromRoom()
        loadAllDefinitions()
    }

    fun mergeDictionaries(
        defaultTiles: List<TileDefinition>,
        userTiles: List<TileDefinition>,
    ): List<TileDefinition> {
        return (defaultTiles + userTiles)
            .associateBy { definition ->
                definition.languageCode to definition.id
            }
            .values
            .toList()
    }
}