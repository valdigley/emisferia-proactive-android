package com.emisferia.proactive.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * TTS State
 */
sealed class TtsState {
    object Idle : TtsState()
    object Speaking : TtsState()
    data class Error(val message: String) : TtsState()
}

/**
 * Text-to-Speech Service
 * Handles speech synthesis for EmisferIA responses
 */
class TextToSpeechService(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Queue of texts to speak
    private val speechQueue = mutableListOf<String>()

    // Callback when speech completes
    var onSpeechComplete: (() -> Unit)? = null

    /**
     * Initialize TTS engine
     */
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("pt", "BR"))

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Try Portuguese without region
                    tts?.setLanguage(Locale("pt"))
                }

                // Configure voice parameters
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(1.0f)

                // Set up progress listener
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = TtsState.Speaking
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false

                        // Check if there's more in the queue
                        if (speechQueue.isNotEmpty()) {
                            val next = speechQueue.removeAt(0)
                            speakInternal(next)
                        } else {
                            _state.value = TtsState.Idle
                            onSpeechComplete?.invoke()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = TtsState.Error("Erro ao falar")
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _state.value = TtsState.Error("Erro ao falar: $errorCode")
                        _isSpeaking.value = false
                    }
                })

                isInitialized = true
            } else {
                _state.value = TtsState.Error("Falha ao inicializar TTS")
            }
        }
    }

    /**
     * Speak text
     */
    fun speak(text: String, queueMode: Boolean = false) {
        if (!isInitialized) {
            initialize()
            // Queue the text to speak after initialization
            speechQueue.add(text)
            return
        }

        if (queueMode && _isSpeaking.value) {
            speechQueue.add(text)
        } else {
            stop()
            speakInternal(text)
        }
    }

    private fun speakInternal(text: String) {
        val params = android.os.Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            UUID.randomUUID().toString()
        )
    }

    /**
     * Speak multiple sentences with pauses
     */
    fun speakSentences(sentences: List<String>) {
        if (sentences.isEmpty()) return

        // Add all to queue
        speechQueue.addAll(sentences)

        // Start with first
        val first = speechQueue.removeAt(0)
        speak(first)
    }

    /**
     * Stop speaking
     */
    fun stop() {
        tts?.stop()
        speechQueue.clear()
        _state.value = TtsState.Idle
        _isSpeaking.value = false
    }

    /**
     * Check if currently speaking
     */
    fun isBusy(): Boolean {
        return tts?.isSpeaking == true || speechQueue.isNotEmpty()
    }

    /**
     * Set speech rate (0.5 to 2.0)
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    /**
     * Set pitch (0.5 to 2.0)
     */
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    /**
     * Release resources
     */
    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
