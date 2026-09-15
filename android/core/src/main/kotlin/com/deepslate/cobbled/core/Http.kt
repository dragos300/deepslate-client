package com.deepslate.cobbled.core

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val CobbledJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

fun cobbledHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder().header("User-Agent", USER_AGENT)
            if (original.header("Accept") == null) {
                builder.header("Accept", "application/json")
            }
            chain.proceed(builder.build())
        }
        .build()
