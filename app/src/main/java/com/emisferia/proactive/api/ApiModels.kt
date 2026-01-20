package com.emisferia.proactive.api

import com.google.gson.annotations.SerializedName

/**
 * API Response wrapper
 */
data class ApiResponse<T>(
    val data: T?,
    val message: String?,
    val error: String?,
    val success: Boolean = true
)

/**
 * AI Chat Request - Main endpoint for all interactions
 */
data class AIChatRequest(
    val message: String,
    @SerializedName("conversationHistory") val conversationHistory: List<ChatMessage> = emptyList()
)

data class ChatMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

/**
 * AI Chat Response
 */
data class AIChatResponse(
    val response: String,
    @SerializedName("toolsUsed") val toolsUsed: List<String>?
)

/**
 * Dashboard data
 */
data class DashboardData(
    val stats: DashboardStats?,
    @SerializedName("urgentTasks") val urgentTasks: List<Task>?,
    @SerializedName("pendingMessages") val pendingMessages: List<Message>?,
    @SerializedName("upcomingSchedule") val upcomingSchedule: List<Schedule>?
)

data class DashboardStats(
    @SerializedName("revenueThisMonth") val revenueThisMonth: Double = 0.0,
    @SerializedName("pendingTasks") val pendingTasks: Int = 0,
    @SerializedName("newLeads") val newLeads: Int = 0,
    @SerializedName("productiveHoursToday") val productiveHoursToday: Double = 0.0,
    @SerializedName("pendingResponses") val pendingResponses: Int = 0,
    @SerializedName("overdueReceivables") val overdueReceivables: Double = 0.0,
    @SerializedName("upcomingEvents") val upcomingEvents: Int = 0
)

/**
 * Task model
 */
data class Task(
    val id: String,
    val title: String,
    val description: String?,
    val category: String?,
    val priority: String?, // urgent, high, medium, low
    val status: String?, // pending, in_progress, waiting, done, cancelled
    @SerializedName("dueDate") val dueDate: String?,
    @SerializedName("dueTime") val dueTime: String?,
    @SerializedName("isMoneyBlocker") val isMoneyBlocker: Boolean = false,
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
    val type: String?, // shoot, meeting, editing, delivery, personal, other
    @SerializedName("startAt") val startAt: String,
    @SerializedName("endAt") val endAt: String?,
    val location: String?,
    @SerializedName("isOnline") val isOnline: Boolean = false,
    val status: String?,
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
 * Message model
 */
data class Message(
    val id: String,
    val content: String?,
    @SerializedName("contactId") val contactId: String?,
    val contact: Contact?,
    val direction: String?, // incoming, outgoing
    val platform: String?, // whatsapp, instagram, email
    @SerializedName("createdAt") val createdAt: String?
)

/**
 * Idea model
 */
data class Idea(
    val id: String,
    val title: String,
    val description: String?,
    val category: String?,
    val priority: String?,
    val status: String?,
    @SerializedName("createdAt") val createdAt: String?
)

/**
 * Financial dashboard
 */
data class FinancialDashboard(
    @SerializedName("totalReceivable") val totalReceivable: Double = 0.0,
    @SerializedName("totalPayable") val totalPayable: Double = 0.0,
    @SerializedName("overdueReceivable") val overdueReceivable: Double = 0.0,
    @SerializedName("overduePayable") val overduePayable: Double = 0.0,
    @SerializedName("receivedThisMonth") val receivedThisMonth: Double = 0.0,
    @SerializedName("paidThisMonth") val paidThisMonth: Double = 0.0,
    val balance: Double = 0.0,
    @SerializedName("mercadoPagoBalance") val mercadoPagoBalance: Double = 0.0
)

/**
 * Financial entry
 */
data class FinancialEntry(
    val id: String,
    val type: String, // receivable, payable
    val category: String?,
    val description: String?,
    val amount: Double,
    @SerializedName("paidAmount") val paidAmount: Double = 0.0,
    @SerializedName("dueDate") val dueDate: String?,
    val status: String?, // pending, paid, overdue, cancelled
    val contact: Contact?
)

/**
 * Deal/Lead model
 */
data class Deal(
    val id: String,
    val title: String?,
    val stage: String?, // lead, contact, meeting, proposal, negotiation, won, lost
    val value: Double?,
    val contact: Contact?,
    @SerializedName("nextAction") val nextAction: String?,
    @SerializedName("nextActionDate") val nextActionDate: String?
)

/**
 * Work/Project model
 */
data class Work(
    val id: String,
    val title: String?,
    val status: String?, // scheduled, preparing, shooting, editing, review, completed, delivered
    val deadline: String?,
    @SerializedName("totalValue") val totalValue: Double = 0.0,
    @SerializedName("paidValue") val paidValue: Double = 0.0,
    val contact: Contact?
)

/**
 * Proactive alert from server
 */
data class ProactiveAlert(
    val type: String, // urgent_task, overdue_payment, upcoming_event, new_message, etc
    val title: String,
    val message: String,
    val priority: String, // high, medium, low
    val data: Map<String, Any>?
)

/**
 * Checkin data
 */
data class CheckinData(
    val type: String, // morning, afternoon, evening
    val message: String,
    val summary: CheckinSummary?
)

data class CheckinSummary(
    val tasks: Int = 0,
    val events: Int = 0,
    val messages: Int = 0,
    val urgentItems: Int = 0
)

/**
 * Payment summary
 */
data class PaymentSummary(
    @SerializedName("totalReceived") val totalReceived: Double = 0.0,
    @SerializedName("totalPending") val totalPending: Double = 0.0,
    @SerializedName("totalOverdue") val totalOverdue: Double = 0.0,
    @SerializedName("mercadoPagoBalance") val mercadoPagoBalance: Double = 0.0
)

/**
 * Focus/ADHD helper response
 */
data class FocusTask(
    val task: Task?,
    val reason: String?,
    val estimatedTime: String?
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

/**
 * Create idea request
 */
data class CreateIdeaRequest(
    val title: String,
    val description: String? = null,
    val category: String = "other",
    val priority: String = "medium"
)

/**
 * Create schedule request
 */
data class CreateScheduleRequest(
    val title: String,
    val description: String? = null,
    @SerializedName("startAt") val startAt: String,
    @SerializedName("endAt") val endAt: String? = null,
    val type: String = "other",
    val location: String? = null
)
