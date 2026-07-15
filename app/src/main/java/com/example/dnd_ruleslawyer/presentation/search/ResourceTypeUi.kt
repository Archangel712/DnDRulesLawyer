package com.example.dnd_ruleslawyer.presentation.search

import androidx.annotation.DrawableRes
import com.example.dnd_ruleslawyer.R
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleSource

@get:DrawableRes
val ResourceType.placeholderDrawableRes: Int
    get() = when (this) {
        ResourceType.SPELL,
        ResourceType.MAGIC_SCHOOL -> R.drawable.placeholder_spell

        ResourceType.MONSTER -> R.drawable.placeholder_monster

        ResourceType.CLASS,
        ResourceType.SUBCLASS -> R.drawable.placeholder_class

        ResourceType.RACE,
        ResourceType.SUBRACE,
        ResourceType.TRAIT -> R.drawable.placeholder_race

        ResourceType.MAGIC_ITEM,
        ResourceType.EQUIPMENT,
        ResourceType.EQUIPMENT_CATEGORY,
        ResourceType.WEAPON_PROPERTY -> R.drawable.placeholder_item

        ResourceType.FEAT,
        ResourceType.FEATURE -> R.drawable.placeholder_feat

        ResourceType.CONDITION,
        ResourceType.DAMAGE_TYPE -> R.drawable.placeholder_condition

        ResourceType.BACKGROUND -> R.drawable.placeholder_background

        ResourceType.ABILITY_SCORE,
        ResourceType.ALIGNMENT,
        ResourceType.LANGUAGE,
        ResourceType.PROFICIENCY,
        ResourceType.RULE,
        ResourceType.RULE_SECTION,
        ResourceType.SKILL -> R.drawable.placeholder_rule
    }

fun ResourceType.displayName(): String = name.toTitleLabel()

fun RuleSource.displayName(): String = name.toTitleLabel()

private fun String.toTitleLabel(): String =
    split("_").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { char -> char.uppercase() }
    }
