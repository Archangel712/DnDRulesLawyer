package com.example.dnd_ruleslawyer.data.local.mapper

import com.example.dnd_ruleslawyer.data.local.entity.RuleDetailEntity
import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSection
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LocalRuleDetailMapper(private val gson: Gson = Gson()) {
    fun toEntity(detail: RuleDetail): RuleDetailEntity {
        return RuleDetailEntity(
            id = detail.id,
            resourceId = detail.resource.id,
            sectionsJson = gson.toJson(detail.sections),
            rawJson = detail.rawJson
        )
    }

    fun toDomain(entity: RuleDetailEntity, resource: RuleResource): RuleDetail {
        val sectionType = object : TypeToken<List<RuleSection>>() {}.type
        val sections: List<RuleSection> = gson.fromJson(entity.sectionsJson, sectionType)

        return RuleDetail(
            id = entity.id,
            resource = resource,
            sections = sections,
            rawJson = entity.rawJson
        )
    }
}