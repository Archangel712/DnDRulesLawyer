package com.example.dnd_ruleslawyer.data.local.mapper

import com.example.dnd_ruleslawyer.data.local.entity.RuleResourceEntity
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleSource
import com.example.dnd_ruleslawyer.testing.ruleResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRuleMapperTest {
    private val mapper = LocalRuleMapper()

    @Test
    fun toDomain_mapsStoredEntityToDomainResource() {
        val entity = RuleResourceEntity(
            id = "custom:spells:test",
            name = "Test Spell",
            type = "SPELL",
            source = "CUSTOM",
            description = "Stored spell",
            apiUrl = null,
            isFavorite = true,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
            imageUrl = "https://example.test/spell.png"
        )

        val resource = mapper.toDomain(entity)

        assertEquals(ResourceType.SPELL, resource.type)
        assertEquals(RuleSource.CUSTOM, resource.source)
        assertTrue(resource.isFavorite)
        assertEquals(entity.imageUrl, resource.imageUrl)
    }

    @Test
    fun toEntity_preservesDomainResourceFields() {
        val resource = ruleResource(
            id = "official:monsters:owlbear",
            name = "Owlbear",
            type = ResourceType.MONSTER,
            source = RuleSource.OFFICIAL,
            description = "Large monstrosity",
            apiUrl = "/api/monsters/owlbear",
            isFavorite = true,
            imageUrl = "https://example.test/owlbear.png"
        )

        val entity = mapper.toEntity(resource)

        assertEquals(resource.id, entity.id)
        assertEquals("MONSTER", entity.type)
        assertEquals("OFFICIAL", entity.source)
        assertTrue(entity.isFavorite)
        assertEquals(resource.apiUrl, entity.apiUrl)
    }
}
