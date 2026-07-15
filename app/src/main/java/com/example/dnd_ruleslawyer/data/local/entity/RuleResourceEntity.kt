package com.example.dnd_ruleslawyer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rule_resources",
    indices = [
        Index(value = ["name"]),
        Index(value = ["type"]),
        Index(value = ["source"]),
        Index(value = ["isFavorite"]),
        Index(value = ["spellLevel"]),
        Index(value = ["monsterChallengeRating"]),
        Index(value = ["equipmentGroup"])
    ]
)
data class RuleResourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val source: String,
    val description: String,
    val apiUrl: String?,
    val isFavorite: Boolean = false,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
    val imageUrl: String? = null,
    val spellLevel: Int? = null,
    val monsterChallengeRating: Double? = null,
    val equipmentGroup: String? = null
)
