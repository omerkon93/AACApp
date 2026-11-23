package com.kon.myaacapp.service.audio

import android.app.Application
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class AudioPreviewManager(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    val audioService: AudioRecordingService,
    private val textToSpeechHelper: TextToSpeechHelper = TextToSpeechHelper(application),
    private val profileRepository: ProfileRepository? = null,
) {
    private val tileSpeechPlayer = TileSpeechPlayer(
        audioPlayer = audioService,
        speechSynthesizer = textToSpeechHelper,
    )

    val lastPlaybackEvent: StateFlow<TilePlaybackEvent?> =
        tileSpeechPlayer.lastPlaybackEvent

    val lastRequestedTtsLocale: StateFlow<Locale?> =
        textToSpeechHelper.lastRequestedLocale

    @Volatile
    private var currentTilesUseTts: Boolean? = null

    @Volatile
    private var currentBoardLanguageCode: String? = null

    init {
        val boardLanguageFlow =
            profileRepository?.activeBoardLanguage ?: settingsRepository.languageCodeFlow

        scope.launch {
            boardLanguageFlow
                .collectLatest { languageCode ->
                    val normalized = LocaleHelper.normalize(languageCode)
                    currentBoardLanguageCode = normalized
                    textToSpeechHelper.setLanguage(normalized)
                }
        }

        scope.launch {
            settingsRepository
                .tilesUseTtsFlow
                .collectLatest { enabled ->
                    currentTilesUseTts = enabled
                }
        }
    }

    fun playPreview(
        ttsText: String,
        audioUri: String?,
        isCustomAudio: Boolean = true,
        tileId: String? = null,
    ) {
        currentBoardLanguageCode?.let { lang ->
            textToSpeechHelper.setLanguage(lang)
        }
        val cachedTilesUseTts = currentTilesUseTts
        if (cachedTilesUseTts != null) {
            tileSpeechPlayer.speakTile(
                tileId = tileId,
                ttsText = ttsText,
                audioUri = audioUri,
                isCustomAudio = isCustomAudio,
                tilesUseTts = cachedTilesUseTts,
            )
        } else {
            scope.launch {
                val boardLanguage = currentBoardLanguageCode ?: (
                    profileRepository?.activeBoardLanguage?.first()
                        ?: settingsRepository.languageCodeFlow.first()
                )
                textToSpeechHelper.setLanguage(LocaleHelper.normalize(boardLanguage))
                val useTts = settingsRepository.tilesUseTtsFlow.first()
                currentTilesUseTts = useTts
                tileSpeechPlayer.speakTile(
                    tileId = tileId,
                    ttsText = ttsText,
                    audioUri = audioUri,
                    isCustomAudio = isCustomAudio,
                    tilesUseTts = useTts,
                )
            }
        }
    }
}
