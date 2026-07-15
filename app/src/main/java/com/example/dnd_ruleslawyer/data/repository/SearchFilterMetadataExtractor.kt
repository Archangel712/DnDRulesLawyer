package com.example.dnd_ruleslawyer.data.repository

import com.example.dnd_ruleslawyer.core.json.obj
import com.example.dnd_ruleslawyer.core.json.stringOrNumber
import com.example.dnd_ruleslawyer.domain.model.EquipmentSecondaryGroup
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.toChallengeRatingValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class SearchFilterMetadata(
    val spellLevel: Int? = null,
    val monsterChallengeRating: Double? = null,
    val equipmentGroup: EquipmentSecondaryGroup? = null
)

object SearchFilterMetadataExtractor {
    fun fromRawJson(type: ResourceType, rawJson: String?): SearchFilterMetadata {
        val json = rawJson?.let(::parseJsonObject)
        return fromJson(type, json)
    }

    fun fromJson(type: ResourceType, json: JsonObject?): SearchFilterMetadata {
        return when (type) {
            ResourceType.SPELL -> SearchFilterMetadata(
                spellLevel = json?.get("level")?.takeIf { it.isJsonPrimitive }?.asInt
            )
            ResourceType.MONSTER -> SearchFilterMetadata(
                monsterChallengeRating = json
                    ?.stringOrNumber("challenge_rating")
                    ?.toChallengeRatingValue()
            )
            ResourceType.MAGIC_ITEM -> SearchFilterMetadata(equipmentGroup = EquipmentSecondaryGroup.MAGIC_ITEMS)
            ResourceType.WEAPON_PROPERTY -> SearchFilterMetadata(equipmentGroup = EquipmentSecondaryGroup.WEAPON_PROPERTIES)
            ResourceType.EQUIPMENT_CATEGORY -> SearchFilterMetadata(equipmentGroup = EquipmentSecondaryGroup.CATEGORIES)
            ResourceType.EQUIPMENT -> SearchFilterMetadata(
                equipmentGroup = equipmentGroupForCategory(
                    json?.obj("equipment_category")?.stringOrNumber("name").orEmpty()
                )
            )
            else -> SearchFilterMetadata()
        }
    }

    private fun parseJsonObject(rawJson: String): JsonObject? {
        return runCatching {
            JsonParser.parseString(rawJson).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull()
    }

    private fun equipmentGroupForCategory(categoryName: String): EquipmentSecondaryGroup {
        val normalized = categoryName.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

        return when {
            normalized.contains("weapon") -> EquipmentSecondaryGroup.WEAPONS
            normalized.contains("armor") || normalized.contains("armour") -> EquipmentSecondaryGroup.ARMOR
            normalized.contains("adventuring gear") -> EquipmentSecondaryGroup.ADVENTURING_GEAR
            normalized.contains("tool") -> EquipmentSecondaryGroup.TOOLS
            normalized.contains("mount") || normalized.contains("vehicle") || normalized.contains("tack") -> {
                EquipmentSecondaryGroup.MOUNTS_AND_VEHICLES
            }
            else -> EquipmentSecondaryGroup.OTHER
        }
    }
}
