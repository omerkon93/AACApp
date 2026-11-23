package com.kon.myaacapp.service.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.kon.myaacapp.core.locale.LocaleHelper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class TextToSpeechHelper(context: Context) : SpeechSynthesizer {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLanguageCode: String? = null

    private val _lastRequestedLocale = MutableStateFlow<Locale?>(null)
    val lastRequestedLocale: StateFlow<Locale?> = _lastRequestedLocale.asStateFlow()

    // OPTIMIZATION: Multiplexer map for coroutines.
    // This allows multiple TTS requests to safely resolve their specific coroutines
    // without overwriting each other's listeners.
    private val activeContinuations = ConcurrentHashMap<String, CancellableContinuation<Unit>>()

    init {
        // FIX: Use applicationContext to prevent severe Activity memory leaks.
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                setupGlobalListener()
                currentLanguageCode?.let { setLanguage(it) } ?: setLanguage("he")
            } else {
                Log.e("TTS", "Initialization failed")
            }
        }
    }

    // FIX: Set the listener exactly ONCE during initialization.
    private fun setupGlobalListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                // Safely remove and resume the specific coroutine that requested this utterance
                id?.let { activeContinuations.remove(it)?.resume(Unit) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                Log.e("TTS", "Error speaking utterance ID: $id")
                id?.let { activeContinuations.remove(it)?.resume(Unit) }
            }

            override fun onError(id: String?, errorCode: Int) {
                Log.e("TTS", "Error speaking utterance ID: $id (error code: $errorCode)")
                id?.let { activeContinuations.remove(it)?.resume(Unit) }
            }

            // Handle cases where QUEUE_FLUSH interrupts a previous utterance
            override fun onStop(id: String?, interrupted: Boolean) {
                super.onStop(id, interrupted)
                id?.let { activeContinuations.remove(it)?.resume(Unit) }
            }
        })
    }

    fun setLanguage(languageCode: String) {
        val normalized = LocaleHelper.normalize(languageCode)
        currentLanguageCode = normalized
        val locale = resolveLocale(normalized)
        _lastRequestedLocale.value = locale
        if (!isInitialized) return

        val result = tts?.setLanguage(locale)
        Log.i("TTS", "setLanguage($normalized) -> locale=${locale.toLanguageTag()}, result=$result")
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("TTS", "Language $normalized is not supported or missing data")
        }
    }

    override fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.e("TTS", "TTS not initialized yet")
        }
    }

    override suspend fun speakSuspend(text: String) = suspendCancellableCoroutine { continuation ->
        if (!isInitialized) {
            Log.e("TTS", "TTS not initialized yet")
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        val utteranceId = UUID.randomUUID().toString()

        // Register this coroutine in the multiplexer
        activeContinuations[utteranceId] = continuation

        // OPTIMIZATION: Immediate hardware halt on UI cancellation.
        // If the ViewModel scope dies or the user navigates back, stop the hardware engine instantly.
        continuation.invokeOnCancellation {
            activeContinuations.remove(utteranceId)
            tts?.stop()
        }

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e("TTS", "speak() returned ERROR")
            activeContinuations.remove(utteranceId)?.resume(Unit)
        }
    }

    override fun stop() {
        activeContinuations.values.forEach { if (it.isActive) it.resume(Unit) }
        activeContinuations.clear()
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
    }

    companion object {
        fun resolveLocale(languageCode: String): Locale {
            val normalized = LocaleHelper.normalize(languageCode)
            return if (normalized == "he") {
                Locale.forLanguageTag("he-IL")
            } else {
                Locale.forLanguageTag(normalized)
            }
        }
    }
}