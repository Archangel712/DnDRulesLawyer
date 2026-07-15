package com.example.dnd_ruleslawyer.data.remote.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {
    val api: DndApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DndApiConfig.API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DndApiService::class.java)
    }
}
