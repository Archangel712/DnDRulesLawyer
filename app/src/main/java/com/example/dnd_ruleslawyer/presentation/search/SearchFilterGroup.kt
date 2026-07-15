package com.example.dnd_ruleslawyer.presentation.search

import androidx.annotation.StringRes
import com.example.dnd_ruleslawyer.R
import com.example.dnd_ruleslawyer.domain.model.ResourceType

enum class SearchFilterGroup(@param:StringRes val labelResId: Int) {
    SPELLS(R.string.filter_spells),
    MONSTERS(R.string.filter_monsters),
    CLASSES(R.string.filter_classes),
    EQUIPMENT(R.string.filter_equipment),
    RACES(R.string.filter_races),
    RULES(R.string.filter_rules);

    val resourceTypes: Set<ResourceType>
        get() = when (this) {
            SPELLS -> setOf(ResourceType.SPELL)
            MONSTERS -> setOf(ResourceType.MONSTER)
            CLASSES -> setOf(ResourceType.CLASS, ResourceType.SUBCLASS)
            EQUIPMENT -> setOf(
                ResourceType.MAGIC_ITEM,
                ResourceType.EQUIPMENT,
                ResourceType.EQUIPMENT_CATEGORY,
                ResourceType.WEAPON_PROPERTY
            )
            RACES -> setOf(ResourceType.RACE, ResourceType.SUBRACE)
            RULES -> ResourceType.entries.toSet() - primaryFilterTypes
        }

    companion object {
        private val primaryFilterTypes = setOf(
            ResourceType.SPELL,
            ResourceType.MONSTER,
            ResourceType.CLASS,
            ResourceType.SUBCLASS,
            ResourceType.MAGIC_ITEM,
            ResourceType.EQUIPMENT,
            ResourceType.EQUIPMENT_CATEGORY,
            ResourceType.WEAPON_PROPERTY,
            ResourceType.RACE,
            ResourceType.SUBRACE
        )

        fun resourceTypesFor(groups: Set<SearchFilterGroup>): Set<ResourceType> {
            return groups.flatMap { group -> group.resourceTypes }.toSet()
        }
    }
}
