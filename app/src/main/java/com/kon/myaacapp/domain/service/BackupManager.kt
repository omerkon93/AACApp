package com.kon.myaacapp.domain.service

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import com.kon.myaacapp.R
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.service.backup.BackupService
import com.kon.myaacapp.service.backup.MissingMediaReference
import com.kon.myaacapp.service.backup.RestoreApplyStep
import com.kon.myaacapp.service.backup.RestoreReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BackupOperationResult(
    val isSuccess: Boolean,
    val message: String,
    val missingMedia: List<MissingMediaReference> = emptyList(),
)

class BackupManager(
    private val application: Application,
    private val backupService: BackupService,
    private val repository: AACRepository,
    private val profileRepository: ProfileRepository,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "BackupManager"
        private const val INITIAL_DATA_ASSET =
            "initial_data.zip"
    }

    private val _result =
        MutableStateFlow<BackupOperationResult?>(null)

    val result: StateFlow<BackupOperationResult?> =
        _result.asStateFlow()

    fun clearResult() {
        _result.value = null
    }

    fun resetToDefault() {
        clearResult()

        scope.launch {
            val success = runCatching {
                /*
                 * Restore shared factory data while preserving
                 * all existing profile files.
                 *
                 * This restores:
                 * - tile definitions
                 * - factory audio recordings
                 * - factory images
                 * - Room tile data
                 */
                val factoryDataRestored =
                    backupService.importFromAssets(
                        INITIAL_DATA_ASSET
                    )

                if (!factoryDataRestored) {
                    return@runCatching false
                }

                /*
                 * Reload the restored shared tile definitions.
                 */
                repository.reload()

                /*
                 * Restore the active profile's factory layout.
                 *
                 * Other profiles remain available.
                 */
                val profileRestored =
                    profileRepository
                        .resetActiveProfileToDefault()

                if (!profileRestored) {
                    return@runCatching false
                }

                /*
                 * Reload once more after the profile layout
                 * has been restored.
                 */
                repository.reload()

                true
            }.getOrDefault(false)

            val localizedContext = resolveMessageContext()
            _result.value = BackupOperationResult(
                isSuccess = success,
                message = localizedContext.getString(
                    if (success) {
                        R.string.reset_success
                    } else {
                        R.string.reset_failed
                    }
                ),
            )
        }
    }

    fun exportDatabase(
        uri: Uri,
        contentResolver: ContentResolver,
    ) {
        clearResult()

        scope.launch {
            val success = runCatching {
                withContext(Dispatchers.IO) {
                    backupService.exportDatabase(
                        contentResolver,
                        uri,
                    )
                }
            }.getOrElse { error ->
                Log.e(TAG, "Export failed", error)
                false
            }

            val localizedContext = resolveMessageContext()
            _result.value = BackupOperationResult(
                isSuccess = success,
                message = localizedContext.getString(
                    if (success) {
                        R.string.export_success
                    } else {
                        R.string.export_failed
                    }
                ),
            )
        }
    }

    fun importDatabase(
        uri: Uri,
        contentResolver: ContentResolver,
    ) {
        importDatabase(
            uri = uri,
            contentResolver = contentResolver,
            onApplyStep = null,
        )
    }

    @VisibleForTesting
    internal fun importDatabase(
        uri: Uri,
        contentResolver: ContentResolver,
        onApplyStep: (suspend (RestoreApplyStep) -> Unit)?,
    ) {
        clearResult()

        scope.launch {
            val report = runCatching {
                withContext(Dispatchers.IO) {
                    backupService.importDatabaseWithReport(
                        contentResolver = contentResolver,
                        uri = uri,
                        onApplyStep = onApplyStep,
                        onCommittedReload = {
                            reloadRestoredBoardAndAwaitReady()
                        },
                        onRollbackReload = {
                            reloadBoardAfterRollback()
                        },
                    )
                }
            }.getOrElse { error ->
                Log.e(TAG, "Restore operation failed", error)
                RestoreReport(success = false)
            }

            val localizedContext = resolveMessageContext()
            val message = when {
                !report.success -> {
                    localizedContext.getString(R.string.import_failed)
                }

                report.missingMedia.isNotEmpty() -> {
                    localizedContext.getString(
                        R.string.import_success_with_missing_media,
                        report.missingMedia.size,
                    )
                }

                else -> {
                    localizedContext.getString(R.string.import_success)
                }
            }

            _result.value = BackupOperationResult(
                isSuccess = report.success,
                message = message,
                missingMedia = report.missingMedia,
            )
        }
    }

    private suspend fun resolveMessageContext(): Context =
        if (backupService.settingsRepository.hasExplicitInterfaceLanguagePreference()) {
            LocaleHelper.createLocalizedContext(
                application,
                backupService.settingsRepository.interfaceLanguageCodeFlow.first(),
            )
        } else {
            application
        }

    private suspend fun reloadRestoredBoardAndAwaitReady() {
        /*
         * Load the imported profiles first because profile JSON is the
         * authoritative source for repeated tile placements.
         */
        profileRepository.reload()

        /*
         * Salvage any legacy Room tiles and placements contained in an
         * older backup. This requires an active imported profile.
         */
        repository.completeLegacyMigration()

        /*
         * Migration may have modified a profile, so reload the final
         * persisted profile state.
         */
        profileRepository.reload()

        /*
         * Finally reload shared tile definitions. Normal repository
         * reload must not repopulate or alter profile layouts.
         */
        repository.reload()

        awaitActiveProfileReady(requireNonEmpty = true)
    }

    private suspend fun reloadBoardAfterRollback() {
        profileRepository.reload()
        repository.reload()
        awaitActiveProfileReady(requireNonEmpty = false)
    }

    private suspend fun awaitActiveProfileReady(
        requireNonEmpty: Boolean,
    ) {
        val availableProfileIds = profileRepository.profiles.value
            .map { it.profileId }
            .toSet()

        if (requireNonEmpty) {
            check(availableProfileIds.isNotEmpty()) {
                "No profiles available after restore reload"
            }
        }

        if (availableProfileIds.isNotEmpty()) {
            profileRepository.activeProfile
                .filterNotNull()
                .first { profile ->
                    profile.profileId in availableProfileIds
                }
        }
    }

    fun exportAndShare(
        onReady: (Uri?) -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            val secureUri = runCatching {
                val backupsDirectory = File(
                    application.cacheDir,
                    "backups",
                )

                if (!backupsDirectory.exists()) {
                    backupsDirectory.mkdirs()
                }

                backupsDirectory
                    .listFiles()
                    ?.forEach { oldBackup ->
                        oldBackup.delete()
                    }

                val backupFile = File(
                    backupsDirectory,
                    "myaac_backup_" +
                            "${System.currentTimeMillis()}.zip",
                )

                val fileUri = Uri.fromFile(backupFile)

                val success = backupService.exportDatabase(
                    application.contentResolver,
                    fileUri,
                )

                if (!success) {
                    return@runCatching null
                }

                FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.fileprovider",
                    backupFile,
                )
            }.getOrNull()

            withContext(Dispatchers.Main) {
                onReady(secureUri)
            }
        }
    }
}