package com.kon.myaacapp.ui.editor.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kon.myaacapp.R
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.RecordingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AudioSection(
    audioUri: String?,
    onAudioUriChange: (String?) -> Unit,
    languageCode: String,
    audioService: AudioRecordingService,
    ttsText: String = "",
    onTestAudio: ((ttsText: String, audioUri: String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var isProcessingRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableLongStateOf(0L) }
    var feedbackMessageResId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTimeNano = System.nanoTime()
            while (isRecording) {
                recordingDuration = (System.nanoTime() - startTimeNano) / 1_000_000_000L
                delay(100.milliseconds)
            }
        } else {
            recordingDuration = 0L
        }
    }

    DisposableEffect(audioService) {
        onDispose {
            if (isRecording) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    audioService.cancelRecording()
                }
            }
        }
    }

    fun startCapture() {
        feedbackMessageResId = null
        val tempId = UUID.randomUUID().toString()
        val started = audioService.startRecording(tempId, languageCode)
        if (started) {
            isRecording = true
        } else {
            isRecording = false
            feedbackMessageResId = R.string.recording_start_failed_error
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCapture()
            } else {
                feedbackMessageResId = R.string.recording_permission_denied
            }
        }

    EditSection(
        title = stringResource(R.string.section_audio),
        icon = Icons.Default.Mic,
        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        iconColor = MaterialTheme.colorScheme.secondary,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (isRecording) {
                            isRecording = false
                            isProcessingRecording = true
                            val result = audioService.stopRecording()
                            isProcessingRecording = false
                            val hadPreviousRecording = !audioUri.isNullOrBlank()
                            when (result) {
                                is RecordingResult.Success -> {
                                    feedbackMessageResId = null
                                    onAudioUriChange(result.filePath)
                                }

                                RecordingResult.Silent -> {
                                    feedbackMessageResId = if (hadPreviousRecording) {
                                        R.string.recording_silent_kept_previous
                                    } else {
                                        R.string.recording_silent_error
                                    }
                                }

                                RecordingResult.Cancelled -> {
                                    feedbackMessageResId = if (hadPreviousRecording) {
                                        R.string.recording_cancelled_kept_previous
                                    } else {
                                        R.string.recording_cancelled_error
                                    }
                                }

                                is RecordingResult.Failed -> {
                                    feedbackMessageResId = if (hadPreviousRecording) {
                                        R.string.recording_save_failed_kept_previous
                                    } else {
                                        R.string.recording_save_failed_error
                                    }
                                }
                            }
                        } else if (!isProcessingRecording) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                startCapture()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                },
                enabled = !isProcessingRecording,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRecording) {
                        stringResource(R.string.stop_recording, recordingDuration)
                    } else {
                        stringResource(R.string.custom_voice_recording)
                    }
                )
            }

            feedbackMessageResId?.let { resId ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(resId),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            if ((audioUri != null) && !isRecording && !isProcessingRecording) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (onTestAudio != null) {
                                onTestAudio(ttsText, audioUri)
                            } else {
                                audioService.playRecording(audioUri)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(stringResource(R.string.test_audio))
                    }
                    OutlinedButton(
                        onClick = {
                            feedbackMessageResId = null
                            onAudioUriChange(null)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(stringResource(R.string.delete_action))
                    }
                }
            }
        }
    }
}
