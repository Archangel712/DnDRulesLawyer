package com.example.dnd_ruleslawyer.testing

import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSection
import com.example.dnd_ruleslawyer.domain.model.RuleSource

fun ruleResource(
    id: String = "custom:rules:test-rule",
    name: String = "Test Rule",
    type: ResourceType = ResourceType.RULE,
    source: RuleSource = RuleSource.CUSTOM,
    description: String = "A useful rule for testing.",
    apiUrl: String? = null,
    isFavorite: Boolean = false,
    createdAtEpochMillis: Long = 10L,
    updatedAtEpochMillis: Long = 20L,
    imageUrl: String? = null
): RuleResource {
    return RuleResource(
        id = id,
        name = name,
        type = type,
        source = source,
        description = description,
        apiUrl = apiUrl,
        isFavorite = isFavorite,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        imageUrl = imageUrl
    )
}

fun ruleDetail(
    resource: RuleResource = ruleResource(),
    sections: List<RuleSection> = listOf(RuleSection("Ruling", "Use the written rule first.", 0)),
    rawJson: String? = null
): RuleDetail {
    return RuleDetail(
        id = resource.id,
        resource = resource,
        sections = sections,
        rawJson = rawJson
    )
}
