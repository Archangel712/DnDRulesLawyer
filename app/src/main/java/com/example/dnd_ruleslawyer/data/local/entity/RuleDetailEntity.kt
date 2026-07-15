package com.example.dnd_ruleslawyer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rule_details",
    foreignKeys = [
        ForeignKey(
            entity = RuleResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["resourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["resourceId"], unique = true)
    ]
)
data class RuleDetailEntity(
    @PrimaryKey val id: String,
    val resourceId: String,
    val sectionsJson: String,
    val rawJson: String?
)
