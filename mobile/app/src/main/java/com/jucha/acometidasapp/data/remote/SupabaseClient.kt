package com.jucha.acometidasapp.data.remote

import com.jucha.acometidasapp.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SupabaseClient {

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Interceptor que agrega los headers requeridos por Supabase en cada request
    private val authInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .addHeader("apikey", BuildConfig.SUPABASE_KEY)
            .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
        // Agregar Content-Type: application/json si no tiene uno ya definido
        if (original.header("Content-Type") == null) {
            builder.addHeader("Content-Type", "application/json")
        }
        chain.proceed(builder.build())
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("${BuildConfig.SUPABASE_URL}/rest/v1/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
