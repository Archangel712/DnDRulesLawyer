package com.example.dnd_ruleslawyer.domain.model

data class RuleResource(
    val id: String,
    val name: String,
    val type: ResourceType,
    val source: RuleSource,
    val description: String,
    val apiUrl: String?,
    val isFavorite: Boolean = false,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
    val imageUrl: String? = null,
)
