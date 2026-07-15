package com.example.dnd_ruleslawyer.data.repository

import com.example.dnd_ruleslawyer.domain.model.RuleSection
import com.example.dnd_ruleslawyer.domain.model.RuleSource
import com.example.dnd_ruleslawyer.testing.createRepository
import com.example.dnd_ruleslawyer.testing.ruleDetail
import com.example.dnd_ruleslawyer.testing.ruleResource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRulesRepositoryCustomRulesTest {

    @Test
    fun addCustomRule_storesResourceAsCustomAndMakesItSearchable() = runBlocking {
        val repository = createRepository()
        val rule = ruleResource(source = RuleSource.OFFICIAL, name = "Bonus Action Potion")

        repository.addCustomRule(rule)
        val saved = repository.searchRules("potion").single()

        assertEquals(rule.id, saved.id)
        assertEquals(RuleSource.CUSTOM, saved.source)
    }

    @Test
    fun addEditAndDeleteLocalRule_keepsResourceAndDetailConsistent() = runBlocking {
        val repository = createRepository()
        val resource = ruleResource(id = "custom:rules:counterspell", name = "Counterspell Timing")
        val detail = ruleDetail(
            resource = resource,
            sections = listOf(RuleSection("Ruling", "Declare it after identifying the spell.", 0))
        )

        repository.addLocalRule(detail)
        val edited = repository.editLocalRule(
            detail.copy(
                sections = listOf(RuleSection("Ruling", "Declare it before damage is rolled.", 0)),
                resource = resource.copy(description = "Updated ruling.")
            )
        )
        val savedDetail = repository.getRuleDetail(resource.id)
        val savedResource = repository.getRuleResource(resource.id)

        assertTrue(edited)
        assertEquals("Updated ruling.", savedResource?.description)
        assertEquals("Declare it before damage is rolled.", savedDetail?.sections?.single()?.body)

        val deleted = repository.deleteLocalRule(resource.id)

        assertTrue(deleted)
        assertNull(repository.getRuleResource(resource.id))
        assertNull(repository.getRuleDetail(resource.id))
    }

    @Test
    fun favoriteApiUpdatesAndReturnsFavorites() = runBlocking {
        val repository = createRepository()
        val rule = ruleResource(id = "custom:rules:favorite", name = "Favorite Rule")
        repository.addCustomRule(rule)

        assertTrue(repository.setFavorite(rule.id, true))
        assertEquals(listOf(rule.id), repository.getFavoriteRules().map { it.id })
        assertTrue(repository.getFavoriteRules().single().isFavorite)
        assertFalse(repository.setFavorite("missing", true))
    }
}
