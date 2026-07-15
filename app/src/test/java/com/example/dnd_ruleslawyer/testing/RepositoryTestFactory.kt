package com.example.dnd_ruleslawyer.testing

import com.example.dnd_ruleslawyer.data.local.dao.RuleDetailDao
import com.example.dnd_ruleslawyer.data.local.dao.RuleResourceDao
import com.example.dnd_ruleslawyer.data.local.entity.RuleDetailEntity
import com.example.dnd_ruleslawyer.data.local.entity.RuleResourceEntity
import com.example.dnd_ruleslawyer.data.local.mapper.LocalRuleDetailMapper
import com.example.dnd_ruleslawyer.data.local.mapper.LocalRuleMapper
import com.example.dnd_ruleslawyer.data.remote.api.DndApiService
import com.example.dnd_ruleslawyer.data.remote.dto.ResourceListDto
import com.example.dnd_ruleslawyer.data.remote.mapper.RemoteRuleDetailMapper
import com.example.dnd_ruleslawyer.data.remote.mapper.RemoteRuleMapper
import com.example.dnd_ruleslawyer.data.repository.DefaultRulesRepository
import com.google.gson.JsonArray
import com.google.gson.JsonObject

fun createRepository(
    api: FakeDndApiService = FakeDndApiService(),
    resourceDao: FakeRuleResourceDao = FakeRuleResourceDao(),
    detailDao: FakeRuleDetailDao = FakeRuleDetailDao(),
    currentTimeMillis: () -> Long = { 1000L }
): DefaultRulesRepository {
    return DefaultRulesRepository(
        api = api,
        dao = resourceDao,
        localMapper = LocalRuleMapper(),
        remoteMapper = RemoteRuleMapper(),
        detailDao = detailDao,
        detailMapper = LocalRuleDetailMapper(),
        remoteDetailMapper = RemoteRuleDetailMapper(),
        currentTimeMillis = currentTimeMillis
    )
}

class FakeDndApiService(
    private val resourcesByEndpoint: Map<String, ResourceListDto> = emptyMap(),
    private val detailsByEndpointAndIndex: Map<Pair<String, String>, JsonObject> = emptyMap(),
    private val classLevelsByIndex: Map<String, JsonArray> = emptyMap(),
    private val subclassLevelsByIndex: Map<String, JsonArray> = emptyMap()
) : DndApiService {
    val detailRequests = mutableListOf<Pair<String, String>>()

    override suspend fun getResources(endpoint: String): ResourceListDto {
        return resourcesByEndpoint.getValue(endpoint)
    }

    override suspend fun getResourceDetail(endpoint: String, index: String): JsonObject {
        detailRequests += endpoint to index
        return detailsByEndpointAndIndex.getValue(endpoint to index)
    }

    override suspend fun getClassLevels(index: String, subclass: String?): JsonArray {
        return classLevelsByIndex[index] ?: JsonArray()
    }

    override suspend fun getSubclassLevels(index: String): JsonArray {
        return subclassLevelsByIndex[index] ?: JsonArray()
    }
}

class FakeRuleResourceDao : RuleResourceDao {
    private val resources = linkedMapOf<String, RuleResourceEntity>()

    override suspend fun search(query: String): List<RuleResourceEntity> {
        val normalizedQuery = query.trim()
        return resources.values
            .filter { entity ->
                normalizedQuery.isBlank() ||
                    entity.name.contains(normalizedQuery, ignoreCase = true) ||
                    entity.description.contains(normalizedQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<RuleResourceEntity> { it.isFavorite }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
    }

    override suspend fun getFavorites(): List<RuleResourceEntity> {
        return resources.values
            .filter { it.isFavorite }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    override suspend fun getById(id: String): RuleResourceEntity? = resources[id]

    override suspend fun upsert(resource: RuleResourceEntity) {
        resources[resource.id] = resource
    }

    override suspend fun upsertAll(resources: List<RuleResourceEntity>) {
        resources.forEach { upsert(it) }
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean, updatedAtEpochMillis: Long): Int {
        val resource = resources[id] ?: return 0
        resources[id] = resource.copy(
            isFavorite = isFavorite,
            updatedAtEpochMillis = updatedAtEpochMillis
        )
        return 1
    }

    override suspend fun updateSearchMetadata(
        id: String,
        spellLevel: Int?,
        monsterChallengeRating: Double?,
        equipmentGroup: String?
    ): Int {
        val resource = resources[id] ?: return 0
        resources[id] = resource.copy(
            spellLevel = spellLevel,
            monsterChallengeRating = monsterChallengeRating,
            equipmentGroup = equipmentGroup
        )
        return 1
    }

    override suspend fun availableMonsterChallengeRatings(
        query: String,
        types: Set<String>,
        typesSize: Int
    ): List<Double> {
        val normalizedQuery = query.trim()
        return resources.values
            .filter { entity ->
                (normalizedQuery.isBlank() ||
                    entity.name.contains(normalizedQuery, ignoreCase = true) ||
                    entity.description.contains(normalizedQuery, ignoreCase = true)) &&
                    (typesSize == 0 || entity.type in types)
            }
            .mapNotNull { entity -> entity.monsterChallengeRating }
            .distinct()
            .sorted()
    }

    override suspend fun deleteById(id: String): Int {
        return if (resources.remove(id) != null) 1 else 0
    }

    override suspend fun countByType(type: String): Int {
        return resources.values.count { it.type == type }
    }
}

class FakeRuleDetailDao : RuleDetailDao {
    private val details = linkedMapOf<String, RuleDetailEntity>()

    override suspend fun getById(id: String): RuleDetailEntity? = details[id]

    override suspend fun getByResourceId(resourceId: String): RuleDetailEntity? {
        return details.values.firstOrNull { it.resourceId == resourceId }
    }

    override suspend fun upsert(detail: RuleDetailEntity) {
        details[detail.id] = detail
    }

    override suspend fun deleteByResourceId(resourceId: String): Int {
        val idsToRemove = details.values
            .filter { it.resourceId == resourceId }
            .map { it.id }

        idsToRemove.forEach { details.remove(it) }
        return idsToRemove.size
    }
}
