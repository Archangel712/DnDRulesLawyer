package com.example.dnd_ruleslawyer.data.repository

import com.example.dnd_ruleslawyer.core.json.array
import com.example.dnd_ruleslawyer.core.json.obj
import com.example.dnd_ruleslawyer.core.json.objects
import com.example.dnd_ruleslawyer.core.json.string
import com.example.dnd_ruleslawyer.data.local.dao.RuleDetailDao
import com.example.dnd_ruleslawyer.data.local.dao.RuleResourceDao
import com.example.dnd_ruleslawyer.data.local.entity.RuleResourceEntity
import com.example.dnd_ruleslawyer.data.local.mapper.LocalRuleDetailMapper
import com.example.dnd_ruleslawyer.data.local.mapper.LocalRuleMapper
import com.example.dnd_ruleslawyer.data.remote.api.DndApiService
import com.example.dnd_ruleslawyer.data.remote.mapper.RemoteRuleDetailMapper
import com.example.dnd_ruleslawyer.data.remote.mapper.RemoteRuleMapper
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSearchFilters
import com.example.dnd_ruleslawyer.domain.model.RuleSource
import com.example.dnd_ruleslawyer.domain.model.SearchSecondaryFilter
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultRulesRepository(
    private val api: DndApiService,
    private val dao: RuleResourceDao,
    private val localMapper: LocalRuleMapper,
    private val remoteMapper: RemoteRuleMapper,
    private val detailDao: RuleDetailDao,
    private val detailMapper: LocalRuleDetailMapper,
    private val remoteDetailMapper: RemoteRuleDetailMapper,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) : RulesRepository {

    override suspend fun searchRules(query: String, filters: RuleSearchFilters): List<RuleResource> {
        return searchResourceEntities(query, filters).map { entity -> localMapper.toDomain(entity) }
    }

    override suspend fun searchRules(
        query: String,
        filters: RuleSearchFilters,
        secondaryFilter: SearchSecondaryFilter?
    ): List<RuleResource> {
        return searchResourceEntities(query, filters)
            .filter { entity -> secondaryFilter == null || entity.matchesSecondaryFilter(secondaryFilter) }
            .map { entity -> localMapper.toDomain(entity) }
    }

    override suspend fun availableMonsterChallengeRatings(
        query: String,
        filters: RuleSearchFilters
    ): List<Double> {
        return dao.availableMonsterChallengeRatings(
            query = query.trim(),
            types = filters.resourceTypes.map { type -> type.name }.toSet(),
            typesSize = filters.resourceTypes.size
        )
    }

    private suspend fun searchResourceEntities(
        query: String,
        filters: RuleSearchFilters
    ): List<RuleResourceEntity> {
        val trimmedQuery = query.trim()
        val normalizedQuery = trimmedQuery.normalizedSearchText()

        val broadMatches = broadSearch(trimmedQuery, normalizedQuery, filters)
        val exactNameMatches = broadMatches.filter { resource ->
            resource.name.normalizedSearchText().containsWholeSearchPhrase(normalizedQuery)
        }
        val exactTypeMatches = broadMatches.filter { resource ->
            resource.resourceTypeOrNull()?.searchAliases()?.any { alias -> alias == normalizedQuery } == true
        }

        return exactNameMatches
            .ifEmpty { exactTypeMatches }
            .ifEmpty { broadMatches }
    }

    private suspend fun broadSearch(
        rawQuery: String,
        normalizedQuery: String,
        filters: RuleSearchFilters
    ): List<RuleResourceEntity> {
        val typeMatches = ResourceType.entries.filter { type ->
            type.searchAliases().any { alias -> alias == normalizedQuery }
        }.toSet()

        if (typeMatches.isNotEmpty()) {
            return allFilteredResources(filters)
                .filter { entity -> entity.resourceTypeOrNull() in typeMatches }
        }

        if (normalizedQuery.isBlank()) return allFilteredResources(filters)

        val directCandidates = directSearchCandidates(rawQuery, normalizedQuery, filters)
        val directMatches = withContext(Dispatchers.Default) {
            directCandidates.filter { entity ->
                entity.matchesNormalizedQuery(normalizedQuery)
            }
        }

        return directMatches.ifEmpty {
            withContext(Dispatchers.Default) {
                allFilteredResources(filters).filter { entity ->
                    entity.fuzzilyMatches(normalizedQuery)
                }
            }
        }
    }

    private suspend fun directSearchCandidates(
        rawQuery: String,
        normalizedQuery: String,
        filters: RuleSearchFilters
    ): List<RuleResourceEntity> {
        return listOf(rawQuery.trim(), normalizedQuery)
            .filter { query -> query.isNotBlank() }
            .distinct()
            .flatMap { query -> dao.search(query) }
            .distinctBy { entity -> entity.id }
            .filter { entity -> filters.matches(entity.toFilterResource()) }
    }

    private suspend fun allFilteredResources(filters: RuleSearchFilters): List<RuleResourceEntity> {
        return dao.search("")
            .filter { entity -> filters.matches(entity.toFilterResource()) }
    }

    private fun String.containsWholeSearchPhrase(query: String): Boolean {
        if (query.isBlank()) return true

        val wholePhraseRegex = Regex(
            pattern = "(^|[^\\p{L}\\p{N}])${Regex.escape(query)}($|[^\\p{L}\\p{N}])",
            option = RegexOption.IGNORE_CASE
        )

        return wholePhraseRegex.containsMatchIn(this)
    }

    private fun RuleResourceEntity.matchesNormalizedQuery(query: String): Boolean {
        val searchableText = listOf(name, description)
            .joinToString(" ")
            .normalizedSearchText()

        return searchableText.containsWholeSearchPhrase(query) ||
                searchableText.contains(query) ||
                resourceTypeOrNull()?.searchAliases()?.any { alias -> alias == query } == true
    }

    private fun RuleResourceEntity.fuzzilyMatches(query: String): Boolean {
        val queryTokens = query.split(" ").filter { token -> token.isNotBlank() }
        if (queryTokens.isEmpty()) return true

        val candidateTokens = buildList {
            addAll(name.normalizedSearchText().split(" "))
            resourceTypeOrNull()?.searchAliases()?.forEach { alias -> addAll(alias.split(" ")) }
        }.filter { token -> token.isNotBlank() }

        return queryTokens.all { queryToken ->
            candidateTokens.any { candidateToken ->
                queryToken.isCloseTo(candidateToken)
            }
        }
    }

    private fun String.normalizedSearchText(): String {
        return lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun ResourceType.searchAliases(): Set<String> {
        return setOf(
            name,
            endpoint,
            endpoint.singularEndpoint()
        )
            .map { value -> value.normalizedSearchText() }
            .filter { value -> value.isNotBlank() }
            .toSet()
    }

    private fun String.singularEndpoint(): String {
        return when {
            endsWith("ies") -> dropLast(3) + "y"
            endsWith("ses") -> dropLast(2)
            endsWith("s") -> dropLast(1)
            else -> this
        }
    }

    private fun String.isCloseTo(other: String): Boolean {
        if (this == other) return true
        if (length < 4 || other.length < 4) return false

        val maxDistance = when (maxOf(length, other.length)) {
            in 0..4 -> 1
            in 5..7 -> 2
            else -> 3
        }

        return levenshteinDistanceAtMost(other, maxDistance)
    }

    private fun String.levenshteinDistanceAtMost(other: String, maxDistance: Int): Boolean {
        if (kotlin.math.abs(length - other.length) > maxDistance) return false

        var previous = IntArray(other.length + 1) { index -> index }
        var current = IntArray(other.length + 1)

        for (i in 1..length) {
            current[0] = i
            var rowMinimum = current[0]

            for (j in 1..other.length) {
                val substitutionCost = if (this[i - 1] == other[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + substitutionCost
                )
                rowMinimum = minOf(rowMinimum, current[j])
            }

            if (rowMinimum > maxDistance) return false

            val swap = previous
            previous = current
            current = swap
        }

        return previous[other.length] <= maxDistance
    }

    override suspend fun syncOfficialResources(type: ResourceType) {
        val response = api.getResources(type.endpoint)
        val syncedAt = currentTimeMillis()

        val entities = response.results.map { dto ->
            val rule = remoteMapper.toDomain(dto, type)
            val cachedRule = dao.getById(rule.id)
            val metadata = officialMetadataFor(rule)

            localMapper.toEntity(
                rule.copy(
                    isFavorite = cachedRule?.isFavorite ?: false,
                    createdAtEpochMillis = cachedRule?.createdAtEpochMillis?.takeIf { it > 0L } ?: syncedAt,
                    updatedAtEpochMillis = syncedAt
                )
            ).withSearchMetadata(metadata)
        }

        dao.upsertAll(entities)
    }

    override suspend fun addCustomRule(rule: RuleResource) {
        val customRule = rule.asCustomResource()
        dao.upsert(localMapper.toEntity(customRule))
    }

    override suspend fun addCustomRule(detail: RuleDetail) {
        addLocalRule(
            detail.copy(
                resource = detail.resource.copy(source = RuleSource.CUSTOM)
            )
        )
    }

    override suspend fun editCustomRule(rule: RuleResource): Boolean {
        val existingRule = editableCustomRuleEntity(rule.id) ?: return false

        val editedRule = rule.asCustomResource(
            apiUrl = null,
            isFavorite = existingRule.isFavorite,
            createdAtEpochMillis = existingRule.createdAtEpochMillis,
            updatedAtEpochMillis = currentTimeMillis()
        )

        dao.upsert(localMapper.toEntity(editedRule))
        return true
    }

    override suspend fun editCustomRule(detail: RuleDetail): Boolean {
        val existingRule = editableCustomRuleEntity(detail.resource.id) ?: return false

        val editedResource = detail.resource.asCustomResource(
            apiUrl = null,
            isFavorite = existingRule.isFavorite,
            createdAtEpochMillis = existingRule.createdAtEpochMillis,
            updatedAtEpochMillis = currentTimeMillis()
        )

        val editedDetail = detail.copy(
            id = editedResource.id,
            resource = editedResource
        )

        dao.upsert(localMapper.toEntity(editedResource).withSearchMetadata(detail.searchMetadata()))
        detailDao.upsert(detailMapper.toEntity(editedDetail))
        return true
    }

    override suspend fun deleteCustomRule(id: String): Boolean {
        editableCustomRuleEntity(id) ?: return false

        detailDao.deleteByResourceId(id)
        return dao.deleteById(id) > 0
    }

    override suspend fun addLocalRule(detail: RuleDetail) {
        val now = currentTimeMillis()

        val localResource = detail.resource.copy(
            apiUrl = null,
            createdAtEpochMillis = detail.resource.createdAtEpochMillis.takeIf { it > 0L } ?: now,
            updatedAtEpochMillis = now
        )

        val localDetail = detail.copy(
            id = localResource.id,
            resource = localResource
        )

        dao.upsert(localMapper.toEntity(localResource).withSearchMetadata(localDetail.searchMetadata()))
        detailDao.upsert(detailMapper.toEntity(localDetail))
    }

    override suspend fun editLocalRule(detail: RuleDetail): Boolean {
        val existingRule = editableLocalRuleEntity(detail.resource.id) ?: return false

        val editedResource = detail.resource.copy(
            apiUrl = null,
            isFavorite = existingRule.isFavorite,
            createdAtEpochMillis = existingRule.createdAtEpochMillis,
            updatedAtEpochMillis = currentTimeMillis()
        )

        val editedDetail = detail.copy(id = editedResource.id, resource = editedResource)

        detailDao.upsert(detailMapper.toEntity(editedDetail))
        dao.upsert(localMapper.toEntity(editedResource).withSearchMetadata(editedDetail.searchMetadata()))
        return true
    }

    override suspend fun deleteLocalRule(id: String): Boolean {
        editableLocalRuleEntity(id) ?: return false
        detailDao.deleteByResourceId(id)
        return dao.deleteById(id) > 0
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean): Boolean {
        dao.getById(id) ?: return false

        return dao.setFavorite(
            id = id,
            isFavorite = isFavorite,
            updatedAtEpochMillis = currentTimeMillis()
        ) > 0
    }

    override suspend fun getFavoriteRules(): List<RuleResource> {
        return dao.getFavorites().map { entity -> localMapper.toDomain(entity) }
    }

    override suspend fun getRuleResource(id: String): RuleResource? {
        return dao.getById(id)?.let { localMapper.toDomain(it) }
    }

    override suspend fun getRuleDetail(id: String): RuleDetail? {
        val resource = getRuleResource(id) ?: return null

        detailDao.getByResourceId(resource.id)?.let { cached ->
            return detailMapper.toDomain(cached, resource)
        }

        val apiUrl = resource.apiUrl ?: return null
        val parts = apiUrl.trim('/').split('/')
        val endpoint = parts.getOrNull(parts.size - 2) ?: return null
        val index = parts.lastOrNull() ?: return null

        val json = api.getResourceDetail(endpoint, index)
        val enrichedJson = when (resource.type) {
            ResourceType.CLASS -> enrichClassJson(index, json)
            ResourceType.SUBCLASS -> enrichClassJson(index, json, isSubclass = true)
            ResourceType.RACE -> enrichRaceJson(json)
            ResourceType.SUBRACE -> enrichSubraceJson(json)
            else -> json
        }
        val detail = remoteDetailMapper.toDomain(resource, enrichedJson)

        detailDao.upsert(detailMapper.toEntity(detail))
        dao.updateSearchMetadata(
            id = resource.id,
            metadata = detail.searchMetadata()
        )
        return detail
    }

    override suspend fun hasRuleResources(type: ResourceType): Boolean {
        return dao.countByType(type.name) > 0
    }

    private fun RuleResource.asCustomResource(
        apiUrl: String? = this.apiUrl,
        isFavorite: Boolean = this.isFavorite,
        createdAtEpochMillis: Long = this.createdAtEpochMillis,
        updatedAtEpochMillis: Long = this.updatedAtEpochMillis
    ): RuleResource {
        return copy(
            source = RuleSource.CUSTOM,
            apiUrl = apiUrl,
            isFavorite = isFavorite,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis
        )
    }

    private suspend fun editableCustomRuleEntity(id: String): RuleResourceEntity? {
        return dao.getById(id)?.takeIf { entity -> entity.source == RuleSource.CUSTOM.name }
    }

    private suspend fun editableLocalRuleEntity(id: String): RuleResourceEntity? {
        return dao.getById(id)?.takeIf { entity -> entity.isUserOwned() }
    }

    private suspend fun enrichClassJson(index: String, json: JsonObject, isSubclass: Boolean = false): JsonObject {
        val levels = if (isSubclass) {
            api.getSubclassLevels(index)
        } else {
            api.getClassLevels(index)
        }
        json.add("_levels", levels)
        json.add("_features", loadFeatureDetails(levels))
        return json
    }

    private suspend fun enrichRaceJson(json: JsonObject): JsonObject {
        json.add("_traits", loadReferencedDetails(json, "traits", "traits"))
        return json
    }

    private suspend fun enrichSubraceJson(json: JsonObject): JsonObject {
        json.add("_racial_traits", loadReferencedDetails(json, "racial_traits", "traits"))
        return json
    }

    private suspend fun loadFeatureDetails(levels: JsonArray): JsonArray {
        val featureDetails = JsonArray()

        val featureRefs = levels
            .mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject }
            .flatMap { level ->
                level.get("features")
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.mapNotNull { feature -> feature.takeIf { it.isJsonObject }?.asJsonObject }
                    ?: emptyList()
            }
            .distinctBy { feature -> feature.string("url") ?: feature.string("index") }

        featureRefs.forEach { feature ->
            val index = feature.string("index") ?: return@forEach

            runCatching {
                api.getResourceDetail("features", index)
            }.getOrNull()?.let { featureDetails.add(it) }
        }

        return featureDetails
    }

    private suspend fun loadReferencedDetails(
        json: JsonObject,
        referenceKey: String,
        endpoint: String
    ): JsonArray {
        val details = JsonArray()
        val references = json.array(referenceKey)
            ?.objects()
            ?.distinctBy { reference -> reference.string("url") ?: reference.string("index") }
            ?: emptyList()

        references.forEach { reference ->
            val index = reference.string("index") ?: return@forEach

            runCatching {
                api.getResourceDetail(endpoint, index).also { detail ->
                    detail.add("_subtraits", loadTraitSubtraits(detail))
                }
            }.getOrNull()?.let { detail -> details.add(detail) }
        }

        return details
    }

    private suspend fun loadTraitSubtraits(trait: JsonObject): JsonArray {
        val subtraits = JsonArray()
        val options = trait.obj("trait_specific")
            ?.obj("subtrait_options")
            ?.obj("from")
            ?.array("options")
            ?.objects()
            ?: return subtraits

        options
            .mapNotNull { option -> option.obj("item") }
            .distinctBy { item -> item.string("url") ?: item.string("index") }
            .forEach { item ->
                val index = item.string("index") ?: return@forEach

                runCatching {
                    api.getResourceDetail("traits", index)
                }.getOrNull()?.let { subtrait -> subtraits.add(subtrait) }
            }

        return subtraits
    }

    private fun RuleResourceEntity.isUserOwned(): Boolean =
        source == RuleSource.CUSTOM.name || source == RuleSource.HOMEBREWERY.name

    private suspend fun officialMetadataFor(rule: RuleResource): SearchFilterMetadata {
        val apiUrl = rule.apiUrl ?: return SearchFilterMetadataExtractor.fromJson(rule.type, null)
        val parts = apiUrl.trim('/').split('/')
        val endpoint = parts.getOrNull(parts.size - 2) ?: return SearchFilterMetadataExtractor.fromJson(rule.type, null)
        val index = parts.lastOrNull() ?: return SearchFilterMetadataExtractor.fromJson(rule.type, null)

        return runCatching {
            SearchFilterMetadataExtractor.fromJson(rule.type, api.getResourceDetail(endpoint, index))
        }.getOrDefault(SearchFilterMetadataExtractor.fromJson(rule.type, null))
    }

    private fun RuleDetail.searchMetadata(): SearchFilterMetadata =
        SearchFilterMetadataExtractor.fromRawJson(resource.type, rawJson)

    private fun RuleResourceEntity.withSearchMetadata(metadata: SearchFilterMetadata): RuleResourceEntity {
        return copy(
            spellLevel = metadata.spellLevel,
            monsterChallengeRating = metadata.monsterChallengeRating,
            equipmentGroup = metadata.equipmentGroup?.name
        )
    }

    private suspend fun RuleResourceDao.updateSearchMetadata(
        id: String,
        metadata: SearchFilterMetadata
    ) {
        updateSearchMetadata(
            id = id,
            spellLevel = metadata.spellLevel,
            monsterChallengeRating = metadata.monsterChallengeRating,
            equipmentGroup = metadata.equipmentGroup?.name
        )
    }

    private fun RuleResourceEntity.matchesSecondaryFilter(filter: SearchSecondaryFilter): Boolean {
        return when (filter) {
            is SearchSecondaryFilter.SpellLevel -> {
                resourceTypeOrNull() == ResourceType.SPELL &&
                    if (filter.level == null) spellLevel == null || spellLevel !in 0..9 else spellLevel == filter.level
            }
            is SearchSecondaryFilter.MonsterChallengeRatingRange -> {
                resourceTypeOrNull() == ResourceType.MONSTER &&
                    monsterChallengeRating?.let { rating -> rating in filter.min..filter.max } == true
            }
            is SearchSecondaryFilter.ResourceTypes -> resourceTypeOrNull() in filter.types
            is SearchSecondaryFilter.EquipmentGroup -> equipmentGroup == filter.group.name
        }
    }

    private fun RuleResourceEntity.resourceTypeOrNull(): ResourceType? =
        runCatching { ResourceType.valueOf(type) }.getOrNull()

    private fun RuleResourceEntity.toFilterResource(): RuleResource =
        RuleResource(
            id = id,
            name = name,
            type = resourceTypeOrNull() ?: ResourceType.RULE,
            source = RuleSource.valueOf(source),
            description = description,
            apiUrl = apiUrl,
            isFavorite = isFavorite,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
            imageUrl = imageUrl
        )
}
