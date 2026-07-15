package com.example.dnd_ruleslawyer.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.dnd_ruleslawyer.data.local.entity.RuleResourceEntity

@Dao
interface RuleResourceDao {
    @Query("""
        SELECT * FROM rule_resources
        WHERE :query = ''
        OR name LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%'
        ORDER BY isFavorite DESC, name COLLATE NOCASE ASC
    """)
    suspend fun search(query: String): List<RuleResourceEntity>

    @Query("""
        SELECT * FROM rule_resources
        WHERE isFavorite = 1
        ORDER BY name COLLATE NOCASE ASC
    """)
    suspend fun getFavorites(): List<RuleResourceEntity>

    @Query("SELECT * FROM rule_resources WHERE id = :id")
    suspend fun getById(id: String): RuleResourceEntity?

    @Upsert
    suspend fun upsert(resource: RuleResourceEntity)

    @Upsert
    suspend fun upsertAll(resources: List<RuleResourceEntity>)

    @Query("""
        UPDATE rule_resources
        SET isFavorite = :isFavorite,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
    """)
    suspend fun setFavorite(
        id: String,
        isFavorite: Boolean,
        updatedAtEpochMillis: Long
    ): Int

    @Query("""
        UPDATE rule_resources
        SET spellLevel = :spellLevel,
            monsterChallengeRating = :monsterChallengeRating,
            equipmentGroup = :equipmentGroup
        WHERE id = :id
    """)
    suspend fun updateSearchMetadata(
        id: String,
        spellLevel: Int?,
        monsterChallengeRating: Double?,
        equipmentGroup: String?
    ): Int

    @Query("""
        SELECT DISTINCT monsterChallengeRating FROM rule_resources
        WHERE monsterChallengeRating IS NOT NULL
        AND (:query = ''
            OR name LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%')
        AND (:typesSize = 0 OR type IN (:types))
        ORDER BY monsterChallengeRating ASC
    """)
    suspend fun availableMonsterChallengeRatings(
        query: String,
        types: Set<String>,
        typesSize: Int
    ): List<Double>

    @Query("DELETE FROM rule_resources WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT COUNT(*) FROM rule_resources WHERE type = :type")
    suspend fun countByType(type: String): Int
}
