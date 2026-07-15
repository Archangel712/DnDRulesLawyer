package com.example.dnd_ruleslawyer.domain.model

import com.example.dnd_ruleslawyer.core.json.obj
import com.example.dnd_ruleslawyer.core.json.stringOrNumber
import com.google.gson.JsonObject
import com.google.gson.JsonParser

sealed interface SearchSecondaryFilter {
    data class SpellLevel(val level: Int?) : SearchSecondaryFilter
    data class MonsterChallengeRatingRange(val min: Double, val max: Double) : SearchSecondaryFilter
    data class ResourceTypes(val types: Set<ResourceType>) : SearchSecondaryFilter
    data class EquipmentGroup(val group: EquipmentSecondaryGroup) : SearchSecondaryFilter
}

enum class EquipmentSecondaryGroup {
    WEAPONS,
    ARMOR,
    ADVENTURING_GEAR,
    TOOLS,
    MOUNTS_AND_VEHICLES,
    MAGIC_ITEMS,
    WEAPON_PROPERTIES,
    CATEGORIES,
    OTHER
}

fun SearchSecondaryFilter.matches(resource: RuleResource, detail: RuleDetail?): Boolean {
    return when (this) {
        is SearchSecondaryFilter.SpellLevel -> {
            if (resource.type != ResourceType.SPELL) return false
            val actualLevel = detail.rawJsonObject()?.get("level")?.takeIf { it.isJsonPrimitive }?.asInt
            if (level == null) actualLevel == null || actualLevel !in 0..9 else actualLevel == level
        }
        is SearchSecondaryFilter.MonsterChallengeRatingRange -> {
            if (resource.type != ResourceType.MONSTER) return false
            val challengeRating = detail.challengeRating() ?: return false
            challengeRating in min..max
        }
        is SearchSecondaryFilter.ResourceTypes -> resource.type in types
        is SearchSecondaryFilter.EquipmentGroup -> equipmentGroupMatches(resource, detail, group)
    }
}

fun RuleDetail?.challengeRating(): Double? {
    return rawJsonObject()
        ?.stringOrNumber("challenge_rating")
        ?.toChallengeRatingValue()
}

fun String.toChallengeRatingValue(): Double? {
    val trimmed = trim()
    val fractionParts = trimmed.split("/", limit = 2)

    if (fractionParts.size == 2) {
        val numerator = fractionParts[0].toDoubleOrNull()
        val denominator = fractionParts[1].toDoubleOrNull()
        if (numerator != null && denominator != null && denominator != 0.0) {
            return numerator / denominator
        }
    }

    return trimmed.toDoubleOrNull()
}

private fun RuleDetail?.rawJsonObject(): JsonObject? {
    val rawJson = this?.rawJson ?: return null

    return runCatching {
        JsonParser.parseString(rawJson).takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull()
}

private fun equipmentGroupMatches(
    resource: RuleResource,
    detail: RuleDetail?,
    group: EquipmentSecondaryGroup
): Boolean {
    return when (group) {
        EquipmentSecondaryGroup.MAGIC_ITEMS -> resource.type == ResourceType.MAGIC_ITEM
        EquipmentSecondaryGroup.WEAPON_PROPERTIES -> resource.type == ResourceType.WEAPON_PROPERTY
        EquipmentSecondaryGroup.CATEGORIES -> resource.type == ResourceType.EQUIPMENT_CATEGORY
        EquipmentSecondaryGroup.WEAPONS,
        EquipmentSecondaryGroup.ARMOR,
        EquipmentSecondaryGroup.ADVENTURING_GEAR,
        EquipmentSecondaryGroup.TOOLS,
        EquipmentSecondaryGroup.MOUNTS_AND_VEHICLES,
        EquipmentSecondaryGroup.OTHER -> {
            if (resource.type != ResourceType.EQUIPMENT) return false
            val category = detail.equipmentCategoryName().normalizedCategory()
            val matchedKnownGroup = when {
                category.contains("weapon") -> EquipmentSecondaryGroup.WEAPONS
                category.contains("armor") || category.contains("armour") -> EquipmentSecondaryGroup.ARMOR
                category.contains("adventuring gear") -> EquipmentSecondaryGroup.ADVENTURING_GEAR
                category.contains("tool") -> EquipmentSecondaryGroup.TOOLS
                category.contains("mount") || category.contains("vehicle") || category.contains("tack") -> {
                    EquipmentSecondaryGroup.MOUNTS_AND_VEHICLES
                }
                else -> EquipmentSecondaryGroup.OTHER
            }

            matchedKnownGroup == group
        }
    }
}

private fun RuleDetail?.equipmentCategoryName(): String {
    return rawJsonObject()
        ?.obj("equipment_category")
        ?.stringOrNumber("name")
        .orEmpty()
}

private fun String.normalizedCategory(): String =
    lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
