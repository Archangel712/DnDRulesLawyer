package com.example.dnd_ruleslawyer.data.remote.mapper

import com.example.dnd_ruleslawyer.data.remote.dto.ApiReferenceDto
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSource

class RemoteRuleMapper {
    fun toDomain(dto: ApiReferenceDto, type: ResourceType): RuleResource =
        RuleResource(
            id = "official:${type.endpoint}:${dto.index}",
            name = dto.name,
            type = type,
            source = RuleSource.OFFICIAL,
            description = "",
            apiUrl = dto.url
        )
}