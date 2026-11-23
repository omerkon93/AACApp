package com.kon.myaacapp.domain.service

import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.TextToSpeechHelper
import com.kon.myaacapp.service.audio.TileSpeechPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SentenceManager(
    private val audioService: AudioRecordingService,
    private val ttsHelper: TextToSpeechHelper,
    private val tileService: AACTileService,
    private val scope: CoroutineScope,
    private val tileSpeechPlayer: TileSpeechPlayer = TileSpeechPlayer(audioService, ttsHelper),
    private val tilesUseTtsProvider: suspend () -> Boolean = { false },
) {
    private val _selectedSentence =
        MutableStateFlow<List<CombinedTile>>(emptyList())

    val selectedSentence: StateFlow<List<CombinedTile>> =
        _selectedSentence.asStateFlow()

    fun addTile(
        tile: CombinedTile,
    ) {
        _selectedSentence.value =
            _selectedSentence.value + tile
    }

    fun backspace() {
        val currentSentence = _selectedSentence.value

        if (currentSentence.isNotEmpty()) {
            _selectedSentence.value =
                currentSentence.dropLast(1)
        }
    }

    fun clear() {
        _selectedSentence.value = emptyList()
    }

    fun speak() {
        val sentenceSnapshot =
            _selectedSentence.value

        if (sentenceSnapshot.isEmpty()) {
            return
        }

        scope.launch {
            audioService.speakSentence(
                sentence = sentenceSnapshot,
                ttsHelper = ttsHelper,
                tileService = tileService,
                tilesUseTts = tilesUseTtsProvider(),
                speechPlayer = tileSpeechPlayer,
            )
        }
    }

    fun playPreview(
        ttsText: String,
        audioUri: String?,
        isCustomAudio: Boolean = true,
        tilesUseTts: Boolean = false,
    ) {
        tileSpeechPlayer.speakTile(
            ttsText = ttsText,
            audioUri = audioUri,
            isCustomAudio = isCustomAudio,
            tilesUseTts = tilesUseTts,
        )
    }
}
