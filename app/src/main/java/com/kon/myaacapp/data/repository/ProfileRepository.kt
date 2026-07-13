package com.kon.myaacapp.data.repository

import android.content.Context
import android.util.Log
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.zip.ZipInputStream
import java.io.File
import java.util.UUID

class ProfileRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    internal val scope: CoroutineScope,
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

    private val _profiles =
        MutableStateFlow<List<UserProfile>>(emptyList())

    val profiles: StateFlow<List<UserProfile>> =
        _profiles.asStateFlow()

    val activeProfile: StateFlow<UserProfile?> = combine(
        profiles,
        settingsRepository.activeProfileIdFlow,
    ) { profileList, activeId ->
        profileList.find { profile ->
            profile.profileId == activeId
        } ?: profileList.firstOrNull()
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    init {
        scope.launch(Dispatchers.IO) {
            ensureProfilesDirectory()
            loadProfilesSync()
            bootstrapDefaultProfileSync()
        }
    }

    fun reload() {
        scope.launch(Dispatchers.IO) {
            ensureProfilesDirectory()
            loadProfilesSync()
            bootstrapDefaultProfileSync()
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

    private fun bootstrapDefaultProfileSync() {
        if (_profiles.value.isNotEmpty()) {
            return
        }

        val defaultProfile = UserProfile(
            profileId = DEFAULT_PROFILE_ID,
            profileName = context.getString(
                R.string.default_profile_name
            ),
            activeLanguageCode = DEFAULT_LANGUAGE_CODE,
        )

        if (!saveProfileInternal(defaultProfile)) {
            return
        }

        _profiles.value = listOf(defaultProfile)

        scope.launch {
            settingsRepository.updateActiveProfileId(
                DEFAULT_PROFILE_ID
            )
        }
    }

    suspend fun createProfile(
        name: String,
        creationMode: ProfileCreationMode,
    ) = withContext(Dispatchers.IO) {
        val normalizedName = name.trim()

        if (normalizedName.isBlank()) {
            return@withContext
        }

        val currentProfile = activeProfile.value

        val defaultProfile = _profiles.value.find { profile ->
            profile.profileId == DEFAULT_PROFILE_ID
        }

        val initialLayout = when (creationMode) {
            ProfileCreationMode.DUPLICATE_CURRENT -> {
                currentProfile?.layout?.toMap().orEmpty()
            }

            ProfileCreationMode.DEFAULT_TEMPLATE -> {
                defaultProfile?.layout?.toMap().orEmpty()
            }

            ProfileCreationMode.BLANK -> {
                emptyMap()
            }
        }

        val initialLanguageCode = when (creationMode) {
            ProfileCreationMode.DEFAULT_TEMPLATE -> {
                defaultProfile?.activeLanguageCode
                    ?: DEFAULT_LANGUAGE_CODE
            }

            ProfileCreationMode.DUPLICATE_CURRENT,
            ProfileCreationMode.BLANK,
                -> {
                currentProfile?.activeLanguageCode
                    ?: DEFAULT_LANGUAGE_CODE
            }
        }

        val newProfile = UserProfile(
            profileId = UUID.randomUUID().toString(),
            profileName = normalizedName,
            activeLanguageCode = initialLanguageCode,
            layout = initialLayout,
        )

        if (!saveProfileInternal(newProfile)) {
            return@withContext
        }

        _profiles.update { currentProfiles ->
            currentProfiles + newProfile
        }

        settingsRepository.updateActiveProfileId(
            newProfile.profileId
        )
    }

    suspend fun switchProfile(
        profileId: String,
    ) {
        val profileExists = _profiles.value.any { profile ->
            profile.profileId == profileId
        }

        if (profileExists) {
            settingsRepository.updateActiveProfileId(profileId)
        }
    }

    suspend fun deleteProfile(
        profileId: String,
    ) = withContext(Dispatchers.IO) {
        /*
         * Keep the default profile available as a reliable fallback.
         */
        if (profileId == DEFAULT_PROFILE_ID) {
            return@withContext
        }

        val profileFile = profileFile(profileId)

        if (profileFile.exists() && !profileFile.delete()) {
            Log.e(
                TAG,
                "Failed to delete profile: $profileId",
            )

            return@withContext
        }

        _profiles.update { currentProfiles ->
            currentProfiles.filterNot { profile ->
                profile.profileId == profileId
            }
        }

        if (activeProfile.value?.profileId == profileId) {
            val fallbackProfileId =
                _profiles.value.firstOrNull()?.profileId
                    ?: DEFAULT_PROFILE_ID

            settingsRepository.updateActiveProfileId(
                fallbackProfileId
            )
        }
    }

    suspend fun resetActiveProfileToDefault(): Boolean =
        withContext(Dispatchers.IO) {
            val currentProfile =
                activeProfile.value ?: return@withContext false

            val factoryProfile =
                loadFactoryDefaultProfile()
                    ?: return@withContext false

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
                return@withContext false
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

            true
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
        if (!saveProfileInternal(updatedProfile)) {
            return@withContext
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