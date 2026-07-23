package com.kon.myaacapp.service.audio

import android.app.Application
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AudioPreviewManager(
    application: Application,
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    val audioService: AudioRecordingService,
) {
    private val textToSpeechHelper =
        TextToSpeechHelper(application)

    init {
        scope.launch {
            settingsRepository
                .languageCodeFlow
                .collectLatest { languageCode ->
                    textToSpeechHelper.setLanguage(
                        LocaleHelper.normalize(
                            languageCode
                        )
                    )
                }
        }
    }

    fun playPreview(
        ttsText: String,
        audioUri: String?,
    ) {
        when {
            !audioUri.isNullOrBlank() -> {
                audioService.playRecording(
                    audioUri = audioUri,
                )
            }

            ttsText.isNotBlank() -> {
                textToSpeechHelper.speak(
                    text = ttsText,
                )
            }
        }
    }
}