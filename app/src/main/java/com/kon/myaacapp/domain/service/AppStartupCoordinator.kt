package com.kon.myaacapp.domain.service

import android.content.Context
import android.util.Log
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.service.backup.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AppStartupCoordinator(
    context: Context,
    private val backupService: BackupService,
    private val repository: AACRepository,
    private val profileRepository: ProfileRepository,
) {
    private val applicationContext =
        context.applicationContext

    suspend fun initialize(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                installInitialDataIfRequired()

                repository.completeLegacyMigration()

                profileRepository.reload()
                repository.reload()

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

    private suspend fun installInitialDataIfRequired() =
        withContext(Dispatchers.IO) {
            val firstBootFlag = File(
                applicationContext.filesDir,
                FIRST_BOOT_FLAG,
            )

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