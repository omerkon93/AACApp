package com.kon.myaacapp.data.repository

import android.content.Context
import android.util.Log
import com.kon.myaacapp.R
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementProvider
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementState
import com.kon.myaacapp.domain.entitlement.ProfileAvailabilityPartition
import com.kon.myaacapp.domain.entitlement.ProfileLimitPolicy
import com.kon.myaacapp.domain.entitlement.StoreFamilyEntitlementProvider
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

sealed interface CreateProfileResult {
    data class Success(
        val profile: UserProfile,
    ) : CreateProfileResult

    data class LimitReached(
        val savedProfileCount: Int,
        val freeProfileLimit: Int,
    ) : CreateProfileResult

    data object InvalidName : CreateProfileResult

    data object SaveFailed : CreateProfileResult
}

sealed interface SelectFreeCustomProfileResult {
    data class Success(
        val selectedProfile: UserProfile,
        val activeProfileChanged: Boolean,
    ) : SelectFreeCustomProfileResult

    data object Locked : SelectFreeCustomProfileResult

    data object NotApplicable : SelectFreeCustomProfileResult

    data object InvalidTarget : SelectFreeCustomProfileResult
}

class ProfileRepository(
    private val context: Context,
    internal val settingsRepository: SettingsRepository,
    internal val scope: CoroutineScope,
    private val familyEntitlementProvider: FamilyEntitlementProvider =
        StoreFamilyEntitlementProvider(),
) {
    companion object {
        private const val TAG = "ProfileRepository"
        private const val DEFAULT_PROFILE_ID = "default"
        private const val DEFAULT_LANGUAGE_CODE = "he"

        private const val INITIAL_DATA_ASSET =
            "initial_data.zip"

        private const val DEFAULT_PROFILE_ZIP_PATH =
            "profiles/profile_default.json"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val profilesDir = File(
        context.filesDir,
        "profiles",
    )

    private val profileMutex = Mutex()

    private val _profiles =
        MutableStateFlow<List<UserProfile>>(emptyList())

    val profiles: StateFlow<List<UserProfile>> =
        _profiles.asStateFlow()

    private val _activeProfile =
        MutableStateFlow<UserProfile?>(null)

    val activeProfile: StateFlow<UserProfile?> =
        _activeProfile.asStateFlow()

    private val _profileAvailability =
        MutableStateFlow(ProfileAvailabilityPartition())

    val profileAvailability: StateFlow<ProfileAvailabilityPartition> =
        _profileAvailability.asStateFlow()

    init {
        scope.launch {
            combine(
                _profiles,
                settingsRepository.activeProfileIdFlow,
                settingsRepository.selectedCustomProfileIdFlow,
                settingsRepository.freeCustomProfileLockedFlow,
                familyEntitlementProvider.entitlementState,
            ) { profileList, activeId, selectedCustomId, isFreeLocked, entitlement ->
                val shouldUnlock = entitlement.hasFamilyEntitlement && isFreeLocked
                val resolved = resolveStateForInputs(
                    profileList = profileList,
                    activeId = activeId,
                    selectedCustomId = selectedCustomId,
                    isFreeSlotLocked = isFreeLocked && !entitlement.hasFamilyEntitlement,
                    entitlement = entitlement,
                )
                shouldUnlock to resolved
            }.collect { (shouldUnlock, resolved) ->
                if (shouldUnlock) {
                    settingsRepository.updateFreeCustomProfileLocked(false)
                }
                _profileAvailability.value = resolved.first
                _activeProfile.value = resolved.second
            }
        }
    }

    val activeBoardLanguage: Flow<String> = activeProfile
        .filterNotNull()
        .map { profile ->
            LocaleHelper.normalize(
                profile.activeLanguageCode.ifBlank { DEFAULT_LANGUAGE_CODE },
            )
        }
        .distinctUntilChanged()

    suspend fun reload() =
        withContext(Dispatchers.IO) {
            profileMutex.withLock {
                ensureProfilesDirectory()
                loadProfilesSync()
                bootstrapDefaultProfileSync()
                ensureValidActiveProfile()
            }
        }

    private fun ensureProfilesDirectory() {
        if (!profilesDir.exists() && !profilesDir.mkdirs()) {
            Log.e(
                TAG,
                "Failed to create profiles directory: " +
                        profilesDir.absolutePath,
            )
        }
    }

    private fun loadProfilesSync() {
        val files = profilesDir
            .listFiles { file ->
                file.isFile &&
                        file.extension.equals(
                            other = "json",
                            ignoreCase = true,
                        )
            }
            ?.sortedBy { file ->
                file.name
            }
            .orEmpty()

        val loadedProfiles: List<UserProfile> =
            files.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<UserProfile>(
                        file.readText()
                    )
                }.onFailure { error ->
                    Log.e(
                        TAG,
                        "Failed to parse profile: ${file.name}",
                        error,
                    )
                }.getOrNull()
            }

        _profiles.value = loadedProfiles
    }

    private suspend fun bootstrapDefaultProfileSync() {
        if (_profiles.value.isNotEmpty()) {
            return
        }

        /*
         * If no extracted profile was found, load the complete factory profile
         * directly from initial_data.zip.
         *
         * This preserves every profile placement, including repeated shared
         * tiles such as more, done, pita, cookie, yogurt, and open.
         */
        val factoryProfile = loadFactoryDefaultProfile()

        val defaultProfile = if (factoryProfile != null) {
            factoryProfile.copy(
                profileId = DEFAULT_PROFILE_ID,
                profileName = context.getString(
                    R.string.default_profile_name
                ),
            )
        } else {
            /*
             * Last-resort fallback. This should happen only when the factory
             * profile is missing or invalid.
             */
            Log.e(
                TAG,
                "Factory profile unavailable; creating an empty fallback profile",
            )

            UserProfile(
                profileId = DEFAULT_PROFILE_ID,
                profileName = context.getString(
                    R.string.default_profile_name
                ),
                activeLanguageCode = DEFAULT_LANGUAGE_CODE,
                layout = emptyMap(),
            )
        }

        if (!saveProfileInternal(defaultProfile)) {
            error("Failed to create the default profile")
        }

        _profiles.value = listOf(defaultProfile)

        settingsRepository.updateActiveAndSelectedCustomProfile(
            activeProfileId = DEFAULT_PROFILE_ID,
            selectedCustomProfileId = null,
        )
        recomputeResolvedStateLocked(
            activeIdOverride = DEFAULT_PROFILE_ID,
            selectedCustomIdOverride = null,
        )

        Log.d(
            TAG,
            "Bootstrapped default profile with " +
                    "${defaultProfile.layout.size} layout entries",
        )
    }

    private suspend fun ensureValidActiveProfile() {
        val availableProfiles = _profiles.value

        if (availableProfiles.isEmpty()) {
            _profileAvailability.value = ProfileAvailabilityPartition()
            _activeProfile.value = null
            return
        }

        val storedProfileId =
            settingsRepository.activeProfileIdFlow.first()
        val storedSelectedCustomId =
            settingsRepository.selectedCustomProfileIdFlow.first()
        val storedFreeLocked =
            settingsRepository.freeCustomProfileLockedFlow.first()
        val entitlement = familyEntitlementProvider.currentEntitlement()

        if (entitlement.hasFamilyEntitlement && storedFreeLocked) {
            settingsRepository.updateFreeCustomProfileLocked(false)
        }
        val effectiveLocked = storedFreeLocked && !entitlement.hasFamilyEntitlement

        val partition = ProfileLimitPolicy.partitionProfiles(
            profiles = availableProfiles,
            activeProfileId = storedProfileId,
            selectedCustomProfileId = storedSelectedCustomId,
            isFreeSlotLocked = effectiveLocked,
            entitlement = entitlement,
            defaultProfileId = DEFAULT_PROFILE_ID,
        )

        val resolvedActiveId =
            if (storedProfileId != null && partition.isAvailable(storedProfileId)) {
                storedProfileId
            } else {
                partition.availableProfiles.find { profile ->
                    profile.profileId == DEFAULT_PROFILE_ID
                }?.profileId ?: partition.availableProfiles.first().profileId
            }

        val resolvedSelectedCustomId =
            if (effectiveLocked) {
                storedSelectedCustomId
            } else {
                partition.selectedCustomProfileId
            }

        if (
            resolvedActiveId != storedProfileId ||
            resolvedSelectedCustomId != storedSelectedCustomId
        ) {
            settingsRepository.updateActiveAndSelectedCustomProfile(
                activeProfileId = resolvedActiveId,
                selectedCustomProfileId = resolvedSelectedCustomId,
            )
        }

        recomputeResolvedStateLocked(
            activeIdOverride = resolvedActiveId,
            selectedCustomIdOverride = resolvedSelectedCustomId,
            isFreeLockedOverride = effectiveLocked,
        )
    }

    private suspend fun resolveCurrentActiveProfile(): UserProfile? {
        val profileList = _profiles.value
        val activeId = settingsRepository.activeProfileIdFlow.first()
        val selectedCustomId = settingsRepository.selectedCustomProfileIdFlow.first()
        val entitlement = familyEntitlementProvider.currentEntitlement()
        val isFreeLocked =
            settingsRepository.freeCustomProfileLockedFlow.first() &&
                !entitlement.hasFamilyEntitlement
        val (_, resolvedActive) = resolveStateForInputs(
            profileList = profileList,
            activeId = activeId,
            selectedCustomId = selectedCustomId,
            isFreeSlotLocked = isFreeLocked,
            entitlement = entitlement,
        )
        return resolvedActive
    }

    private suspend fun recomputeResolvedStateLocked(
        activeIdOverride: String? = null,
        selectedCustomIdOverride: String? = null,
        isFreeLockedOverride: Boolean = false,
        useOverrides: Boolean = true,
    ) {
        val entitlement = familyEntitlementProvider.currentEntitlement()
        val activeId = if (useOverrides) {
            activeIdOverride
        } else {
            settingsRepository.activeProfileIdFlow.first()
        }
        val selectedCustomId = if (useOverrides) {
            selectedCustomIdOverride
        } else {
            settingsRepository.selectedCustomProfileIdFlow.first()
        }
        val effectiveLocked = if (useOverrides) {
            isFreeLockedOverride && !entitlement.hasFamilyEntitlement
        } else {
            settingsRepository.freeCustomProfileLockedFlow.first() &&
                !entitlement.hasFamilyEntitlement
        }
        val (partition, resolvedActive) = resolveStateForInputs(
            profileList = _profiles.value,
            activeId = activeId,
            selectedCustomId = selectedCustomId,
            isFreeSlotLocked = effectiveLocked,
            entitlement = entitlement,
        )
        _profileAvailability.value = partition
        _activeProfile.value = resolvedActive
    }

    private fun resolveStateForInputs(
        profileList: List<UserProfile>,
        activeId: String?,
        selectedCustomId: String?,
        isFreeSlotLocked: Boolean,
        entitlement: FamilyEntitlementState,
    ): Pair<ProfileAvailabilityPartition, UserProfile?> {
        if (profileList.isEmpty()) {
            return ProfileAvailabilityPartition() to null
        }

        val partition = ProfileLimitPolicy.partitionProfiles(
            profiles = profileList,
            activeProfileId = activeId,
            selectedCustomProfileId = selectedCustomId,
            isFreeSlotLocked = isFreeSlotLocked,
            entitlement = entitlement,
            defaultProfileId = DEFAULT_PROFILE_ID,
        )
        val resolvedActive =
            partition.availableProfiles.find { it.profileId == activeId }
                ?: partition.availableProfiles.find { it.profileId == DEFAULT_PROFILE_ID }
                ?: partition.availableProfiles.firstOrNull()

        return partition to resolvedActive
    }

    suspend fun createProfile(
        name: String,
        creationMode: ProfileCreationMode,
        boardLanguageCode: String = DEFAULT_LANGUAGE_CODE,
    ): CreateProfileResult = withContext(Dispatchers.IO) {
        val normalizedName = name.trim()

        if (normalizedName.isBlank()) {
            return@withContext CreateProfileResult.InvalidName
        }

        profileMutex.withLock {
            ensureProfilesDirectory()
            loadProfilesSync()

            val currentProfiles = _profiles.value
            val entitlement = familyEntitlementProvider.currentEntitlement()
            if (!ProfileLimitPolicy.canCreateProfile(currentProfiles.size, entitlement)) {
                return@withLock CreateProfileResult.LimitReached(
                    savedProfileCount = currentProfiles.size,
                    freeProfileLimit = entitlement.freeProfileLimit,
                )
            }

            val currentProfile = resolveCurrentActiveProfile()

            val defaultProfile = currentProfiles.find { profile ->
                profile.profileId == DEFAULT_PROFILE_ID
            }

            val initialLanguageCode = when (creationMode) {
                ProfileCreationMode.DUPLICATE_CURRENT -> {
                    normalizeSupportedLanguageOrNull(currentProfile?.activeLanguageCode)
                        ?: DEFAULT_LANGUAGE_CODE
                }

                ProfileCreationMode.DEFAULT_TEMPLATE,
                ProfileCreationMode.BLANK,
                -> {
                    normalizeSupportedLanguageOrNull(boardLanguageCode)
                        ?: DEFAULT_LANGUAGE_CODE
                }
            }

            val initialLayout = when (creationMode) {
                ProfileCreationMode.DUPLICATE_CURRENT -> {
                    currentProfile?.layout?.toMap().orEmpty()
                }

                ProfileCreationMode.DEFAULT_TEMPLATE -> {
                    val factoryLayout = loadFactoryDefaultProfile()
                        ?.layout
                        ?.mapValues { (_, layoutState) ->
                            layoutState.copy(
                                clickCount = 0,
                                isHidden = false,
                            )
                        }

                    factoryLayout ?: defaultProfile?.layout?.mapValues { (_, layoutState) ->
                        layoutState.copy(
                            clickCount = 0,
                            isHidden = false,
                        )
                    }.orEmpty()
                }

                ProfileCreationMode.BLANK -> {
                    emptyMap()
                }
            }

            if (creationMode != ProfileCreationMode.DUPLICATE_CURRENT) {
                ensureStarterDictionaryForLanguageSync(initialLanguageCode)
            }

            val newProfile = UserProfile(
                profileId = UUID.randomUUID().toString(),
                profileName = normalizedName,
                activeLanguageCode = initialLanguageCode,
                layout = initialLayout,
            )

            if (!saveProfileInternal(newProfile)) {
                return@withLock CreateProfileResult.SaveFailed
            }

            _profiles.update { existing ->
                existing + newProfile
            }

            val currentLocked = settingsRepository.freeCustomProfileLockedFlow.first()
            if (entitlement.hasFamilyEntitlement && currentLocked) {
                settingsRepository.updateFreeCustomProfileLocked(false)
            }
            val effectiveLocked = currentLocked && !entitlement.hasFamilyEntitlement

            settingsRepository.updateActiveAndSelectedCustomProfile(
                activeProfileId = newProfile.profileId,
                selectedCustomProfileId = newProfile.profileId,
            )
            recomputeResolvedStateLocked(
                activeIdOverride = newProfile.profileId,
                selectedCustomIdOverride = newProfile.profileId,
                isFreeLockedOverride = effectiveLocked,
            )

            CreateProfileResult.Success(newProfile)
        }
    }

    suspend fun switchProfile(
        profileId: String,
    ): Boolean = withContext(Dispatchers.IO) {
        profileMutex.withLock {
            val storedActiveId = settingsRepository.activeProfileIdFlow.first()
            val storedSelectedCustomId = settingsRepository.selectedCustomProfileIdFlow.first()
            val storedFreeLocked = settingsRepository.freeCustomProfileLockedFlow.first()
            val entitlement = familyEntitlementProvider.currentEntitlement()
            if (entitlement.hasFamilyEntitlement && storedFreeLocked) {
                settingsRepository.updateFreeCustomProfileLocked(false)
            }
            val effectiveLocked = storedFreeLocked && !entitlement.hasFamilyEntitlement

            val (partition, currentActive) = resolveStateForInputs(
                profileList = _profiles.value,
                activeId = storedActiveId,
                selectedCustomId = storedSelectedCustomId,
                isFreeSlotLocked = effectiveLocked,
                entitlement = entitlement,
            )

            val targetProfile = partition.availableProfiles.find { profile ->
                profile.profileId == profileId
            } ?: return@withLock false

            val nextSelectedCustomId = when {
                effectiveLocked -> storedSelectedCustomId
                targetProfile.profileId != DEFAULT_PROFILE_ID -> targetProfile.profileId
                else -> currentActive?.profileId?.takeIf { it != DEFAULT_PROFILE_ID }
                    ?: partition.selectedCustomProfileId
            }

            settingsRepository.updateActiveAndSelectedCustomProfile(
                activeProfileId = targetProfile.profileId,
                selectedCustomProfileId = nextSelectedCustomId,
            )
            recomputeResolvedStateLocked(
                activeIdOverride = targetProfile.profileId,
                selectedCustomIdOverride = nextSelectedCustomId,
                isFreeLockedOverride = effectiveLocked,
            )
            true
        }
    }

    suspend fun confirmFreeCustomProfile(
        profileId: String,
    ): SelectFreeCustomProfileResult = withContext(Dispatchers.IO) {
        profileMutex.withLock {
            val entitlement = familyEntitlementProvider.currentEntitlement()
            if (!entitlement.isProfileCapEnforced || entitlement.hasFamilyEntitlement) {
                return@withLock SelectFreeCustomProfileResult.NotApplicable
            }
            if (settingsRepository.readFreeCustomProfileLocked()) {
                return@withLock SelectFreeCustomProfileResult.Locked
            }
            if (profileId == DEFAULT_PROFILE_ID) {
                return@withLock SelectFreeCustomProfileResult.InvalidTarget
            }

            val targetProfile = _profiles.value.find { profile ->
                profile.profileId == profileId
            } ?: return@withLock SelectFreeCustomProfileResult.InvalidTarget

            val currentActive = resolveCurrentActiveProfile()
            val currentActiveId = currentActive?.profileId
            val shouldSwitchActive =
                currentActiveId != null &&
                    currentActiveId != DEFAULT_PROFILE_ID &&
                    currentActiveId != profileId

            val nextActiveId = if (shouldSwitchActive) {
                profileId
            } else {
                currentActiveId ?: DEFAULT_PROFILE_ID
            }

            settingsRepository.updateFreeProfileSlot(
                activeProfileId = nextActiveId,
                selectedCustomProfileId = profileId,
                isFreeSlotLocked = true,
            )
            recomputeResolvedStateLocked(
                activeIdOverride = nextActiveId,
                selectedCustomIdOverride = profileId,
                isFreeLockedOverride = true,
            )

            SelectFreeCustomProfileResult.Success(
                selectedProfile = targetProfile,
                activeProfileChanged = shouldSwitchActive,
            )
        }
    }

    suspend fun deleteProfile(
        profileId: String,
    ) = withContext(Dispatchers.IO) {
        profileMutex.withLock {
            /*
             * Keep the default profile available as a reliable fallback.
             */
            if (profileId == DEFAULT_PROFILE_ID) {
                return@withLock
            }

            val profileFile = profileFile(profileId)

            if (profileFile.exists() && !profileFile.delete()) {
                Log.e(
                    TAG,
                    "Failed to delete profile: $profileId",
                )

                return@withLock
            }

            _profiles.update { currentProfiles ->
                currentProfiles.filterNot { profile ->
                    profile.profileId == profileId
                }
            }

            val storedActiveId = settingsRepository.activeProfileIdFlow.first()
            val storedSelectedCustomId = settingsRepository.selectedCustomProfileIdFlow.first()
            val storedFreeLocked = settingsRepository.freeCustomProfileLockedFlow.first()
            val entitlement = familyEntitlementProvider.currentEntitlement()
            val effectiveLocked = storedFreeLocked && !entitlement.hasFamilyEntitlement

            val candidateActiveId = if (storedActiveId == profileId) null else storedActiveId
            val candidateSelectedCustomId = if (effectiveLocked) {
                storedSelectedCustomId
            } else if (storedSelectedCustomId == profileId) {
                null
            } else {
                storedSelectedCustomId
            }

            val (partition, resolvedActive) = resolveStateForInputs(
                profileList = _profiles.value,
                activeId = candidateActiveId,
                selectedCustomId = candidateSelectedCustomId,
                isFreeSlotLocked = effectiveLocked,
                entitlement = entitlement,
            )
            val nextActiveId = resolvedActive?.profileId ?: DEFAULT_PROFILE_ID
            val nextSelectedCustomId = if (effectiveLocked) {
                storedSelectedCustomId
            } else {
                partition.selectedCustomProfileId
            }

            if (
                nextActiveId != storedActiveId ||
                nextSelectedCustomId != storedSelectedCustomId
            ) {
                settingsRepository.updateActiveAndSelectedCustomProfile(
                    activeProfileId = nextActiveId,
                    selectedCustomProfileId = nextSelectedCustomId,
                )
            }

            _profileAvailability.value = partition
            _activeProfile.value = resolvedActive
        }
    }

    suspend fun resetActiveProfileToDefault(): Boolean =
        withContext(Dispatchers.IO) {
            profileMutex.withLock {
                val currentProfile =
                    resolveCurrentActiveProfile() ?: return@withLock false

                val factoryProfile =
                    loadFactoryDefaultProfile()
                        ?: return@withLock false

                /*
                 * Reset only the active profile's layout.
                 *
                 * Preserve:
                 * - profile ID
                 * - profile name
                 * - currently selected language
                 *
                 * Replace:
                 * - tile placement
                 * - visibility
                 * - aggregate click counts
                 * - category links and quick-fire layout state
                 */
                val resetProfile = currentProfile.copy(
                    activeLanguageCode =
                        currentProfile.activeLanguageCode,
                    layout = factoryProfile.layout.mapValues { (_, layoutState) ->
                        layoutState.copy(
                            clickCount = 0,
                            isHidden = false,
                        )
                    },
                )

                if (!saveProfileInternal(resetProfile)) {
                    return@withLock false
                }

                _profiles.update { currentProfiles ->
                    currentProfiles.map { profile ->
                        if (
                            profile.profileId ==
                            resetProfile.profileId
                        ) {
                            resetProfile
                        } else {
                            profile
                        }
                    }
                }
                recomputeResolvedStateLocked(useOverrides = false)

                true
            }
        }

    private fun loadFactoryDefaultProfile():
            UserProfile? {
        return runCatching {
            context.assets
                .open(INITIAL_DATA_ASSET)
                .use { assetInputStream ->
                    ZipInputStream(
                        assetInputStream.buffered()
                    ).use { zipInputStream ->
                        var entry =
                            zipInputStream.nextEntry

                        while (entry != null) {
                            val normalizedName =
                                entry.name
                                    .replace('\\', '/')
                                    .removePrefix("/")

                            if (
                                !entry.isDirectory &&
                                normalizedName ==
                                DEFAULT_PROFILE_ZIP_PATH
                            ) {
                                val profileJson =
                                    zipInputStream
                                        .bufferedReader()
                                        .readText()
                                        .removePrefix("\uFEFF")

                                return@runCatching json
                                    .decodeFromString<UserProfile>(
                                        profileJson
                                    )
                            }

                            zipInputStream.closeEntry()
                            entry =
                                zipInputStream.nextEntry
                        }

                        null
                    }
                }
        }.onFailure { error ->
            Log.e(
                TAG,
                "Failed to load factory profile template",
                error,
            )
        }.getOrNull()
    }

    suspend fun updateActiveProfile(
        updatedProfile: UserProfile,
    ) = withContext(Dispatchers.IO) {
        profileMutex.withLock {
            if (!saveProfileInternal(updatedProfile)) {
                return@withLock
            }

            val existingProfileFound =
                _profiles.value.any { profile ->
                    profile.profileId == updatedProfile.profileId
                }

            if (existingProfileFound) {
                _profiles.update { currentProfiles ->
                    currentProfiles.map { profile ->
                        if (
                            profile.profileId ==
                            updatedProfile.profileId
                        ) {
                            updatedProfile
                        } else {
                            profile
                        }
                    }
                }
            } else {
                _profiles.update { currentProfiles ->
                    currentProfiles + updatedProfile
                }
            }
            recomputeResolvedStateLocked(useOverrides = false)
        }
    }

    suspend fun repairProfilesLanguageForLegacyMigration(
        legacyLanguageCode: String,
        hasExplicitLegacyPreference: Boolean,
        activeProfileId: String?,
    ) = withContext(Dispatchers.IO) {
        profileMutex.withLock {
            val normalizedLegacy = normalizeSupportedLanguageOrNull(legacyLanguageCode)
                ?: DEFAULT_LANGUAGE_CODE
            val currentProfiles = _profiles.value
            if (currentProfiles.isEmpty()) {
                return@withLock
            }

            val updatedProfiles = currentProfiles.map { profile ->
                val currentCanonical = normalizeSupportedLanguageOrNull(profile.activeLanguageCode)
                val isTargetActiveProfile =
                    (activeProfileId != null && profile.profileId == activeProfileId) ||
                        (activeProfileId == null && currentProfiles.size == 1)

                val desiredLanguage = when {
                    isTargetActiveProfile && hasExplicitLegacyPreference -> normalizedLegacy
                    currentCanonical != null -> currentCanonical
                    else -> normalizedLegacy
                }

                if (profile.activeLanguageCode == desiredLanguage) {
                    profile
                } else {
                    val updated = profile.copy(activeLanguageCode = desiredLanguage)
                    if (saveProfileInternal(updated)) updated else profile
                }
            }

            _profiles.value = updatedProfiles
            recomputeResolvedStateLocked(useOverrides = false)
        }
    }

    private fun ensureStarterDictionaryForLanguageSync(languageCode: String) {
        val targetZipEntry = "tiles/$languageCode/default_dictionary.json"
        val audioPrefix = "audio_tiles/$languageCode/"
        var bundledDictionaryJson: String? = null
        val filesCanonicalPrefix = context.filesDir.canonicalPath + File.separator

        runCatching {
            context.assets.open(INITIAL_DATA_ASSET).use { assetInputStream ->
                ZipInputStream(assetInputStream.buffered()).use { zipInputStream ->
                    var entry = zipInputStream.nextEntry
                    while (entry != null) {
                        val normalizedName = entry.name
                            .replace('\\', '/')
                            .removePrefix("/")

                        if (!entry.isDirectory) {
                            when {
                                normalizedName == targetZipEntry -> {
                                    bundledDictionaryJson = zipInputStream
                                        .bufferedReader()
                                        .readText()
                                        .removePrefix("\uFEFF")
                                }

                                normalizedName.startsWith(audioPrefix) -> {
                                    val destAudioFile = File(context.filesDir, normalizedName)
                                    val destCanonical = runCatching { destAudioFile.canonicalPath }.getOrNull()
                                    if (destCanonical != null &&
                                        destCanonical.startsWith(filesCanonicalPrefix) &&
                                        !destAudioFile.exists()
                                    ) {
                                        destAudioFile.parentFile?.mkdirs()
                                        FileOutputStream(destAudioFile).use { output ->
                                            zipInputStream.copyTo(output)
                                        }
                                    }
                                }
                            }
                        }

                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to read starter dictionary from $INITIAL_DATA_ASSET for $languageCode", error)
        }

        val rawBundled = bundledDictionaryJson ?: return
        val bundledDefs = runCatching {
            json.decodeFromString<List<TileDefinition>>(rawBundled)
        }.getOrNull() ?: return

        val resolvedBundledDefs = bundledDefs.map { definition ->
            val resolvedAudio = resolveMediaReferenceIfExists(definition.audioUri)
            val resolvedImage = resolveMediaReferenceIfExists(definition.imageUri)
            definition.copy(
                audioUri = resolvedAudio,
                imageUri = resolvedImage,
                isCustomAudio = if (resolvedAudio == null) false else definition.isCustomAudio,
            )
        }

        val destinationFile = File(context.filesDir, targetZipEntry)
        val existingDefs = if (destinationFile.exists()) {
            runCatching {
                json.decodeFromString<List<TileDefinition>>(
                    destinationFile.readText().removePrefix("\uFEFF")
                )
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val mergedDefs = (existingDefs + resolvedBundledDefs)
            .associateBy { it.languageCode to it.id }
            .values
            .toList()

        if (destinationFile.exists() && existingDefs == mergedDefs) {
            return
        }

        destinationFile.parentFile?.mkdirs()
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        runCatching {
            tempFile.writeText(json.encodeToString(mergedDefs))
            if (destinationFile.exists() && !destinationFile.delete()) {
                error("Failed to replace ${destinationFile.name}")
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
        }.onFailure { error ->
            tempFile.delete()
            Log.e(TAG, "Failed to update starter dictionary for $languageCode", error)
        }
    }

    private fun resolveMediaReferenceIfExists(rawUri: String?): String? {
        val trimmed = rawUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("android.resource://", ignoreCase = true)
        ) {
            return trimmed
        }

        val normalized = trimmed.replace('\\', '/').removePrefix("file://").removePrefix("/")
        val candidateFile = if (normalized.startsWith("/")) {
            File(normalized)
        } else {
            File(context.filesDir, normalized)
        }
        return if (candidateFile.isFile) candidateFile.absolutePath else null
    }

    private fun normalizeSupportedLanguageOrNull(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val primary = trimmed.lowercase().substringBefore('-').substringBefore('_')
        return when (LocaleHelper.normalize(primary)) {
            "he" -> "he"
            "en" -> "en"
            else -> null
        }
    }

    private fun saveProfileInternal(
        profile: UserProfile,
    ): Boolean {
        ensureProfilesDirectory()

        val destinationFile = profileFile(profile.profileId)
        val temporaryFile = File(
            profilesDir,
            "${destinationFile.name}.tmp",
        )

        return runCatching {
            val profileJson = json.encodeToString(
                UserProfile.serializer(),
                profile,
            )

            /*
             * Write to a temporary file first. This reduces the risk of
             * leaving a partially written profile after interruption.
             */
            temporaryFile.writeText(profileJson)

            if (
                destinationFile.exists() &&
                !destinationFile.delete()
            ) {
                error(
                    "Could not replace existing profile file."
                )
            }

            if (!temporaryFile.renameTo(destinationFile)) {
                /*
                 * Fallback for filesystems where renameTo cannot complete.
                 */
                temporaryFile.copyTo(
                    target = destinationFile,
                    overwrite = true,
                )

                temporaryFile.delete()
            }

            true
        }.onFailure { error ->
            temporaryFile.delete()

            Log.e(
                TAG,
                "Failed to save profile: ${profile.profileId}",
                error,
            )
        }.getOrDefault(false)
    }

    private fun profileFile(
        profileId: String,
    ): File {
        return File(
            profilesDir,
            "profile_$profileId.json",
        )
    }
}