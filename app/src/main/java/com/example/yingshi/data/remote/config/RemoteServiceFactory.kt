package com.example.yingshi.data.remote.config

import com.example.yingshi.data.remote.api.AlbumApi
import com.example.yingshi.data.remote.api.AuthApi
import com.example.yingshi.data.remote.api.CommentApi
import com.example.yingshi.data.remote.api.ChatApi
import com.example.yingshi.data.remote.api.HealthApi
import com.example.yingshi.data.remote.api.LedgerApi
import com.example.yingshi.data.remote.api.LifeConsoleApi
import com.example.yingshi.data.remote.api.MediaApi
import com.example.yingshi.data.remote.api.NotificationApi
import com.example.yingshi.data.remote.api.SmallAlbumApi
import com.example.yingshi.data.remote.api.TrashApi
import com.example.yingshi.data.remote.api.UploadApi
import com.example.yingshi.data.remote.auth.AuthInterceptor
import com.example.yingshi.data.remote.auth.AuthRefreshCoordinator
import com.example.yingshi.data.remote.auth.AuthSessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RemoteServiceFactory {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(AuthSessionManager))
            .authenticator(AuthRefreshCoordinator.createAuthenticator())
            .build()
    }

    private val uploadOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .callTimeout(10, TimeUnit.MINUTES)
            .addInterceptor(AuthInterceptor(AuthSessionManager))
            .authenticator(AuthRefreshCoordinator.createAuthenticator())
            .build()
    }

    private data class ServiceGraph(
        val baseUrl: String,
        val retrofit: Retrofit,
        val uploadRetrofit: Retrofit,
    )

    @Volatile
    private var serviceGraph: ServiceGraph? = null

    fun invalidate() {
        synchronized(this) {
            serviceGraph = null
        }
    }

    fun currentBaseUrl(): String = currentGraph().baseUrl

    private fun currentGraph(): ServiceGraph {
        val expectedBaseUrl = BackendDebugConfig.currentBaseUrl()
        val cachedGraph = serviceGraph
        if (cachedGraph != null && cachedGraph.baseUrl == expectedBaseUrl) {
            return cachedGraph
        }
        return synchronized(this) {
            val latestGraph = serviceGraph
            if (latestGraph != null && latestGraph.baseUrl == expectedBaseUrl) {
                latestGraph
            } else {
                ServiceGraph(
                    baseUrl = expectedBaseUrl,
                    retrofit = Retrofit.Builder()
                        .baseUrl(expectedBaseUrl)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build(),
                    uploadRetrofit = Retrofit.Builder()
                        .baseUrl(expectedBaseUrl)
                        .client(uploadOkHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build(),
                ).also { serviceGraph = it }
            }
        }
    }

    private inline fun <reified T> createService(): T = currentGraph().retrofit.create(T::class.java)

    private inline fun <reified T> createUploadService(): T = currentGraph().uploadRetrofit.create(T::class.java)

    val authApi: AuthApi
        get() = createService()
    val healthApi: HealthApi
        get() = createService()
    val ledgerApi: LedgerApi
        get() = createService()
    val mediaApi: MediaApi
        get() = createService()
    val postApi: SmallAlbumApi
        get() = createService()
    val albumApi: AlbumApi
        get() = createService()
    val commentApi: CommentApi
        get() = createService()
    val chatApi: ChatApi
        get() = createService()
    val notificationApi: NotificationApi
        get() = createService()
    val trashApi: TrashApi
        get() = createService()
    val lifeConsoleApi: LifeConsoleApi
        get() = createService()
    val uploadApi: UploadApi
        get() = createUploadService()

    init {
        AuthRefreshCoordinator.registerAuthApiFactory {
            createService<AuthApi>()
        }
    }
}
