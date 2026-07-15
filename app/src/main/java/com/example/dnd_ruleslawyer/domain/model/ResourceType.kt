package com.example.dnd_ruleslawyer.domain.model

enum class ResourceType(val endpoint: String, val syncByDefault: Boolean = true) {
    SPELL("spells"),
    MONSTER("monsters"),
    CONDITION("conditions"),
    CLASS("classes"),
    SUBCLASS("subclasses"),
    RACE("races"),
    SUBRACE("subraces"),
    FEAT("feats"),
    MAGIC_ITEM("magic-items"),
    EQUIPMENT("equipment"),
    BACKGROUND("backgrounds"),
    ABILITY_SCORE("ability-scores"),
    ALIGNMENT("alignments"),
    DAMAGE_TYPE("damage-types"),
    EQUIPMENT_CATEGORY("equipment-categories"),
    FEATURE("features"),
    LANGUAGE("languages"),
    MAGIC_SCHOOL("magic-schools"),
    PROFICIENCY("proficiencies"),
    RULE_SECTION("rule-sections"),
    SKILL("skills"),
    TRAIT("traits"),
    WEAPON_PROPERTY("weapon-properties"),
    RULE("rules", syncByDefault = false);

    companion object {
        fun fromEndpoint(endpoint: String): ResourceType? =
            entries.firstOrNull { it.endpoint == endpoint }
    }
}