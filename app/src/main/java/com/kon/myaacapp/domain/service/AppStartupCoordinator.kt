package com.kon.myaacapp.domain.service

import android.content.Context
import android.util.Log
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.service.backup.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class AppStartupCoordinator(
    context: Context,
    private val backupService: BackupService,
    private val repository: AACRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository = profileRepository.settingsRepository,
) {
    private val applicationContext =
        context.applicationContext

    suspend fun initialize(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val recovered =
                    backupService.recoverIncompleteRestoreIfNeeded()
                if (!recovered) {
                    error("Failed to recover incomplete backup restore")
                }

                val firstBootFlag = File(
                    applicationContext.filesDir,
                    FIRST_BOOT_FLAG,
                )
                val isFirstBoot = !firstBootFlag.exists()

                if (isFirstBoot) {
                    installInitialDataIfRequired(firstBootFlag)
                } else {
                    val synced = backupService.syncStarterDataFromAssetsIfNeeded(INITIAL_DATA_ASSET)
                    if (!synced) {
                        Log.w(TAG, "Starter data sync did not complete; using existing dictionaries")
                    }
                }

                /*
                 * The imported profile JSON is authoritative for layout,
                 * including repeated/shared tile placements.
                 */
                profileRepository.reload()

                val loadedProfile =
                    profileRepository.activeProfile
                        .filterNotNull()
                        .first()

                Log.d(
                    TAG,
                    "Initial active profile: ${loadedProfile.profileId}, " +
                        "layout entries: ${loadedProfile.layout.size}",
                )

                repository.completeLegacyMigration()

                /*
                 * Legacy migration may have updated the profile on disk.
                 */
                profileRepository.reload()

                migrateLanguageSeparationIfNeeded(isFirstBoot = isFirstBoot)

                val finalProfile =
                    profileRepository.activeProfile
                        .filterNotNull()
                        .first()

                Log.d(
                    TAG,
                    "Final active profile: ${finalProfile.profileId}, " +
                        "board language: ${finalProfile.activeLanguageCode}, " +
                        "layout entries: ${finalProfile.layout.size}",
                )

                /*
                 * Load shared definitions after the authoritative profile layout.
                 */
                repository.reload()

                Log.d(
                    TAG,
                    "Definitions loaded: " +
                        "${repository.baseDefinitions.value.size}",
                )

                true
            }.getOrElse { error ->
                Log.e(
                    TAG,
                    "Application initialization failed",
                    error,
                )

                false
            }
        }

    private suspend fun migrateLanguageSeparationIfNeeded(
        isFirstBoot: Boolean,
    ) {
        if (settingsRepository.isLanguageSeparationMigratedFlow.first()) {
            return
        }

        if (settingsRepository.hasExplicitInterfaceLanguagePreference()) {
            val currentInterfaceLanguage = settingsRepository.interfaceLanguageCodeFlow.first()
            settingsRepository.markLanguageSeparationMigrated(currentInterfaceLanguage)
            return
        }

        val hasLegacyPref = settingsRepository.hasExplicitLegacyLanguagePreference()
        val effectiveLegacyLanguage = settingsRepository.readLegacyLanguageCodeForMigration()
        val activeProfileId = profileRepository.activeProfile.value?.profileId
            ?: settingsRepository.activeProfileIdFlow.first()

        if (!isFirstBoot || hasLegacyPref) {
            profileRepository.repairProfilesLanguageForLegacyMigration(
                legacyLanguageCode = effectiveLegacyLanguage,
                hasExplicitLegacyPreference = hasLegacyPref,
                activeProfileId = activeProfileId,
            )
        }

        settingsRepository.markLanguageSeparationMigrated(effectiveLegacyLanguage)
    }

    private suspend fun installInitialDataIfRequired(
        firstBootFlag: File,
    ) = withContext(Dispatchers.IO) {
        if (firstBootFlag.exists()) {
            return@withContext
        }

        val importSucceeded =
            backupService.importFromAssets(
                INITIAL_DATA_ASSET
            )

        if (!importSucceeded) {
            error(
                "Failed to import $INITIAL_DATA_ASSET"
            )
        }

        val flagCreated =
            firstBootFlag.createNewFile()

        if (
            !flagCreated &&
            !firstBootFlag.exists()
        ) {
            error(
                "Failed to create $FIRST_BOOT_FLAG"
            )
        }
    }

    private companion object {
        const val TAG = "AppStartupCoordinator"

        const val INITIAL_DATA_ASSET =
            "initial_data.zip"

        const val FIRST_BOOT_FLAG =
            "first_boot_complete.flag"
    }
}
