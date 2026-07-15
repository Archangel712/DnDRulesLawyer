package com.example.dnd_ruleslawyer.data.remote.mapper

import com.example.dnd_ruleslawyer.testing.ruleResource
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RemoteRuleDetailMapperTest {
    private val mapper = RemoteRuleDetailMapper()

    @Test
    fun toDomain_extractsDescriptionSectionAndKeepsRawJson() {
        val resource = ruleResource(id = "official:spells:fireball", name = "Fireball")
        val json = JsonParser.parseString(
            """
            {
              "name": "Fireball",
              "desc": ["A bright streak flashes.", "The target takes fire damage."]
            }
            """.trimIndent()
        ).asJsonObject

        val detail = mapper.toDomain(resource, json)

        assertEquals(resource.id, detail.id)
        assertEquals("Description", detail.sections.single().title)
        assertEquals("A bright streak flashes.\n\nThe target takes fire damage.", detail.sections.single().body)
        assertNotNull(detail.rawJson)
    }
}
