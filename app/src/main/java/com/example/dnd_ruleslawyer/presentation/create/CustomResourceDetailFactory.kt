package com.example.dnd_ruleslawyer.presentation.create

import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSection
import com.example.dnd_ruleslawyer.domain.model.RuleSource
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class CustomResourceDetailFactory(
    private val gson: Gson = Gson(),
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) {
    fun build(draft: CustomResourceDraft): RuleDetail {
        return when (draft) {
            is GenericResourceDraft -> buildGeneric(draft)
            is HomebreweryResourceDraft -> buildHomebrewery(draft)
            is SpellDraft -> buildSpell(draft)
            is MonsterDraft -> buildMonster(draft)
            is ClassDraft -> buildClass(draft)
            is ConditionDraft -> buildCondition(draft)
            is FeatDraft -> buildFeat(draft)
            is MagicItemDraft -> buildMagicItem(draft)
            is EquipmentDraft -> buildEquipment(draft)
            is BackgroundDraft -> buildBackground(draft)
            is RuleSectionDraft -> buildRuleSection(draft)
            is RuleDraft -> buildRule(draft)
            is FoundationResourceDraft -> buildFoundationResource(draft)
            is TraitDraft -> buildTrait(draft)
            is RaceDraft -> buildRace(draft)
            is SubraceDraft -> buildSubrace(draft)
            is FeatureDraft -> buildFeature(draft)
            is SubclassDraft -> buildSubclass(draft)
        }
    }

    private fun baseResource(
        basics: ResourceBasicsDraft,
        source: RuleSource,
        idPrefix: String,
    ): RuleResource {
        val now = currentTimeMillis()
        val slug = basics.name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "resource" }

        return RuleResource(
            id = "$idPrefix:${basics.type.endpoint}:$slug-$now",
            name = basics.name,
            type = basics.type,
            source = source,
            description = basics.description,
            apiUrl = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            imageUrl = basics.imageUrl
        )
    }

    private fun buildHomebrewery(draft: HomebreweryResourceDraft): RuleDetail {
        val resource = baseResource(
            basics = draft.basics,
            source = RuleSource.HOMEBREWERY,
            idPrefix = "homebrewery"
        )

        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            addProperty("homebrewery_url", draft.shareUrl)
            addProperty("resource_type", draft.basics.type.name)
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(
                RuleSection("Homebrewery", draft.shareUrl, 0)
        ),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildSpell(draft: SpellDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")

        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            addProperty("level", draft.level)
            add("school", referenceObject(draft.school))
            addProperty("casting_time", draft.castingTime)
            addProperty("range", draft.range)
            add("components", stringArray(draft.components))
            if (draft.material.isNotBlank()) addProperty("material", draft.material)
            addProperty("ritual", draft.ritual)
            addProperty("concentration", draft.concentration)
            addProperty("duration", draft.duration)
            if (draft.attackType.isNotBlank()) addProperty("attack_type", draft.attackType)
            if (draft.savingThrowAbility.isNotBlank() || draft.savingThrowSuccess.isNotBlank()) {
                add("dc", JsonObject().apply {
                    if (draft.savingThrowAbility.isNotBlank()) {
                        add("dc_type", JsonObject().apply { addProperty("name", draft.savingThrowAbility) })
                    }
                    if (draft.savingThrowSuccess.isNotBlank()) addProperty("dc_success", draft.savingThrowSuccess)
                })
            }
            if (draft.damageType.name.isNotBlank() || draft.damageBySlotLevel.isNotEmpty()) {
                add("damage", JsonObject().apply {
                    if (draft.damageType.name.isNotBlank()) {
                        add("damage_type", referenceObject(draft.damageType))
                    }
                    if (draft.damageBySlotLevel.isNotEmpty()) {
                        add("damage_at_slot_level", slotLevelObject(draft.damageBySlotLevel))
                    }
                })
            }
            if (draft.healingBySlotLevel.isNotEmpty()) {
                add("heal_at_slot_level", slotLevelObject(draft.healingBySlotLevel))
            }
            if (draft.areaType.isNotBlank() || draft.areaSize != null) {
                add("area_of_effect", JsonObject().apply {
                    if (draft.areaType.isNotBlank()) addProperty("type", draft.areaType)
                    draft.areaSize?.let { addProperty("size", it) }
                })
            }
            add("classes", referenceArray(draft.classes))
            add("subclasses", referenceArray(draft.subclasses))
            add("desc", stringArray(draft.description))
            add("higher_level", stringArray(draft.higherLevel))
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(RuleSection("Description", draft.description.joinToString("\n\n"), 0)),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildMonster(draft: MonsterDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")

        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            if (draft.basics.imageUrl?.isNotBlank() == true) addProperty("image", draft.basics.imageUrl)
            if (draft.size.isNotBlank()) addProperty("size", draft.size)
            if (draft.type.isNotBlank()) addProperty("type", draft.type)
            if (draft.alignment.isNotBlank()) addProperty("alignment", draft.alignment)
            draft.armorClass?.let {
                add("armor_class", JsonArray().apply {
                    add(JsonObject().apply { addProperty("value", it) })
                })
            }
            draft.hitPoints?.let { addProperty("hit_points", it) }
            addProperty("hit_dice", draft.hitDice)
            add("speed", JsonObject().apply {
                draft.speed.forEach { (key, value) -> addProperty(key, value) }
            })
            addProperty("strength", draft.abilityScores.strength)
            addProperty("dexterity", draft.abilityScores.dexterity)
            addProperty("constitution", draft.abilityScores.constitution)
            addProperty("intelligence", draft.abilityScores.intelligence)
            addProperty("wisdom", draft.abilityScores.wisdom)
            addProperty("charisma", draft.abilityScores.charisma)
            add("proficiencies", monsterProficiencyArray(draft.proficiencies))
            add("damage_vulnerabilities", stringArray(draft.damageVulnerabilities))
            add("damage_resistances", stringArray(draft.damageResistances))
            add("damage_immunities", stringArray(draft.damageImmunities))
            add("condition_immunities", referenceArray(draft.conditionImmunities))
            add("senses", JsonObject().apply {
                draft.senses.forEach { (key, value) ->
                    if (key.isNotBlank() && value.isNotBlank()) addProperty(key, value)
                }
            })
            if (draft.languages.isNotBlank()) addProperty("languages", draft.languages)
            if (draft.challengeRating.isNotBlank()) addProperty("challenge_rating", draft.challengeRating)
            draft.xp?.let { addProperty("xp", it) }
            add("actions", namedDescriptionArray(draft.actions))
            add("special_abilities", namedDescriptionArray(draft.traits))
            add("reactions", namedDescriptionArray(draft.reactions))
            add("legendary_actions", namedDescriptionArray(draft.legendaryActions))
        }

        return RuleDetail(resource.id, resource, emptyList(), gson.toJson(raw))
    }

    private fun buildClass(draft: ClassDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")

        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            addProperty("hit_die", draft.hitDie)
            add("saving_throws", namedReferenceArray(draft.savingThrows))
            add("proficiencies", namedReferenceArray(draft.proficiencies))
            add("proficiency_choices", descriptionArray(draft.proficiencyChoices))
            add("starting_equipment", equipmentQuantityArray(draft.startingEquipment))
            add("starting_equipment_options", descriptionArray(draft.startingEquipmentOptions))
            add("subclasses", referenceArray(draft.subclasses))
            if (draft.spellcastingInfo.isNotEmpty()) {
                add("spellcasting", JsonObject().apply {
                    add("info", namedTextArray(draft.spellcastingInfo))
                })
            }
            add("_levels", JsonArray().apply {
                draft.levels.forEach { level ->
                    add(JsonObject().apply {
                        addProperty("level", level.level)
                        addProperty("prof_bonus", level.proficiencyBonus)
                        add("features", namedReferenceArray(level.featureNames))
                        addLevelExtras(level)
                    })
                }
            })
            addCustomLevelColumns(draft.customLevelColumns)
            add("_features", JsonArray().apply {
                draft.features.forEach { feature ->
                    add(JsonObject().apply {
                        addProperty("name", feature.name)
                        addProperty("level", feature.level)
                        add("desc", stringArray(feature.description))
                    })
                }
            })
        }

        return RuleDetail(resource.id, resource, emptyList(), gson.toJson(raw))
    }

    private fun buildCondition(draft: ConditionDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            add("desc", stringArray(draft.description))
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(RuleSection("Description", draft.description.joinToString("\n\n"), 0)),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildFeat(draft: FeatDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            add("prerequisites", JsonArray().apply {
                draft.prerequisites.filter { it.isNotBlank() }.forEach { prerequisite ->
                    add(JsonObject().apply { addProperty("desc", prerequisite) })
                }
            })
            add("desc", stringArray(draft.description))
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(RuleSection("Description", draft.description.joinToString("\n\n"), 0)),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildMagicItem(draft: MagicItemDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            if (draft.equipmentCategory.isNotBlank()) {
                add("equipment_category", JsonObject().apply { addProperty("name", draft.equipmentCategory) })
            }
            if (draft.rarity.isNotBlank()) {
                add("rarity", JsonObject().apply { addProperty("name", draft.rarity) })
            }
            add("desc", stringArray(draft.description))
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(RuleSection("Description", draft.description.joinToString("\n\n"), 0)),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildEquipment(draft: EquipmentDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            if (draft.equipmentCategory.name.isNotBlank()) add("equipment_category", referenceObject(draft.equipmentCategory))
            if (draft.gearCategory.isNotBlank()) {
                add("gear_category", JsonObject().apply { addProperty("name", draft.gearCategory) })
            }
            if (draft.costQuantity != null || draft.costUnit.isNotBlank()) {
                add("cost", JsonObject().apply {
                    draft.costQuantity?.let { addProperty("quantity", it) }
                    if (draft.costUnit.isNotBlank()) addProperty("unit", draft.costUnit)
                })
            }
            draft.weight?.let { addProperty("weight", it) }
            add("desc", stringArray(draft.description))
            if (draft.weaponCategory.isNotBlank()) addProperty("weapon_category", draft.weaponCategory)
            if (draft.weaponRange.isNotBlank()) addProperty("weapon_range", draft.weaponRange)
            if (draft.categoryRange.isNotBlank()) addProperty("category_range", draft.categoryRange)
            if (draft.damageDice.isNotBlank() || draft.damageType.name.isNotBlank()) {
                add("damage", JsonObject().apply {
                    if (draft.damageDice.isNotBlank()) addProperty("damage_dice", draft.damageDice)
                    if (draft.damageType.name.isNotBlank()) add("damage_type", referenceObject(draft.damageType))
                })
            }
            if (draft.twoHandedDamageDice.isNotBlank()) {
                add("two_handed_damage", JsonObject().apply {
                    addProperty("damage_dice", draft.twoHandedDamageDice)
                    if (draft.damageType.name.isNotBlank()) add("damage_type", referenceObject(draft.damageType))
                })
            }
            add("properties", referenceArray(draft.properties))
            if (draft.rangeNormal != null || draft.rangeLong != null) {
                add("range", JsonObject().apply {
                    draft.rangeNormal?.let { addProperty("normal", it) }
                    draft.rangeLong?.let { addProperty("long", it) }
                })
            }
            if (draft.armorCategory.isNotBlank()) addProperty("armor_category", draft.armorCategory)
            if (draft.armorBase != null || draft.armorDexBonus || draft.armorMaxBonus != null) {
                add("armor_class", JsonObject().apply {
                    draft.armorBase?.let { addProperty("base", it) }
                    addProperty("dex_bonus", draft.armorDexBonus)
                    draft.armorMaxBonus?.let { addProperty("max_bonus", it) }
                })
            }
            draft.strMinimum?.let { addProperty("str_minimum", it) }
            addProperty("stealth_disadvantage", draft.stealthDisadvantage)
            if (draft.toolCategory.isNotBlank()) addProperty("tool_category", draft.toolCategory)
            add("contents", equipmentQuantityArray(draft.contents))
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(RuleSection("Description", draft.description.joinToString("\n\n"), 0)),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildBackground(draft: BackgroundDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            add("starting_proficiencies", referenceArray(draft.startingProficiencies))
            add("_language_choice_descriptions", stringArray(draft.languageOptions))
            add("starting_equipment", equipmentQuantityArray(draft.startingEquipment))
            add("starting_equipment_options", descriptionArray(draft.startingEquipmentOptions))
            if (draft.feature.name.isNotBlank() || draft.feature.text.isNotBlank()) {
                add("feature", JsonObject().apply {
                    addProperty("name", draft.feature.name)
                    add("desc", stringArray(listOf(draft.feature.text)))
                })
            }
            if (draft.personalityTraits.isNotEmpty()) {
                add("personality_traits", stringChoiceObject(2, "personality traits", draft.personalityTraits))
            }
            if (draft.ideals.isNotEmpty()) {
                add("ideals", stringChoiceObject(1, "ideals", draft.ideals))
            }
            if (draft.bonds.isNotEmpty()) {
                add("bonds", stringChoiceObject(1, "bonds", draft.bonds))
            }
            if (draft.flaws.isNotEmpty()) {
                add("flaws", stringChoiceObject(1, "flaws", draft.flaws))
            }
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(RuleSection("Feature", draft.feature.text, 0)),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildRuleSection(draft: RuleSectionDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            add("desc", stringArray(draft.description))
            if (draft.parentRule.name.isNotBlank()) add("rule", referenceObject(draft.parentRule))
            add("subsections", referenceArray(draft.subsections))
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(RuleSection("Description", draft.description.joinToString("\n\n"), 0)),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildRule(draft: RuleDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            add("desc", stringArray(draft.description))
            add("subsections", ruleSubsectionArray(draft.subsectionReferences, draft.sections))
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = buildList {
                if (draft.description.isNotEmpty()) {
                    add(RuleSection("Description", draft.description.joinToString("\n\n"), 0))
                }
                draft.sections.forEachIndexed { index, section ->
                    add(RuleSection(section.titleOrDefault(), section.text, index + 1))
                }
            },
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildFoundationResource(draft: FoundationResourceDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            add("desc", stringArray(draft.description))
            addProperty("resource_type", draft.basics.type.name)
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(RuleSection("Description", draft.description.joinToString("\n\n"), 0)),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildTrait(draft: TraitDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            add("desc", stringArray(draft.description))
            add("proficiencies", namedReferenceArray(draft.proficiencies))
            add("_choice_descriptions", stringArray(draft.choices))
            add("_subtraits", namedTextArray(draft.subtraits))
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(RuleSection("Description", draft.description.joinToString("\n\n"), 0)),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildRace(draft: RaceDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            draft.speed?.let { addProperty("speed", it) }
            add("ability_bonuses", abilityBonusArray(draft.abilityBonuses))
            if (draft.alignment.isNotBlank()) addProperty("alignment", draft.alignment)
            if (draft.age.isNotBlank()) addProperty("age", draft.age)
            if (draft.size.isNotBlank()) addProperty("size", draft.size)
            if (draft.sizeDescription.isNotBlank()) addProperty("size_description", draft.sizeDescription)
            if (draft.languageDescription.isNotBlank()) addProperty("language_desc", draft.languageDescription)
            add("_ability_bonus_choice_descriptions", stringArray(draft.abilityChoices))
            add("_language_choice_descriptions", stringArray(draft.languageChoices))
            add("_traits", traitArray(draft.traitReferences, draft.traits))
            add("subraces", referenceArray(draft.subraces))
        }

        return RuleDetail(resource.id, resource, emptyList(), gson.toJson(raw))
    }

    private fun buildSubrace(draft: SubraceDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            if (draft.parentRace.name.isNotBlank()) add("race", referenceObject(draft.parentRace))
            add("desc", stringArray(draft.description))
            add("ability_bonuses", abilityBonusArray(draft.abilityBonuses))
            add("_racial_traits", traitArray(draft.traitReferences, draft.traits))
        }

        return RuleDetail(resource.id, resource, emptyList(), gson.toJson(raw))
    }

    private fun buildFeature(draft: FeatureDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            draft.level?.let { addProperty("level", it) }
            if (draft.parentClass.name.isNotBlank()) add("class", referenceObject(draft.parentClass))
            if (draft.parentSubclass.name.isNotBlank()) add("subclass", referenceObject(draft.parentSubclass))
            add("prerequisites", JsonArray().apply {
                draft.prerequisites.filter { it.isNotBlank() }.forEach { prerequisite ->
                    add(JsonObject().apply { addProperty("desc", prerequisite) })
                }
            })
            add("desc", stringArray(draft.description))
        }

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = listOf(RuleSection("Description", draft.description.joinToString("\n\n"), 0)),
            rawJson = gson.toJson(raw)
        )
    }

    private fun buildSubclass(draft: SubclassDraft): RuleDetail {
        val resource = baseResource(draft.basics, RuleSource.CUSTOM, "custom")
        val raw = JsonObject().apply {
            addProperty("name", draft.basics.name)
            if (draft.parentClass.name.isNotBlank()) add("class", referenceObject(draft.parentClass))
            if (draft.flavor.isNotBlank()) addProperty("subclass_flavor", draft.flavor)
            add("desc", stringArray(draft.description))
            add("_levels", JsonArray().apply {
                draft.levels.forEach { level ->
                    add(JsonObject().apply {
                        addProperty("level", level.level)
                        add("features", namedReferenceArray(level.featureNames))
                        addLevelExtras(level)
                    })
                }
            })
            addCustomLevelColumns(draft.customLevelColumns)
            add("_features", JsonArray().apply {
                draft.features.forEach { feature ->
                    add(JsonObject().apply {
                        addProperty("name", feature.name)
                        addProperty("level", feature.level)
                        add("desc", stringArray(feature.description))
                    })
                }
            })
            add("spells", subclassSpellArray(draft.spells))
        }

        return RuleDetail(resource.id, resource, emptyList(), gson.toJson(raw))
    }

    private fun buildGeneric(draft: GenericResourceDraft): RuleDetail {
        val resource = baseResource(
            basics = draft.basics,
            source = RuleSource.CUSTOM,
            idPrefix = "custom"
        )

        return RuleDetail(
            id = resource.id,
            resource = resource,
            sections = draft.sections.mapIndexed { index, section ->
                RuleSection(section.title, section.body, index)
            },
            rawJson = null
        )
    }

    private fun stringArray(values: List<String>): JsonArray =
        JsonArray().apply {
            values.filter { it.isNotBlank() }.forEach { add(it) }
        }

    private fun namedDescriptionArray(values: List<NamedTextDraft>): JsonArray =
        JsonArray().apply {
            values.filter { it.name.isNotBlank() || it.text.isNotBlank() }.forEach { item ->
                add(JsonObject().apply {
                    addProperty("name", item.name)
                    addProperty("desc", item.text)
                })
            }
        }

    private fun namedTextArray(values: List<NamedTextDraft>): JsonArray =
        JsonArray().apply {
            values.filter { it.name.isNotBlank() || it.text.isNotBlank() }.forEach { item ->
                add(JsonObject().apply {
                    addProperty("name", item.name)
                    add("desc", stringArray(listOf(item.text)))
                })
            }
        }

    private fun descriptionArray(values: List<String>): JsonArray =
        JsonArray().apply {
            values.filter { it.isNotBlank() }.forEach { description ->
                add(JsonObject().apply { addProperty("desc", description) })
            }
        }

    private fun equipmentQuantityArray(values: List<EquipmentQuantityDraft>): JsonArray =
        JsonArray().apply {
            values.filter { item -> item.name.isNotBlank() }.forEach { item ->
                add(JsonObject().apply {
                    add("equipment", JsonObject().apply { addProperty("name", item.name) })
                    addProperty("quantity", item.quantity)
                })
            }
        }

    private fun subclassSpellArray(values: List<SubclassSpellDraft>): JsonArray =
        JsonArray().apply {
            values.filter { item -> item.spell.name.isNotBlank() }.forEach { item ->
                add(JsonObject().apply {
                    add("spell", referenceObject(item.spell))
                    add("prerequisites", JsonArray().apply {
                        item.level?.let { level ->
                            add(JsonObject().apply {
                                addProperty("type", "level")
                                addProperty("name", "Subclass Level $level")
                            })
                        }
                        if (item.groupName.isNotBlank()) {
                            add(JsonObject().apply {
                                addProperty("type", "feature")
                                addProperty("name", item.groupName)
                            })
                        }
                    })
                })
            }
        }

    private fun monsterProficiencyArray(values: List<MonsterProficiencyDraft>): JsonArray =
        JsonArray().apply {
            values.filter { item -> item.name.isNotBlank() }.forEach { item ->
                add(JsonObject().apply {
                    add("proficiency", JsonObject().apply { addProperty("name", item.name) })
                    addProperty("value", item.value)
                })
            }
        }

    private fun traitArray(
        references: List<ResourceReferenceDraft>,
        traits: List<NamedTextDraft>
    ): JsonArray =
        JsonArray().apply {
            references
                .filter { reference -> reference.name.isNotBlank() }
                .forEach { reference -> add(referenceObject(reference)) }
            traits
                .filter { trait -> trait.name.isNotBlank() || trait.text.isNotBlank() }
                .forEach { trait ->
                    add(JsonObject().apply {
                        addProperty("name", trait.name)
                        add("desc", stringArray(listOf(trait.text)))
                    })
                }
        }

    private fun ruleSubsectionArray(
        references: List<ResourceReferenceDraft>,
        sections: List<NamedTextDraft>
    ): JsonArray =
        JsonArray().apply {
            references
                .filter { reference -> reference.name.isNotBlank() }
                .forEach { reference -> add(referenceObject(reference)) }
            sections
                .filter { section -> section.name.isNotBlank() || section.text.isNotBlank() }
                .forEach { section ->
                    add(JsonObject().apply {
                        addProperty("name", section.titleOrDefault())
                        add("desc", stringArray(listOf(section.text)))
                    })
                }
        }

    private fun namedReferenceArray(values: List<String>): JsonArray =
        JsonArray().apply {
            values.filter { it.isNotBlank() }.forEach { name ->
                add(JsonObject().apply { addProperty("name", name) })
            }
        }

    private fun referenceArray(values: List<ResourceReferenceDraft>): JsonArray =
        JsonArray().apply {
            values
                .filter { reference -> reference.name.isNotBlank() }
                .forEach { reference -> add(referenceObject(reference)) }
        }

    private fun referenceObject(reference: ResourceReferenceDraft): JsonObject =
        JsonObject().apply {
            addProperty("name", reference.name)
            reference.resourceId
                ?.takeIf { id -> id.isNotBlank() }
                ?.let { id -> addProperty("id", id) }
        }

    private fun JsonObject.addLevelExtras(level: ClassLevelDraft) {
        if (level.spellcasting.isNotEmpty()) {
            add("spellcasting", JsonObject().apply {
                level.spellcasting
                    .filter { (_, value) -> value != 0 }
                    .forEach { (key, value) -> addProperty(key, value) }
            })
        }
        if (level.classSpecific.isNotEmpty()) {
            add("class_specific", JsonObject().apply {
                level.classSpecific
                    .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
                    .forEach { (key, value) -> addProperty(key, value) }
            })
        }
    }

    private fun JsonObject.addCustomLevelColumns(columns: List<CustomLevelColumnDraft>) {
        val cleanColumns = columns.filter { column -> column.key.isNotBlank() && column.label.isNotBlank() }
        if (cleanColumns.isEmpty()) return

        add("_custom_level_columns", JsonArray().apply {
            cleanColumns.forEach { column ->
                add(JsonObject().apply {
                    addProperty("key", column.key)
                    addProperty("label", column.label)
                    addProperty("type", column.type)
                })
            }
        })
    }

    private fun stringChoiceObject(choose: Int, type: String, values: List<String>): JsonObject =
        JsonObject().apply {
            addProperty("choose", choose)
            addProperty("type", type)
            add("from", JsonObject().apply {
                addProperty("option_set_type", "options_array")
                add("options", JsonArray().apply {
                    values.filter { it.isNotBlank() }.forEach { value ->
                        add(JsonObject().apply {
                            addProperty("option_type", "string")
                            addProperty("string", value)
                        })
                    }
                })
            })
        }

    private fun abilityBonusArray(values: List<AbilityBonusDraft>): JsonArray =
        JsonArray().apply {
            values.filter { bonus -> bonus.ability.isNotBlank() }.forEach { bonus ->
                add(JsonObject().apply {
                    add("ability_score", JsonObject().apply { addProperty("name", bonus.ability) })
                    addProperty("bonus", bonus.bonus)
                })
            }
        }

    private fun slotLevelObject(values: Map<Int, String>): JsonObject =
        JsonObject().apply {
            values
                .filter { (level, value) -> level in 0..9 && value.isNotBlank() }
                .toSortedMap()
                .forEach { (level, value) -> addProperty(level.toString(), value) }
        }

    private fun NamedTextDraft.titleOrDefault(): String =
        name.ifBlank { "Section" }
}
