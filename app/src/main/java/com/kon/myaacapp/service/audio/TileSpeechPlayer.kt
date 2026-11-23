package com.kon.myaacapp.service.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AudioClipPlayer {
    fun canPlayAudio(audioUri: String): Boolean
    fun playRecording(audioUri: String, onError: (() -> Unit)? = null)
    suspend fun playRecordingSuspend(path: String): Boolean
    fun stopPlayback()
}

interface SpeechSynthesizer {
    fun speak(text: String)
    suspend fun speakSuspend(text: String)
    fun stop()
}

enum class TilePlaybackPath {
    AUDIO_RECORDING,
    TTS,
    NONE,
}

data class TilePlaybackEvent(
    val tileId: String?,
    val path: TilePlaybackPath,
    val audioUri: String?,
    val isCustomAudio: Boolean,
    val tilesUseTts: Boolean,
    val fileExists: Boolean,
    val usedFallback: Boolean,
)

sealed interface TilePlaybackDecision {
    data class PlayAudio(
        val audioUri: String,
        val fallbackTtsText: String?,
    ) : TilePlaybackDecision

    data class SpeakTts(
        val text: String,
        val usedFallback: Boolean,
    ) : TilePlaybackDecision

    data object None : TilePlaybackDecision
}

object TilePlaybackResolver {
    fun resolve(
        ttsText: String,
        audioUri: String?,
        isCustomAudio: Boolean,
        tilesUseTts: Boolean,
        canPlayAudio: (String) -> Boolean,
    ): TilePlaybackDecision {
        val cleanAudioUri = audioUri?.trim()?.takeIf { it.isNotEmpty() }
        val cleanTtsText = ttsText.trim().takeIf { it.isNotEmpty() }

        val shouldPreferAudio = cleanAudioUri != null && (isCustomAudio || !tilesUseTts)
        if (shouldPreferAudio) {
            if (canPlayAudio(cleanAudioUri)) {
                return TilePlaybackDecision.PlayAudio(
                    audioUri = cleanAudioUri,
                    fallbackTtsText = cleanTtsText,
                )
            }

            return if (cleanTtsText != null) {
                TilePlaybackDecision.SpeakTts(
                    text = cleanTtsText,
                    usedFallback = true,
                )
            } else {
                TilePlaybackDecision.None
            }
        }

        return if (cleanTtsText != null) {
            TilePlaybackDecision.SpeakTts(
                text = cleanTtsText,
                usedFallback = false,
            )
        } else {
            TilePlaybackDecision.None
        }
    }
}

