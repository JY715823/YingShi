package com.example.yingshi.data.remote.config

import com.example.yingshi.data.remote.api.CommentApi
import com.example.yingshi.data.remote.api.MediaApi
import com.example.yingshi.data.remote.api.PostApi
import com.example.yingshi.data.remote.api.TrashApi
import com.example.yingshi.data.remote.api.UploadApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RemoteServiceFactory {
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(RemoteConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val mediaApi: MediaApi by lazy { retrofit.create(MediaApi::class.java) }
    val postApi: PostApi by lazy { retrofit.create(PostApi::class.java) }
    val commentApi: CommentApi by lazy { retrofit.create(CommentApi::class.java) }
    val trashApi: TrashApi by lazy { retrofit.create(TrashApi::class.java) }
    val uploadApi: UploadApi by lazy { retrofit.create(UploadApi::class.java) }
}
