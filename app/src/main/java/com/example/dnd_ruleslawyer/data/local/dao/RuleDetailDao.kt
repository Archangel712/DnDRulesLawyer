package com.example.dnd_ruleslawyer.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.dnd_ruleslawyer.data.local.entity.RuleDetailEntity

@Dao
interface RuleDetailDao {
    @Query("SELECT * FROM rule_details WHERE id = :id")
    suspend fun getById(id: String): RuleDetailEntity?

    @Query("SELECT * FROM rule_details WHERE resourceId = :resourceId")
    suspend fun getByResourceId(resourceId: String): RuleDetailEntity?

    @Upsert
    suspend fun upsert(detail: RuleDetailEntity)

    @Query("DELETE FROM rule_details WHERE resourceId = :resourceId")
    suspend fun deleteByResourceId(resourceId: String): Int
}
