package com.example.dnd_ruleslawyer.data.remote.api

import com.example.dnd_ruleslawyer.data.remote.dto.ResourceListDto
import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import com.google.gson.JsonArray
import retrofit2.http.Query

interface DndApiService {
    @GET("{endpoint}")
    suspend fun getResources(
        @Path("endpoint") endpoint: String
    ): ResourceListDto

    @GET("{endpoint}/{index}")
    suspend fun getResourceDetail(
        @Path("endpoint") endpoint: String,
        @Path("index") index: String
    ): JsonObject

    @GET("classes/{index}/levels")
    suspend fun getClassLevels(
        @Path("index") index: String,
        @Query("subclass") subclass: String? = null
    ): JsonArray

    @GET("subclasses/{index}/levels")
    suspend fun getSubclassLevels(
        @Path("index") index: String
    ): JsonArray
}