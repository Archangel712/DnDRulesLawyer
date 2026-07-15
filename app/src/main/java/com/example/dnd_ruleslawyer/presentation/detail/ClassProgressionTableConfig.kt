package com.example.dnd_ruleslawyer.presentation.detail

import com.example.dnd_ruleslawyer.core.json.array
import com.example.dnd_ruleslawyer.core.json.int
import com.example.dnd_ruleslawyer.core.json.obj
import com.example.dnd_ruleslawyer.core.json.referenceNameList
import com.example.dnd_ruleslawyer.core.json.string
import com.example.dnd_ruleslawyer.core.json.stringOrNumber
import com.example.dnd_ruleslawyer.presentation.utils.signedBonus
import com.google.gson.JsonObject

internal data class ClassTableColumn(
    val id: String,
    val labelHtml: String,
    val groupHtml: String? = null,
    val cssClass: String = "number",
    val value: (JsonObject) -> String?
)

internal data class ClassSpecificColumnMetadata(
    val key: String,
    val label: String,
    val type: String
)

internal object ClassProgressionTableConfig {
    fun columnsFor(
        className: String,
        levels: List<JsonObject>,
        customColumns: List<ClassSpecificColumnMetadata> = emptyList()
    ): List<ClassTableColumn> {
        val key = className.lowercase()
        val baseColumns = listOf(levelColumn, proficiencyColumn) +
            (phbColumnsByClass[key] ?: fallbackColumns(levels, customColumns))
        val baseColumnIds = baseColumns.map { column -> column.id }.toSet()

        return baseColumns + classSpecificFallbackColumns(levels, customColumns)
            .filterNot { column -> column.id in baseColumnIds }
    }

    private val levelColumn = ClassTableColumn("level", "Level") {
        it.int("level")?.toOrdinal()
    }

    private val proficiencyColumn = ClassTableColumn("prof_bonus", "Proficiency<br />Bonus") {
        it.int("prof_bonus").signedBonus()
    }

    private val featuresColumn = ClassTableColumn("features", "Features", cssClass = "features") {
        it.referenceNameList("features").joinToString(", ").ifBlank { "-" }
    }

    private val spellSlots1To9 = (1..9).map { spellSlotColumn(it) }
    private val spellSlots1To5 = (1..5).map { spellSlotColumn(it) }

    private val phbColumnsByClass = mapOf(
        "barbarian" to listOf(
            featuresColumn,
            specific("rage_count", "Rages"),
            specific("rage_damage_bonus", "Rage<br />Damage") { it.int("rage_damage_bonus")?.let { value -> "+$value" } }
        ),

        "bard" to listOf(
            featuresColumn,
            spell("cantrips_known", "Cantrips<br />Known"),
            spell("spells_known", "Spells<br />Known")
        ) + spellSlots1To9,

        "cleric" to listOf(
            featuresColumn,
            spell("cantrips_known", "Cantrips<br />Known")
        ) + spellSlots1To9,

        "druid" to listOf(
            featuresColumn,
            spell("cantrips_known", "Cantrips<br />Known")
        ) + spellSlots1To9,

        "fighter" to listOf(featuresColumn),

        "monk" to listOf(
            diceSpecific("martial_arts", "Martial<br />Arts"),
            specific("ki_points", "Ki<br />Points"),
            specific("unarmored_movement", "Unarmored<br />Movement") { it.int("unarmored_movement")?.let { value -> "+$value ft." } },
            featuresColumn
        ),

        "paladin" to listOf(featuresColumn) + spellSlots1To5,

        "ranger" to listOf(
            featuresColumn,
            spell("spells_known", "Spells<br />Known")
        ) + spellSlots1To5,

        "rogue" to listOf(
            diceSpecific("sneak_attack", "Sneak<br />Attack"),
            featuresColumn
        ),

        "sorcerer" to listOf(
            featuresColumn,
            specific("sorcery_points", "Sorcery<br />Points"),
            spell("cantrips_known", "Cantrips<br />Known"),
            spell("spells_known", "Spells<br />Known")
        ) + spellSlots1To9,

        "warlock" to listOf(
            featuresColumn,
            spell("cantrips_known", "Cantrips<br />Known"),
            spell("spells_known", "Spells<br />Known"),
            spellOrSpecific("spell_slots", "Spell<br />Slots"),
            spellOrSpecific("slot_level", "Slot<br />Level"),
            specific("invocations_known", "Invocations<br />Known")
        ),

        "wizard" to listOf(
            featuresColumn,
            spell("cantrips_known", "Cantrips<br />Known")
        ) + spellSlots1To9
    )

    private fun spell(
        key: String,
        labelHtml: String,
        groupHtml: String? = null
    ): ClassTableColumn =
        ClassTableColumn(key, labelHtml, groupHtml, "number spell-column") { level ->
            level.obj("spellcasting")?.stringOrNumber(key).tableValue()
        }

