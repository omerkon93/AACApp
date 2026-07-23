package com.kon.myaacapp.ui.admin.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kon.myaacapp.R
import com.kon.myaacapp.core.locale.DownloadStatus
import com.kon.myaacapp.domain.service.Gender

@Composable
fun AdminSystemSettings(
    state: SystemSettingsState,
    onAction: (SystemSettingsAction) -> Unit,
) {
    val speakOnTilePress =
        state.speakOnTilePress

    val langCode =
        state.languageCode

    val userGender =
        state.userGender

    val downloadStatus =
        state.languageDownloadStatus

    val onProfilesClick: () -> Unit = {
        onAction(
            SystemSettingsAction.OpenProfilesClicked
        )
    }

    val onToggleSpeak: (Boolean) -> Unit = { value ->
        onAction(
            SystemSettingsAction
                .SpeakOnTilePressChanged(
                    value = value,
                )
        )
    }

    val onMaleClick: () -> Unit = {
        onAction(
            SystemSettingsAction.GenderChanged(
                value = Gender.MALE,
            )
        )
    }

    val onFemaleClick: () -> Unit = {
        onAction(
            SystemSettingsAction.GenderChanged(
                value = Gender.FEMALE,
            )
        )
    }

    val onHebrewClick: () -> Unit = {
        onAction(
            SystemSettingsAction.LanguageChanged(
                languageCode = "he",
            )
        )
    }

    val onEnglishClick: () -> Unit = {
        onAction(
            SystemSettingsAction.LanguageChanged(
                languageCode = "en",
            )
        )
    }

    val onSaveBackupClick: () -> Unit = {
        onAction(
            SystemSettingsAction.SaveBackupClicked
        )
    }

    val onExportClick: () -> Unit = {
        onAction(
            SystemSettingsAction.ShareBackupClicked
        )
    }

    val onImportClick: () -> Unit = {
        onAction(
            SystemSettingsAction.ImportBackupClicked
        )
    }

    val onShowReset: () -> Unit = {
        onAction(
            SystemSettingsAction
                .ShowResetConfirmation
        )
    }

    val onDismissReset: () -> Unit = {
        onAction(
            SystemSettingsAction
                .HideResetConfirmation
        )
    }

    val onConfirmReset: () -> Unit = {
        onAction(
            SystemSettingsAction.ConfirmReset
        )
    }

    if (state.showResetConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissReset,
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = onConfirmReset,
                    enabled = !state.isResetting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissReset,
                    enabled = !state.isResetting,
                ) {
                    Text(
                        text = stringResource(R.string.cancel)
                    )
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.general_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onProfilesClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_manager))
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.speak_on_press))
                    Switch(
                        checked = speakOnTilePress,
                        onCheckedChange = onToggleSpeak,
                        enabled = !state.isBusy,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    stringResource(R.string.grammatical_gender),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = userGender == Gender.MALE,
                        onClick = onMaleClick,
                        label = { Text(stringResource(R.string.male)) },
                        enabled = !state.isBusy,
                    )
                    FilterChip(
                        selected = userGender == Gender.FEMALE,
                        onClick = onFemaleClick,
                        label = { Text(stringResource(R.string.female)) },
                        enabled = !state.isBusy,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.language), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = langCode == "he",
                        onClick = onHebrewClick,
                        label = { Text(stringResource(R.string.hebrew)) },
                        enabled = state.canChangeLanguage
                    )
                    FilterChip(
                        selected = langCode == "en",
                        onClick = onEnglishClick,
                        label = { Text(stringResource(R.string.english)) },
                        enabled = state.canChangeLanguage
                    )

                    if (
                        downloadStatus is DownloadStatus.Downloading ||
                        downloadStatus is DownloadStatus.Installing
                    ) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                when (downloadStatus) {
                    is DownloadStatus.Downloading -> Text(
                        stringResource(
                            R.string.downloading_lang,
                            downloadStatus.progress
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    is DownloadStatus.Installing -> Text(
                        stringResource(R.string.installing_lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    is DownloadStatus.Error -> Text(
                        stringResource(
                            R.string.lang_download_failed,
                            downloadStatus.message
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> {}
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.backup_and_restore),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSaveBackupClick,
                    enabled = state.canRunBackupOperation,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text = stringResource(
                            R.string.save_backup_locally
                        )
                    )
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                OutlinedButton(
                    onClick = onExportClick,
                    enabled = state.canRunBackupOperation,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text = stringResource(
                            R.string.share_backup
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onImportClick,
                    enabled = state.canRunBackupOperation,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text = stringResource(
                            R.string.import_backup
                        )
                    )
                }

                Text(
                    text = stringResource(
                        R.string.backup_file_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 8.dp,
                            vertical = 4.dp,
                        ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onShowReset,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reset_to_default))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}