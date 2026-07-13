package com.kon.myaacapp.ui.admin.system

import android.app.Activity
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import androidx.compose.material.icons.filled.Save

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.R
import com.kon.myaacapp.core.locale.DownloadStatus
import com.kon.myaacapp.domain.service.Gender

@Composable
fun AdminSystemSettings(
    viewModel: AACViewModel,
    onNavigateToProfiles: () -> Unit
) {
    val speakOnTilePress by viewModel.speakOnTilePress.collectAsState()
    val langCode by viewModel.languageCode.collectAsState()
    val importExportStatus by viewModel.importExportStatus.collectAsState()
    val context = LocalContext.current
    var showResetConfirm by remember { mutableStateOf(false) }

    val backupShareTitle = stringResource(R.string.share_backup)
    val backupFailedMsg = stringResource(R.string.backup_failed)

    val backupFileName = remember {
        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd_HH-mm",
            Locale.US,
        ).format(Date())

        "MyAACApp_Backup_$timestamp.zip"
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { selectedUri ->
            viewModel.importDatabase(
                uri = selectedUri,
                contentResolver = context.contentResolver,
            )
        }
    }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/zip"
        ),
    ) { uri ->
        uri?.let { selectedUri ->
            viewModel.exportDatabase(
                uri = selectedUri,
                contentResolver = context.contentResolver,
            )
        }
    }

    val onSaveBackupClick = remember(
        saveBackupLauncher,
        backupFileName,
    ) {
        {
            saveBackupLauncher.launch(backupFileName)
        }
    }

    // OPTIMIZATION: Cache the MIME types array to prevent JVM reallocation on every tap/recomposition.
    val backupMimeTypes = remember {
        arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream",
        )
    }

    LaunchedEffect(importExportStatus) {
        importExportStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearImportExportStatus()
        }
    }

    // OPTIMIZATION: Memoize all click actions. This allows To Compose's "Strong Skipping Mode"
    // to bypass recomposing buttons and chips when unrelated state changes occur.
    val onProfilesClick = remember(onNavigateToProfiles) { { onNavigateToProfiles() } }
    val onToggleSpeak =
        remember(viewModel) { { it: Boolean -> viewModel.updateSpeakOnTilePress(it) } }

    val onMaleClick = remember(viewModel) { { viewModel.updateUserGender(Gender.MALE) } }
    val onFemaleClick = remember(viewModel) { { viewModel.updateUserGender(Gender.FEMALE) } }

    val switchLanguage: (String) -> Unit = remember(
        viewModel,
        context,
        langCode,
    ) {
        { requestedLanguage ->
            if (requestedLanguage != langCode) {
                viewModel.downloadAndSetLanguage(
                    requestedLanguage
                ) { success ->
                    if (success) {
                        (context as? Activity)?.recreate()
                    }
                }
            }
        }
    }

    val onHebrewClick: () -> Unit = remember(
        switchLanguage
    ) {
        {
            switchLanguage("he")
        }
    }

    val onEnglishClick: () -> Unit = remember(
        switchLanguage
    ) {
        {
            switchLanguage("en")
        }
    }

    val onExportClick = remember(viewModel, context, backupShareTitle, backupFailedMsg) {
        {
            viewModel.exportAndShareDatabase(context) { uri ->
                if (uri != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, backupShareTitle))
                } else {
                    Toast.makeText(context, backupFailedMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val onImportClick = remember(
        importLauncher,
        backupMimeTypes,
    ) {
        {
            importLauncher.launch(backupMimeTypes)
        }
    }

    val onShowReset = remember { { showResetConfirm = true } }
    val onDismissReset = remember { { showResetConfirm = false } }
    val onConfirmReset = remember(viewModel, context) {
        {
            showResetConfirm = false
            viewModel.resetToDefault(context)
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = onDismissReset,
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = onConfirmReset,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReset) {
                    Text(stringResource(R.string.cancel))
                }
            }
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
                        onCheckedChange = onToggleSpeak
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val userGender by viewModel.userGender.collectAsState()
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
                        label = { Text(stringResource(R.string.male)) }
                    )
                    FilterChip(
                        selected = userGender == Gender.FEMALE,
                        onClick = onFemaleClick,
                        label = { Text(stringResource(R.string.female)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val downloadStatus by viewModel.languageDownloadStatus.collectAsState()
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
                        enabled = downloadStatus is DownloadStatus.Idle || downloadStatus is DownloadStatus.Success || downloadStatus is DownloadStatus.Error
                    )
                    FilterChip(
                        selected = langCode == "en",
                        onClick = onEnglishClick,
                        label = { Text(stringResource(R.string.english)) },
                        enabled = downloadStatus is DownloadStatus.Idle || downloadStatus is DownloadStatus.Success || downloadStatus is DownloadStatus.Error
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

                when (val status = downloadStatus) {
                    is DownloadStatus.Downloading -> Text(
                        stringResource(
                            R.string.downloading_lang,
                            status.progress
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
                            status.message
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