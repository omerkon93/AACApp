package com.kon.myaacapp.ui.admin.system

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kon.myaacapp.R

@Composable
fun SystemSettingsRoute(
    viewModelFactory: SystemSettingsViewModelFactory,
    onNavigateToProfiles: () -> Unit,
    onSystemDataChanged: () -> Unit,
    content: @Composable (
        state: SystemSettingsState,
        onAction: (SystemSettingsAction) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current

    val systemSettingsViewModel:
            SystemSettingsViewModel =
        viewModel(
            key = "system-settings",
            factory = viewModelFactory,
        )

    val state by systemSettingsViewModel.state
        .collectAsStateWithLifecycle()

    val backupDestinationLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.CreateDocument(
                    "application/zip"
                ),
        ) { uri ->
            if (uri != null) {
                systemSettingsViewModel.onAction(
                    SystemSettingsAction
                        .SaveBackupDestinationSelected(
                            uri = uri,
                        )
                )
            }
        }

    val backupImportLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                systemSettingsViewModel.onAction(
                    SystemSettingsAction
                        .ImportBackupFileSelected(
                            uri = uri,
                        )
                )
            }
        }

    val backupMimeTypes = arrayOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/octet-stream",
    )

    val shareBackupTitle =
        stringResource(R.string.share_backup)

    LaunchedEffect(
        systemSettingsViewModel,
        context,
        onNavigateToProfiles,
        onSystemDataChanged,
    ) {
        systemSettingsViewModel.effects.collect { effect ->
            when (effect) {
                SystemSettingsEffect.OpenProfiles -> {
                    onNavigateToProfiles()
                }

                is SystemSettingsEffect
                .OpenBackupDestinationPicker -> {
                    backupDestinationLauncher.launch(
                        effect.suggestedFileName
                    )
                }

                SystemSettingsEffect
                    .OpenBackupImportPicker -> {
                    backupImportLauncher.launch(
                        backupMimeTypes
                    )
                }

                is SystemSettingsEffect.ShareBackup -> {
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"

                            putExtra(
                                Intent.EXTRA_STREAM,
                                effect.uri,
                            )

                            addFlags(
                                Intent
                                    .FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }

                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            shareBackupTitle,
                        )
                    )
                }

                SystemSettingsEffect
                    .RecreateActivity -> {
                    onSystemDataChanged()

                    (context as? Activity)?.recreate()
                }

                is SystemSettingsEffect.ShowMessage -> {
                    Toast.makeText(
                        context,
                        effect.message,
                        Toast.LENGTH_LONG,
                    ).show()

                    systemSettingsViewModel.onAction(
                        SystemSettingsAction
                            .StatusMessageConsumed
                    )

                    onSystemDataChanged()
                }

                is SystemSettingsEffect.ShowError -> {
                    Toast.makeText(
                        context,
                        effect.message,
                        Toast.LENGTH_LONG,
                    ).show()

                    systemSettingsViewModel.onAction(
                        SystemSettingsAction
                            .ErrorConsumed
                    )
                }
            }
        }
    }

    content(
        state,
        systemSettingsViewModel::onAction,
    )
}