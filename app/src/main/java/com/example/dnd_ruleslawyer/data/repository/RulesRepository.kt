package com.example.dnd_ruleslawyer.data.repository

import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSearchFilters
import com.example.dnd_ruleslawyer.domain.model.SearchSecondaryFilter

interface RulesRepository {
    suspend fun searchRules(query: String, filters: RuleSearchFilters = RuleSearchFilters()): List<RuleResource>

    suspend fun searchRules(
        query: String,
        filters: RuleSearchFilters = RuleSearchFilters(),
        secondaryFilter: SearchSecondaryFilter?
    ): List<RuleResource>

    suspend fun availableMonsterChallengeRatings(
        query: String,
        filters: RuleSearchFilters = RuleSearchFilters()
    ): List<Double>

    suspend fun syncOfficialResources(type: ResourceType)

    suspend fun addCustomRule(rule: RuleResource)
    suspend fun addCustomRule(detail: RuleDetail)
    suspend fun editCustomRule(rule: RuleResource): Boolean
    suspend fun editCustomRule(detail: RuleDetail): Boolean
    suspend fun deleteCustomRule(id: String): Boolean

    suspend fun addLocalRule(detail: RuleDetail)
    suspend fun editLocalRule(detail: RuleDetail): Boolean
    suspend fun deleteLocalRule(id: String): Boolean

    suspend fun setFavorite(id: String, isFavorite: Boolean): Boolean
    suspend fun getFavoriteRules(): List<RuleResource>

    suspend fun getRuleResource(id: String): RuleResource?
    suspend fun getRuleDetail(id: String): RuleDetail?
    suspend fun hasRuleResources(type: ResourceType): Boolean
}
