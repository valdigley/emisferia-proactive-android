package com.emisferia.proactive.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Voice recognition state
 */
sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    object Processing : VoiceState()
    data class Result(val text: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

/**
 * Voice Recognition Service
 * Handles speech-to-text conversion
 */
class VoiceRecognitionService(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionListener: RecognitionListener? = null

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // Wake word detection
    private val wakeWords = listOf(
        "emisfera", "emisferia", "hey emisfera", "oi emisfera",
        "emisféria", "ei emisféria"
    )

    // Callback for when command is recognized
    var onCommandRecognized: ((String) -> Unit)? = null

    /**
     * Initialize the speech recognizer
     */
    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceState.Error("Reconhecimento de voz nao disponivel")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceState.Listening
            }

            override fun onBeginningOfSpeech() {
                // User started speaking
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Convert RMS dB to 0-1 range
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _audioLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = VoiceState.Processing
                _audioLevel.value = 0f
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Erro de audio"
                    SpeechRecognizer.ERROR_CLIENT -> "Erro do cliente"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissao necessaria"
                    SpeechRecognizer.ERROR_NETWORK -> "Erro de rede"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de rede"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Nao entendi"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecedor ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "Erro do servidor"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout de fala"
                    else -> "Erro desconhecido"
                }

                // For no match or timeout, just go back to idle (not an error state)
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    _state.value = VoiceState.Idle
                } else {
                    _state.value = VoiceState.Error(message)
                }
                _audioLevel.value = 0f
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""

                _state.value = VoiceState.Result(text)
                _audioLevel.value = 0f

                // Check for wake word and extract command
                processRecognizedText(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""

                // Could show partial text in UI
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer?.setRecognitionListener(recognitionListener)
    }

    /**
     * Start listening for voice input
     */
    fun startListening(continuous: Boolean = false) {
        if (speechRecognizer == null) {
            initialize()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

            if (continuous) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _state.value = VoiceState.Error("Erro ao iniciar: ${e.message}")
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        _state.value = VoiceState.Idle
        _audioLevel.value = 0f
    }

    /**
     * Process recognized text for wake word and command
     */
    private fun processRecognizedText(text: String) {
        val lowerText = text.lowercase(Locale.getDefault())

        // Check if starts with wake word
        for (wakeWord in wakeWords) {
            if (lowerText.startsWith(wakeWord)) {
                // Extract command after wake word
                val command = text.substring(wakeWord.length).trim()
                if (command.isNotEmpty()) {
                    onCommandRecognized?.invoke(command)
                }
                return
            }
        }

        // If no wake word, treat entire text as command (when already in command mode)
        if (text.isNotEmpty()) {
            onCommandRecognized?.invoke(text)
        }
    }

    /**
     * Release resources
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        recognitionListener = null
    }

    /**
     * Reset to idle state
     */
    fun reset() {
        _state.value = VoiceState.Idle
        _audioLevel.value = 0f
    }
}
