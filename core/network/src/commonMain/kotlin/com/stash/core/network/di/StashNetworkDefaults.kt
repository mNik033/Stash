package com.stash.core.network.di

import com.stash.core.network.dns.ResilientDns
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Shared network defaults and client construction logic across Android and Desktop.
 */
object StashNetworkDefaults {

    const val TIMEOUT_SECONDS = 30L

    val REDACTED_HEADERS = listOf(
        "Authorization",
        "Cookie",
        "X-Stash-Edit-Key",
        "X-Stash-Community-Key",
        "X-Stash-Auth",
        "X-Stash-Entitlement",
    )

    fun createOkHttpClient(
        isDebug: Boolean,
        appInterceptors: Set<Interceptor> = emptySet(),
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor(
            object : HttpLoggingInterceptor.Logger {
                override fun log(message: String) {
                    HttpLoggingInterceptor.Logger.DEFAULT.log(LogRedaction.redactSecrets(message))
                }
            },
        ).apply {
            level = if (isDebug) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            REDACTED_HEADERS.forEach(::redactHeader)
        }

        return OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .dns(ResilientDns())
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .apply { appInterceptors.forEach { addInterceptor(it) } }
            .build()
    }
}
