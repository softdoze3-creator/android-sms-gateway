package com.smsgateway.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var instance: GatewayApi? = null
    private var currentBaseUrl: String = ""

    fun get(baseUrl: String): GatewayApi {
        // Rebuild if URL changed
        val normalizedUrl = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
        if (instance == null || normalizedUrl != currentBaseUrl) {
            currentBaseUrl = normalizedUrl
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20,    TimeUnit.SECONDS)
                .build()

            instance = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GatewayApi::class.java)
        }
        return instance!!
    }
}
