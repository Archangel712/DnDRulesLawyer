package com.example.dnd_ruleslawyer.data.remote.mapper

import com.example.dnd_ruleslawyer.data.remote.dto.ApiReferenceDto
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleSource
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteRuleMapperTest {
    private val mapper = RemoteRuleMapper()

    @Test
    fun toDomain_createsStableOfficialResourceIdFromEndpointAndIndex() {
        val resource = mapper.toDomain(
            ApiReferenceDto(index = "fireball", name = "Fireball", url = "/api/spells/fireball"),
            ResourceType.SPELL
        )

        assertEquals("official:spells:fireball", resource.id)
        assertEquals("Fireball", resource.name)
        assertEquals(ResourceType.SPELL, resource.type)
        assertEquals(RuleSource.OFFICIAL, resource.source)
        assertEquals("/api/spells/fireball", resource.apiUrl)
    }
}
