package com.smsgateway.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// ── Data models ───────────────────────────────────────────────────────────────

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?
)

data class LoginData(
    @SerializedName("device_id")    val deviceId: Int,
    @SerializedName("device_name")  val deviceName: String,
    @SerializedName("polling_interval") val pollingInterval: Int
)

data class PendingSms(
    val id: Int,
    @SerializedName("device_id")  val deviceId: Int,
    val receiver: String,
    val message: String,
    @SerializedName("sim_slot")   val simSlot: Int
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface GatewayApi {

    @POST("device/login")
    @FormUrlEncoded
    suspend fun login(
        @Field("api_key") apiKey: String
    ): Response<ApiResponse<LoginData>>

    @POST("device/heartbeat")
    suspend fun heartbeat(
        @Header("X-Device-Key")  key: String,
        @Query("device_model")   model: String = android.os.Build.MODEL
    ): Response<ApiResponse<Any>>

    @GET("device/pending-sms")
    suspend fun pendingSms(
        @Header("X-Device-Key") key: String
    ): Response<ApiResponse<List<PendingSms>>>

    @POST("device/update-status")
    @FormUrlEncoded
    suspend fun updateStatus(
        @Header("X-Device-Key") key: String,
        @Field("queue_id")      queueId: Int,
        @Field("status")        status: String,   // sent | failed
        @Field("error")         error: String = ""
    ): Response<ApiResponse<Any>>

    @POST("delivery-report")
    @FormUrlEncoded
    suspend fun deliveryReport(
        @Field("queue_id") queueId: Int,
        @Field("status")   status: String   // delivered | failed
    ): Response<ApiResponse<Any>>
}
