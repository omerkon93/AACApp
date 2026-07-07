package com.kon.myaacapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ProfileRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    val scope: CoroutineScope
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val profilesDir = File(context.filesDir, "profiles")

    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    val activeProfile: StateFlow<UserProfile?> = combine(
        profiles,
        settingsRepository.activeProfileIdFlow
    ) { profiles, activeId ->
        profiles.find { it.profileId == activeId } ?: profiles.firstOrNull()
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        if (!profilesDir.exists()) {
            profilesDir.mkdirs()
        }
        loadProfiles()
        bootstrapper()
    }

    fun reload() {
        loadProfiles()
        bootstrapper()
    }

    private fun loadProfiles() {
        // Updated to walkTopDown() so it searches recursively inside subfolders like /he/
        val files = profilesDir.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.toList()

        val loadedProfiles = files.mapNotNull { file ->
            try {
                json.decodeFromString<UserProfile>(file.readText())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        _profiles.value = loadedProfiles
    }

    fun bootstrapper() {
        scope.launch {
            if (_profiles.value.isEmpty()) {
                val defaultProfile = UserProfile(
                    profileId = "default",
                    profileName = context.getString(R.string.default_profile_name),
                    activeLanguageCode = "he"
                )
                
                // IMPORTANT: We need access to the repository to populate the initial layout.
                // However, Repository depends on ProfileRepository.
                // We'll let the Repository call populateInitialLayout when it initializes.

                saveProfileInternal(defaultProfile)
                _profiles.value = listOf(defaultProfile)
                settingsRepository.updateActiveProfileId(defaultProfile.profileId)
            }
        }
    }

    suspend fun createProfile(name: String) = withContext(Dispatchers.IO) {
        val newProfile = UserProfile(
            profileId = UUID.randomUUID().toString(),
            profileName = name,
            activeLanguageCode = "he"
        )
        saveProfileInternal(newProfile)
        loadProfiles()
        settingsRepository.updateActiveProfileId(newProfile.profileId)
    }

    suspend fun switchProfile(profileId: String) {
        settingsRepository.updateActiveProfileId(profileId)
    }

    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        val file = File(profilesDir, "profile_$profileId.json")
        if (file.exists()) {
            file.delete()
        }
        loadProfiles()
    }

    suspend fun updateActiveProfile(updatedProfile: UserProfile) = withContext(Dispatchers.IO) {
        saveProfileInternal(updatedProfile)
        // Refresh local list
        val currentList = _profiles.value.toMutableList()
        val index = currentList.indexOfFirst { it.profileId == updatedProfile.profileId }
        if (index != -1) {
            currentList[index] = updatedProfile
            _profiles.value = currentList
        } else {
            loadProfiles()
        }
    }

    private fun saveProfileInternal(profile: UserProfile) {
        val file = File(profilesDir, "profile_${profile.profileId}.json")
        try {
            // BULLETPROOF CHECK: Recreate the directory if the restore script wiped it!
            file.parentFile?.mkdirs()

            val profileJson = json.encodeToString(UserProfile.serializer(), profile)
            file.writeText(profileJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getProfilesDir(): File = profilesDir


}
