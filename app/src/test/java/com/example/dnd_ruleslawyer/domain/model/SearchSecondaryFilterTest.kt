package com.example.dnd_ruleslawyer.domain.model

import com.example.dnd_ruleslawyer.testing.ruleResource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSecondaryFilterTest {

    @Test
    fun spellLevel_matchesCantripsSpecificLevelsAndOther() {
        val spell = ruleResource(type = ResourceType.SPELL)

        assertTrue(SearchSecondaryFilter.SpellLevel(0).matches(spell, spell.detail("""{ "level": 0 }""")))
        assertTrue(SearchSecondaryFilter.SpellLevel(3).matches(spell, spell.detail("""{ "level": 3 }""")))
        assertTrue(SearchSecondaryFilter.SpellLevel(null).matches(spell, spell.detail("""{ "level": 12 }""")))
        assertTrue(SearchSecondaryFilter.SpellLevel(null).matches(spell, spell.detail("""{ "name": "Mystery Spell" }""")))
        assertFalse(SearchSecondaryFilter.SpellLevel(4).matches(spell, spell.detail("""{ "level": 3 }""")))
    }

    @Test
    fun monsterChallengeRatingRange_supportsFractionalValues() {
        val monster = ruleResource(type = ResourceType.MONSTER)
        val filter = SearchSecondaryFilter.MonsterChallengeRatingRange(0.125, 0.5)

        assertTrue(filter.matches(monster, monster.detail("""{ "challenge_rating": "1/4" }""")))
        assertFalse(filter.matches(monster, monster.detail("""{ "challenge_rating": 2 }""")))
    }

    @Test
    fun resourceTypes_matchClassSubclassAndRaceSubraceFilters() {
        assertTrue(
            SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.CLASS))
                .matches(ruleResource(type = ResourceType.CLASS), null)
        )
        assertFalse(
            SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.CLASS))
                .matches(ruleResource(type = ResourceType.SUBCLASS), null)
        )
        assertTrue(
            SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.SUBRACE))
                .matches(ruleResource(type = ResourceType.SUBRACE), null)
        )
    }

    @Test
    fun equipmentGroup_matchesTypeAndRawCategoryGroups() {
        val sword = ruleResource(type = ResourceType.EQUIPMENT)
        val magicItem = ruleResource(type = ResourceType.MAGIC_ITEM)
        val weaponProperty = ruleResource(type = ResourceType.WEAPON_PROPERTY)

        assertTrue(
            SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.WEAPONS)
                .matches(sword, sword.detail("""{ "equipment_category": { "name": "Weapon" } }"""))
        )
        assertTrue(
            SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.MAGIC_ITEMS)
                .matches(magicItem, null)
        )
        assertTrue(
            SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.WEAPON_PROPERTIES)
                .matches(weaponProperty, null)
        )
        assertTrue(
            SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.OTHER)
                .matches(sword, sword.detail("""{ "equipment_category": { "name": "Treasure" } }"""))
        )
    }

    private fun RuleResource.detail(rawJson: String): RuleDetail {
        return RuleDetail(
            id = id,
            resource = this,
            sections = emptyList(),
            rawJson = rawJson
        )
    }
}
