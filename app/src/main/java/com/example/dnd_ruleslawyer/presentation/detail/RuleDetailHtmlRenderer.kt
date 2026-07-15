package com.example.dnd_ruleslawyer.presentation.detail

import com.example.dnd_ruleslawyer.core.json.array
import com.example.dnd_ruleslawyer.core.json.arrayStrings
import com.example.dnd_ruleslawyer.core.json.boolean
import com.example.dnd_ruleslawyer.core.json.choiceDescriptions
import com.example.dnd_ruleslawyer.core.json.equipmentQuantityList
import com.example.dnd_ruleslawyer.core.json.int
import com.example.dnd_ruleslawyer.core.json.namedDescriptionList
import com.example.dnd_ruleslawyer.core.json.namedTextList
import com.example.dnd_ruleslawyer.core.json.obj
import com.example.dnd_ruleslawyer.core.json.objects
import com.example.dnd_ruleslawyer.core.json.referenceNameList
import com.example.dnd_ruleslawyer.core.json.spellReferenceList
import com.example.dnd_ruleslawyer.core.json.string
import com.example.dnd_ruleslawyer.core.json.stringOrNumber
import com.example.dnd_ruleslawyer.core.json.textList
import com.example.dnd_ruleslawyer.data.remote.api.DndApiConfig
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.example.dnd_ruleslawyer.presentation.utils.escapeHtml
import com.example.dnd_ruleslawyer.presentation.utils.ordinalSpellLevel
import com.example.dnd_ruleslawyer.presentation.utils.rawJsonObject
import com.example.dnd_ruleslawyer.presentation.utils.tableCellValue
import com.example.dnd_ruleslawyer.presentation.utils.withModifier
import com.google.gson.JsonObject

class RuleDetailHtmlRenderer {
    private data class FeatureEntry(
        val name: String,
        val normalizedName: String,
        val level: Int?,
        val description: List<String>,
        val normalizedDescription: String
    )

    private data class FeatureBlock(
        val name: String,
        val levels: List<Int>,
        val description: List<String>
    )

    private data class SubclassSpellEntry(
        val groupName: String,
        val level: Int?,
        val spellHtml: String
    )

