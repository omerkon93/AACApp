package com.kon.myaacapp.domain.service

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.core.content.FileProvider
import com.kon.myaacapp.R
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.service.backup.BackupService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BackupManager(
    private val application: Application,
    private val backupService: BackupService,
    private val repository: AACRepository,
    private val profileRepository: ProfileRepository,
    private val scope: CoroutineScope,
) {
    private val _status =
        MutableStateFlow<String?>(null)

    val status: StateFlow<String?> =
        _status.asStateFlow()

    fun clearStatus() {
        _status.value = null
    }

    fun resetToDefault() {
        scope.launch {
            _status.value = application.getString(
                R.string.resetting
            )

            val success =
                profileRepository
                    .resetActiveProfileToDefault()

            _status.value = application.getString(
                if (success) {
                    R.string.reset_success
                } else {
                    R.string.reset_failed
                }
            )
        }
    }

    fun exportDatabase(
        uri: Uri,
        contentResolver: ContentResolver,
    ) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                backupService.exportDatabase(
                    contentResolver,
                    uri,
                )
            }

            _status.value = application.getString(
                if (success) {
                    R.string.export_success
                } else {
                    R.string.export_failed
                }
            )
        }
    }

    fun importDatabase(
        uri: Uri,
        contentResolver: ContentResolver,
    ) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                backupService.importDatabase(
                    contentResolver,
                    uri,
                )
            }

            if (success) {
                profileRepository.reload()
                repository.reload()
            }

            _status.value = application.getString(
                if (success) {
                    R.string.import_success
                } else {
                    R.string.import_failed
                }
            )
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

    fun setStatus(
        message: String,
    ) {
        _status.value = message
    }
}