package com.example.dnd_ruleslawyer.data.repository

import com.example.dnd_ruleslawyer.data.remote.dto.ApiReferenceDto
import com.example.dnd_ruleslawyer.data.remote.dto.ResourceListDto
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleSource
import com.example.dnd_ruleslawyer.domain.model.RuleSearchFilters
import com.example.dnd_ruleslawyer.domain.model.SearchSecondaryFilter
import com.example.dnd_ruleslawyer.testing.FakeDndApiService
import com.example.dnd_ruleslawyer.testing.createRepository
import com.example.dnd_ruleslawyer.testing.ruleResource
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRulesRepositorySyncAndDetailsTest {

    @Test
    fun syncOfficialResources_loadsApiResourcesIntoLocalCache() = runBlocking {
        val api = FakeDndApiService(
            resourcesByEndpoint = mapOf(
                "spells" to ResourceListDto(
                    count = 1,
                    results = listOf(ApiReferenceDto("fireball", "Fireball", "/api/spells/fireball"))
                )
            )
        )
        val repository = createRepository(api = api)

        repository.syncOfficialResources(ResourceType.SPELL)
        val saved = repository.searchRules("fireball").single()

        assertEquals("official:spells:fireball", saved.id)
        assertEquals(ResourceType.SPELL, saved.type)
        assertEquals(RuleSource.OFFICIAL, saved.source)
        assertTrue(repository.hasRuleResources(ResourceType.SPELL))
    }

    @Test
    fun syncOfficialResources_storesSearchMetadataForOfficialResources() = runBlocking {
        val detailJson = JsonObject().apply {
            addProperty("name", "Fireball")
            addProperty("level", 3)
        }
        val api = FakeDndApiService(
            resourcesByEndpoint = mapOf(
                "spells" to ResourceListDto(
                    count = 1,
                    results = listOf(ApiReferenceDto("fireball", "Fireball", "/api/spells/fireball"))
                )
            ),
            detailsByEndpointAndIndex = mapOf(("spells" to "fireball") to detailJson)
        )
        val repository = createRepository(api = api)

        repository.syncOfficialResources(ResourceType.SPELL)
        api.detailRequests.clear()

        val results = repository.searchRules(
            query = "",
            filters = RuleSearchFilters(resourceTypes = setOf(ResourceType.SPELL)),
            secondaryFilter = SearchSecondaryFilter.SpellLevel(3)
        )

        assertEquals(listOf("official:spells:fireball"), results.map { it.id })
        assertEquals(emptyList<Pair<String, String>>(), api.detailRequests)
    }

    @Test
    fun getRuleDetail_fetchesRemoteDetailOnceAndThenUsesLocalCache() = runBlocking {
        val detailJson = JsonObject().apply {
            addProperty("name", "Fireball")
            add("desc", JsonArray().apply { add("A bright streak flashes.") })
        }
        val api = FakeDndApiService(
            detailsByEndpointAndIndex = mapOf(("spells" to "fireball") to detailJson)
        )
        val repository = createRepository(api = api)
        repository.addCustomRule(
            ruleResource(
                id = "official:spells:fireball",
                name = "Fireball",
                type = ResourceType.SPELL,
                source = RuleSource.OFFICIAL,
                apiUrl = "/api/spells/fireball"
            )
        )

        val first = repository.getRuleDetail("official:spells:fireball")
        val second = repository.getRuleDetail("official:spells:fireball")

        assertEquals("A bright streak flashes.", first?.sections?.single()?.body)
        assertEquals(first, second)
        assertEquals(listOf("spells" to "fireball"), api.detailRequests)
    }

    @Test
    fun getRuleDetail_enrichesClassDetailsWithLevelsAndFeatureDetails() = runBlocking {
        val classJson = JsonObject().apply {
            addProperty("name", "Wizard")
            add("desc", JsonArray().apply { add("A scholarly magic-user.") })
        }
        val levels = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("level", 1)
                add("features", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("index", "spellcasting")
                        addProperty("url", "/api/features/spellcasting")
                    })
                })
            })
        }
        val featureJson = JsonObject().apply {
            addProperty("name", "Spellcasting")
            add("desc", JsonArray().apply { add("You can cast wizard spells.") })
        }
        val api = FakeDndApiService(
            detailsByEndpointAndIndex = mapOf(
                ("classes" to "wizard") to classJson,
                ("features" to "spellcasting") to featureJson
            ),
            classLevelsByIndex = mapOf("wizard" to levels)
        )
        val repository = createRepository(api = api)
        repository.addCustomRule(
            ruleResource(
                id = "official:classes:wizard",
                name = "Wizard",
                type = ResourceType.CLASS,
                source = RuleSource.OFFICIAL,
                apiUrl = "/api/classes/wizard"
            )
        )

        val detail = repository.getRuleDetail("official:classes:wizard")

        assertTrue(detail?.rawJson.orEmpty().contains("_levels"))
        assertTrue(detail?.rawJson.orEmpty().contains("_features"))
        assertEquals(listOf("classes" to "wizard", "features" to "spellcasting"), api.detailRequests)
    }
}
