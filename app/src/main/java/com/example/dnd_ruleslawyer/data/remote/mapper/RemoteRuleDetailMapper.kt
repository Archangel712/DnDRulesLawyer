package com.example.dnd_ruleslawyer.data.remote.mapper

import com.example.dnd_ruleslawyer.core.json.textList
import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSection
import com.google.gson.Gson
import com.google.gson.JsonObject

class RemoteRuleDetailMapper(
    private val gson: Gson = Gson()
) {
    fun toDomain(resource: RuleResource, json: JsonObject): RuleDetail {
        val sections = mutableListOf<RuleSection>()

        val description = json.textList("desc").joinToString("\n\n")

        if (description.isNotBlank()) {
            sections += RuleSection(
                title = "Description",
                body = description,
                order = 0
            )
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = sections,
            rawJson = gson.toJson(json)
        )
    }
}
