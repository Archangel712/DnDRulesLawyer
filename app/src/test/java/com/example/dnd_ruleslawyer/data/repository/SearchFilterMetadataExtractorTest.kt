package com.example.dnd_ruleslawyer.data.repository

import com.example.dnd_ruleslawyer.domain.model.EquipmentSecondaryGroup
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFilterMetadataExtractorTest {

    @Test
    fun fromRawJson_extractsSpellLevel() {
        val metadata = SearchFilterMetadataExtractor.fromRawJson(
            ResourceType.SPELL,
            """{ "level": 0 }"""
        )

        assertEquals(0, metadata.spellLevel)
        assertNull(metadata.monsterChallengeRating)
    }

    @Test
    fun fromRawJson_extractsFractionalMonsterChallengeRating() {
        val metadata = SearchFilterMetadataExtractor.fromRawJson(
            ResourceType.MONSTER,
            """{ "challenge_rating": "1/4" }"""
        )

        assertEquals(0.25, metadata.monsterChallengeRating ?: -1.0, 0.0)
    }

    @Test
    fun fromRawJson_extractsEquipmentGroups() {
        assertEquals(
            EquipmentSecondaryGroup.WEAPONS,
            SearchFilterMetadataExtractor.fromRawJson(
                ResourceType.EQUIPMENT,
                """{ "equipment_category": { "name": "Weapon" } }"""
            ).equipmentGroup
        )
        assertEquals(
            EquipmentSecondaryGroup.MAGIC_ITEMS,
            SearchFilterMetadataExtractor.fromRawJson(ResourceType.MAGIC_ITEM, null).equipmentGroup
        )
        assertEquals(
            EquipmentSecondaryGroup.OTHER,
            SearchFilterMetadataExtractor.fromRawJson(
                ResourceType.EQUIPMENT,
                """{ "equipment_category": { "name": "Treasure" } }"""
            ).equipmentGroup
        )
    }
}
