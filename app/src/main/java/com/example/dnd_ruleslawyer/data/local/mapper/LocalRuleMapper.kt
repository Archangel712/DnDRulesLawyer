package com.example.dnd_ruleslawyer.data.local.mapper

import com.example.dnd_ruleslawyer.data.local.entity.RuleResourceEntity
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSource

class LocalRuleMapper {
    fun toDomain(entity: RuleResourceEntity): RuleResource =
        RuleResource(
            id = entity.id,
            name = entity.name,
            type = ResourceType.valueOf(entity.type),
            source = RuleSource.valueOf(entity.source),
            description = entity.description,
            apiUrl = entity.apiUrl,
            isFavorite = entity.isFavorite,
            createdAtEpochMillis = entity.createdAtEpochMillis,
            updatedAtEpochMillis = entity.updatedAtEpochMillis,
            imageUrl = entity.imageUrl
        )

    fun toEntity(rule: RuleResource): RuleResourceEntity =
        RuleResourceEntity(
            id = rule.id,
            name = rule.name,
            type = rule.type.name,
            source = rule.source.name,
            description = rule.description,
            apiUrl = rule.apiUrl,
            isFavorite = rule.isFavorite,
            createdAtEpochMillis = rule.createdAtEpochMillis,
            updatedAtEpochMillis = rule.updatedAtEpochMillis,
            imageUrl = rule.imageUrl
        )
}
