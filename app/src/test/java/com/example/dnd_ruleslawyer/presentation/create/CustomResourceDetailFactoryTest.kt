package com.example.dnd_ruleslawyer.presentation.create

import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleSource
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomResourceDetailFactoryTest {
    private val factory = CustomResourceDetailFactory(currentTimeMillis = { 42L })

    @Test
    fun buildGeneric_createsCustomResourceWithStableSections() {
        val detail = factory.build(
            GenericResourceDraft(
                basics = ResourceBasicsDraft(ResourceType.RULE, "Table Ruling", "Short note"),
                sections = listOf(
                    EditableSectionDraft("Situation", "A player asks for a ruling."),
                    EditableSectionDraft("Decision", "Apply the written rule.")
                )
            )
        )

        assertEquals("custom:rules:table-ruling-42", detail.id)
        assertEquals(RuleSource.CUSTOM, detail.resource.source)
        assertEquals(listOf("Situation", "Decision"), detail.sections.map { it.title })
    }

    @Test
    fun buildHomebrewery_keepsShareUrlInRawJsonAndSection() {
        val detail = factory.build(
            HomebreweryResourceDraft(
                basics = ResourceBasicsDraft(ResourceType.RULE, "Homebrew Link", "External document"),
                shareUrl = "https://homebrewery.naturalcrit.com/share/test"
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals(RuleSource.HOMEBREWERY, detail.resource.source)
        assertEquals("https://homebrewery.naturalcrit.com/share/test", raw.get("homebrewery_url").asString)
        assertEquals("Homebrewery", detail.sections.single().title)
    }

    @Test
    fun buildSpell_writesStructuredSpellJsonForRendererAndRepository() {
        val detail = factory.build(
            SpellDraft(
                basics = ResourceBasicsDraft(ResourceType.SPELL, "Test Bolt", "A test spell"),
                level = 2,
                school = ResourceReferenceDraft("Evocation", "official:magic-schools:evocation"),
                castingTime = "1 action",
                range = "60 feet",
                components = listOf("V", "S"),
                material = "",
                ritual = false,
                concentration = true,
                duration = "1 minute",
                attackType = "ranged",
                savingThrowAbility = "DEX",
                savingThrowSuccess = "half",
                damageType = ResourceReferenceDraft("Lightning"),
                damageBySlotLevel = mapOf(2 to "3d6", 3 to "4d6"),
                healingBySlotLevel = emptyMap(),
                areaType = "line",
                areaSize = 30,
                classes = listOf(
                    ResourceReferenceDraft("Wizard", "official:classes:wizard"),
                    ResourceReferenceDraft("Witch")
                ),
                subclasses = emptyList(),
                description = listOf("Lightning arcs toward a target."),
                higherLevel = listOf("Damage increases by 1d6.")
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals(ResourceType.SPELL, detail.resource.type)
        assertEquals(2, raw.get("level").asInt)
        assertTrue(raw.get("concentration").asBoolean)
        assertEquals("3d6", raw.getAsJsonObject("damage").getAsJsonObject("damage_at_slot_level").get("2").asString)
        assertEquals("Wizard", raw.getAsJsonArray("classes").first().asJsonObject.get("name").asString)
        assertEquals("official:classes:wizard", raw.getAsJsonArray("classes").first().asJsonObject.get("id").asString)
        assertEquals("Witch", raw.getAsJsonArray("classes")[1].asJsonObject.get("name").asString)
        assertTrue(!raw.getAsJsonArray("classes")[1].asJsonObject.has("id"))
    }

    @Test
    fun buildMonster_writesExpandedMonsterJsonAndImage() {
        val detail = factory.build(
            MonsterDraft(
                basics = ResourceBasicsDraft(
                    type = ResourceType.MONSTER,
                    name = "Moon Drake",
                    description = "A lunar dragonkin.",
                    imageUrl = "https://example.com/moon-drake.png"
                ),
                size = "Large",
                type = "dragon",
                alignment = "chaotic neutral",
                armorClass = 15,
                hitPoints = 85,
                hitDice = "10d10 + 30",
                speed = mapOf("walk" to "30 ft.", "fly" to "60 ft."),
                abilityScores = AbilityScoresDraft(18, 14, 16, 10, 12, 15),
                proficiencies = listOf(MonsterProficiencyDraft("Saving Throw: DEX", 5)),
                damageVulnerabilities = listOf("radiant"),
                damageResistances = listOf("cold"),
                damageImmunities = listOf("necrotic"),
                conditionImmunities = listOf(ResourceReferenceDraft("Charmed", "official:conditions:charmed")),
                senses = mapOf("darkvision" to "120 ft.", "passive Perception" to "14"),
                languages = "Draconic",
                challengeRating = "5",
                xp = 1800,
                traits = listOf(NamedTextDraft("Moonlit Hide", "The drake glows faintly.")),
                actions = listOf(NamedTextDraft("Bite", "Melee Weapon Attack.")),
                reactions = listOf(NamedTextDraft("Lunar Rebuke", "The drake lashes back.")),
                legendaryActions = listOf(NamedTextDraft("Wing Beat", "The drake moves half its speed."))
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:monsters:moon-drake-42", detail.id)
        assertEquals("https://example.com/moon-drake.png", detail.resource.imageUrl)
        assertEquals("https://example.com/moon-drake.png", raw.get("image").asString)
        assertEquals("Large", raw.get("size").asString)
        assertEquals("Saving Throw: DEX", raw.getAsJsonArray("proficiencies")[0].asJsonObject.getAsJsonObject("proficiency").get("name").asString)
        assertEquals("official:conditions:charmed", raw.getAsJsonArray("condition_immunities")[0].asJsonObject.get("id").asString)
        assertEquals("Lunar Rebuke", raw.getAsJsonArray("reactions")[0].asJsonObject.get("name").asString)
        assertEquals("Wing Beat", raw.getAsJsonArray("legendary_actions")[0].asJsonObject.get("name").asString)
    }

    @Test
    fun buildCondition_writesStructuredConditionJson() {
        val detail = factory.build(
            ConditionDraft(
                basics = ResourceBasicsDraft(ResourceType.CONDITION, "Dazed", "A dazed creature is limited."),
                description = listOf("The creature cannot take reactions.", "It has disadvantage on checks.")
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:conditions:dazed-42", detail.id)
        assertEquals(ResourceType.CONDITION, detail.resource.type)
        assertEquals("The creature cannot take reactions.", raw.getAsJsonArray("desc")[0].asString)
        assertEquals("Description", detail.sections.single().title)
    }

    @Test
    fun buildFeat_writesPrerequisitesAndDescription() {
        val detail = factory.build(
            FeatDraft(
                basics = ResourceBasicsDraft(ResourceType.FEAT, "Pact Adept", "A pact-enhancing feat."),
                prerequisites = listOf("5th level", "Eldritch Blast cantrip"),
                description = listOf("You learn an eldritch technique.")
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:feats:pact-adept-42", detail.id)
        assertEquals(ResourceType.FEAT, detail.resource.type)
        assertEquals("5th level", raw.getAsJsonArray("prerequisites")[0].asJsonObject.get("desc").asString)
        assertEquals("You learn an eldritch technique.", raw.getAsJsonArray("desc")[0].asString)
    }

    @Test
    fun buildMagicItem_writesCategoryRarityAndDescription() {
        val detail = factory.build(
            MagicItemDraft(
                basics = ResourceBasicsDraft(ResourceType.MAGIC_ITEM, "Clockwork Amulet", "A small amulet."),
                equipmentCategory = "Wondrous Items",
                rarity = "Common",
                description = listOf("The amulet steadies fate.")
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:magic-items:clockwork-amulet-42", detail.id)
        assertEquals(ResourceType.MAGIC_ITEM, detail.resource.type)
        assertEquals("Wondrous Items", raw.getAsJsonObject("equipment_category").get("name").asString)
        assertEquals("Common", raw.getAsJsonObject("rarity").get("name").asString)
        assertEquals("The amulet steadies fate.", raw.getAsJsonArray("desc")[0].asString)
    }

    @Test
    fun buildEquipment_writesRendererCompatibleEquipmentJson() {
        val detail = factory.build(
            EquipmentDraft(
                basics = ResourceBasicsDraft(ResourceType.EQUIPMENT, "Moonblade", "A silvered blade."),
                equipmentCategory = ResourceReferenceDraft("Weapon", "official:equipment-categories:weapon"),
                gearCategory = "",
                costQuantity = 150,
                costUnit = "gp",
                weight = 3,
                description = listOf("A blade that hums under moonlight."),
                weaponCategory = "Martial",
                weaponRange = "Melee",
                categoryRange = "Martial Melee Weapons",
                damageDice = "1d8",
                damageType = ResourceReferenceDraft("Slashing", "official:damage-types:slashing"),
                twoHandedDamageDice = "1d10",
                properties = listOf(ResourceReferenceDraft("Versatile", "official:weapon-properties:versatile")),
                rangeNormal = 5,
                rangeLong = null,
                armorCategory = "",
                armorBase = null,
                armorDexBonus = false,
                armorMaxBonus = null,
                strMinimum = null,
                stealthDisadvantage = false,
                toolCategory = "",
                contents = listOf(EquipmentQuantityDraft("Moonstone", 1))
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:equipment:moonblade-42", detail.id)
        assertEquals(ResourceType.EQUIPMENT, detail.resource.type)
        assertEquals("official:equipment-categories:weapon", raw.getAsJsonObject("equipment_category").get("id").asString)
        assertEquals(150, raw.getAsJsonObject("cost").get("quantity").asInt)
        assertEquals("1d8", raw.getAsJsonObject("damage").get("damage_dice").asString)
        assertEquals("official:damage-types:slashing", raw.getAsJsonObject("damage").getAsJsonObject("damage_type").get("id").asString)
        assertEquals("1d10", raw.getAsJsonObject("two_handed_damage").get("damage_dice").asString)
        assertEquals("official:weapon-properties:versatile", raw.getAsJsonArray("properties")[0].asJsonObject.get("id").asString)
        assertEquals("Moonstone", raw.getAsJsonArray("contents")[0].asJsonObject.getAsJsonObject("equipment").get("name").asString)
    }

    @Test
    fun buildBackground_writesRendererCompatibleBackgroundJson() {
        val detail = factory.build(
            BackgroundDraft(
                basics = ResourceBasicsDraft(ResourceType.BACKGROUND, "Moon Acolyte", "A moon temple servant."),
                startingProficiencies = listOf(
                    ResourceReferenceDraft("Insight", "official:proficiencies:skill-insight")
                ),
                languageOptions = listOf("Choose two languages."),
                startingEquipment = listOf(EquipmentQuantityDraft("Holy symbol", 1)),
                startingEquipmentOptions = listOf("Choose a prayer book or moon chart."),
                feature = NamedTextDraft("Lunar Shelter", "You can find shelter in moon temples."),
                personalityTraits = listOf("I speak in quiet omens."),
                ideals = listOf("Change. All things move in phases."),
                bonds = listOf("My temple is my family."),
                flaws = listOf("I trust omens over evidence.")
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:backgrounds:moon-acolyte-42", detail.id)
        assertEquals(ResourceType.BACKGROUND, detail.resource.type)
        assertEquals("official:proficiencies:skill-insight", raw.getAsJsonArray("starting_proficiencies")[0].asJsonObject.get("id").asString)
        assertEquals("Choose two languages.", raw.getAsJsonArray("_language_choice_descriptions")[0].asString)
        assertEquals("Holy symbol", raw.getAsJsonArray("starting_equipment")[0].asJsonObject.getAsJsonObject("equipment").get("name").asString)
        assertEquals("Lunar Shelter", raw.getAsJsonObject("feature").get("name").asString)
        assertEquals("I speak in quiet omens.", raw.getAsJsonObject("personality_traits").getAsJsonObject("from").getAsJsonArray("options")[0].asJsonObject.get("string").asString)
    }

    @Test
    fun buildRuleSection_writesRendererCompatibleRuleSectionJson() {
        val detail = factory.build(
            RuleSectionDraft(
                basics = ResourceBasicsDraft(ResourceType.RULE_SECTION, "Cover", "Walls and other obstacles."),
                parentRule = ResourceReferenceDraft("Combat", "official:rules:combat"),
                description = listOf("Walls, trees, creatures, and other obstacles can provide cover during combat."),
                subsections = listOf(ResourceReferenceDraft("Half Cover", "custom:rule-sections:half-cover-42"))
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:rule-sections:cover-42", detail.id)
        assertEquals(ResourceType.RULE_SECTION, detail.resource.type)
        assertEquals("official:rules:combat", raw.getAsJsonObject("rule").get("id").asString)
        assertEquals("Walls, trees, creatures, and other obstacles can provide cover during combat.", raw.getAsJsonArray("desc")[0].asString)
        assertEquals("custom:rule-sections:half-cover-42", raw.getAsJsonArray("subsections")[0].asJsonObject.get("id").asString)
    }

    @Test
    fun buildRule_writesRendererCompatibleRuleJson() {
        val detail = factory.build(
            RuleDraft(
                basics = ResourceBasicsDraft(ResourceType.RULE, "Combat Options", "Optional combat procedures."),
                description = listOf("These rules expand tactical combat."),
                subsectionReferences = listOf(ResourceReferenceDraft("Cover", "custom:rule-sections:cover-42")),
                sections = listOf(NamedTextDraft("Flanking", "When two allies threaten a foe, they may flank it."))
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:rules:combat-options-42", detail.id)
        assertEquals(ResourceType.RULE, detail.resource.type)
        assertEquals("These rules expand tactical combat.", raw.getAsJsonArray("desc")[0].asString)
        assertEquals("custom:rule-sections:cover-42", raw.getAsJsonArray("subsections")[0].asJsonObject.get("id").asString)
        assertEquals("Flanking", raw.getAsJsonArray("subsections")[1].asJsonObject.get("name").asString)
        assertEquals("When two allies threaten a foe, they may flank it.", raw.getAsJsonArray("subsections")[1].asJsonObject.getAsJsonArray("desc")[0].asString)
    }

    @Test
    fun buildFoundationResource_writesStructuredDescriptionAndSection() {
        val detail = factory.build(
            FoundationResourceDraft(
                basics = ResourceBasicsDraft(ResourceType.MAGIC_SCHOOL, "Chronomancy", "Magic of time."),
                description = listOf("Chronomancy bends moments and timelines.")
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:magic-schools:chronomancy-42", detail.id)
        assertEquals(ResourceType.MAGIC_SCHOOL, detail.resource.type)
        assertEquals("Chronomancy bends moments and timelines.", raw.getAsJsonArray("desc")[0].asString)
        assertEquals("MAGIC_SCHOOL", raw.get("resource_type").asString)
        assertEquals("Description", detail.sections.single().title)
    }

    @Test
    fun buildTrait_writesDescriptionProficienciesChoicesAndSubtraits() {
        val detail = factory.build(
            TraitDraft(
                basics = ResourceBasicsDraft(ResourceType.TRAIT, "Fey Step", "A fey movement trait."),
                description = listOf("You can briefly step through the Feywild."),
                proficiencies = listOf("Stealth", "Perception"),
                choices = listOf("Choose 1 skill proficiency."),
                subtraits = listOf(NamedTextDraft("Autumn Step", "Creatures are charmed by your arrival."))
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:traits:fey-step-42", detail.id)
        assertEquals(ResourceType.TRAIT, detail.resource.type)
        assertEquals("Stealth", raw.getAsJsonArray("proficiencies")[0].asJsonObject.get("name").asString)
        assertEquals("Choose 1 skill proficiency.", raw.getAsJsonArray("_choice_descriptions")[0].asString)
        assertEquals("Autumn Step", raw.getAsJsonArray("_subtraits")[0].asJsonObject.get("name").asString)
    }

    @Test
    fun buildRace_writesRendererCompatibleRaceJson() {
        val detail = factory.build(
            RaceDraft(
                basics = ResourceBasicsDraft(ResourceType.RACE, "Starlit Kin", "A custom lineage."),
                speed = 30,
                abilityBonuses = listOf(AbilityBonusDraft("CHA", 2)),
                alignment = "Usually chaotic good.",
                age = "Starlit kin mature quickly.",
                size = "Medium",
                sizeDescription = "Starlit kin are about the size of humans.",
                languageDescription = "You can speak Common and Celestial.",
                abilityChoices = listOf("Choose one other ability score to increase by 1."),
                languageChoices = listOf("Choose one additional language."),
                traitReferences = listOf(ResourceReferenceDraft("Darkvision", "official:traits:darkvision")),
                traits = listOf(NamedTextDraft("Star Step", "You briefly flicker through starlight.")),
                subraces = listOf(ResourceReferenceDraft("Moon Kin", "custom:subraces:moon-kin-42"))
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:races:starlit-kin-42", detail.id)
        assertEquals(30, raw.get("speed").asInt)
        assertEquals("CHA", raw.getAsJsonArray("ability_bonuses")[0].asJsonObject.getAsJsonObject("ability_score").get("name").asString)
        assertEquals("Choose one other ability score to increase by 1.", raw.getAsJsonArray("_ability_bonus_choice_descriptions")[0].asString)
        assertEquals("official:traits:darkvision", raw.getAsJsonArray("_traits")[0].asJsonObject.get("id").asString)
        assertEquals("Star Step", raw.getAsJsonArray("_traits")[1].asJsonObject.get("name").asString)
        assertEquals("custom:subraces:moon-kin-42", raw.getAsJsonArray("subraces")[0].asJsonObject.get("id").asString)
    }

    @Test
    fun buildSubrace_writesRendererCompatibleSubraceJson() {
        val detail = factory.build(
            SubraceDraft(
                basics = ResourceBasicsDraft(ResourceType.SUBRACE, "Moon Kin", "A lunar ancestry."),
                parentRace = ResourceReferenceDraft("Starlit Kin", "custom:races:starlit-kin-42"),
                description = listOf("Moon kin glow softly in darkness."),
                abilityBonuses = listOf(AbilityBonusDraft("WIS", 1)),
                traitReferences = listOf(ResourceReferenceDraft("Keen Senses", "official:traits:keen-senses")),
                traits = listOf(NamedTextDraft("Moonlit Calm", "You resist fear under moonlight."))
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:subraces:moon-kin-42", detail.id)
        assertEquals("custom:races:starlit-kin-42", raw.getAsJsonObject("race").get("id").asString)
        assertEquals("Moon kin glow softly in darkness.", raw.getAsJsonArray("desc")[0].asString)
        assertEquals("WIS", raw.getAsJsonArray("ability_bonuses")[0].asJsonObject.getAsJsonObject("ability_score").get("name").asString)
        assertEquals("official:traits:keen-senses", raw.getAsJsonArray("_racial_traits")[0].asJsonObject.get("id").asString)
        assertEquals("Moonlit Calm", raw.getAsJsonArray("_racial_traits")[1].asJsonObject.get("name").asString)
    }

    @Test
    fun buildFeature_writesRendererCompatibleFeatureJson() {
        val detail = factory.build(
            FeatureDraft(
                basics = ResourceBasicsDraft(ResourceType.FEATURE, "Moonlit Strike", "A lunar combat feature."),
                level = 3,
                parentClass = ResourceReferenceDraft("Ranger", "official:classes:ranger"),
                parentSubclass = ResourceReferenceDraft("Gloom Stalker", "official:subclasses:gloom-stalker"),
                prerequisites = listOf("Moon Kin ancestry"),
                description = listOf("You add radiant force to one weapon attack.")
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:features:moonlit-strike-42", detail.id)
        assertEquals(ResourceType.FEATURE, detail.resource.type)
        assertEquals(3, raw.get("level").asInt)
        assertEquals("official:classes:ranger", raw.getAsJsonObject("class").get("id").asString)
        assertEquals("official:subclasses:gloom-stalker", raw.getAsJsonObject("subclass").get("id").asString)
        assertEquals("Moon Kin ancestry", raw.getAsJsonArray("prerequisites")[0].asJsonObject.get("desc").asString)
        assertEquals("You add radiant force to one weapon attack.", raw.getAsJsonArray("desc")[0].asString)
    }

    @Test
    fun buildClass_writesExpandedClassJson() {
        val detail = factory.build(
            ClassDraft(
                basics = ResourceBasicsDraft(ResourceType.CLASS, "Warden", "A guardian class."),
                hitDie = 10,
                savingThrows = listOf("STR", "WIS"),
                proficiencies = listOf("Light Armor", "Martial Weapons"),
                proficiencyChoices = listOf("Choose 2 skills from Athletics, Insight, and Survival."),
                startingEquipment = listOf(EquipmentQuantityDraft("Longsword", 1)),
                startingEquipmentOptions = listOf("Choose a shield or a second weapon."),
                subclasses = listOf(ResourceReferenceDraft("Oathbound", "custom:subclasses:oathbound-42")),
                spellcastingInfo = listOf(NamedTextDraft("Spellcasting Ability", "Wisdom is your spellcasting ability.")),
                levels = listOf(ClassLevelDraft(1, 2, listOf("Guardian's Mark"))),
                features = listOf(ClassFeatureDraft("Guardian's Mark", 1, listOf("You mark a nearby foe.")))
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:classes:warden-42", detail.id)
        assertEquals(10, raw.get("hit_die").asInt)
        assertEquals("Choose 2 skills from Athletics, Insight, and Survival.", raw.getAsJsonArray("proficiency_choices")[0].asJsonObject.get("desc").asString)
        assertEquals("Longsword", raw.getAsJsonArray("starting_equipment")[0].asJsonObject.getAsJsonObject("equipment").get("name").asString)
        assertEquals("custom:subclasses:oathbound-42", raw.getAsJsonArray("subclasses")[0].asJsonObject.get("id").asString)
        assertEquals("Spellcasting Ability", raw.getAsJsonObject("spellcasting").getAsJsonArray("info")[0].asJsonObject.get("name").asString)
    }

    @Test
    fun buildClass_preservesCustomLevelColumnsForEditingAndRendering() {
        val detail = factory.build(
            ClassDraft(
                basics = ResourceBasicsDraft(ResourceType.CLASS, "Pactbinder", "A pact magic class."),
                hitDie = 8,
                savingThrows = listOf("CHA", "WIS"),
                proficiencies = emptyList(),
                proficiencyChoices = emptyList(),
                startingEquipment = emptyList(),
                startingEquipmentOptions = emptyList(),
                subclasses = emptyList(),
                spellcastingInfo = emptyList(),
                levels = listOf(
                    ClassLevelDraft(
                        level = 2,
                        proficiencyBonus = 2,
                        featureNames = listOf("Eldritch Secrets"),
                        classSpecific = mapOf(
                            "pact_slots" to "2",
                            "slot_level" to "1",
                            "pact_boon" to "true",
                            "pact_die" to "1d6"
                        )
                    )
                ),
                features = emptyList(),
                customLevelColumns = listOf(
                    CustomLevelColumnDraft("pact_slots", "Pact Slots", "number"),
                    CustomLevelColumnDraft("slot_level", "Slot Level", "number"),
                    CustomLevelColumnDraft("pact_boon", "Pact Boon", "checkbox"),
                    CustomLevelColumnDraft("pact_die", "Pact Die", "dice")
                )
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject
        val level = raw.getAsJsonArray("_levels")[0].asJsonObject
        val columns = raw.getAsJsonArray("_custom_level_columns")

        assertEquals("2", level.getAsJsonObject("class_specific").get("pact_slots").asString)
        assertEquals("true", level.getAsJsonObject("class_specific").get("pact_boon").asString)
        assertEquals("pact_slots", columns[0].asJsonObject.get("key").asString)
        assertEquals("Pact Slots", columns[0].asJsonObject.get("label").asString)
        assertEquals("checkbox", columns[2].asJsonObject.get("type").asString)
        assertEquals("dice", columns[3].asJsonObject.get("type").asString)
    }

    @Test
    fun buildSubclass_writesRendererCompatibleSubclassJson() {
        val detail = factory.build(
            SubclassDraft(
                basics = ResourceBasicsDraft(ResourceType.SUBCLASS, "Oathbound", "A custom subclass."),
                parentClass = ResourceReferenceDraft("Warden", "custom:classes:warden-42"),
                flavor = "Warden Calling",
                description = listOf("Oathbound wardens swear themselves to a charge."),
                levels = listOf(ClassLevelDraft(3, 0, listOf("Sacred Charge"))),
                features = listOf(ClassFeatureDraft("Sacred Charge", 3, listOf("You protect your chosen ally."))),
                spells = listOf(SubclassSpellDraft(3, ResourceReferenceDraft("Shield"), "Oathbound Spells"))
            )
        )
        val raw = JsonParser.parseString(detail.rawJson).asJsonObject

        assertEquals("custom:subclasses:oathbound-42", detail.id)
        assertEquals("custom:classes:warden-42", raw.getAsJsonObject("class").get("id").asString)
        assertEquals("Warden Calling", raw.get("subclass_flavor").asString)
        assertEquals("Sacred Charge", raw.getAsJsonArray("_features")[0].asJsonObject.get("name").asString)
        assertEquals("Shield", raw.getAsJsonArray("spells")[0].asJsonObject.getAsJsonObject("spell").get("name").asString)
        assertEquals("Oathbound Spells", raw.getAsJsonArray("spells")[0].asJsonObject.getAsJsonArray("prerequisites")[1].asJsonObject.get("name").asString)
    }
}
