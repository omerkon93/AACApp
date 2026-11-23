package com.kon.myaacapp.data.repository

import android.content.Context
import android.util.Log
import com.kon.myaacapp.R
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementProvider
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

    init {
        scope.launch {
            combine(
                _profiles,
                settingsRepository.activeProfileIdFlow,
            ) { profileList, activeId ->
                profileList.find { profile ->
                    profile.profileId == activeId
                } ?: profileList.firstOrNull()
            }.collect { resolved ->
                _activeProfile.value = resolved
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
                _activeProfile.value = resolveCurrentActiveProfile()
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

        settingsRepository.updateActiveProfileId(
            DEFAULT_PROFILE_ID
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
            return
        }

        val storedProfileId =
            settingsRepository.activeProfileIdFlow.first()

        val storedProfileExists =
            availableProfiles.any { profile ->
                profile.profileId == storedProfileId
            }

        if (storedProfileExists) {
            return
        }

        val fallbackProfile =
            availableProfiles.find { profile ->
                profile.profileId == DEFAULT_PROFILE_ID
            } ?: availableProfiles.first()

        settingsRepository.updateActiveProfileId(
            fallbackProfile.profileId
        )
    }

    private suspend fun resolveCurrentActiveProfile(): UserProfile? {
        val profileList = _profiles.value
        val activeId = settingsRepository.activeProfileIdFlow.first()
        return profileList.find { profile ->
            profile.profileId == activeId
        } ?: profileList.firstOrNull()
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

            settingsRepository.updateActiveProfileId(
                newProfile.profileId
            )
            _activeProfile.value = newProfile

            CreateProfileResult.Success(newProfile)
        }
    }

    suspend fun switchProfile(
        profileId: String,
    ) = withContext(Dispatchers.IO) {
        profileMutex.withLock {
            val targetProfile = _profiles.value.find { profile ->
                profile.profileId == profileId
            }

            if (targetProfile != null) {
                settingsRepository.updateActiveProfileId(profileId)
                _activeProfile.value = targetProfile
            }
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

            if (resolveCurrentActiveProfile()?.profileId == profileId ||
                _activeProfile.value?.profileId == profileId
            ) {
                val fallbackProfile = _profiles.value.firstOrNull()
                val fallbackProfileId =
                    fallbackProfile?.profileId ?: DEFAULT_PROFILE_ID

                settingsRepository.updateActiveProfileId(
                    fallbackProfileId
                )
                _activeProfile.value = fallbackProfile
            } else {
                _activeProfile.value = resolveCurrentActiveProfile()
            }
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
                _activeProfile.value = resetProfile

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
            _activeProfile.value = resolveCurrentActiveProfile()
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
            _activeProfile.value = resolveCurrentActiveProfile()
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