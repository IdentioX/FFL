package ru.ffl24.zakaz.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.ffl24.zakaz.BuildConfig
import ru.ffl24.zakaz.data.auth.AuthSessionManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

object RetrofitProvider {

    fun createDriverApiService(
        baseUrl: String,
        authSessionManager: AuthSessionManager? = null
    ): DriverApiService {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val gson = GsonBuilder().create()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(createOkHttpClient(authSessionManager))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(DriverApiService::class.java)
    }

    private fun createOkHttpClient(authSessionManager: AuthSessionManager?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)

        if (authSessionManager != null) {
            builder.addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                val token = if (path == "/api/auth/login" || path == "/api/auth/drivers") {
                    null
                } else {
                    runBlocking {
                        authSessionManager.getSession().firstOrNull()?.accessToken
                    }
                }

                val authorizedRequest = if (token.isNullOrBlank()) {
                    request
                } else {
                    request.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }

                chain.proceed(authorizedRequest)
            }
        }

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }
}
