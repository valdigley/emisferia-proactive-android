package com.emisferia.proactive.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val errorMessage: String? = null
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
            // Load dashboard
            repository.getDashboard().onSuccess { data ->
                _dashboard.value = data
                _uiState.update { it.copy(isConnected = true) }
            }.onFailure {
                _uiState.update { it.copy(isConnected = false) }
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
     * Process voice command
     */
    private fun processCommand(command: String) {
        // Add user message to history
        addMessage(command, isUser = true)

        _uiState.update { it.copy(currentText = "", isProcessing = true) }

        viewModelScope.launch {
            // First try local command processing
            val localResult = processLocalCommand(command.lowercase())

            if (localResult != null) {
                _uiState.update { it.copy(isProcessing = false) }
                addMessage(localResult, isUser = false)
                speak(localResult)
            } else {
                // Send to server for AI processing
                repository.processVoiceCommand(command).onSuccess { response ->
                    _uiState.update { it.copy(isProcessing = false) }
                    addMessage(response.spokenResponse, isUser = false)
                    speak(response.spokenResponse)

                    // Refresh data if action was taken
                    response.action?.let {
                        if (it.type == "create" || it.type == "update") {
                            loadInitialData()
                        }
                    }

                }.onFailure {
                    _uiState.update { it.copy(isProcessing = false) }
                    val errorMsg = "Desculpe, nao consegui processar. Pode repetir?"
                    addMessage(errorMsg, isUser = false)
                    speak(errorMsg)
                }
            }
        }
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
