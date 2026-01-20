package com.emisferia.proactive.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emisferia.proactive.api.*
import com.emisferia.proactive.service.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Message in conversation
 */
data class ConversationMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
)

/**
 * UI State for the main screen
 */
data class MainUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isProcessing: Boolean = false,
    val isConnected: Boolean = true,
    val isServiceRunning: Boolean = false,
    val currentText: String = "",
    val errorMessage: String? = null,
    val updateInfo: UpdateChecker.UpdateInfo? = null,
    val showUpdateDialog: Boolean = false
)

/**
 * Main ViewModel
 * Manages voice interaction with EmisferIA AI
 * Uses AI Chat for full system access
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val repository = EmisferiaRepository()

    // Services
    val voiceService = VoiceRecognitionService(application)
    private val ttsService = TextToSpeechService(application)

    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Conversation history
    private val _conversationHistory = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationMessage>> = _conversationHistory.asStateFlow()

    init {
        try {
            initializeServices()
            observeVoiceState()
            observeTtsState()
            observeServiceState()
            checkConnection()
            checkForUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Error in ViewModel init: ${e.message}")
        }
    }

    private fun initializeServices() {
        try {
            voiceService.initialize()
            ttsService.initialize()

            // Set up voice command callback
            voiceService.onCommandRecognized = { command ->
                processCommand(command)
            }

            // Start foreground service with delay for proactive features
            viewModelScope.launch {
                delay(2000) // Wait for app to fully initialize
                try {
                    EmisferiaForegroundService.start(getApplication())
                    Log.d(TAG, "Foreground service started")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start foreground service: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing services: ${e.message}")
        }
    }

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceService.state.collect { state ->
                when (state) {
                    is VoiceState.Idle -> {
                        _uiState.update { it.copy(isListening = false, isProcessing = false) }
                    }
                    is VoiceState.Listening -> {
                        _uiState.update { it.copy(isListening = true, isProcessing = false, currentText = "") }
                    }
                    is VoiceState.Processing -> {
                        _uiState.update { it.copy(isListening = false, isProcessing = true) }
                    }
                    is VoiceState.Result -> {
                        _uiState.update { it.copy(currentText = state.text) }
                    }
                    is VoiceState.Error -> {
                        _uiState.update { it.copy(isListening = false, isProcessing = false) }
                    }
                }
            }
        }
    }

    private fun observeTtsState() {
        viewModelScope.launch {
            ttsService.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            EmisferiaForegroundService.isRunning.collect { running ->
                _uiState.update { it.copy(isServiceRunning = running) }
            }
        }
    }

    private fun checkConnection() {
        viewModelScope.launch {
            repository.getDashboard().onSuccess {
                _uiState.update { it.copy(isConnected = true) }
                Log.d(TAG, "Connection successful")
            }.onFailure { error ->
                _uiState.update { it.copy(isConnected = false) }
                Log.e(TAG, "Connection failed: ${error.message}")
            }
        }
    }

    /**
     * Check for app updates from GitHub
     */
    private fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val updateInfo = UpdateChecker.checkForUpdate()
                if (updateInfo != null && updateInfo.hasUpdate) {
                    Log.d(TAG, "Update available: ${updateInfo.latestVersion}")
                    _uiState.update { it.copy(updateInfo = updateInfo, showUpdateDialog = true) }
                } else {
                    Log.d(TAG, "No update available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates: ${e.message}")
            }
        }
    }

    /**
     * Dismiss update dialog
     */
    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    /**
     * Open download page for update
     */
    fun downloadUpdate() {
        _uiState.value.updateInfo?.let { info ->
            if (info.downloadUrl.isNotEmpty()) {
                UpdateChecker.openDownloadPage(getApplication(), info.downloadUrl)
            }
        }
        dismissUpdateDialog()
    }

    /**
     * Start listening for voice input
     */
    fun startListening() {
        if (_uiState.value.isSpeaking) return
        voiceService.startListening()
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        voiceService.stopListening()
    }

    /**
     * Stop speaking
     */
    fun stopSpeaking() {
        ttsService.stop()
    }

    /**
     * Refresh connection status
     */
    fun refreshConnection() {
        checkConnection()
    }

    /**
     * Add message to conversation history
     */
    private fun addMessage(text: String, isUser: Boolean) {
        val message = ConversationMessage(text = text, isUser = isUser)
        _conversationHistory.update { it + message }
    }

    /**
     * Speak text
     */
    private fun speak(text: String) {
        ttsService.speak(text)
    }

    /**
     * Process voice command - Uses AI Chat for intelligent responses
     */
    private fun processCommand(command: String) {
        Log.d(TAG, "Processing command: $command")

        // Add user message to history
        addMessage(command, isUser = true)

        _uiState.update { it.copy(currentText = "", isProcessing = true) }

        viewModelScope.launch {
            // Check if it's a simple local command first
            val localResult = processLocalCommand(command.lowercase())

            if (localResult != null) {
                _uiState.update { it.copy(isProcessing = false) }
                addMessage(localResult, isUser = false)
                speak(localResult)
            } else {
                // Use AI Chat for full interaction - AI has access to all EmisferIA tools
                repository.chat(command).onSuccess { response ->
                    _uiState.update { it.copy(isProcessing = false, isConnected = true) }
                    addMessage(response.response, isUser = false)
                    speak(response.response)

                    // Log tools used for debugging
                    response.toolsUsed?.let { tools ->
                        Log.d(TAG, "AI used tools: ${tools.joinToString(", ")}")
                    }

                }.onFailure { error ->
                    Log.e(TAG, "AI Chat failed: ${error.message}")
                    _uiState.update { it.copy(isProcessing = false, isConnected = false) }

                    val errorMsg = "Desculpe, não consegui me conectar ao EmisferIA. Verifique sua conexão."
                    addMessage(errorMsg, isUser = false)
                    speak(errorMsg)
                }
            }
        }
    }

    /**
     * Process simple local commands for faster response
     * More complex queries go to AI Chat
     */
    private fun processLocalCommand(command: String): String? {
        return when {
            // Simple greetings
            command.matches(Regex("^(oi|olá|ola|hey|ei)$")) -> {
                val greeting = getTimeGreeting()
                "$greeting! Estou aqui. Pergunte qualquer coisa sobre suas tarefas, agenda, finanças ou ideias."
            }

            command.matches(Regex("^(bom dia|boa tarde|boa noite)$")) -> {
                val greeting = getTimeGreeting()
                "$greeting! Como posso ajudar?"
            }

            // Thanks
            command.contains("obrigado") || command.contains("valeu") || command.contains("thanks") -> {
                "De nada! Estou aqui se precisar de mais alguma coisa."
            }

            // Help
            command == "ajuda" || command == "o que você faz" || command == "o que voce faz" -> {
                "Sou o EmisferIA, seu assistente pessoal. Posso: " +
                "consultar suas tarefas e agenda, " +
                "ver seu financeiro e saldo, " +
                "gerenciar suas ideias, " +
                "criar novas tarefas ou compromissos, " +
                "e muito mais. Apenas pergunte naturalmente!"
            }

            // Stop/cancel
            command == "para" || command == "parar" || command == "cancela" || command == "cancelar" -> {
                stopSpeaking()
                null // Don't respond
            }

            else -> null // Let AI handle it
        }
    }

    private fun getTimeGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Bom dia"
            hour < 18 -> "Boa tarde"
            else -> "Boa noite"
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceService.destroy()
        ttsService.destroy()
        // Note: Don't stop the foreground service here - it should keep running
    }
}
