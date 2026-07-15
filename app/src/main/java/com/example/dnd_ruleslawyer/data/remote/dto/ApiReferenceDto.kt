package com.example.dnd_ruleslawyer.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ApiReferenceDto(
    val index: String,
    val name: String,
    val url: String,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)
