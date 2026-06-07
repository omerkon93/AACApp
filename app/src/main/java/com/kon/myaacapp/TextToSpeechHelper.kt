package com.kon.myaacapp

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

class TextToSpeechHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLanguageCode: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                currentLanguageCode?.let { setLanguage(it) } ?: setLanguage("he")
            } else {
                Log.e("TTS", "Initialization failed")
            }
        }
    }

    fun setLanguage(languageCode: String) {
        currentLanguageCode = languageCode
        if (!isInitialized) return

        val locale = if (languageCode == "he") {
            Locale.forLanguageTag("he-IL")
        } else {
            Locale.US
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("TTS", "Language $languageCode is not supported or missing data")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.e("TTS", "TTS not initialized yet")
        }
    }

    suspend fun speakSuspend(text: String) = suspendCancellableCoroutine { continuation ->
        if (!isInitialized) {
            Log.e("TTS", "TTS not initialized yet")
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        val utteranceId = UUID.randomUUID().toString()
        
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                
                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) {
                        Log.e("TTS", "Error speaking: $text")
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) {
                        Log.e("TTS", "Error speaking: $text (error code: $errorCode)")
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            },
        )

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e("TTS", "speak() returned ERROR")
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}