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

    // Dashboard
    @GET("dashboard")
    suspend fun getDashboard(): Response<ApiResponse<DashboardData>>

    // Tasks
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

    @PATCH("tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: String,
        @Body updates: Map<String, Any>
    ): Response<ApiResponse<Task>>

    @PATCH("tasks/{id}/complete")
    suspend fun completeTask(@Path("id") id: String): Response<ApiResponse<Task>>

    // Schedule
    @GET("schedule")
    suspend fun getSchedule(
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null
    ): Response<ApiResponse<List<Schedule>>>

    @GET("schedule/today")
    suspend fun getTodaySchedule(): Response<ApiResponse<List<Schedule>>>

    // Financial
    @GET("financial/summary")
    suspend fun getFinancialSummary(): Response<ApiResponse<FinancialSummary>>

    @GET("financial/entries")
    suspend fun getFinancialEntries(
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<FinancialEntry>>>

    // Deals
    @GET("deals")
    suspend fun getDeals(
        @Query("stage") stage: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Deal>>>

    // Works
    @GET("works")
    suspend fun getWorks(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Work>>>

    // Contacts
    @GET("contacts")
    suspend fun getContacts(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Contact>>>

    @GET("contacts/{id}")
    suspend fun getContact(@Path("id") id: String): Response<ApiResponse<Contact>>

    // Messages
    @GET("messages/conversations")
    suspend fun getConversations(
        @Query("unread_only") unreadOnly: Boolean = false
    ): Response<ApiResponse<List<Conversation>>>

    // Proactive
    @GET("proactive/alerts")
    suspend fun getAlerts(): Response<ApiResponse<List<ProactiveNotification>>>

    @GET("proactive/notifications/pending")
    suspend fun getPendingNotifications(): Response<ApiResponse<List<ProactiveNotification>>>

    // AI Chat (full access to EmisferIA tools)
    @POST("ai/chat")
    suspend fun chat(
        @Body request: ChatRequest
    ): Response<ApiResponse<ChatResponse>>

    // Voice Commands (legacy)
    @POST("proactive/voice/command")
    suspend fun processVoiceCommand(
        @Body request: VoiceCommandRequest
    ): Response<ApiResponse<VoiceCommandResponse>>
}

/**
 * API Client singleton
 */
object EmisferiaApi {

    private const val DEFAULT_TIMEOUT = 30L

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
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
                // Add auth token if available
                // .addHeader("Authorization", "Bearer $token")
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
            println("[EmisferiaAPI] Making API call...")
            val response = call()
            println("[EmisferiaAPI] Response code: ${response.code()}")
            if (response.isSuccessful) {
                val body = response.body()
                println("[EmisferiaAPI] Body success: ${body?.success}, has data: ${body?.data != null}")
                if (body?.data != null) {
                    Result.success(body.data)
                } else {
                    println("[EmisferiaAPI] Empty data, message: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Empty response"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("[EmisferiaAPI] Error response: $errorBody")
                Result.failure(Exception("Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            println("[EmisferiaAPI] Exception: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

/**
 * Repository for accessing EmisferIA data
 */
class EmisferiaRepository {

    private val api = EmisferiaApi.service

    suspend fun getDashboard(): Result<DashboardData> {
        return EmisferiaApi.safeApiCall { api.getDashboard() }
    }

    suspend fun getTasks(status: String? = null, priority: String? = null): Result<List<Task>> {
        return EmisferiaApi.safeApiCall { api.getTasks(status, priority) }
    }

    suspend fun createTask(title: String, priority: String = "medium"): Result<Task> {
        return EmisferiaApi.safeApiCall {
            api.createTask(CreateTaskRequest(title = title, priority = priority))
        }
    }

    suspend fun completeTask(taskId: String): Result<Task> {
        return EmisferiaApi.safeApiCall { api.completeTask(taskId) }
    }

    suspend fun getTodaySchedule(): Result<List<Schedule>> {
        return EmisferiaApi.safeApiCall { api.getTodaySchedule() }
    }

    suspend fun getFinancialSummary(): Result<FinancialSummary> {
        return EmisferiaApi.safeApiCall { api.getFinancialSummary() }
    }

    suspend fun getFinancialEntries(type: String? = null, status: String? = null): Result<List<FinancialEntry>> {
        return EmisferiaApi.safeApiCall { api.getFinancialEntries(type, status) }
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

    suspend fun getConversations(unreadOnly: Boolean = false): Result<List<Conversation>> {
        return EmisferiaApi.safeApiCall { api.getConversations(unreadOnly) }
    }

    suspend fun getAlerts(): Result<List<ProactiveNotification>> {
        return EmisferiaApi.safeApiCall { api.getAlerts() }
    }

    /**
     * Chat with EmisferIA AI - full access to all tools
     * Sends sessionId for conversation context persistence
     */
    suspend fun chat(message: String, sessionId: String?, deviceId: String): Result<ChatResponse> {
        println("[EmisferiaRepo] Chat request: $message (session: ${sessionId?.take(8) ?: "new"})")
        return EmisferiaApi.safeApiCall {
            api.chat(ChatRequest(message = message, sessionId = sessionId, deviceId = deviceId))
        }
    }

    suspend fun processVoiceCommand(command: String): Result<VoiceCommandResponse> {
        return EmisferiaApi.safeApiCall {
            api.processVoiceCommand(VoiceCommandRequest(command = command, context = null))
        }
    }
}
