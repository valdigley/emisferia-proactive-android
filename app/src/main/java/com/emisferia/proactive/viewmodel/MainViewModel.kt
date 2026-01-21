package com.emisferia.proactive.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emisferia.proactive.MainActivity
import com.emisferia.proactive.api.*
import com.emisferia.proactive.service.*
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
    val currentText: String = "",
    val errorMessage: String? = null,
    val autoListenEnabled: Boolean = true  // Auto-listen after AI speaks
)

/**
 * Main ViewModel
 * Manages voice interaction in real-time with EmisferIA
 * No buttons, no menus - pure voice interface
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

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

    // Data states (cached for voice responses)
    private val _dashboard = MutableStateFlow<DashboardData?>(null)
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    private val _schedule = MutableStateFlow<List<Schedule>>(emptyList())
    private val _financialSummary = MutableStateFlow<FinancialSummary?>(null)

    init {
        initializeServices()
        loadInitialData()
        observeVoiceState()
        observeTtsState()
    }

    private fun initializeServices() {
        voiceService.initialize()
        ttsService.initialize()

        // Set up voice command callback
        voiceService.onCommandRecognized = { command ->
            processCommand(command)
        }

        // Auto-listen after TTS finishes speaking
        ttsService.onSpeechComplete = {
            if (_uiState.value.autoListenEnabled && !_uiState.value.isProcessing) {
                // Small delay before starting to listen
                viewModelScope.launch {
                    kotlinx.coroutines.delay(300)
                    startListening()
                }
            }
        }
    }

    /**
     * Toggle auto-listen mode
     */
    fun toggleAutoListen() {
        _uiState.update { it.copy(autoListenEnabled = !it.autoListenEnabled) }
    }

    /**
     * Set auto-listen mode
     */
    fun setAutoListen(enabled: Boolean) {
        _uiState.update { it.copy(autoListenEnabled = enabled) }
    }

    /**
     * Check if app was activated by wake word and start listening
     */
    fun checkWakeWordActivation() {
        if (MainActivity.startListeningOnResume) {
            MainActivity.startListeningOnResume = false

            // Greet and start listening
            viewModelScope.launch {
                // Small delay to let UI settle
                kotlinx.coroutines.delay(500)

                // Speak greeting
                speak("Olá! Em que posso ajudar?")
            }
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
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                isProcessing = false
                            )
                        }
                        // Don't show error for normal timeouts
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

    /**
     * Load data for voice responses
     */
    fun loadInitialData() {
        viewModelScope.launch {
            println("[MainViewModel] Loading initial data from: ${com.emisferia.proactive.BuildConfig.API_URL}")

            // Load dashboard
            repository.getDashboard().onSuccess { data ->
                _dashboard.value = data
                _uiState.update { it.copy(isConnected = true) }
                println("[MainViewModel] Dashboard loaded successfully!")
            }.onFailure { error ->
                _uiState.update { it.copy(isConnected = false) }
                println("[MainViewModel] Dashboard failed: ${error.javaClass.simpleName} - ${error.message}")

                // Show connection error on startup
                val startupError = buildString {
                    append("Nao conectou ao servidor.\n")
                    append("API: ${com.emisferia.proactive.BuildConfig.API_URL}\n")
                    append("Erro: ${error.message?.take(100) ?: "Desconhecido"}")
                }
                addMessage(startupError, isUser = false)
            }

            // Load today's schedule
            repository.getTodaySchedule().onSuccess { data ->
                _schedule.value = data
            }

            // Load pending tasks
            repository.getTasks(status = "pending").onSuccess { data ->
                _tasks.value = data
            }

            // Load financial summary
            repository.getFinancialSummary().onSuccess { data ->
                _financialSummary.value = data
            }

            // Check for proactive alerts
            repository.getAlerts().onSuccess { alerts ->
                val highPriority = alerts.filter { it.priority == "high" }
                if (highPriority.isNotEmpty()) {
                    val greeting = getTimeGreeting()
                    val message = buildString {
                        append("$greeting! ")
                        append("Voce tem ${highPriority.size} alerta${if (highPriority.size > 1) "s" else ""} importante${if (highPriority.size > 1) "s" else ""}. ")
                        highPriority.take(2).forEach { alert ->
                            append("${alert.title}. ")
                        }
                    }
                    addMessage(message, isUser = false)
                    speak(message)
                }
            }
        }
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
     * Add message to conversation history
     */
    private fun addMessage(text: String, isUser: Boolean) {
        val message = ConversationMessage(text = text, isUser = isUser)
        _conversationHistory.update { it + message }
    }

    /**
     * Speak text and add to history
     */
    private fun speak(text: String) {
        ttsService.speak(text)
    }

    /**
     * Process voice command - Always use AI Chat for full access
     */
    private fun processCommand(command: String) {
        // Add user message to history
        addMessage(command, isUser = true)

        _uiState.update { it.copy(currentText = "", isProcessing = true) }

        viewModelScope.launch {
            // Always send to AI Chat for full processing with tools
            repository.chat(command).onSuccess { response ->
                _uiState.update { it.copy(isProcessing = false, isConnected = true) }

                // Show full response with markdown in chat
                addMessage(response.response, isUser = false)

                // Clean response for TTS (remove markdown)
                val cleanResponse = cleanForSpeech(response.response)
                speak(cleanResponse)

                // Log tools used
                response.toolsUsed?.let { tools ->
                    if (tools.isNotEmpty()) {
                        println("AI used tools: ${tools.joinToString(", ")}")
                    }
                }

            }.onFailure { error ->
                _uiState.update { it.copy(isProcessing = false, isConnected = false) }

                // Log full error for debugging
                val fullError = "${error.javaClass.simpleName}: ${error.message}"
                println("[MainViewModel] Chat error: $fullError")
                error.printStackTrace()

                // Show detailed error to help diagnose
                val errorMsg = buildString {
                    append("Erro de conexao.\n")
                    append("URL: ${com.emisferia.proactive.BuildConfig.API_URL}\n")
                    when {
                        error.message?.contains("UnknownHost") == true ||
                        error.message?.contains("Unable to resolve") == true -> {
                            append("Causa: DNS nao resolveu o dominio.\n")
                            append("Tente usar dados moveis ou outra rede WiFi.")
                        }
                        error.message?.contains("timeout") == true -> {
                            append("Causa: Servidor nao respondeu a tempo.\n")
                            append("Verifique sua conexao.")
                        }
                        error.message?.contains("SSL") == true ||
                        error.message?.contains("Certificate") == true -> {
                            append("Causa: Problema de certificado SSL.\n")
                            append("Detalhe: ${error.message}")
                        }
                        error.message?.contains("Connection refused") == true -> {
                            append("Causa: Conexao recusada pelo servidor.")
                        }
                        else -> {
                            append("Detalhe: $fullError")
                        }
                    }
                }
                addMessage(errorMsg, isUser = false)
                speak("Desculpe, nao consegui me conectar ao servidor.")
            }
        }
    }

    /**
     * Clean markdown formatting for TTS
     */
    private fun cleanForSpeech(text: String): String {
        return text
            // Remove bold/italic markers
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("_(.+?)_"), "$1")
            // Remove headers
            .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
            // Remove bullet points but keep text
            .replace(Regex("^[\\-\\*•]\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^\\d+\\.\\s*", RegexOption.MULTILINE), "")
            // Remove ALL emojis
            .let { removeEmojis(it) }
            // Clean extra whitespace
            .replace(Regex("\\n{3,}"), "\n\n")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    /**
     * Remove all emojis from text using comprehensive Unicode ranges
     */
    private fun removeEmojis(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)

            // Skip if it's an emoji or emoji-related character
            val isEmoji = when {
                // Emoticons
                codePoint in 0x1F600..0x1F64F -> true
                // Miscellaneous Symbols and Pictographs
                codePoint in 0x1F300..0x1F5FF -> true
                // Transport and Map Symbols
                codePoint in 0x1F680..0x1F6FF -> true
                // Supplemental Symbols and Pictographs
                codePoint in 0x1F900..0x1F9FF -> true
                // Symbols and Pictographs Extended-A
                codePoint in 0x1FA00..0x1FA6F -> true
                codePoint in 0x1FA70..0x1FAFF -> true
                // Dingbats
                codePoint in 0x2700..0x27BF -> true
                // Miscellaneous Symbols
                codePoint in 0x2600..0x26FF -> true
                // Geometric Shapes Extended
                codePoint in 0x1F780..0x1F7FF -> true
                // Enclosed Alphanumeric Supplement
                codePoint in 0x1F100..0x1F1FF -> true
                // Regional Indicator Symbols (flags)
                codePoint in 0x1F1E0..0x1F1FF -> true
                // Mahjong Tiles
                codePoint in 0x1F000..0x1F02F -> true
                // Playing Cards
                codePoint in 0x1F0A0..0x1F0FF -> true
                // Arrows (some are used as emoji)
                codePoint in 0x2190..0x21FF -> true
                // Mathematical Operators (some are used as emoji)
                codePoint in 0x2200..0x22FF -> true
                // Box Drawing and Geometric shapes
                codePoint in 0x25A0..0x25FF -> true
                // Variation Selectors
                codePoint == 0xFE0F || codePoint == 0xFE0E -> true
                // Zero Width Joiner
                codePoint == 0x200D -> true
                // Copyright, Registered, Trademark symbols often rendered as emoji
                codePoint == 0x00A9 || codePoint == 0x00AE || codePoint == 0x2122 -> true
                // Other common emoji-like symbols
                codePoint in 0x2300..0x23FF -> true  // Miscellaneous Technical
                codePoint in 0x2B00..0x2BFF -> true  // Miscellaneous Symbols and Arrows (⭕, ⬛, etc.)
                codePoint in 0x3000..0x303F -> true  // CJK Symbols
                // Number/letter enclosed in circles
                codePoint in 0x2460..0x24FF -> true
                else -> false
            }

            if (!isEmoji) {
                sb.appendCodePoint(codePoint)
            }

            i += Character.charCount(codePoint)
        }
        return sb.toString().replace(Regex(" {2,}"), " ").trim()
    }

    /**
     * Process common commands locally for faster response
     */
    private fun processLocalCommand(command: String): String? {
        return when {
            // Tasks
            command.contains("tarefa") && (command.contains("quais") || command.contains("minhas")) -> {
                buildTasksResponse()
            }
            command.contains("tarefa") && command.contains("urgente") -> {
                val urgent = _tasks.value.filter { it.priority == "urgent" }
                if (urgent.isEmpty()) {
                    "Voce nao tem tarefas urgentes no momento."
                } else {
                    "Voce tem ${urgent.size} tarefa${if (urgent.size > 1) "s" else ""} urgente${if (urgent.size > 1) "s" else ""}. " +
                        urgent.take(3).joinToString(". ") { it.title }
                }
            }

            // Schedule
            command.contains("compromisso") || command.contains("agenda") ||
                (command.contains("hoje") && !command.contains("tarefa")) -> {
                buildScheduleResponse()
            }

            // Financial
            command.contains("financeiro") || command.contains("dinheiro") ||
                command.contains("receber") || command.contains("pagar") -> {
                buildFinancialResponse()
            }

            // Messages
            command.contains("mensagem") || command.contains("whatsapp") -> {
                buildMessagesResponse()
            }

            // General status
            command.contains("status") || command.contains("resumo") -> {
                buildStatusResponse()
            }

            // Greetings
            command.contains("ola") || command.contains("oi") ||
                command.contains("bom dia") || command.contains("boa tarde") ||
                command.contains("boa noite") -> {
                val greeting = getTimeGreeting()
                "$greeting! Estou aqui. O que precisa?"
            }

            // Help
            command.contains("ajuda") || command.contains("o que voce faz") ||
                command.contains("como funciona") -> {
                "Voce pode me perguntar sobre suas tarefas, compromissos, financeiro, ou pedir um resumo do dia. Tambem posso criar tarefas e enviar mensagens."
            }

            // Thanks
            command.contains("obrigado") || command.contains("valeu") -> {
                "De nada! Estou aqui se precisar."
            }

            else -> null // Let server handle unknown commands
        }
    }

    private fun buildTasksResponse(): String {
        val pending = _tasks.value.filter { it.status != "done" && it.status != "cancelled" }
        return if (pending.isEmpty()) {
            "Voce nao tem tarefas pendentes. Parabens!"
        } else {
            val urgent = pending.count { it.priority == "urgent" }
            val high = pending.count { it.priority == "high" }

            buildString {
                append("Voce tem ${pending.size} tarefa${if (pending.size > 1) "s" else ""} pendente${if (pending.size > 1) "s" else ""}. ")
                if (urgent > 0) append("$urgent urgente${if (urgent > 1) "s" else ""}. ")
                if (high > 0) append("$high de alta prioridade. ")
                append("As principais sao: ")
                pending.take(3).forEachIndexed { index, task ->
                    append("${index + 1}, ${task.title}. ")
                }
            }
        }
    }

    private fun buildScheduleResponse(): String {
        val today = _schedule.value
        return if (today.isEmpty()) {
            "Voce nao tem compromissos agendados para hoje."
        } else {
            buildString {
                append("Voce tem ${today.size} compromisso${if (today.size > 1) "s" else ""} hoje. ")
                today.take(3).forEach { event ->
                    val time = try {
                        event.startAt.substring(11, 16)
                    } catch (e: Exception) { "" }
                    append("As $time, ${event.title}. ")
                }
            }
        }
    }

    private fun buildFinancialResponse(): String {
        val summary = _financialSummary.value
            ?: return "Nao consegui carregar as informacoes financeiras."

        return buildString {
            append("Resumo financeiro. ")
            if (summary.totalReceivable > 0) {
                append("Voce tem ${formatCurrency(summary.totalReceivable)} a receber. ")
            }
            if (summary.totalPayable > 0) {
                append("E ${formatCurrency(summary.totalPayable)} a pagar. ")
            }
            if (summary.overdueReceivable > 0) {
                append("Atencao: ${formatCurrency(summary.overdueReceivable)} esta vencido para receber. ")
            }
            if (summary.overduePayable > 0) {
                append("E ${formatCurrency(summary.overduePayable)} esta vencido para pagar. ")
            }
            append("Saldo do mes: ${formatCurrency(summary.balance)}.")
        }
    }

    private fun buildMessagesResponse(): String {
        val pending = _dashboard.value?.stats?.pendingResponses ?: 0
        return if (pending == 0) {
            "Voce nao tem mensagens pendentes."
        } else {
            "Voce tem $pending mensagem${if (pending > 1) "ns" else ""} aguardando resposta."
        }
    }

    private fun buildStatusResponse(): String {
        val stats = _dashboard.value?.stats
            ?: return "Nao consegui carregar seu status."

        return buildString {
            append(getTimeGreeting() + "! ")
            append("Aqui esta seu resumo. ")

            if (stats.pendingTasks > 0) {
                append("${stats.pendingTasks} tarefa${if (stats.pendingTasks > 1) "s" else ""} pendente${if (stats.pendingTasks > 1) "s" else ""}. ")
            }

            if (stats.upcomingEvents > 0) {
                append("${stats.upcomingEvents} compromisso${if (stats.upcomingEvents > 1) "s" else ""} proximo${if (stats.upcomingEvents > 1) "s" else ""}. ")
            }

            if (stats.pendingResponses > 0) {
                append("${stats.pendingResponses} mensagem${if (stats.pendingResponses > 1) "ns" else ""} para responder. ")
            }

            if (stats.overdueReceivables > 0) {
                append("${formatCurrency(stats.overdueReceivables)} a cobrar. ")
            }

            append("Faturamento do mes: ${formatCurrency(stats.revenueThisMonth)}.")
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

    private fun formatCurrency(value: Double): String {
        return "R$ ${String.format("%.2f", value).replace(".", ",")}"
    }

    override fun onCleared() {
        super.onCleared()
        voiceService.destroy()
        ttsService.destroy()
    }
}
