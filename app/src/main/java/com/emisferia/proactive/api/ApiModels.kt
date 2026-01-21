package com.emisferia.proactive.api

import com.google.gson.annotations.SerializedName

/**
 * API Response wrapper
 */
data class ApiResponse<T>(
    val data: T?,
    val message: String?,
    val success: Boolean = true
)

/**
 * Dashboard data
 */
data class DashboardData(
    val stats: DashboardStats,
    @SerializedName("urgent_tasks") val urgentTasks: List<Task>,
    @SerializedName("pending_messages") val pendingMessages: List<Conversation>,
    @SerializedName("upcoming_schedule") val upcomingSchedule: List<Schedule>
)

data class DashboardStats(
    @SerializedName("revenue_this_month") val revenueThisMonth: Double,
    @SerializedName("pending_tasks") val pendingTasks: Int,
    @SerializedName("new_leads") val newLeads: Int,
    @SerializedName("productive_hours_today") val productiveHoursToday: Double,
    @SerializedName("pending_responses") val pendingResponses: Int,
    @SerializedName("overdue_receivables") val overdueReceivables: Double,
    @SerializedName("upcoming_events") val upcomingEvents: Int
)

/**
 * Task model
 */
data class Task(
    val id: String,
    val title: String,
    val description: String?,
    val category: String,
    val priority: String, // urgent, high, medium, low
    val status: String, // pending, in_progress, waiting, done, cancelled
    @SerializedName("dueDate") val dueDate: String?,
    @SerializedName("dueTime") val dueTime: String?,
    @SerializedName("isMoneyBlocker") val isMoneyBlocker: Boolean,
    @SerializedName("contactId") val contactId: String?,
    val contact: Contact?
)

/**
 * Schedule/Event model
 */
data class Schedule(
    val id: String,
    val title: String,
    val description: String?,
    val type: String, // shoot, meeting, editing, delivery, personal, other
    @SerializedName("startAt") val startAt: String,
    @SerializedName("endAt") val endAt: String?,
    val location: String?,
    @SerializedName("isOnline") val isOnline: Boolean,
    val status: String,
    val contact: Contact?
)

/**
 * Contact model
 */
data class Contact(
    val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val whatsapp: String?
)

/**
 * Conversation model
 */
data class Conversation(
    val id: String,
    @SerializedName("contact_id") val contactId: String,
    val contact: Contact?,
    @SerializedName("last_message") val lastMessage: String?,
    @SerializedName("last_message_at") val lastMessageAt: String?,
    @SerializedName("unread_count") val unreadCount: Int,
    val platform: String // whatsapp, instagram, email
)

/**
 * Financial entry model
 */
data class FinancialEntry(
    val id: String,
    val type: String, // receivable, payable
    val category: String,
    val description: String,
    val amount: Double,
    @SerializedName("paid_amount") val paidAmount: Double,
    @SerializedName("due_date") val dueDate: String,
    val status: String, // pending, paid, overdue, cancelled
    val contact: Contact?
)

/**
 * Financial summary
 */
data class FinancialSummary(
    @SerializedName("total_receivable") val totalReceivable: Double,
    @SerializedName("total_payable") val totalPayable: Double,
    @SerializedName("overdue_receivable") val overdueReceivable: Double,
    @SerializedName("overdue_payable") val overduePayable: Double,
    @SerializedName("received_this_month") val receivedThisMonth: Double,
    @SerializedName("paid_this_month") val paidThisMonth: Double,
    val balance: Double
)

/**
 * Deal/Lead model
 */
data class Deal(
    val id: String,
    val title: String,
    val stage: String, // lead, contact, meeting, proposal, negotiation, won, lost
    val value: Double?,
    val contact: Contact?,
    @SerializedName("next_action") val nextAction: String?,
    @SerializedName("next_action_date") val nextActionDate: String?
)

/**
 * Work/Project model
 */
data class Work(
    val id: String,
    val title: String,
    val status: String, // scheduled, preparing, shooting, editing, review, completed, delivered
    val deadline: String?,
    @SerializedName("total_value") val totalValue: Double,
    @SerializedName("paid_value") val paidValue: Double,
    val contact: Contact?
)

/**
 * Proactive notification from server
 */
data class ProactiveNotification(
    val id: String,
    val type: String, // alert, reminder, info, action_required
    val title: String,
    val message: String,
    val priority: String, // high, medium, low
    val action: NotificationAction?,
    @SerializedName("created_at") val createdAt: String
)

data class NotificationAction(
    val type: String, // open_task, open_deal, call_contact, send_message
    @SerializedName("target_id") val targetId: String?,
    val label: String
)

/**
 * AI Chat request (to /ai/chat endpoint)
 */
data class ChatRequest(
    val message: String,
    val conversationHistory: List<ChatMessage>? = null
)

data class ChatMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

/**
 * AI Chat response
 */
data class ChatResponse(
    val response: String,
    val toolsUsed: List<String>?
)

/**
 * Voice command request (legacy)
 */
data class VoiceCommandRequest(
    val command: String,
    val context: VoiceContext?
)

data class VoiceContext(
    @SerializedName("current_screen") val currentScreen: String?,
    @SerializedName("selected_item_id") val selectedItemId: String?
)

/**
 * Voice command response (legacy)
 */
data class VoiceCommandResponse(
    val success: Boolean,
    @SerializedName("spoken_response") val spokenResponse: String,
    val action: CommandAction?,
    val data: Any?
)

data class CommandAction(
    val type: String, // navigate, create, update, list, confirm
    val target: String?,
    val params: Map<String, Any>?
)

/**
 * Create task request
 */
data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val priority: String = "medium",
    val category: String = "other",
    @SerializedName("dueDate") val dueDate: String? = null,
    @SerializedName("contactId") val contactId: String? = null
)
