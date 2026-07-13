package com.kon.myaacapp.domain.service

import android.content.Context
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.service.audio.AudioRecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class QuickRecordingManager(
    private val context: Context,
    private val repository: AACRepository,
    private val audioService: AudioRecordingService,
    private val scope: CoroutineScope,
) {
    private val _recordingTileId =
        MutableStateFlow<String?>(null)

    val recordingTileId: StateFlow<String?> =
        _recordingTileId.asStateFlow()

    fun startRecording(
        tileId: String,
        languageCode: String,
    ) {
        if (_recordingTileId.value != null) {
            return
        }

        val outputPath = audioService.startRecording(
            tileId = tileId,
            languageCode = languageCode,
        )

        if (outputPath != null) {
            _recordingTileId.value = tileId
        }
    }

    fun stopRecording(
        tileId: String,
        languageCode: String,
    ) {
        if (_recordingTileId.value != tileId) {
            return
        }

        scope.launch {
            try {
                audioService.stopRecording()

                val outputFile = withContext(Dispatchers.IO) {
                    File(
                        context.filesDir,
                        "audio_tiles/$languageCode/audio_$tileId.wav",
                    )
                }

                if (outputFile.exists()) {
                    updateTileAudioUri(
                        tileId = tileId,
                        languageCode = languageCode,
                        audioUri = outputFile.absolutePath,
                    )
                }
            } finally {
                _recordingTileId.value = null
            }
        }
    }

    private suspend fun updateTileAudioUri(
        tileId: String,
        languageCode: String,
        audioUri: String?,
    ) {
        val tile = repository.getTileById(
            id = tileId,
            langCode = languageCode,
        ) ?: return

        repository.updateTile(
            tile.copy(audioUri = audioUri)
        )
    }
}