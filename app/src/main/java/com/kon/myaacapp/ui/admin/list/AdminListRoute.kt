package com.kon.myaacapp.ui.admin.list

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.RecordingFailureReason
import com.kon.myaacapp.service.audio.RecordingResult

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
        isCustomAudio: Boolean,
    ) -> Unit,
    onError: (String) -> Unit = {},
    content: @Composable (
        state: AdminListState,
        onAction: (AdminListAction) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current
    val recordingStartFailedMsg = stringResource(R.string.recording_start_failed_error)
    val recordingSilentMsg = stringResource(R.string.recording_silent_error)
    val recordingSilentKeptMsg = stringResource(R.string.recording_silent_kept_previous)
    val recordingCancelledMsg = stringResource(R.string.recording_cancelled_error)
    val recordingCancelledKeptMsg = stringResource(R.string.recording_cancelled_kept_previous)
    val recordingSaveFailedMsg = stringResource(R.string.recording_save_failed_error)
    val recordingSaveFailedKeptMsg = stringResource(R.string.recording_save_failed_kept_previous)

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
                    val started = runCatching {
                        audioService.startRecording(
                            languageCode = effect.languageCode,
                            tileId = effect.tile.id,
                        )
                    }.getOrDefault(false)

                    if (started) {
                        adminListViewModel.onAction(
                            AdminListAction.RecordingStarted
                        )
                    } else {
                        onError(recordingStartFailedMsg)
                        adminListViewModel.onAction(
                            AdminListAction.RecordingFailed(recordingStartFailedMsg)
                        )
                    }
                }

                AdminListEffect.StopRecording -> {
                    val hadPrevious =
                        !adminListViewModel.state.value.quickRecordTile?.audioUri.isNullOrBlank() ||
                            !adminListViewModel.state.value.temporaryAudioPath.isNullOrBlank()
                    val result = runCatching {
                        audioService.stopRecording()
                    }.getOrElse { error ->
                        RecordingResult.Failed(
                            reason = RecordingFailureReason.IO_ERROR,
                            cause = error,
                        )
                    }

                    when (result) {
                        is RecordingResult.Success -> {
                            adminListViewModel.onAction(
                                AdminListAction.RecordingCompleted(result.filePath)
                            )
                        }

                        RecordingResult.Silent -> {
                            val message = if (hadPrevious) {
                                recordingSilentKeptMsg
                            } else {
                                recordingSilentMsg
                            }
                            onError(message)
                            adminListViewModel.onAction(
                                AdminListAction.RecordingFailed(message)
                            )
                        }

                        RecordingResult.Cancelled -> {
                            val message = if (hadPrevious) {
                                recordingCancelledKeptMsg
                            } else {
                                recordingCancelledMsg
                            }
                            onError(message)
                            adminListViewModel.onAction(
                                AdminListAction.RecordingFailed(message)
                            )
                        }

                        is RecordingResult.Failed -> {
                            val message = if (hadPrevious) {
                                recordingSaveFailedKeptMsg
                            } else {
                                recordingSaveFailedMsg
                            }
                            onError(message)
                            adminListViewModel.onAction(
                                AdminListAction.RecordingFailed(message)
                            )
                        }
                    }
                }

                AdminListEffect.CancelRecording -> {
                    runCatching {
                        audioService.cancelRecording()
                    }
                    adminListViewModel.onAction(
                        AdminListAction.RecordingStopped
                    )
                }

                is AdminListEffect.PlayAudioPreview -> {
                    onPlayPreview(
                        effect.ttsText,
                        effect.audioUri,
                        effect.isCustomAudio,
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
