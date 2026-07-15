package com.example.dnd_ruleslawyer.data.local.mapper

import com.example.dnd_ruleslawyer.domain.model.RuleSection
import com.example.dnd_ruleslawyer.testing.ruleDetail
import com.example.dnd_ruleslawyer.testing.ruleResource
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRuleDetailMapperTest {
    private val mapper = LocalRuleDetailMapper()

    @Test
    fun mapsDetailToEntityAndBackWithoutLosingSectionsOrRawJson() {
        val resource = ruleResource()
        val detail = ruleDetail(
            resource = resource,
            sections = listOf(
                RuleSection("Overview", "A short summary.", 0),
                RuleSection("Details", "The exact ruling.", 1)
            ),
            rawJson = """{"name":"Test Rule"}"""
        )

        val entity = mapper.toEntity(detail)
        val restored = mapper.toDomain(entity, resource)

        assertEquals(resource.id, entity.resourceId)
        assertEquals(detail.sections, restored.sections)
        assertEquals(detail.rawJson, restored.rawJson)
    }
}
