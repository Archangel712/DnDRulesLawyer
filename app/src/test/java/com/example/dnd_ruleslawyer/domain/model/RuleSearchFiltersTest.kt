package com.example.dnd_ruleslawyer.domain.model

import com.example.dnd_ruleslawyer.testing.ruleResource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSearchFiltersTest {

    @Test
    fun matches_returnsTrueWhenNoFiltersAreActive() {
        val resource = ruleResource(type = ResourceType.SPELL, source = RuleSource.OFFICIAL)

        assertTrue(RuleSearchFilters().matches(resource))
    }

    @Test
    fun matches_requiresSelectedTypeSourceAndFavoriteStatus() {
        val favoriteSpell = ruleResource(
            type = ResourceType.SPELL,
            source = RuleSource.CUSTOM,
            isFavorite = true
        )
        val filters = RuleSearchFilters(
            resourceTypes = setOf(ResourceType.SPELL),
            sources = setOf(RuleSource.CUSTOM),
            favoritesOnly = true
        )

        assertTrue(filters.matches(favoriteSpell))
        assertFalse(filters.matches(favoriteSpell.copy(type = ResourceType.MONSTER)))
        assertFalse(filters.matches(favoriteSpell.copy(source = RuleSource.OFFICIAL)))
        assertFalse(filters.matches(favoriteSpell.copy(isFavorite = false)))
    }
}