class TileSpeechPlayer(
    private val audioPlayer: AudioClipPlayer,
    private val speechSynthesizer: SpeechSynthesizer,
) {
    private val _lastPlaybackEvent = MutableStateFlow<TilePlaybackEvent?>(null)
    val lastPlaybackEvent: StateFlow<TilePlaybackEvent?> = _lastPlaybackEvent.asStateFlow()

    fun speakTile(
        tileId: String? = null,
        ttsText: String,
        audioUri: String?,
        isCustomAudio: Boolean,
        tilesUseTts: Boolean,
    ): TilePlaybackPath {
        val fileExists = !audioUri.isNullOrBlank() && audioPlayer.canPlayAudio(audioUri)
        val decision = TilePlaybackResolver.resolve(
            ttsText = ttsText,
            audioUri = audioUri,
            isCustomAudio = isCustomAudio,
            tilesUseTts = tilesUseTts,
            canPlayAudio = { path -> audioPlayer.canPlayAudio(path) },
        )

        return when (decision) {
            is TilePlaybackDecision.PlayAudio -> {
                speechSynthesizer.stop()
                recordEvent(
                    TilePlaybackEvent(
                        tileId = tileId,
                        path = TilePlaybackPath.AUDIO_RECORDING,
                        audioUri = decision.audioUri,
                        isCustomAudio = isCustomAudio,
                        tilesUseTts = tilesUseTts,
                        fileExists = true,
                        usedFallback = false,
                    )
                )
                audioPlayer.playRecording(
                    audioUri = decision.audioUri,
                    onError = {
                        val fallbackText = decision.fallbackTtsText
                        if (!fallbackText.isNullOrBlank()) {
                            recordEvent(
                                TilePlaybackEvent(
                                    tileId = tileId,
                                    path = TilePlaybackPath.TTS,
                                    audioUri = decision.audioUri,
                                    isCustomAudio = isCustomAudio,
                                    tilesUseTts = tilesUseTts,
                                    fileExists = false,
                                    usedFallback = true,
                                )
                            )
                            speechSynthesizer.speak(fallbackText)
                        } else {
                            recordEvent(
                                TilePlaybackEvent(
                                    tileId = tileId,
                                    path = TilePlaybackPath.NONE,
                                    audioUri = decision.audioUri,
                                    isCustomAudio = isCustomAudio,
                                    tilesUseTts = tilesUseTts,
                                    fileExists = false,
                                    usedFallback = true,
                                )
                            )
                        }
                    },
                )
                TilePlaybackPath.AUDIO_RECORDING
            }

            is TilePlaybackDecision.SpeakTts -> {
                audioPlayer.stopPlayback()
                recordEvent(
                    TilePlaybackEvent(
                        tileId = tileId,
                        path = TilePlaybackPath.TTS,
                        audioUri = audioUri,
                        isCustomAudio = isCustomAudio,
                        tilesUseTts = tilesUseTts,
                        fileExists = fileExists,
                        usedFallback = decision.usedFallback,
                    )
                )
                speechSynthesizer.speak(decision.text)
                TilePlaybackPath.TTS
            }

            TilePlaybackDecision.None -> {
                audioPlayer.stopPlayback()
                speechSynthesizer.stop()
                recordEvent(
                    TilePlaybackEvent(
                        tileId = tileId,
                        path = TilePlaybackPath.NONE,
                        audioUri = audioUri,
                        isCustomAudio = isCustomAudio,
                        tilesUseTts = tilesUseTts,
                        fileExists = fileExists,
                        usedFallback = !audioUri.isNullOrBlank() && (isCustomAudio || !tilesUseTts),
                    )
                )
                TilePlaybackPath.NONE
            }
        }
    }

    suspend fun speakTileSuspend(
        tileId: String? = null,
        ttsText: String,
        audioUri: String?,
        isCustomAudio: Boolean,
        tilesUseTts: Boolean,
    ): TilePlaybackPath {
        val fileExists = !audioUri.isNullOrBlank() && audioPlayer.canPlayAudio(audioUri)
        val decision = TilePlaybackResolver.resolve(
            ttsText = ttsText,
            audioUri = audioUri,
            isCustomAudio = isCustomAudio,
            tilesUseTts = tilesUseTts,
            canPlayAudio = { path -> audioPlayer.canPlayAudio(path) },
        )

        return when (decision) {
            is TilePlaybackDecision.PlayAudio -> {
                speechSynthesizer.stop()
                val played = audioPlayer.playRecordingSuspend(decision.audioUri)
                if (played) {
                    recordEvent(
                        TilePlaybackEvent(
                            tileId = tileId,
                            path = TilePlaybackPath.AUDIO_RECORDING,
                            audioUri = decision.audioUri,
                            isCustomAudio = isCustomAudio,
                            tilesUseTts = tilesUseTts,
                            fileExists = true,
                            usedFallback = false,
                        )
                    )
                    TilePlaybackPath.AUDIO_RECORDING
                } else if (!decision.fallbackTtsText.isNullOrBlank()) {
                    recordEvent(
                        TilePlaybackEvent(
                            tileId = tileId,
                            path = TilePlaybackPath.TTS,
                            audioUri = decision.audioUri,
                            isCustomAudio = isCustomAudio,
                            tilesUseTts = tilesUseTts,
                            fileExists = false,
                            usedFallback = true,
                        )
                    )
                    speechSynthesizer.speakSuspend(decision.fallbackTtsText)
                    TilePlaybackPath.TTS
                } else {
                    recordEvent(
                        TilePlaybackEvent(
                            tileId = tileId,
                            path = TilePlaybackPath.NONE,
                            audioUri = decision.audioUri,
                            isCustomAudio = isCustomAudio,
                            tilesUseTts = tilesUseTts,
                            fileExists = false,
                            usedFallback = true,
                        )
                    )
                    TilePlaybackPath.NONE
                }
            }

            is TilePlaybackDecision.SpeakTts -> {
                audioPlayer.stopPlayback()
                recordEvent(
                    TilePlaybackEvent(
                        tileId = tileId,
                        path = TilePlaybackPath.TTS,
                        audioUri = audioUri,
                        isCustomAudio = isCustomAudio,
                        tilesUseTts = tilesUseTts,
                        fileExists = fileExists,
                        usedFallback = decision.usedFallback,
                    )
                )
                speechSynthesizer.speakSuspend(decision.text)
                TilePlaybackPath.TTS
            }

            TilePlaybackDecision.None -> {
                audioPlayer.stopPlayback()
                speechSynthesizer.stop()
                recordEvent(
                    TilePlaybackEvent(
                        tileId = tileId,
                        path = TilePlaybackPath.NONE,
                        audioUri = audioUri,
                        isCustomAudio = isCustomAudio,
                        tilesUseTts = tilesUseTts,
                        fileExists = fileExists,
                        usedFallback = !audioUri.isNullOrBlank() && (isCustomAudio || !tilesUseTts),
                    )
                )
                TilePlaybackPath.NONE
            }
        }
    }

    private fun recordEvent(event: TilePlaybackEvent) {
        _lastPlaybackEvent.value = event
        Log.i(
            TAG,
            "Playback path=${event.path} tileId=${event.tileId} isCustomAudio=${event.isCustomAudio} " +
                "tilesUseTts=${event.tilesUseTts} fileExists=${event.fileExists} usedFallback=${event.usedFallback} " +
                "audioUri=${event.audioUri}",
        )
    }

    private companion object {
        const val TAG = "TileSpeechPlayer"
    }
}
