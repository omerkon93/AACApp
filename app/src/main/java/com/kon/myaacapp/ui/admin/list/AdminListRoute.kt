package com.kon.myaacapp.ui.admin.list

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.service.audio.AudioRecordingService

@Composable
fun AdminListRoute(
    languageCode: String,
    audioService: AudioRecordingService,
    viewModelFactory: AdminListViewModelFactory,
    onCreateTile: () -> Unit,
    onEditTile: (CombinedTile) -> Unit,
    onDeleteTile: (CombinedTile) -> Unit,
    onPlayPreview: (
        ttsText: String,
        audioUri: String?,
    ) -> Unit,
    onError: (String) -> Unit = {},
    content: @Composable (
        state: AdminListState,
        onAction: (AdminListAction) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current

    val adminListViewModel: AdminListViewModel =
        viewModel(
            key = "admin-list",
            factory = viewModelFactory,
        )

    val state by adminListViewModel.state
        .collectAsStateWithLifecycle()

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                adminListViewModel.onAction(
                    AdminListAction
                        .MicrophonePermissionGranted
                )
            } else {
                adminListViewModel.onAction(
                    AdminListAction
                        .MicrophonePermissionDenied
                )
            }
        }

    /*
     * Keep the list synchronized with the current
     * application language.
     */
    LaunchedEffect(
        adminListViewModel,
        languageCode,
    ) {
        adminListViewModel.updateLanguage(
            languageCode = languageCode,
        )
    }

    /*
     * Handle navigation, permission, audio-service,
     * preview, and error events.
     */
    LaunchedEffect(
        adminListViewModel,
        audioService,
    ) {
        adminListViewModel.effects.collect { effect ->
            when (effect) {
                AdminListEffect.OpenTileCreator -> {
                    onCreateTile()
                }

                is AdminListEffect.OpenTileEditor -> {
                    onEditTile(effect.tile)
                }

                is AdminListEffect
                .RequestTileDeletion -> {
                    onDeleteTile(effect.tile)
                }

                is AdminListEffect
                .RequestMicrophonePermission -> {
                    val permissionGranted =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) ==
                                PackageManager.PERMISSION_GRANTED

                    if (permissionGranted) {
                        adminListViewModel.onAction(
                            AdminListAction
                                .MicrophonePermissionGranted
                        )
                    } else {
                        microphonePermissionLauncher.launch(
                            Manifest.permission.RECORD_AUDIO
                        )
                    }
                }

                is AdminListEffect.StartRecording -> {
                    runCatching {
                        audioService.startRecording(
                            languageCode =
                                effect.languageCode,
                            tileId = effect.tile.id,
                        )
                    }.onSuccess { temporaryPath ->
                        if (temporaryPath != null) {
                            adminListViewModel.onAction(
                                AdminListAction.RecordingStarted(
                                    temporaryAudioPath =
                                        temporaryPath,
                                )
                            )
                        } else {
                            onError(
                                "Failed to create the recording file."
                            )

                            adminListViewModel.onAction(
                                AdminListAction
                                    .CancelRecordingClicked
                            )
                        }
                    }.onFailure { error ->
                        onError(
                            error.message
                                ?: "Failed to start recording."
                        )

                        adminListViewModel.onAction(
                            AdminListAction
                                .CancelRecordingClicked
                        )
                    }
                }

                AdminListEffect.StopRecording -> {
                    runCatching {
                        audioService.stopRecording()
                    }.onFailure { error ->
                        onError(
                            error.message
                                ?: "Failed to stop recording."
                        )
                    }

                    adminListViewModel.onAction(
                        AdminListAction.RecordingStopped
                    )
                }

                is AdminListEffect.PlayAudioPreview -> {
                    onPlayPreview(
                        effect.ttsText,
                        effect.audioUri,
                    )
                }

                is AdminListEffect.ShowError -> {
                    onError(effect.message)
                }
            }
        }
    }

    content(
        state,
        adminListViewModel::onAction,
    )
}