    private fun spellOrSpecific(
        key: String,
        labelHtml: String
    ): ClassTableColumn =
        ClassTableColumn(key, labelHtml, cssClass = "number spell-column") { level ->
            (
                level.obj("spellcasting")?.stringOrNumber(key)
                    ?: level.obj("class_specific")?.stringOrNumber(key)
            ).tableValue()
        }

    private fun spellSlotColumn(level: Int): ClassTableColumn =
        spell(
            key = "spell_slots_level_$level",
            labelHtml = level.toOrdinal(),
            groupHtml = "Spell Slots per Spell Level"
        )

    private fun specific(
        key: String,
        labelHtml: String,
        formatter: (JsonObject) -> String? = { it.stringOrNumber(key) }
    ): ClassTableColumn =
        ClassTableColumn(key, labelHtml, cssClass = "number class-specific-column") { level ->
            level.obj("class_specific")?.let(formatter).tableValue()
        }

    private fun diceSpecific(key: String, labelHtml: String): ClassTableColumn =
        specific(key, labelHtml) { classSpecific ->
            val dice = classSpecific.obj(key) ?: return@specific null
            val count = dice.int("dice_count")
            val value = dice.int("dice_value")

            if (count != null && value != null) "${count}d$value" else null
        }

    private fun fallbackColumns(
        levels: List<JsonObject>,
        customColumns: List<ClassSpecificColumnMetadata> = emptyList()
    ): List<ClassTableColumn> {
        val spellcastingKeys = levels
            .flatMap { it.obj("spellcasting")?.entrySet()?.map { entry -> entry.key } ?: emptyList() }
            .distinct()

        val compactSpellColumns = listOf(
            "cantrips_known" to "Cantrips<br />Known",
            "spells_known" to "Spells<br />Known",
            "spell_slots" to "Spell<br />Slots",
            "slot_level" to "Slot<br />Level"
        )
            .filter { (key) -> key in spellcastingKeys }
            .map { (key, label) -> spell(key, label) }

        val spellSlots = (1..9)
            .map { spellSlotColumn(it) }
            .filter { column -> column.id in spellcastingKeys }

        return listOf(featuresColumn) + compactSpellColumns + spellSlots + classSpecificFallbackColumns(levels, customColumns)
    }

    fun classSpecificFallbackColumns(
        levels: List<JsonObject>,
        customColumns: List<ClassSpecificColumnMetadata> = emptyList()
    ): List<ClassTableColumn> {
        val configuredKeys = customColumns.map { column -> column.key }.toSet()
        val inferredColumns = levels
            .flatMap { level -> level.obj("class_specific")?.entrySet()?.map { entry -> entry.key } ?: emptyList() }
            .distinct()
            .filterNot { key -> key in configuredKeys }
            .map { key -> ClassSpecificColumnMetadata(key, key.toColumnLabel(), "text") }

        return (customColumns + inferredColumns)
            .map { key ->
                classSpecificColumn(key)
            }
    }

    fun classSpecificColumnMetadata(json: JsonObject): List<ClassSpecificColumnMetadata> =
        json.array("_custom_level_columns")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { column ->
                val key = column.string("key")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ClassSpecificColumnMetadata(
                    key = key,
                    label = column.string("label")?.takeIf { it.isNotBlank() } ?: key.toColumnLabel(),
                    type = column.string("type").orEmpty()
                )
            }
            .orEmpty()

    private fun classSpecificColumn(metadata: ClassSpecificColumnMetadata): ClassTableColumn {
        val cssClass = when (metadata.type) {
            "number" -> "number class-specific-column"
            "checkbox" -> "number class-specific-column"
            else -> "class-specific-column"
        }

        return ClassTableColumn(
            id = metadata.key,
            labelHtml = metadata.label.replace(" ", "<br />"),
            cssClass = cssClass
        ) { level ->
            val rawValue = level.obj("class_specific")?.stringOrNumber(metadata.key)
            if (metadata.type == "checkbox") {
                when (rawValue?.lowercase()) {
                    "true", "yes", "1", "checked", "on" -> "Yes"
                    else -> null
                }
            } else {
                rawValue.tableValue()
            }
        }
    }

    private fun String?.tableValue(): String? =
        takeUnless { it.isNullOrBlank() || it == "0" || it == "false" }

    private fun Int.toOrdinal(): String =
        when (this) {
            1 -> "1st"
            2 -> "2nd"
            3 -> "3rd"
            else -> "${this}th"
        }

    private fun String.toColumnLabel(): String =
        split("_", "-")
            .filter { part -> part.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { char -> char.uppercase() } }
}
