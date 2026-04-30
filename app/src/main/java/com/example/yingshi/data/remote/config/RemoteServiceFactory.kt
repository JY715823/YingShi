package com.example.yingshi.data.remote.config

import com.example.yingshi.data.remote.api.AlbumApi
import com.example.yingshi.data.remote.api.AuthApi
import com.example.yingshi.data.remote.api.CommentApi
import com.example.yingshi.data.remote.api.HealthApi
import com.example.yingshi.data.remote.api.MediaApi
import com.example.yingshi.data.remote.api.PostApi
import com.example.yingshi.data.remote.api.TrashApi
import com.example.yingshi.data.remote.api.UploadApi
import com.example.yingshi.data.remote.auth.AuthInterceptor
import com.example.yingshi.data.remote.auth.AuthSessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RemoteServiceFactory {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(AuthSessionManager))
            .build()
    }

    private data class ServiceGraph(
        val baseUrl: String,
        val retrofit: Retrofit,
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
                ).also { serviceGraph = it }
            }
        }
    }

    private inline fun <reified T> createService(): T = currentGraph().retrofit.create(T::class.java)

    val authApi: AuthApi
        get() = createService()
    val healthApi: HealthApi
        get() = createService()
    val mediaApi: MediaApi
        get() = createService()
    val postApi: PostApi
        get() = createService()
    val albumApi: AlbumApi
        get() = createService()
    val commentApi: CommentApi
        get() = createService()
    val trashApi: TrashApi
        get() = createService()
    val uploadApi: UploadApi
        get() = createService()
}
