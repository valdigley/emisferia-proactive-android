package com.emisferia.proactive.api

import com.emisferia.proactive.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * EmisferIA API Interface
 */
interface EmisferiaApiService {

    // ==========================================
    // AI CHAT - Main interaction endpoint
    // ==========================================

    @POST("ai/chat")
    suspend fun chat(@Body request: AIChatRequest): Response<ApiResponse<AIChatResponse>>

    // ==========================================
    // DASHBOARD
    // ==========================================

    @GET("dashboard")
    suspend fun getDashboard(): Response<ApiResponse<DashboardData>>

    @GET("dashboard/stats")
    suspend fun getStats(): Response<ApiResponse<DashboardStats>>

    // ==========================================
    // TASKS
    // ==========================================

    @GET("tasks")
    suspend fun getTasks(
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Task>>>

    @GET("tasks/{id}")
    suspend fun getTask(@Path("id") id: String): Response<ApiResponse<Task>>

    @POST("tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): Response<ApiResponse<Task>>

    @PUT("tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: String,
        @Body updates: Map<String, Any>
    ): Response<ApiResponse<Task>>

    @POST("tasks/{id}/complete")
    suspend fun completeTask(@Path("id") id: String): Response<ApiResponse<Task>>

    @POST("tasks/{id}/postpone")
    suspend fun postponeTask(@Path("id") id: String): Response<ApiResponse<Task>>

    // ==========================================
    // SCHEDULE
    // ==========================================

    @GET("schedule")
    suspend fun getSchedule(
        @Query("start") startDate: String? = null,
        @Query("end") endDate: String? = null
    ): Response<ApiResponse<List<Schedule>>>

    @GET("schedule/today")
    suspend fun getTodaySchedule(): Response<ApiResponse<List<Schedule>>>

    @GET("schedule/week")
    suspend fun getWeekSchedule(): Response<ApiResponse<List<Schedule>>>

    @POST("schedule")
    suspend fun createSchedule(@Body request: CreateScheduleRequest): Response<ApiResponse<Schedule>>

    // ==========================================
    // FINANCIAL
    // ==========================================

    @GET("financial/dashboard")
    suspend fun getFinancialDashboard(): Response<ApiResponse<FinancialDashboard>>

    @GET("financial")
    suspend fun getFinancialEntries(
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<FinancialEntry>>>

    @GET("financial/overdue")
    suspend fun getOverdueEntries(): Response<ApiResponse<List<FinancialEntry>>>

    @GET("financial/upcoming")
    suspend fun getUpcomingPayments(): Response<ApiResponse<List<FinancialEntry>>>

    // ==========================================
    // IDEAS
    // ==========================================

    @GET("ideas")
    suspend fun getIdeas(
        @Query("status") status: String? = null,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Idea>>>

    @GET("ideas/high-priority")
    suspend fun getHighPriorityIdeas(): Response<ApiResponse<List<Idea>>>

    @POST("ideas")
    suspend fun createIdea(@Body request: CreateIdeaRequest): Response<ApiResponse<Idea>>

    // ==========================================
    // DEALS
    // ==========================================

    @GET("deals")
    suspend fun getDeals(
        @Query("stage") stage: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Deal>>>

    // ==========================================
    // WORKS
    // ==========================================

    @GET("works")
    suspend fun getWorks(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Work>>>

    // ==========================================
    // CONTACTS
    // ==========================================

    @GET("contacts")
    suspend fun getContacts(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Contact>>>

    @GET("contacts/{id}")
    suspend fun getContact(@Path("id") id: String): Response<ApiResponse<Contact>>

    // ==========================================
    // MESSAGES
    // ==========================================

    @GET("messages")
    suspend fun getMessages(
        @Query("unreadOnly") unreadOnly: Boolean = false,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Message>>>

    // ==========================================
    // PROACTIVE
    // ==========================================

    @GET("proactive/alerts")
    suspend fun getAlerts(): Response<ApiResponse<List<ProactiveAlert>>>

    @GET("proactive/checkin/morning")
    suspend fun getMorningCheckin(): Response<ApiResponse<CheckinData>>

    @GET("proactive/checkin/afternoon")
    suspend fun getAfternoonCheckin(): Response<ApiResponse<CheckinData>>

    @GET("proactive/checkin/evening")
    suspend fun getEveningCheckin(): Response<ApiResponse<CheckinData>>

    @GET("proactive/focus/next-task")
    suspend fun getNextFocusTask(): Response<ApiResponse<FocusTask>>

    @GET("proactive/focus/message")
    suspend fun getFocusMessage(): Response<ApiResponse<Map<String, String>>>

    @GET("proactive/payments/summary")
    suspend fun getPaymentSummary(): Response<ApiResponse<PaymentSummary>>
}

/**
 * API Client singleton
 */
object EmisferiaApi {

    private const val DEFAULT_TIMEOUT = 60L // Increased for AI responses

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("X-Client", "EmisferIA-Android")
                .build()
            chain.proceed(request)
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_URL + "/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: EmisferiaApiService = retrofit.create(EmisferiaApiService::class.java)

    /**
     * Helper to safely execute API calls
     */
    suspend fun <T> safeApiCall(call: suspend () -> Response<ApiResponse<T>>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.data != null) {
                    Result.success(body.data)
                } else if (body?.error != null) {
                    Result.failure(Exception(body.error))
                } else {
                    Result.failure(Exception(body?.message ?: "Empty response"))
                }
            } else {
                Result.failure(Exception("Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Repository for accessing EmisferIA data
 */
class EmisferiaRepository {

    private val api = EmisferiaApi.service

    // Conversation history for AI chat
    private val conversationHistory = mutableListOf<ChatMessage>()

    /**
     * Chat with AI - Main method for all interactions
     * The AI has access to all EmisferIA tools and data
     */
    suspend fun chat(message: String): Result<AIChatResponse> {
        // Add user message to history
        conversationHistory.add(ChatMessage(role = "user", content = message))

        val result = EmisferiaApi.safeApiCall {
            api.chat(AIChatRequest(
                message = message,
                conversationHistory = conversationHistory.takeLast(10) // Keep last 10 messages
            ))
        }

        // Add assistant response to history if successful
        result.onSuccess { response ->
            conversationHistory.add(ChatMessage(role = "assistant", content = response.response))
        }

        return result
    }

    fun clearConversationHistory() {
        conversationHistory.clear()
    }

    suspend fun getDashboard(): Result<DashboardData> {
        return EmisferiaApi.safeApiCall { api.getDashboard() }
    }

    suspend fun getStats(): Result<DashboardStats> {
        return EmisferiaApi.safeApiCall { api.getStats() }
    }

    suspend fun getTasks(status: String? = null, priority: String? = null): Result<List<Task>> {
        return EmisferiaApi.safeApiCall { api.getTasks(status, priority) }
    }

    suspend fun createTask(title: String, priority: String = "medium", category: String = "other"): Result<Task> {
        return EmisferiaApi.safeApiCall {
            api.createTask(CreateTaskRequest(title = title, priority = priority, category = category))
        }
    }

    suspend fun completeTask(taskId: String): Result<Task> {
        return EmisferiaApi.safeApiCall { api.completeTask(taskId) }
    }

    suspend fun getTodaySchedule(): Result<List<Schedule>> {
        return EmisferiaApi.safeApiCall { api.getTodaySchedule() }
    }

    suspend fun getWeekSchedule(): Result<List<Schedule>> {
        return EmisferiaApi.safeApiCall { api.getWeekSchedule() }
    }

    suspend fun getFinancialDashboard(): Result<FinancialDashboard> {
        return EmisferiaApi.safeApiCall { api.getFinancialDashboard() }
    }

    suspend fun getFinancialEntries(type: String? = null, status: String? = null): Result<List<FinancialEntry>> {
        return EmisferiaApi.safeApiCall { api.getFinancialEntries(type, status) }
    }

    suspend fun getOverdueEntries(): Result<List<FinancialEntry>> {
        return EmisferiaApi.safeApiCall { api.getOverdueEntries() }
    }

    suspend fun getIdeas(status: String? = null): Result<List<Idea>> {
        return EmisferiaApi.safeApiCall { api.getIdeas(status) }
    }

    suspend fun createIdea(title: String, description: String? = null, category: String = "other"): Result<Idea> {
        return EmisferiaApi.safeApiCall {
            api.createIdea(CreateIdeaRequest(title = title, description = description, category = category))
        }
    }

    suspend fun getDeals(): Result<List<Deal>> {
        return EmisferiaApi.safeApiCall { api.getDeals() }
    }

    suspend fun getWorks(status: String? = null): Result<List<Work>> {
        return EmisferiaApi.safeApiCall { api.getWorks(status) }
    }

    suspend fun getContacts(search: String? = null): Result<List<Contact>> {
        return EmisferiaApi.safeApiCall { api.getContacts(search) }
    }

    suspend fun getMessages(unreadOnly: Boolean = false): Result<List<Message>> {
        return EmisferiaApi.safeApiCall { api.getMessages(unreadOnly) }
    }

    suspend fun getAlerts(): Result<List<ProactiveAlert>> {
        return EmisferiaApi.safeApiCall { api.getAlerts() }
    }

    suspend fun getMorningCheckin(): Result<CheckinData> {
        return EmisferiaApi.safeApiCall { api.getMorningCheckin() }
    }

    suspend fun getAfternoonCheckin(): Result<CheckinData> {
        return EmisferiaApi.safeApiCall { api.getAfternoonCheckin() }
    }

    suspend fun getEveningCheckin(): Result<CheckinData> {
        return EmisferiaApi.safeApiCall { api.getEveningCheckin() }
    }

    suspend fun getNextFocusTask(): Result<FocusTask> {
        return EmisferiaApi.safeApiCall { api.getNextFocusTask() }
    }

    suspend fun getPaymentSummary(): Result<PaymentSummary> {
        return EmisferiaApi.safeApiCall { api.getPaymentSummary() }
    }
}
