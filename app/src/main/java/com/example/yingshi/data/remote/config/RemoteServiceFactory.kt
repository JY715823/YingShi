package com.example.yingshi.data.remote.config

import com.example.yingshi.data.remote.api.AlbumApi
import com.example.yingshi.data.remote.api.AuthApi
import com.example.yingshi.data.remote.api.CommentApi
import com.example.yingshi.data.remote.api.ChatApi
import com.example.yingshi.data.remote.api.AppReleaseApi
import com.example.yingshi.feature.chat.data.ChatMediaApi
import com.example.yingshi.data.remote.api.HealthApi
import com.example.yingshi.data.remote.api.LedgerApi
import com.example.yingshi.data.remote.api.LifeConsoleApi
import com.example.yingshi.data.remote.api.MediaApi
import com.example.yingshi.data.remote.api.NotificationApi
import com.example.yingshi.data.remote.api.SmallAlbumApi
import com.example.yingshi.data.remote.api.SyncApi
import com.example.yingshi.data.remote.api.TrashApi
import com.example.yingshi.data.remote.api.UploadApi
import com.example.yingshi.data.remote.auth.AuthInterceptor
import com.example.yingshi.data.remote.auth.AuthRefreshCoordinator
import com.example.yingshi.data.remote.auth.AuthSessionManager
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RemoteServiceFactory {
    // 共享连接池：API 调用与图片请求复用同一池，减少 TLS 握手与连接建立开销。
    // maxIdleConnections=32（默认 5），keepAliveDuration=5min（默认 5min）
    private val sharedConnectionPool: ConnectionPool by lazy {
        ConnectionPool(32, 5, TimeUnit.MINUTES)
    }

    // 共享调度器：放宽 maxRequests/maxRequestsPerHost 以支持高并发图片加载
    private val sharedDispatcher: Dispatcher by lazy {
        Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(AuthSessionManager))
            .authenticator(AuthRefreshCoordinator.createAuthenticator())
            .build()
    }

    // 图片专用 OkHttpClient：
    // - 共享连接池与调度器（与 API 调用复用 TCP/TLS 连接，节省握手时间）
    // - 更长的 readTimeout（图片可能较大，60s vs 25s）
    // - 同样接入 AuthInterceptor + Authenticator，token 过期可自动刷新
    //   (此前 backendMediaImageRequest 手动加 Authorization 头会被 AuthInterceptor
    //    的 header() 覆盖为相同值，无冲突；且额外获得 401 自动重试能力)
    val imageOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(AuthSessionManager))
            .authenticator(AuthRefreshCoordinator.createAuthenticator())
            .build()
    }

    private val uploadOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
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
    val chatMediaApi: ChatMediaApi
        get() = createUploadService()
    val notificationApi: NotificationApi
        get() = createService()
    val trashApi: TrashApi
        get() = createService()
    val lifeConsoleApi: LifeConsoleApi
        get() = createService()
    val syncApi: SyncApi
        get() = createService()
    val uploadApi: UploadApi
        get() = createUploadService()
    val appReleaseApi: AppReleaseApi
        get() = createService()

    init {
        AuthRefreshCoordinator.registerAuthApiFactory {
            createService<AuthApi>()
        }
    }
}
