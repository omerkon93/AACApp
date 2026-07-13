package com.kon.myaacapp.domain.service

import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.TextToSpeechHelper
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
            )
        }
    }

    fun playPreview(
        ttsText: String,
        audioUri: String?,
    ) {
        when {
            !audioUri.isNullOrBlank() -> {
                audioService.playRecording(audioUri)
            }

            ttsText.isNotBlank() -> {
                ttsHelper.speak(ttsText)
            }
        }
    }
}