    private fun apiImageUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return "${DndApiConfig.PUBLIC_BASE_URL}${if (path.startsWith("/")) "" else "/"}$path"
    }

    private fun renderImage(imageUrl: String?, alt: String): String {
        val resolvedUrl = apiImageUrl(imageUrl) ?: return ""

        return """
        <img class="resource-image" src="${resolvedUrl.escapeHtml()}" alt="${alt.escapeHtml()}" />
    """.trimIndent()
    }

    fun render(detail: RuleDetail): String {
        val body = when (detail.resource.type) {
            ResourceType.SPELL -> renderSpell(detail)
            ResourceType.MONSTER -> renderMonster(detail)
            ResourceType.CONDITION -> renderCondition(detail)
            ResourceType.RULE -> renderRule(detail)
            ResourceType.RULE_SECTION -> renderRuleSection(detail)
            ResourceType.FEAT -> renderFeat(detail)
            ResourceType.MAGIC_ITEM -> renderMagicItem(detail)
            ResourceType.EQUIPMENT -> renderEquipment(detail)
            ResourceType.BACKGROUND -> renderBackground(detail)
            ResourceType.RACE -> renderRace(detail)
            ResourceType.SUBRACE -> renderSubrace(detail)
            ResourceType.CLASS -> renderClass(detail)
            ResourceType.SUBCLASS -> renderSubclass(detail)
            ResourceType.TRAIT -> renderTrait(detail)
            ResourceType.FEATURE -> renderFeature(detail)
            else -> renderGeneric(detail)
        }

        return renderDocument(body)
    }

    private fun renderSpell(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val level = json.int("level")
        val school = json.obj("school")
        val castingTime = json.string("casting_time")
        val range = json.string("range")
        val duration = json.string("duration")
        val components = json.arrayStrings("components").joinToString(", ")
        val material = json.string("material")
        val ritual = json.boolean("ritual") ?: false
        val concentration = json.boolean("concentration") ?: false
        val attackType = json.string("attack_type")
        val dc = json.obj("dc")
        val saveAbility = dc?.obj("dc_type")?.string("name")
        val saveSuccess = dc?.string("dc_success")
        val damage = json.obj("damage")
        val damageType = damage?.obj("damage_type")
        val damageBySlotLevel = damage?.obj("damage_at_slot_level").slotLevelEntries()
        val healingBySlotLevel = json.obj("heal_at_slot_level").slotLevelEntries()
        val areaOfEffect = json.obj("area_of_effect")
        val area = formatAreaOfEffect(areaOfEffect)
        val classes = json.referenceObjects("classes")
        val subclasses = json.referenceObjects("subclasses")
        val description = json.arrayStrings("desc")
        val higherLevel = json.arrayStrings("higher_level")

        val spellType = spellTypeLineHtml(level, school, ritual)

        return """
            <article class="spell">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">$spellType</p>

                <p class="property"><strong>Casting Time:</strong> ${castingTime.escapeHtml()}</p>
                <p class="property"><strong>Range:</strong> ${range.escapeHtml()}</p>
                <p class="property"><strong>Components:</strong> ${formatComponents(components, material).escapeHtml()}</p>
                <p class="property"><strong>Duration:</strong> ${formatDuration(duration, concentration).escapeHtml()}</p>

                ${renderProperty("Attack/Save", formatAttackSave(attackType, saveAbility, saveSuccess))}
                ${renderHtmlProperty("Damage/Effect", formatDamageEffectHtml(damageType, area))}

                <section class="section">
                    ${description.joinToString("") { "<p>${it.escapeHtml()}</p>" }}
                </section>

                ${renderParagraphSection("At Higher Levels", higherLevel)}
                ${renderSpellScalingTable("Damage at Higher Levels", damageBySlotLevel)}
                ${renderSpellScalingTable("Healing at Higher Levels", healingBySlotLevel)}
                ${renderSpellAccess(classes, subclasses)}
            </article>
        """.trimIndent()
    }

    private fun spellTypeLineHtml(level: Int?, school: JsonObject?, ritual: Boolean): String {
        val schoolHtml = school?.let { RuleDetailLink.linkedReferenceHtml(it, "magic-schools") }.orEmpty()
        val base = if (level == 0) {
            "$schoolHtml cantrip"
        } else {
            "${level.ordinalSpellLevel().escapeHtml()} $schoolHtml"
        }.trim()

        return if (ritual) "$base (ritual)" else base
    }

    private fun formatComponents(components: String, material: String?): String =
        if (material.isNullOrBlank()) {
            components
        } else {
            "$components ($material)"
        }

    private fun formatDuration(duration: String?, concentration: Boolean): String =
        if (concentration && !duration.isNullOrBlank() && !duration.startsWith("Concentration", ignoreCase = true)) {
            "Concentration, $duration"
        } else {
            duration.orEmpty()
        }

    private fun formatAttackSave(attackType: String?, saveAbility: String?, saveSuccess: String?): String? {
        val parts = buildList {
            if (!attackType.isNullOrBlank()) add("${attackType.replaceFirstChar { it.uppercase() }} spell attack")
            if (!saveAbility.isNullOrBlank()) {
                add(
                    if (saveSuccess.isNullOrBlank()) {
                        "$saveAbility saving throw"
                    } else {
                        "$saveAbility saving throw ($saveSuccess on success)"
                    }
                )
            }
        }

        return parts.joinToString("; ").takeIf { it.isNotBlank() }
    }

    private fun formatDamageEffectHtml(damageType: JsonObject?, area: String?): String? {
        val parts = buildList {
            damageType?.let { add(RuleDetailLink.linkedReferenceHtml(it, "damage-types")) }
            area?.takeIf { it.isNotBlank() }?.let { add(it.escapeHtml()) }
        }

        return parts.joinToString("; ").takeIf { it.isNotBlank() }
    }

    private fun formatAreaOfEffect(areaOfEffect: JsonObject?): String? {
        val type = areaOfEffect?.string("type")?.takeIf { it.isNotBlank() }
        val size = areaOfEffect?.int("size")

        return when {
            type != null && size != null -> "$size-foot $type"
            type != null -> type
            size != null -> "$size feet"
            else -> null
        }
    }

    private fun renderParagraphSection(title: String, paragraphs: List<String>): String {
        if (paragraphs.isEmpty()) return ""

        return """
            <section class="section">
                <h2>${title.escapeHtml()}</h2>
                ${paragraphs.joinToString("") { "<p>${it.escapeHtml()}</p>" }}
            </section>
        """.trimIndent()
    }

    private fun renderSpellScalingTable(title: String, entries: List<Pair<Int, String>>): String {
        if (entries.size < 2) return ""

        return """
            <section class="section spell-scaling">
                <h2>${title.escapeHtml()}</h2>
                <table class="spell-scaling-table">
                    <thead>
                        <tr>
                            <th>Slot Level</th>
                            <th>Effect</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${
            entries.joinToString("") { (level, value) ->
                """
                            <tr>
                                <td>${level.ordinalSpellLevel().escapeHtml()}</td>
                                <td>${value.escapeHtml()}</td>
                            </tr>
                """.trimIndent()
            }
        }
                    </tbody>
                </table>
            </section>
        """.trimIndent()
    }

    private fun renderSpellAccess(classes: List<JsonObject>, subclasses: List<JsonObject>): String {
        if (classes.isEmpty() && subclasses.isEmpty()) return ""

        return """
            <section class="section spell-access">
                <h2>Available To</h2>
                ${renderLinkedReferenceProperty("Classes", classes, "classes")}
                ${renderLinkedReferenceProperty("Subclasses", subclasses, "subclasses")}
            </section>
        """.trimIndent()
    }

    private fun JsonObject?.slotLevelEntries(): List<Pair<Int, String>> =
        this
            ?.entrySet()
            ?.mapNotNull { entry ->
                val level = entry.key.toIntOrNull() ?: return@mapNotNull null
                val value = entry.value
                    ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                    ?.asString
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                level to value
            }
            ?.sortedBy { (level) -> level }
            ?: emptyList()

    private fun renderCondition(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val description = json.arrayStrings("desc")

        return """
        <h1>${name.escapeHtml()}</h1>
        <p class="meta">Condition</p>

        <section class="descriptive">
            ${description.joinToString("") { "<p>${it.escapeHtml()}</p>" }}
        </section>
    """.trimIndent()
    }

    private fun renderMonster(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val size = json.string("size")
        val type = json.string("type")
        val alignment = json.string("alignment")

        val armorClass = json.get("armor_class")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.firstOrNull()
            ?.asJsonObject
            ?.int("value")

        val hitPoints = json.int("hit_points")
        val hitDice = json.string("hit_dice")
        val speed = json.obj("speed")
        val image = json.string("image") ?: detail.resource.imageUrl

        val strength = json.int("strength")
        val dexterity = json.int("dexterity")
        val constitution = json.int("constitution")
        val intelligence = json.int("intelligence")
        val wisdom = json.int("wisdom")
        val charisma = json.int("charisma")

        val damageVulnerabilities = json.arrayStrings("damage_vulnerabilities")
        val damageResistances = json.arrayStrings("damage_resistances")
        val damageImmunities = json.arrayStrings("damage_immunities")
        val conditionImmunities = json.referenceObjects("condition_immunities")
        val proficiencies = json.referenceObjects("proficiencies")
        val senses = json.obj("senses")
        val languages = json.string("languages")
        val challengeRating = json.stringOrNumber("challenge_rating")
        val xp = json.int("xp")
        val reactions = json.namedDescriptionList("reactions")
        val legendaryActions = json.namedDescriptionList("legendary_actions")

        val actions = json.namedDescriptionList("actions")
        val traits = json.namedDescriptionList("special_abilities")

        return """
        <article class="monster">
            <h1>${name.escapeHtml()}</h1>
            <p class="monster-type">${listOfNotNull(size, type, alignment).joinToString(", ").escapeHtml()}</p>

            <div class="rule"></div>

            <p class="property"><strong>Armor Class</strong> ${armorClass ?: "Unknown"}</p>
            <p class="property"><strong>Hit Points</strong> ${hitPoints ?: "Unknown"} ${hitDice?.let { "($it)" } ?: ""}</p>
            <p class="property"><strong>Speed</strong> ${speed?.entrySet()?.joinToString(", ") { "${it.key} ${it.value.asString}" }.escapeHtml()}</p>

            <div class="rule"></div>
            
            ${renderImage(image, name)}
            
            <div class="rule"></div>

            <table class="ability-table">
                <tr>
                    <th>STR</th>
                    <th>DEX</th>
                    <th>CON</th>
                    <th>INT</th>
                    <th>WIS</th>
                    <th>CHA</th>
                </tr>
                <tr>
                    <td>${strength.withModifier()}</td>
                    <td>${dexterity.withModifier()}</td>
                    <td>${constitution.withModifier()}</td>
                    <td>${intelligence.withModifier()}</td>
                    <td>${wisdom.withModifier()}</td>
                    <td>${charisma.withModifier()}</td>
                </tr>
            </table>

            <div class="rule"></div>
            ${renderProperty("Damage Vulnerabilities", damageVulnerabilities.joinToString(", "))}
            ${renderProperty("Damage Resistances", damageResistances.joinToString(", "))}
            ${renderProperty("Damage Immunities", damageImmunities.joinToString(", "))}
            ${renderLinkedReferenceProperty("Condition Immunities", conditionImmunities, "conditions")}
            ${renderMonsterProficiencies(proficiencies)}
            ${renderProperty("Senses", senses?.entrySet()?.joinToString(", ") { "${it.key} ${it.value.asString}" })}
            ${renderProperty("Languages", languages)}
            ${renderProperty("Challenge", listOfNotNull(challengeRating, xp?.let { "$it XP" }).joinToString(" "))}
            ${renderNamedDescriptions("Traits", traits)}
            ${renderNamedDescriptions("Actions", actions)}
            ${renderNamedDescriptions("Reactions", reactions)}
            ${renderNamedDescriptions("Legendary Actions", legendaryActions)}
        </article>
    """.trimIndent()
    }

    private fun renderMonsterProficiencies(proficiencies: List<JsonObject>): String {
        val grouped = proficiencies
            .mapNotNull { proficiency -> proficiency.monsterProficiencyEntry() }
            .groupBy({ (group, _) -> group }, { (_, value) -> value })

        return listOf("Saving Throws", "Skills", "Proficiencies")
            .joinToString("") { group ->
                renderProperty(group, grouped[group]?.joinToString(", "))
            }
    }

    private fun JsonObject.monsterProficiencyEntry(): Pair<String, String>? {
        val name = obj("proficiency")?.string("name") ?: string("name") ?: return null
        val value = int("value")
        val valueText = value?.let { if (it >= 0) "+$it" else it.toString() }

        return when {
            name.startsWith("Saving Throw: ") -> {
                "Saving Throws" to listOfNotNull(name.removePrefix("Saving Throw: "), valueText).joinToString(" ")
            }
            name.startsWith("Skill: ") -> {
                "Skills" to listOfNotNull(name.removePrefix("Skill: "), valueText).joinToString(" ")
            }
            else -> "Proficiencies" to listOfNotNull(name, valueText).joinToString(" ")
        }
    }

    private fun renderFeat(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val prerequisites = json.featPrerequisiteDescriptions()
        val description = json.arrayStrings("desc")

        return """
        <h1>${name.escapeHtml()}</h1>
        <p class="meta">Feat</p>
        ${renderListSection("Prerequisites", prerequisites)}
        <section class="section">
            ${description.joinToString("") { "<p>${it.escapeHtml()}</p>" }}
        </section>
    """.trimIndent()
    }

    private fun JsonObject.featPrerequisiteDescriptions(): List<String> =
        array("prerequisites")
            ?.objects()
            ?.mapNotNull { prerequisite ->
                prerequisite.string("desc")?.takeIf { it.isNotBlank() }
                    ?: prerequisite.string("name")?.takeIf { it.isNotBlank() }
                    ?: prerequisite.formatAbilityScorePrerequisite()
                    ?: prerequisite.formatLevelPrerequisite()
                    ?: prerequisite.firstReferenceName(
                        "feature",
                        "spell",
                        "class",
                        "subclass",
                        "race",
                        "proficiency",
                        "ability_score"
                    )
                    ?: prerequisite.string("type")?.takeIf { it.isNotBlank() }?.replace("_", " ")
            }
            ?: emptyList()

    private fun JsonObject.formatAbilityScorePrerequisite(): String? {
        val ability = obj("ability_score")?.string("name") ?: return null
        val minimumScore = int("minimum_score") ?: return ability
        return "$ability $minimumScore"
    }

    private fun JsonObject.formatLevelPrerequisite(): String? {
        val level = int("level")?.toString() ?: string("level") ?: return null
        return "Level $level"
    }

    private fun JsonObject.firstReferenceName(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            obj(name)?.string("name")?.takeIf { it.isNotBlank() }
        }

    private fun renderFeature(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val level = json.int("level")
        val parentClass = json.obj("class")
        val parentSubclass = json.obj("subclass")
        val prerequisites = json.featPrerequisiteDescriptions()
        val description = json.textList("desc")

        return """
            <article class="feature-page">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">${featureMetaHtml(level, parentClass, parentSubclass)}</p>
                ${renderListSection("Prerequisites", prerequisites)}
                <section class="section feature-block">
                    ${description.joinToString("") { "<p>${it.escapeHtml()}</p>" }}
                </section>
            </article>
        """.trimIndent()
    }

    private fun featureMetaHtml(
        level: Int?,
        parentClass: JsonObject?,
        parentSubclass: JsonObject?
    ): String {
        val parts = buildList {
            add("Feature".escapeHtml())
            level?.let { add("Level $it".escapeHtml()) }
            parentClass?.let { classReference ->
                RuleDetailLink.linkedReferenceHtml(classReference, "classes")
                    .takeIf { it.isNotBlank() }
                    ?.let { add(it) }
            }
            parentSubclass?.let { subclassReference ->
                RuleDetailLink.linkedReferenceHtml(subclassReference, "subclasses")
                    .takeIf { it.isNotBlank() }
                    ?.let { add(it) }
            }
        }

        return parts.joinToString(" - ")
    }

    private fun renderMagicItem(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val equipmentCategory = json.obj("equipment_category")?.string("name")
        val rarity = json.obj("rarity")?.string("name")
        val description = json.arrayStrings("desc")

        return """
        <h1>${name.escapeHtml()}</h1>
        <p class="meta">${listOfNotNull(equipmentCategory, rarity).joinToString(", ").escapeHtml()}</p>
        <section class="descriptive">
            ${description.joinToString("") { "<p>${it.escapeHtml()}</p>" }}
        </section>
    """.trimIndent()
    }

    private fun renderEquipment(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val equipmentCategory = json.obj("equipment_category")
        val gearCategory = json.obj("gear_category")?.string("name")
        val weaponCategory = json.string("weapon_category")
        val armorCategory = json.string("armor_category")
        val toolCategory = json.string("tool_category")
        val description = json.textList("desc")
        val properties = json.referenceObjects("properties")

        return """
            <article class="equipment-page">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">${equipmentMetaHtml(equipmentCategory, gearCategory, weaponCategory, armorCategory, toolCategory)}</p>

                <section class="section">
                    ${renderProperty("Cost", formatCost(json.obj("cost")))}
                    ${renderProperty("Weight", json.int("weight")?.let { "$it lb." })}
                    ${renderProperty("Weapon Range", json.string("weapon_range"))}
                    ${renderProperty("Category Range", json.string("category_range"))}
                    ${renderHtmlProperty("Damage", formatEquipmentDamageHtml(json.obj("damage")))}
                    ${renderHtmlProperty("Two-Handed Damage", formatEquipmentDamageHtml(json.obj("two_handed_damage")))}
                    ${renderProperty("Range", formatEquipmentRange(json.obj("range")))}
                    ${renderLinkedReferenceProperty("Properties", properties, "weapon-properties")}
                    ${renderProperty("Armor Class", formatArmorClass(json.obj("armor_class")))}
                    ${renderProperty("Strength Minimum", json.int("str_minimum")?.toString())}
                    ${renderProperty("Stealth", formatStealthDisadvantage(json.boolean("stealth_disadvantage")))}
                </section>

                ${renderListSection("Contents", json.equipmentQuantityList("contents"))}
                ${renderParagraphSection("Description", description)}
            </article>
        """.trimIndent()
    }

    private fun equipmentMetaHtml(
        equipmentCategory: JsonObject?,
        gearCategory: String?,
        weaponCategory: String?,
        armorCategory: String?,
        toolCategory: String?
    ): String {
        val parts = buildList {
            equipmentCategory?.let { category ->
                RuleDetailLink.linkedReferenceHtml(category, "equipment-categories")
                    .takeIf { it.isNotBlank() }
                    ?.let { add(it) }
            }
            gearCategory?.takeIf { it.isNotBlank() }?.let { add(it.escapeHtml()) }
            weaponCategory?.takeIf { it.isNotBlank() }?.let { add(it.escapeHtml()) }
            armorCategory?.takeIf { it.isNotBlank() }?.let { add("${it.escapeHtml()} Armor") }
            toolCategory?.takeIf { it.isNotBlank() }?.let { add(it.escapeHtml()) }
        }

        return parts.joinToString(" - ").ifBlank { "Equipment" }
    }

    private fun formatCost(cost: JsonObject?): String? {
        val quantity = cost?.stringOrNumber("quantity")
        val unit = cost?.string("unit")
        return listOfNotNull(quantity, unit)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }

    private fun formatEquipmentDamageHtml(damage: JsonObject?): String? {
        val dice = damage?.string("damage_dice")
        val type = damage?.obj("damage_type")
        val typeHtml = type?.let { RuleDetailLink.linkedReferenceHtml(it, "damage-types") }

        return listOfNotNull(
            dice?.takeIf { it.isNotBlank() }?.escapeHtml(),
            typeHtml?.takeIf { it.isNotBlank() }
        ).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun formatEquipmentRange(range: JsonObject?): String? {
        val normal = range?.int("normal")
        val long = range?.int("long")

        return when {
            normal != null && long != null -> "$normal/$long ft."
            normal != null -> "$normal ft."
            long != null -> "$long ft."
            else -> null
        }
    }

    private fun formatArmorClass(armorClass: JsonObject?): String? {
        val base = armorClass?.int("base")
        val dexBonus = armorClass?.boolean("dex_bonus") ?: false
        val maxBonus = armorClass?.int("max_bonus")
        val parts = buildList {
            base?.let { add(it.toString()) }
            if (dexBonus) {
                add(
                    if (maxBonus != null) {
                        "+ Dex modifier (max $maxBonus)"
                    } else {
                        "+ Dex modifier"
                    }
                )
            }
        }

        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun formatStealthDisadvantage(value: Boolean?): String? =
        when (value) {
            true -> "Disadvantage"
            false -> null
            null -> null
        }

    private fun renderBackground(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val proficiencies = json.referenceObjects("starting_proficiencies")
        val languageOptions = json.arrayStrings("_language_choice_descriptions")
            .ifEmpty { json.backgroundChoiceOptions("language_options") }
        val startingEquipment = json.equipmentQuantityList("starting_equipment")
        val startingEquipmentOptions = json.choiceDescriptions("starting_equipment_options")

        return """
            <article class="background-page">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">Background</p>

                <section class="section">
                    ${renderLinkedReferenceListSection("Skill Proficiencies", proficiencies, "proficiencies")}
                    ${renderListSection("Languages", languageOptions)}
                    ${renderListSection("Equipment", startingEquipment)}
                    ${renderListSection("Equipment Choices", startingEquipmentOptions)}
                </section>

                ${renderBackgroundFeature(json.obj("feature"))}
                ${renderBackgroundChoiceSection("Personality Traits", json.obj("personality_traits"))}
                ${renderBackgroundChoiceSection("Ideals", json.obj("ideals"))}
                ${renderBackgroundChoiceSection("Bonds", json.obj("bonds"))}
                ${renderBackgroundChoiceSection("Flaws", json.obj("flaws"))}
            </article>
        """.trimIndent()
    }

    private fun renderBackgroundFeature(feature: JsonObject?): String {
        val name = feature?.string("name")?.takeIf { it.isNotBlank() }
        val description = feature?.textList("desc").orEmpty()
        if (name == null && description.isEmpty()) return ""

        return """
            <section class="section">
                <h2>${listOfNotNull("Feature", name).joinToString(": ").escapeHtml()}</h2>
                ${description.joinToString("") { "<p>${it.escapeHtml()}</p>" }}
            </section>
        """.trimIndent()
    }

    private fun renderBackgroundChoiceSection(title: String, choice: JsonObject?): String {
        val options = choice.backgroundChoiceOptions()
        if (options.isEmpty()) return ""

        return """
            <section class="section">
                <h2>${title.escapeHtml()}</h2>
                ${choice.backgroundChoiceSummary()?.let { "<p class=\"meta\">${it.escapeHtml()}</p>" }.orEmpty()}
                <ul>
                    ${options.joinToString("") { "<li>${it.escapeHtml()}</li>" }}
                </ul>
            </section>
        """.trimIndent()
    }

    private fun JsonObject?.backgroundChoiceOptions(name: String): List<String> =
        this?.obj(name).backgroundChoiceOptions()

    private fun JsonObject?.backgroundChoiceOptions(): List<String> =
        this
            ?.obj("from")
            ?.array("options")
            ?.objects()
            ?.mapNotNull { option ->
                option.string("string")
                    ?: option.string("desc")
                    ?: option.obj("item")?.string("name")
                    ?: option.obj("choice")?.string("desc")
            }
            ?: emptyList()

    private fun JsonObject?.backgroundChoiceSummary(): String? {
        val choice = this ?: return null
        val choose = choice.int("choose") ?: return null
        val type = choice.string("type")?.replace("_", " ").orEmpty()
        return "Choose $choose $type.".takeIf { it.isNotBlank() }
    }

    private fun renderRuleSection(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val parentRule = json.obj("rule")
        val description = json.textList("desc").ifEmpty { detail.sections.map { it.body } }
        val subsections = json.referenceObjects("subsections")
        val linkedSubsections = subsections.filter { subsection -> subsection.textList("desc").isEmpty() }
        val embeddedSubsections = subsections.filter { subsection -> subsection.textList("desc").isNotEmpty() }

        return """
            <article class="rule-page rule-section-page">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">${ruleSectionMetaHtml(parentRule)}</p>
                ${renderParagraphSection("Description", description)}
                ${renderLinkedReferenceListSection("Subsections", linkedSubsections, "rule-sections")}
                ${renderEmbeddedRuleSubsections("Subsections", embeddedSubsections)}
            </article>
        """.trimIndent()
    }

    private fun renderRule(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val description = json.textList("desc").ifEmpty {
            detail.sections
                .filter { section -> section.title.equals("Description", ignoreCase = true) }
                .map { section -> section.body }
        }
        val subsections = json.referenceObjects("subsections")
        val linkedSubsections = subsections.filter { subsection -> subsection.textList("desc").isEmpty() }
        val embeddedSubsections = subsections.filter { subsection -> subsection.textList("desc").isNotEmpty() }

        return """
            <article class="rule-page">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">Rule</p>
                ${renderParagraphSection("Description", description)}
                ${renderLinkedReferenceListSection("Subsections", linkedSubsections, "rule-sections")}
                ${renderEmbeddedRuleSubsections("Rule Sections", embeddedSubsections)}
            </article>
        """.trimIndent()
    }

    private fun ruleSectionMetaHtml(parentRule: JsonObject?): String {
        val parentHtml = parentRule?.let { RuleDetailLink.linkedReferenceHtml(it, "rules") }.orEmpty()
        return listOf("Rule Section".escapeHtml(), parentHtml)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
    }

    private fun renderEmbeddedRuleSubsections(title: String, subsections: List<JsonObject>): String {
        if (subsections.isEmpty()) return ""

        return """
            <section class="section rule-subsections">
                <h2>${title.escapeHtml()}</h2>
                ${
            subsections.joinToString("") { subsection ->
                val subsectionName = subsection.string("name") ?: "Section"
                """
                    <section class="rule-subsection">
                        <h3>${subsectionName.escapeHtml()}</h3>
                        ${subsection.textList("desc").joinToString("") { paragraph -> "<p>${paragraph.escapeHtml()}</p>" }}
                    </section>
                """.trimIndent()
            }
        }
            </section>
        """.trimIndent()
    }

    private fun renderRace(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val speed = json.int("speed")
        val alignment = json.string("alignment")
        val age = json.string("age")
        val size = json.string("size")
        val sizeDescription = json.string("size_description")
        val languages = json.string("language_desc")
        val traits = json.traitDetails("_traits", "traits")
        val subraces = json.referenceObjects("subraces")

        return """
            <article class="race-page">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">Race</p>
                ${renderRaceFeature("Ability Score Increase", formatAbilityBonuses(json))}
                ${renderRaceFeature("Age", age)}
                ${renderRaceFeature("Alignment", alignment)}
                ${renderRaceFeature("Size", listOfNotNull(size, sizeDescription).joinToString(". "))}
                ${renderRaceFeature("Speed", speed?.let { "Your base walking speed is $it feet." })}
                ${renderRaceFeature("Languages", languages)}
                ${renderRaceChoices("Ability Score Options", json.obj("ability_bonus_options"))}
                ${renderListSection("Ability Score Options", json.arrayStrings("_ability_bonus_choice_descriptions"))}
                ${renderRaceChoices("Language Options", json.obj("language_options"))}
                ${renderListSection("Language Options", json.arrayStrings("_language_choice_descriptions"))}
                ${renderTraitDetails("Racial Traits", traits)}
                ${renderLinkedReferenceListSection("Subraces", subraces, "subraces")}
            </article>
        """.trimIndent()
    }

    private fun renderSubrace(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val description = json.textList("desc").joinToString("\n\n")
        val race = json.obj("race")
        val traits = json.traitDetails("_racial_traits", "racial_traits")

        return """
            <article class="race-page subrace-page">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">${subraceMetaHtml(race)}</p>
                ${renderParagraphs(description)}
                ${renderRaceFeature("Ability Score Increase", formatAbilityBonuses(json))}
                ${renderTraitDetails("Racial Traits", traits)}
            </article>
        """.trimIndent()
    }

    private fun subraceMetaHtml(race: JsonObject?): String {
        val raceHtml = race?.let { RuleDetailLink.linkedReferenceHtml(it, "races") }.orEmpty()
        return listOf("Subrace".escapeHtml(), raceHtml)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
    }

    private fun renderTrait(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val description = json.textList("desc")

        return """
            <article class="race-page trait-page">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">Trait</p>
                <section class="section">
                    ${description.joinToString("") { "<p>${it.escapeHtml()}</p>" }}
                    ${renderTraitProficiencies(json)}
                    ${renderTraitChoiceSummary(json)}
                </section>
                ${renderTraitChoiceDescriptions(json)}
                ${renderTraitSubtraits(json)}
            </article>
        """.trimIndent()
    }

    private fun renderRaceFeature(title: String, body: String?): String {
        if (body.isNullOrBlank()) return ""

        return """
            <p class="race-feature"><span class="trait-title">${title.escapeHtml()}.</span> ${body.escapeHtml()}</p>
        """.trimIndent()
    }

    private fun renderParagraphs(text: String): String {
        val paragraphs = text
            .split(Regex("\\n\\s*\\n"))
            .map { paragraph -> paragraph.trim() }
            .filter { paragraph -> paragraph.isNotBlank() }

        return paragraphs.joinToString("") { paragraph -> "<p>${paragraph.escapeHtml()}</p>" }
    }

    private fun formatAbilityBonuses(json: JsonObject): String? {
        val bonuses = json.array("ability_bonuses")
            ?.objects()
            ?.mapNotNull { bonus ->
                val ability = bonus.obj("ability_score")?.string("name") ?: return@mapNotNull null
                val value = bonus.int("bonus") ?: return@mapNotNull null
                "$ability ${value.signedText()}"
            }
            ?: emptyList()

        return bonuses.joinToString(", ").takeIf { it.isNotBlank() }
    }

    private fun renderRaceChoices(title: String, choice: JsonObject?): String {
        val choose = choice?.int("choose") ?: return ""
        val type = choice.string("type")?.replace("_", " ").orEmpty()
        val options = choice.obj("from")
            ?.array("options")
            ?.objects()
            ?.mapNotNull { option -> option.choiceOptionName() }
            ?: emptyList()

        if (options.isEmpty()) return ""

        return """
            <section class="section race-choice">
                <h2>${title.escapeHtml()}</h2>
                <p>Choose $choose ${type.escapeHtml()} from: ${options.joinToString(", ").escapeHtml()}.</p>
            </section>
        """.trimIndent()
    }

    private fun JsonObject.choiceOptionName(): String? {
        obj("item")?.string("name")?.let { return it }

        val ability = obj("ability_score")?.string("name")
        val bonus = int("bonus")
        if (ability != null && bonus != null) return "$ability ${bonus.signedText()}"

        string("name")?.let { return it }
        string("desc")?.let { return it }

        return null
    }

    private fun JsonObject.traitDetails(embeddedKey: String, referenceKey: String): List<JsonObject> {
        val embedded = array(embeddedKey)?.objects().orEmpty()
        if (embedded.isNotEmpty()) return embedded

        return array(referenceKey)?.objects() ?: emptyList()
    }

    private fun renderTraitDetails(title: String, traits: List<JsonObject>): String {
        if (traits.isEmpty()) return ""

        return """
            <section class="section race-traits">
                <h2>${title.escapeHtml()}</h2>
                ${
            traits.joinToString("") { trait ->
                """
                    <section class="race-trait">
                        <h3>${traitNameHtml(trait)}</h3>
                        ${trait.textList("desc").joinToString("") { paragraph -> "<p>${paragraph.escapeHtml()}</p>" }}
                        ${renderTraitProficiencies(trait)}
                        ${renderDraconicAncestryTable(trait)}
                        ${renderTraitChoiceSummary(trait)}
                        ${renderTraitChoiceDescriptions(trait)}
                        ${renderTraitSubtraits(trait)}
                    </section>
                """.trimIndent()
            }
        }
            </section>
        """.trimIndent()
    }

    private fun traitNameHtml(trait: JsonObject): String =
        RuleDetailLink.linkedReferenceHtml(trait, "traits")
            .ifBlank { (trait.string("name") ?: "Trait").escapeHtml() }

    private fun renderTraitProficiencies(trait: JsonObject): String {
        val proficiencies = trait.referenceNameList("proficiencies")
        return renderProperty("Proficiencies", proficiencies.joinToString(", "))
    }

    private fun renderTraitChoiceDescriptions(trait: JsonObject): String =
        renderListSection("Choices", trait.arrayStrings("_choice_descriptions"))

    private fun renderTraitChoiceSummary(trait: JsonObject): String {
        val traitSpecific = trait.obj("trait_specific") ?: return ""
        val optionSummaries = traitSpecific.entrySet()
            .mapNotNull { entry ->
                if (entry.key == "subtrait_options" || !entry.value.isJsonObject) return@mapNotNull null

                val option = entry.value.asJsonObject
                val choose = option.int("choose") ?: return@mapNotNull null
                val type = option.string("type")?.replace("_", " ") ?: entry.key.replace("_", " ")
                "Choose $choose $type."
            }
            .distinct()

        if (optionSummaries.isEmpty()) return ""

        return """
            <p class="meta">${optionSummaries.joinToString(" ").escapeHtml()}</p>
        """.trimIndent()
    }

    private fun renderTraitSubtraits(trait: JsonObject): String {
        val subtraits = trait.array("_subtraits")?.objects() ?: emptyList()
        if (subtraits.isEmpty()) return ""

        return """
            <section class="section race-traits">
                <h2>Subtraits</h2>
                ${
            subtraits.joinToString("") { subtrait ->
                """
                    <section class="race-trait">
                        <h3>${(subtrait.string("name") ?: "Subtrait").escapeHtml()}</h3>
                        ${subtrait.textList("desc").joinToString("") { paragraph -> "<p>${paragraph.escapeHtml()}</p>" }}
                    </section>
                """.trimIndent()
            }
        }
            </section>
        """.trimIndent()
    }

    private fun renderDraconicAncestryTable(trait: JsonObject): String {
        val ancestryRows = trait.array("_subtraits")
            ?.objects()
            ?.mapNotNull { subtrait ->
                val traitSpecific = subtrait.obj("trait_specific") ?: return@mapNotNull null
                val damageType = traitSpecific.obj("damage_type")?.string("name") ?: return@mapNotNull null
                val breathWeapon = traitSpecific.obj("breath_weapon") ?: return@mapNotNull null
                val dragon = subtrait.draconicAncestryName()
                val breath = formatBreathWeapon(breathWeapon)

                Triple(dragon, damageType, breath)
            }
            ?: emptyList()

        if (ancestryRows.isEmpty()) return ""

        return """
            <table class="race-trait-table">
                <thead>
                    <tr>
                        <th>Dragon</th>
                        <th>Damage Type</th>
                        <th>Breath Weapon</th>
                    </tr>
                </thead>
                <tbody>
                    ${
            ancestryRows.joinToString("") { (dragon, damageType, breath) ->
                """
                    <tr>
                        <td>${dragon.escapeHtml()}</td>
                        <td>${damageType.escapeHtml()}</td>
                        <td>${breath.escapeHtml()}</td>
                    </tr>
                """.trimIndent()
            }
        }
                </tbody>
            </table>
        """.trimIndent()
    }

    private fun JsonObject.draconicAncestryName(): String {
        val name = string("name").orEmpty()
        return name
            .substringAfter("(", name)
            .substringBefore(")")
            .ifBlank { name.removePrefix("Draconic Ancestry").trim() }
    }

    private fun formatBreathWeapon(breathWeapon: JsonObject): String {
        val area = formatAreaOfEffect(breathWeapon.obj("area_of_effect")).orEmpty()
        val dcType = breathWeapon.obj("dc")?.obj("dc_type")?.string("name")

        return if (dcType.isNullOrBlank()) {
            area
        } else {
            "$area ($dcType save)"
        }
    }

    private fun Int.signedText(): String =
        if (this >= 0) "+$this" else toString()

    private fun renderClass(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val hitDie = json.int("hit_die")
        val levels = json.array("_levels")?.objects() ?: emptyList()
        val features = json.array("_features")?.objects() ?: emptyList()

        return """
            <article class="class-page">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">Class</p>

                ${renderClassFeaturesIntro(json, name, hitDie)}
                ${renderClassProgressionTable(name, levels, ClassProgressionTableConfig.classSpecificColumnMetadata(json))}
                ${renderNamedTextSection("Spellcasting", json.obj("spellcasting")?.namedTextList("info") ?: emptyList())}
                ${renderFeatureDetailSection("Class Features", features)}
                ${renderWarlockInvocations(name, json, features)}
            </article>
        """.trimIndent()
    }


    private fun renderSubclass(detail: RuleDetail): String {
        val json = detail.rawJsonObject() ?: return renderGeneric(detail)

        val name = json.string("name") ?: detail.resource.name
        val parentClass = json.obj("class")
        val flavor = json.string("subclass_flavor")
        val description = json.textList("desc")
        val spells = json.subclassSpellEntries()
        val levels = json.array("_levels")?.objects() ?: emptyList()
        val features = json.array("_features")?.objects() ?: emptyList()

        return """
            <article class="class-page subclass-page">
                <h1>${name.escapeHtml()}</h1>
                <p class="meta">${subclassMetaHtml(flavor, parentClass)}</p>

                <section class="section">
                    ${description.joinToString("") { "<p>${it.escapeHtml()}</p>" }}
                </section>

                ${renderSubclassProgressionTable(name, levels, ClassProgressionTableConfig.classSpecificColumnMetadata(json))}
                ${renderSubclassSpellTables(spells)}
                ${renderFeatureDetailSection("Subclass Features", features)}
            </article>
        """.trimIndent()
    }

    private fun subclassMetaHtml(flavor: String?, parentClass: JsonObject?): String {
        val parts = buildList {
            flavor?.takeIf { it.isNotBlank() }?.let { add(it.escapeHtml()) }
            parentClass?.let { add(RuleDetailLink.linkedReferenceHtml(it, "classes")) }
        }

        return parts.joinToString(" - ")
    }

    private fun renderGeneric(detail: RuleDetail): String {
        val sections = detail.sections
            .sortedBy { section -> section.order }
            .joinToString("") { section ->
                """
                <section class="section">
                    <h2>${section.title.escapeHtml()}</h2>
                    <p>${section.body.escapeHtml().replace("\n", "<br />")}</p>
                </section>
                """.trimIndent()
            }

        return """
            <h1>${detail.resource.name.escapeHtml()}</h1>
            <p class="meta">${detail.resource.type.name} - ${detail.resource.source.name}</p>
            $sections
        """.trimIndent()
    }

    fun renderMissing(resourceName: String): String {
        return renderDocument(
            """
            <h1>${resourceName.escapeHtml()}</h1>
            <p>No detail available.</p>
        """.trimIndent()
        )
    }

    private fun renderDocument(body: String): String {
        return """
        <!doctype html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <link rel="stylesheet" href="homebrewery-lite.css" />
        </head>
        <body>
            <main class="phb">
                $body
            </main>
        </body>
        </html>
    """.trimIndent()
    }

    private fun renderNamedDescriptions(title: String, items: List<Pair<String, String>>): String {
        if (items.isEmpty()) return ""

        return """
            <section class="section">
                <h2>${title.escapeHtml()}</h2>
                ${
                items.joinToString("") { (name, desc) ->
                    """<p><span class="trait-title">${name.escapeHtml()}.</span> ${desc.escapeHtml()}</p>"""
                }
            }
            </section>
        """.trimIndent()
    }

    private fun renderListSection(title: String, items: List<String>): String {
        if (items.isEmpty()) return ""

        return """
            <section class="section">
                <h2>${title.escapeHtml()}</h2>
                <ul>
                    ${items.joinToString("") { "<li>${it.escapeHtml()}</li>" }}
                </ul>
            </section>
        """.trimIndent()
    }

    private fun renderNamedTextSection(title: String, items: List<Pair<String, String>>): String {
        if (items.isEmpty()) return ""

        return """
            <section class="section">
                <h2>${title.escapeHtml()}</h2>
                ${
                items.joinToString("") { (name, desc) ->
                    """<p><span class="trait-title">${name.escapeHtml()}.</span> ${desc.escapeHtml().replace("\n", "<br />")}</p>"""
                }
            }
            </section>
        """.trimIndent()
    }

    private fun renderProperty(label: String, value: String?): String {
        if (value.isNullOrBlank()) return ""
        return """<p class="property"><strong>${label.escapeHtml()}</strong> ${value.escapeHtml()}</p>"""
    }

    private fun renderHtmlProperty(label: String, valueHtml: String?): String {
        if (valueHtml.isNullOrBlank()) return ""
        return """<p class="property"><strong>${label.escapeHtml()}</strong> $valueHtml</p>"""
    }

    private fun renderLinkedReferenceProperty(
        label: String,
        references: List<JsonObject>,
        fallbackEndpoint: String
    ): String =
        renderHtmlProperty(label, RuleDetailLink.linkedReferencesHtml(references, fallbackEndpoint))

    private fun renderLinkedReferenceListSection(
        title: String,
        references: List<JsonObject>,
        fallbackEndpoint: String
    ): String {
        if (references.isEmpty()) return ""

        return """
            <section class="section">
                <h2>${title.escapeHtml()}</h2>
                <ul>
                    ${
            references.joinToString("") { reference ->
                "<li>${RuleDetailLink.linkedReferenceHtml(reference, fallbackEndpoint)}</li>"
            }
        }
                </ul>
            </section>
        """.trimIndent()
    }

    private fun JsonObject.referenceObjects(name: String): List<JsonObject> =
        array(name)?.objects() ?: emptyList()

    private fun renderClassFeaturesIntro(json: JsonObject, className: String, hitDie: Int?): String {
        val savingThrows = json.referenceNameList("saving_throws")
        val proficiencies = json.referenceNameList("proficiencies")
        val subclasses = json.referenceObjects("subclasses")
        val proficiencyChoices = json.choiceDescriptions("proficiency_choices")
        val startingEquipment = json.equipmentQuantityList("starting_equipment")
        val startingEquipmentOptions = json.choiceDescriptions("starting_equipment_options")

        return """
            <section class="section class-features-intro">
                <h2>Class Features</h2>
                <h3>Hit Points</h3>
                ${renderProperty("Hit Dice", hitDie?.let { "1d$it per $className level" })}
                ${renderProperty("Hit Points at 1st Level", hitDie?.let { "$it + your Constitution modifier" })}
                ${renderProperty("Hit Points at Higher Levels", hitDie?.let { "1d$it + your Constitution modifier per $className level after 1st" })}

                <h3>Proficiencies</h3>
                ${renderProperty("Saving Throws", savingThrows.joinToString(", "))}
                ${renderProperty("Proficiencies", proficiencies.joinToString(", "))}
                ${renderListSection("Proficiency Choices", proficiencyChoices)}

                <h3>Equipment</h3>
                ${renderListSection("Starting Equipment", startingEquipment)}
                ${renderListSection("Starting Equipment Choices", startingEquipmentOptions)}
                ${renderLinkedReferenceListSection("Subclasses", subclasses, "subclasses")}
            </section>
        """.trimIndent()
    }

    private fun renderClassProgressionTable(
        className: String,
        levels: List<JsonObject>,
        customColumns: List<ClassSpecificColumnMetadata>
    ): String {
        if (levels.isEmpty()) return ""

        val columns = ClassProgressionTableConfig.columnsFor(className, levels, customColumns)

        val rows = levels.sortedBy { it.int("level") ?: 0 }.joinToString("") { level ->
            val cells = columns.joinToString("") { column ->
                val value = column.value(level).tableCellValue()
                """<td class="${column.cssClass}">${value.escapeHtml()}</td>"""
            }

            "<tr>$cells</tr>"
        }

        return """
            <section class="section">
                <h2>The ${className.escapeHtml()}</h2>
                <table class="class-table">
                    ${renderClassTableHeader(columns)}
                    <tbody>$rows</tbody>
                </table>
            </section>
        """.trimIndent()
    }

    private fun renderSubclassProgressionTable(
        subclassName: String,
        levels: List<JsonObject>,
        customColumns: List<ClassSpecificColumnMetadata>
    ): String {
        if (levels.isEmpty()) return ""

        val customTableColumns = ClassProgressionTableConfig.classSpecificFallbackColumns(levels, customColumns)
        val headers = listOf("Level", "Features") + customTableColumns.map { column ->
            column.labelHtml.replace("<br />", " ")
        }
        val rows = levels.sortedBy { it.int("level") ?: 0 }.joinToString("") { level ->
            val featureNames = level.referenceNameList("features").joinToString(", ").ifBlank { "-" }
            val cells = listOf(
                level.int("level")?.toString().orEmpty(),
                featureNames
            ) + customTableColumns.map { column -> column.value(level).tableCellValue() }

            "<tr>${cells.joinToString("") { "<td>${it.escapeHtml()}</td>" }}</tr>"
        }

        return """
            <section class="section">
                <h2>The ${subclassName.escapeHtml()}</h2>
                <table class="class-table subclass-table">
                    <thead>
                        <tr>${headers.joinToString("") { "<th>${it.escapeHtml()}</th>" }}</tr>
                    </thead>
                    <tbody>$rows</tbody>
                </table>
            </section>
        """.trimIndent()
    }

    private fun renderClassTableHeader(columns: List<ClassTableColumn>): String {
        val topCells = mutableListOf<String>()
        val bottomCells = mutableListOf<String>()

        var index = 0
        while (index < columns.size) {
            val column = columns[index]
            val group = column.groupHtml

            if (group == null) {
                topCells += """<th rowspan="2" class="${column.cssClass}">${column.labelHtml}</th>"""
                index++
            } else {
                val groupedColumns = columns.drop(index).takeWhile { it.groupHtml == group }

                topCells += """<th colspan="${groupedColumns.size}" class="group-heading">$group</th>"""
                bottomCells += groupedColumns.joinToString("") { groupedColumn ->
                    """<th class="${groupedColumn.cssClass}">${groupedColumn.labelHtml}</th>"""
                }

                index += groupedColumns.size
            }
        }

        return """
            <thead>
                <tr>${topCells.joinToString("")}</tr>
                <tr>${bottomCells.joinToString("")}</tr>
            </thead>
        """.trimIndent()
    }

    private fun renderFeatureDetailSection(title: String, features: List<JsonObject>): String {
        val featureBlocks = features.toFeatureBlocks()
        if (featureBlocks.isEmpty()) return ""

        return """
            <section class="section feature-details">
                <h2>${title.escapeHtml()}</h2>
                ${
                featureBlocks.joinToString("") { feature ->
                    """
                        <section class="feature-block">
                            <h3>${feature.name.escapeHtml()}</h3>
                            ${renderFeatureLevels(feature.levels)}
                            ${feature.description.joinToString("") { "<p>${it.escapeHtml()}</p>" }}
                        </section>
                    """.trimIndent()
                }
            }
            </section>
        """.trimIndent()
    }

    private fun renderWarlockInvocations(
        className: String,
        classJson: JsonObject,
        features: List<JsonObject>
    ): String {
        if (!className.equals("Warlock", ignoreCase = true)) return ""

        val invocations = (
            classJson.referenceObjects("_eldritch_invocations") +
                features.flatMap { feature -> feature.eldritchInvocationReferences() }
            )
            .distinctBy { invocation ->
                invocation.string("id")
                    ?: invocation.string("url")
                    ?: invocation.string("index")
                    ?: invocation.string("name")
            }

        if (invocations.isEmpty()) return ""

        return renderLinkedReferenceListSection(
            title = "Eldritch Invocations",
            references = invocations,
            fallbackEndpoint = "features"
        )
    }

    private fun JsonObject.eldritchInvocationReferences(): List<JsonObject> {
        val name = string("name").orEmpty()
        val featureSpecific = obj("feature_specific")

        val directInvocations = featureSpecific
            ?.array("invocations")
            ?.objects()
            .orEmpty()

        val optionInvocations = featureSpecific
            ?.obj("subfeature_options")
            ?.obj("from")
            ?.array("options")
            ?.objects()
            ?.mapNotNull { option -> option.obj("item") ?: option.obj("choice") }
            .orEmpty()

        return if (
            name.contains("Eldritch Invocation", ignoreCase = true) ||
            directInvocations.isNotEmpty() ||
            optionInvocations.isNotEmpty()
        ) {
            directInvocations + optionInvocations
        } else {
            emptyList()
        }
    }

    private fun renderSubclassSpellTables(spells: List<SubclassSpellEntry>): String {
        if (spells.isEmpty()) return ""

        val groups = spells.groupBy { it.groupName }
        val title = if (groups.size > 1) "Circle Spells" else "Subclass Spells"

        return """
        <section class="section subclass-spells">
            <h2>${title.escapeHtml()}</h2>
            ${
            groups.entries.joinToString("") { (groupName, entries) ->
                val rows = entries
                    .groupBy { it.level ?: Int.MAX_VALUE }
                    .toSortedMap()
                    .entries
                    .joinToString("") { (level, levelEntries) ->
                        val levelLabel = if (level == Int.MAX_VALUE) "-" else level.ordinalSpellLevel()
                        val spellNamesHtml = levelEntries
                            .map { it.spellHtml }
                            .distinct()
                            .joinToString(", ")

                        """
                                <tr>
                                    <td>${levelLabel.escapeHtml()}</td>
                                    <td>$spellNamesHtml</td>
                                </tr>
                            """.trimIndent()
                    }

                """
                        <section class="subclass-spell-group">
                            ${if (groups.size > 1) "<h3>${groupName.escapeHtml()}</h3>" else ""}
                            <table class="subclass-spell-table">
                                <thead>
                                    <tr>
                                        <th>Level</th>
                                        <th>Spells</th>
                                    </tr>
                                </thead>
                                <tbody>$rows</tbody>
                            </table>
                        </section>
                    """.trimIndent()
                }
            }
            </section>
        """.trimIndent()
    }




    private fun List<JsonObject>.toFeatureBlocks(): List<FeatureBlock> {
        return mapNotNull { feature ->
            val name = feature.string("name") ?: return@mapNotNull null
            val description = feature.textList("desc")
            if (description.isEmpty()) return@mapNotNull null

            FeatureEntry(
                name = name,
                normalizedName = name.normalizedFeatureName(),
                level = feature.int("level"),
                description = description,
                normalizedDescription = description.joinToString("\n").trim().lowercase()
            )
        }
            .groupBy { entry -> entry.normalizedName to entry.normalizedDescription }
            .values
            .map { entries ->
                val first = entries.minWith(compareBy({ it.level ?: Int.MAX_VALUE }, { it.name }))

                FeatureBlock(
                    name = first.name,
                    levels = entries.mapNotNull { it.level }.distinct().sorted(),
                    description = first.description
                )
            }
            .sortedWith(compareBy({ it.levels.firstOrNull() ?: Int.MAX_VALUE }, { it.name }))
    }
    private fun renderFeatureLevels(levels: List<Int>): String {
        if (levels.isEmpty()) return ""

        val label = if (levels.size == 1) {
            "Level ${levels.first()}"
        } else {
            "Levels ${levels.joinToString(", ")}"
        }

        return """<p class="meta">${label.escapeHtml()}</p>"""
    }

    private fun String.normalizedFeatureName(): String =
        trim()
            .replace(Regex("""\s*\([^)]*\)\s*$"""), "")
            .replace(Regex("""\s+\d+$"""), "")
            .lowercase()

    private fun String.toSubclassSpellGroupName(): String {
        return if (startsWith("Circle of the Land: ")) {
            removePrefix("Circle of the Land: ")
        } else {
            this
        }
    }


    private fun JsonObject.subclassSpellEntries(): List<SubclassSpellEntry> {
        return array("spells")
            ?.objects()
            ?.mapNotNull { entry ->
                val spellName = entry.obj("spell")?.string("name") ?: return@mapNotNull null
                val spell = entry.obj("spell") ?: return@mapNotNull null
                val prerequisites = entry.array("prerequisites")?.objects() ?: emptyList()

                val level = prerequisites
                    .firstOrNull { it.string("type") == "level" }
                    ?.string("name")
                    ?.substringAfterLast(" ")
                    ?.toIntOrNull()

                val groupName = prerequisites
                    .firstOrNull { it.string("type") == "feature" }
                    ?.string("name")
                    ?.toSubclassSpellGroupName()
                    ?: "Subclass Spells"

                SubclassSpellEntry(
                    groupName = groupName,
                    level = level,
                    spellHtml = RuleDetailLink.linkedReferenceHtml(spell, "spells").ifBlank { spellName.escapeHtml() }
                )
            }
            ?: emptyList()
    }
}
