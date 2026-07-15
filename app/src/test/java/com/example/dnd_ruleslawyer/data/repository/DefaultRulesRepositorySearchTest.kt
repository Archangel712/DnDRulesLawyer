package com.example.dnd_ruleslawyer.data.repository

import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.example.dnd_ruleslawyer.domain.model.RuleSection
import com.example.dnd_ruleslawyer.domain.model.RuleSearchFilters
import com.example.dnd_ruleslawyer.domain.model.SearchSecondaryFilter
import com.example.dnd_ruleslawyer.testing.FakeDndApiService
import com.example.dnd_ruleslawyer.testing.createRepository
import com.example.dnd_ruleslawyer.testing.ruleResource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DefaultRulesRepositorySearchTest {

    @Test
    fun searchRules_findsResourcesByNameAndDescription() = runBlocking {
        val repository = createRepository()
        repository.addCustomRule(ruleResource(name = "Fireball", description = "A bright streak flashes."))
        repository.addCustomRule(ruleResource(id = "custom:rules:cover", name = "Cover", description = "Walls block attacks."))

        val results = repository.searchRules("bright")

        assertEquals(listOf("Fireball"), results.map { it.name })
    }

    @Test
    fun searchRules_appliesTypeSourceAndFavoriteFilters() = runBlocking {
        val repository = createRepository()
        val spell = ruleResource(id = "custom:spells:fire", name = "Fire Spark", type = ResourceType.SPELL)
        val rule = ruleResource(id = "custom:rules:fire", name = "Fire Cover", type = ResourceType.RULE)
        repository.addCustomRule(spell)
        repository.addCustomRule(rule)
        repository.setFavorite(rule.id, true)

        val results = repository.searchRules(
            query = "fire",
            filters = RuleSearchFilters(
                resourceTypes = setOf(ResourceType.RULE),
                favoritesOnly = true
            )
        )

        assertEquals(listOf(rule.id), results.map { it.id })
    }

    @Test
    fun searchRules_prefersWholePhraseMatchesOverLooseSubstringMatches() = runBlocking {
        val repository = createRepository()
        val iceKnife = ruleResource(id = "custom:spells:ice-knife", name = "Ice Knife", type = ResourceType.SPELL)
        val ice = ruleResource(id = "custom:damage-types:ice", name = "Ice", type = ResourceType.DAMAGE_TYPE)
        val artificer = ruleResource(id = "custom:classes:artificer", name = "Artificer", type = ResourceType.CLASS)
        repository.addCustomRule(iceKnife)
        repository.addCustomRule(ice)
        repository.addCustomRule(artificer)

        val results = repository.searchRules("ice")

        assertEquals(setOf(iceKnife.id, ice.id), results.map { it.id }.toSet())
        assertFalse(results.any { it.id == artificer.id })
    }

    @Test
    fun searchRules_supportsResourceTypeQueriesAndSmallTypos() = runBlocking {
        val repository = createRepository()
        val wizard = ruleResource(id = "custom:classes:wizard", name = "Wizard", type = ResourceType.CLASS)
        val fighter = ruleResource(id = "custom:classes:fighter", name = "Fighter", type = ResourceType.CLASS)
        val fireball = ruleResource(id = "custom:spells:fireball", name = "Fireball", type = ResourceType.SPELL)
        repository.addCustomRule(wizard)
        repository.addCustomRule(fighter)
        repository.addCustomRule(fireball)

        assertEquals(setOf(wizard.id, fighter.id), repository.searchRules("class").map { it.id }.toSet())
        assertEquals(listOf(wizard.id), repository.searchRules("witard").map { it.id })
    }

    @Test
    fun searchRules_appliesSecondaryFiltersUsingDetails() = runBlocking {
        val repository = createRepository()
        val fireball = ruleResource(id = "custom:spells:fireball", name = "Fireball", type = ResourceType.SPELL)
        val fireBolt = ruleResource(id = "custom:spells:fire-bolt", name = "Fire Bolt", type = ResourceType.SPELL)

        repository.addLocalRule(fireball.detail("""{ "level": 3 }"""))
        repository.addLocalRule(fireBolt.detail("""{ "level": 0 }"""))

        val results = repository.searchRules(
            query = "fire",
            filters = RuleSearchFilters(resourceTypes = setOf(ResourceType.SPELL)),
            secondaryFilter = SearchSecondaryFilter.SpellLevel(0)
        )

        assertEquals(listOf(fireBolt.id), results.map { it.id })
    }

    @Test
    fun searchRules_appliesSecondaryFiltersWithoutFetchingDetailsDuringSearch() = runBlocking {
        val api = FakeDndApiService()
        val repository = createRepository(api = api)
        val fireball = ruleResource(id = "custom:spells:fireball", name = "Fireball", type = ResourceType.SPELL)
        val fireBolt = ruleResource(id = "custom:spells:fire-bolt", name = "Fire Bolt", type = ResourceType.SPELL)
        repository.addLocalRule(fireball.detail("""{ "level": 3 }"""))
        repository.addLocalRule(fireBolt.detail("""{ "level": 0 }"""))

        val results = repository.searchRules(
            query = "fire",
            filters = RuleSearchFilters(resourceTypes = setOf(ResourceType.SPELL)),
            secondaryFilter = SearchSecondaryFilter.SpellLevel(0)
        )

        assertEquals(listOf(fireBolt.id), results.map { it.id })
        assertEquals(emptyList<Pair<String, String>>(), api.detailRequests)
    }

    @Test
    fun availableMonsterChallengeRatings_returnsSortedDistinctFractionalValues() = runBlocking {
        val repository = createRepository()
        val goblin = ruleResource(id = "custom:monsters:goblin", name = "Goblin", type = ResourceType.MONSTER)
        val dragon = ruleResource(id = "custom:monsters:dragon", name = "Dragon", type = ResourceType.MONSTER)

        repository.addLocalRule(goblin.detail("""{ "challenge_rating": "1/4" }"""))
        repository.addLocalRule(dragon.detail("""{ "challenge_rating": 10 }"""))

        val ratings = repository.availableMonsterChallengeRatings(
            query = "",
            filters = RuleSearchFilters(resourceTypes = setOf(ResourceType.MONSTER))
        )

        assertEquals(listOf(0.25, 10.0), ratings)
    }

    private fun com.example.dnd_ruleslawyer.domain.model.RuleResource.detail(rawJson: String): RuleDetail {
        return RuleDetail(
            id = id,
            resource = this,
            sections = listOf(RuleSection("Description", description, 0)),
            rawJson = rawJson
        )
    }
}
