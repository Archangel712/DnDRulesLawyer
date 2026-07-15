package com.example.dnd_ruleslawyer.presentation.detail

import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.testing.ruleDetail
import com.example.dnd_ruleslawyer.testing.ruleResource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleDetailHtmlRendererTest {
    private val renderer = RuleDetailHtmlRenderer()

    @Test
    fun render_wrapsAndEscapesGenericContent() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(name = "Unknown <Rule>"),
                rawJson = "{ not valid json"
            )
        )

        assertTrue(html.contains("<main class=\"phb\">"))
        assertTrue(html.contains("Unknown &lt;Rule&gt;"))
    }

    @Test
    fun renderSpell_linksImportantReferences() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.SPELL),
                rawJson = """
                {
                  "name": "Fireball",
                  "level": 3,
                  "school": { "name": "Evocation", "url": "/api/magic-schools/evocation" },
                  "casting_time": "1 action",
                  "range": "150 feet",
                  "components": ["V", "S"],
                  "duration": "Instantaneous",
                  "damage": { "damage_type": { "name": "Fire", "url": "/api/damage-types/fire" } },
                  "classes": [{ "name": "Wizard", "url": "/api/classes/wizard" }],
                  "desc": ["A bright streak flashes."]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("class=\"resource-link\""))
        assertTrue(html.contains("official%3Amagic-schools%3Aevocation"))
        assertTrue(html.contains("official%3Adamage-types%3Afire"))
        assertTrue(html.contains("official%3Aclasses%3Awizard"))
    }

    @Test
    fun renderMonster_usesCorrectNegativeAbilityModifiers() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.MONSTER),
                rawJson = """
                {
                  "name": "Test Monster",
                  "armor_class": [{ "value": 10 }],
                  "hit_points": 1,
                  "hit_dice": "1d4",
                  "speed": { "walk": "30 ft." },
                  "strength": 9,
                  "dexterity": 7,
                  "constitution": 10,
                  "intelligence": 11,
                  "wisdom": 13,
                  "charisma": 15,
                  "actions": [],
                  "special_abilities": []
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("9 (-1)"))
        assertTrue(html.contains("7 (-2)"))
        assertTrue(html.contains("15 (+2)"))
    }

    @Test
    fun renderMonster_displaysImageProficienciesAndLinkedConditionImmunities() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.MONSTER),
                rawJson = """
                {
                  "name": "Moon Drake",
                  "image": "https://example.com/moon-drake.png",
                  "size": "Large",
                  "type": "dragon",
                  "alignment": "chaotic neutral",
                  "armor_class": [{ "value": 15 }],
                  "hit_points": 85,
                  "hit_dice": "10d10 + 30",
                  "speed": { "walk": "30 ft.", "fly": "60 ft." },
                  "strength": 18,
                  "dexterity": 14,
                  "constitution": 16,
                  "intelligence": 10,
                  "wisdom": 12,
                  "charisma": 15,
                  "proficiencies": [
                    { "proficiency": { "name": "Saving Throw: DEX" }, "value": 5 },
                    { "proficiency": { "name": "Skill: Perception" }, "value": 4 }
                  ],
                  "condition_immunities": [
                    { "name": "Charmed", "url": "/api/conditions/charmed" }
                  ],
                  "senses": { "darkvision": "120 ft.", "passive Perception": "14" },
                  "languages": "Draconic",
                  "challenge_rating": "5",
                  "xp": 1800,
                  "actions": [],
                  "special_abilities": [],
                  "reactions": [
                    { "name": "Lunar Rebuke", "desc": "The drake lashes back." }
                  ],
                  "legendary_actions": [
                    { "name": "Wing Beat", "desc": "The drake moves half its speed." }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("src=\"https://example.com/moon-drake.png\""))
        assertTrue(html.contains("Saving Throws"))
        assertTrue(html.contains("DEX +5"))
        assertTrue(html.contains("Skills"))
        assertTrue(html.contains("Perception +4"))
        assertTrue(html.contains("official%3Aconditions%3Acharmed"))
        assertTrue(html.contains("Lunar Rebuke"))
        assertTrue(html.contains("Wing Beat"))
    }

    @Test
    fun renderClass_usesCompactSpellSlotHeadersAndHidesRawBooleans() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.CLASS),
                rawJson = """
                {
                  "name": "Druid",
                  "hit_die": 8,
                  "_levels": [
                    {
                      "level": 1,
                      "prof_bonus": 2,
                      "features": [{ "name": "Druidic" }, { "name": "Spellcasting" }],
                      "spellcasting": {
                        "cantrips_known": 2,
                        "spell_slots_level_1": 2
                      },
                      "class_specific": {
                        "wild_shape_fly": false
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Spell Slots per Spell Level"))
        assertTrue(html.contains("Cantrips<br />Known"))
        assertFalse(html.contains("Spell Slots Level 1"))
        assertFalse(html.contains("false"))
    }

    @Test
    fun renderClass_displaysSubclassesEquipmentAndSpellcasting() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.CLASS),
                rawJson = """
                {
                  "name": "Warden",
                  "hit_die": 10,
                  "saving_throws": [{ "name": "STR" }, { "name": "WIS" }],
                  "proficiencies": [{ "name": "Light Armor" }],
                  "proficiency_choices": [{ "desc": "Choose 2 skills from Athletics and Insight." }],
                  "starting_equipment": [
                    { "equipment": { "name": "Longsword" }, "quantity": 1 }
                  ],
                  "starting_equipment_options": [{ "desc": "Choose a shield or a second weapon." }],
                  "subclasses": [
                    { "name": "Oathbound", "id": "custom:subclasses:oathbound-42" }
                  ],
                  "spellcasting": {
                    "info": [
                      {
                        "name": "Spellcasting Ability",
                        "desc": ["Wisdom is your spellcasting ability."]
                      }
                    ]
                  },
                  "_levels": [],
                  "_features": []
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Choose 2 skills from Athletics and Insight."))
        assertTrue(html.contains("1x Longsword"))
        assertTrue(html.contains("Choose a shield or a second weapon."))
        assertTrue(html.contains("custom%3Asubclasses%3Aoathbound-42"))
        assertTrue(html.contains("Spellcasting Ability"))
        assertTrue(html.contains("Wisdom is your spellcasting ability."))
    }

    @Test
    fun renderClass_displaysWarlockPactMagicSlotsAndEldritchInvocations() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.CLASS),
                rawJson = """
                {
                  "name": "Warlock",
                  "hit_die": 8,
                  "_levels": [
                    {
                      "level": 2,
                      "prof_bonus": 2,
                      "features": [{ "name": "Eldritch Invocations" }],
                      "spellcasting": {
                        "cantrips_known": 2,
                        "spells_known": 3
                      },
                      "class_specific": {
                        "spell_slots": 2,
                        "slot_level": 1,
                        "invocations_known": 2
                      }
                    }
                  ],
                  "_features": [
                    {
                      "name": "Eldritch Invocations",
                      "level": 2,
                      "desc": ["You learn fragments of forbidden knowledge."],
                      "feature_specific": {
                        "subfeature_options": {
                          "from": {
                            "options": [
                              {
                                "option_type": "reference",
                                "item": {
                                  "name": "Agonizing Blast",
                                  "url": "/api/features/agonizing-blast"
                                }
                              },
                              {
                                "option_type": "reference",
                                "item": {
                                  "name": "Armor of Shadows",
                                  "url": "/api/features/armor-of-shadows"
                                }
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Spell<br />Slots"))
        assertTrue(html.contains("Slot<br />Level"))
        assertTrue(html.contains("<td class=\"number spell-column\">2</td>"))
        assertTrue(html.contains("<td class=\"number spell-column\">1</td>"))
        assertTrue(html.contains("Invocations<br />Known"))
        assertTrue(html.contains("Eldritch Invocations"))
        assertTrue(html.contains("official%3Afeatures%3Aagonizing-blast"))
        assertTrue(html.contains("Armor of Shadows"))
        assertFalse(html.contains("<td class=\"number spell-column\">-</td>"))
    }

    @Test
    fun renderClass_displaysCustomClassSpecificColumnsFromEditedResources() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.CLASS),
                rawJson = """
                {
                  "name": "Pactbinder",
                  "hit_die": 8,
                  "_custom_level_columns": [
                    { "key": "pact_slots", "label": "Pact Magic Slots", "type": "number" },
                    { "key": "pact_die", "label": "Pact Die", "type": "dice" }
                  ],
                  "_levels": [
                    {
                      "level": 2,
                      "prof_bonus": 2,
                      "features": [{ "name": "Eldritch Secrets" }],
                      "class_specific": {
                        "pact_slots": "2",
                        "pact_die": "1d6"
                      }
                    }
                  ],
                  "_features": []
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Pact<br />Magic<br />Slots"))
        assertTrue(html.contains("Pact<br />Die"))
        assertTrue(html.contains("<td class=\"number class-specific-column\">2</td>"))
        assertTrue(html.contains("1d6"))
    }

    @Test
    fun renderSubclass_displaysCustomClassSpecificColumnsFromEditedResources() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.SUBCLASS),
                rawJson = """
                {
                  "name": "Moon Pact",
                  "class": { "name": "Pactbinder" },
                  "desc": ["You bind a lunar patron."],
                  "_custom_level_columns": [
                    { "key": "moon_gifts", "label": "Moon Gifts", "type": "number" }
                  ],
                  "_levels": [
                    {
                      "level": 3,
                      "features": [{ "name": "Lunar Gift" }],
                      "class_specific": {
                        "moon_gifts": "2"
                      }
                    }
                  ],
                  "_features": []
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Moon Gifts"))
        assertTrue(html.contains("Lunar Gift"))
        assertTrue(html.contains("<td>2</td>"))
    }

    @Test
    fun renderFeat_displaysPrerequisites() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.FEAT),
                rawJson = """
                {
                  "name": "Eldritch Adept",
                  "prerequisites": [
                    { "desc": "Spellcasting or Pact Magic feature" },
                    { "level": 5 },
                    {
                      "ability_score": { "name": "CHA" },
                      "minimum_score": 13
                    }
                  ],
                  "desc": ["You learn one Eldritch Invocation option."]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Prerequisites"))
        assertTrue(html.contains("Spellcasting or Pact Magic feature"))
        assertTrue(html.contains("Level 5"))
        assertTrue(html.contains("CHA 13"))
        assertTrue(html.contains("You learn one Eldritch Invocation option."))
    }

    @Test
    fun renderEquipment_displaysSpecializedFieldsAndLinks() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.EQUIPMENT),
                rawJson = """
                {
                  "name": "Moonblade",
                  "equipment_category": { "name": "Weapon", "url": "/api/equipment-categories/weapon" },
                  "cost": { "quantity": 150, "unit": "gp" },
                  "weight": 3,
                  "weapon_category": "Martial",
                  "weapon_range": "Melee",
                  "category_range": "Martial Melee Weapons",
                  "damage": {
                    "damage_dice": "1d8",
                    "damage_type": { "name": "Slashing", "url": "/api/damage-types/slashing" }
                  },
                  "two_handed_damage": {
                    "damage_dice": "1d10",
                    "damage_type": { "name": "Slashing", "url": "/api/damage-types/slashing" }
                  },
                  "properties": [
                    { "name": "Versatile", "url": "/api/weapon-properties/versatile" }
                  ],
                  "range": { "normal": 5 },
                  "armor_category": "Light",
                  "armor_class": { "base": 12, "dex_bonus": true, "max_bonus": 2 },
                  "str_minimum": 13,
                  "stealth_disadvantage": true,
                  "contents": [
                    { "equipment": { "name": "Moonstone" }, "quantity": 1 }
                  ],
                  "desc": ["A blade that hums under moonlight."]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Moonblade"))
        assertTrue(html.contains("official%3Aequipment-categories%3Aweapon"))
        assertTrue(html.contains("150 gp"))
        assertTrue(html.contains("3 lb."))
        assertTrue(html.contains("1d8"))
        assertTrue(html.contains("official%3Adamage-types%3Aslashing"))
        assertTrue(html.contains("official%3Aweapon-properties%3Aversatile"))
        assertTrue(html.contains("12 + Dex modifier (max 2)"))
        assertTrue(html.contains("Disadvantage"))
        assertTrue(html.contains("1x Moonstone"))
        assertTrue(html.contains("A blade that hums under moonlight."))
    }

    @Test
    fun renderBackground_displaysFeatureEquipmentAndSuggestedCharacteristics() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.BACKGROUND),
                rawJson = """
                {
                  "name": "Moon Acolyte",
                  "starting_proficiencies": [
                    { "name": "Insight", "url": "/api/proficiencies/skill-insight" }
                  ],
                  "_language_choice_descriptions": ["Choose two languages."],
                  "starting_equipment": [
                    { "equipment": { "name": "Holy symbol" }, "quantity": 1 }
                  ],
                  "starting_equipment_options": [
                    { "desc": "Choose a prayer book or moon chart." }
                  ],
                  "feature": {
                    "name": "Lunar Shelter",
                    "desc": ["You can find shelter in moon temples."]
                  },
                  "personality_traits": {
                    "choose": 2,
                    "type": "personality traits",
                    "from": {
                      "options": [
                        { "option_type": "string", "string": "I speak in quiet omens." }
                      ]
                    }
                  },
                  "ideals": {
                    "choose": 1,
                    "type": "ideals",
                    "from": {
                      "options": [
                        { "option_type": "string", "string": "Change. All things move in phases." }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Moon Acolyte"))
        assertTrue(html.contains("official%3Aproficiencies%3Askill-insight"))
        assertTrue(html.contains("Choose two languages."))
        assertTrue(html.contains("1x Holy symbol"))
        assertTrue(html.contains("Choose a prayer book or moon chart."))
        assertTrue(html.contains("Feature: Lunar Shelter"))
        assertTrue(html.contains("You can find shelter in moon temples."))
        assertTrue(html.contains("Choose 2 personality traits."))
        assertTrue(html.contains("I speak in quiet omens."))
        assertTrue(html.contains("Change. All things move in phases."))
    }

    @Test
    fun renderRuleSection_linksParentRuleAndSubsections() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.RULE_SECTION),
                rawJson = """
                {
                  "name": "Cover",
                  "rule": { "name": "Combat", "url": "/api/rules/combat" },
                  "desc": ["Walls, trees, creatures, and other obstacles can provide cover during combat."],
                  "subsections": [
                    { "name": "Half Cover", "id": "custom:rule-sections:half-cover-42" }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Cover"))
        assertTrue(html.contains("official%3Arules%3Acombat"))
        assertTrue(html.contains("Walls, trees, creatures, and other obstacles can provide cover during combat."))
        assertTrue(html.contains("custom%3Arule-sections%3Ahalf-cover-42"))
    }

    @Test
    fun renderRule_displaysLinkedAndEmbeddedSubsections() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.RULE),
                rawJson = """
                {
                  "name": "Combat Options",
                  "desc": ["These rules expand tactical combat."],
                  "subsections": [
                    { "name": "Cover", "url": "/api/rule-sections/cover" },
                    {
                      "name": "Flanking",
                      "desc": ["When two allies threaten a foe, they may flank it."]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Combat Options"))
        assertTrue(html.contains("These rules expand tactical combat."))
        assertTrue(html.contains("official%3Arule-sections%3Acover"))
        assertTrue(html.contains("Flanking"))
        assertTrue(html.contains("When two allies threaten a foe, they may flank it."))
    }

    @Test
    fun renderTrait_displaysProficienciesChoicesAndSubtraits() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.TRAIT),
                rawJson = """
                {
                  "name": "Fey Step",
                  "desc": ["You can briefly step through the Feywild."],
                  "proficiencies": [{ "name": "Stealth" }],
                  "_choice_descriptions": ["Choose 1 skill proficiency."],
                  "_subtraits": [
                    {
                      "name": "Autumn Step",
                      "desc": ["Creatures are charmed by your arrival."]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Fey Step"))
        assertTrue(html.contains("Proficiencies"))
        assertTrue(html.contains("Stealth"))
        assertTrue(html.contains("Choose 1 skill proficiency."))
        assertTrue(html.contains("Autumn Step"))
        assertTrue(html.contains("Creatures are charmed by your arrival."))
    }

    @Test
    fun renderRace_displaysCustomChoicesAndLinkedTraits() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.RACE),
                rawJson = """
                {
                  "name": "Starlit Kin",
                  "speed": 30,
                  "ability_bonuses": [
                    { "ability_score": { "name": "CHA" }, "bonus": 2 }
                  ],
                  "alignment": "Usually chaotic good.",
                  "age": "Starlit kin mature quickly.",
                  "size": "Medium",
                  "size_description": "About the size of humans.",
                  "language_desc": "You can speak Common and Celestial.",
                  "_ability_bonus_choice_descriptions": ["Choose one other ability score to increase by 1."],
                  "_language_choice_descriptions": ["Choose one additional language."],
                  "_traits": [
                    { "name": "Darkvision", "id": "official:traits:darkvision" },
                    { "name": "Star Step", "desc": ["You briefly flicker through starlight."] }
                  ],
                  "subraces": [
                    { "name": "Moon Kin", "id": "custom:subraces:moon-kin-42" }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("CHA +2"))
        assertTrue(html.contains("Choose one other ability score to increase by 1."))
        assertTrue(html.contains("Choose one additional language."))
        assertTrue(html.contains("official%3Atraits%3Adarkvision"))
        assertTrue(html.contains("Star Step"))
        assertTrue(html.contains("custom%3Asubraces%3Amoon-kin-42"))
    }

    @Test
    fun renderSubrace_linksParentRaceAndShowsCustomTraits() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.SUBRACE),
                rawJson = """
                {
                  "name": "Moon Kin",
                  "race": { "name": "Starlit Kin", "id": "custom:races:starlit-kin-42" },
                  "desc": ["Moon kin glow softly in darkness."],
                  "ability_bonuses": [
                    { "ability_score": { "name": "WIS" }, "bonus": 1 }
                  ],
                  "_racial_traits": [
                    { "name": "Moonlit Calm", "desc": ["You resist fear under moonlight."] }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("custom%3Araces%3Astarlit-kin-42"))
        assertTrue(html.contains("Moon kin glow softly in darkness."))
        assertTrue(html.contains("WIS +1"))
        assertTrue(html.contains("Moonlit Calm"))
        assertTrue(html.contains("You resist fear under moonlight."))
    }

    @Test
    fun renderFeature_linksParentsAndDisplaysPrerequisites() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.FEATURE),
                rawJson = """
                {
                  "name": "Moonlit Strike",
                  "level": 3,
                  "class": { "name": "Ranger", "url": "/api/classes/ranger" },
                  "subclass": { "name": "Gloom Stalker", "id": "official:subclasses:gloom-stalker" },
                  "prerequisites": [{ "desc": "Moon Kin ancestry" }],
                  "desc": ["You add radiant force to one weapon attack."]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Moonlit Strike"))
        assertTrue(html.contains("Level 3"))
        assertTrue(html.contains("official%3Aclasses%3Aranger"))
        assertTrue(html.contains("official%3Asubclasses%3Agloom-stalker"))
        assertTrue(html.contains("Moon Kin ancestry"))
        assertTrue(html.contains("You add radiant force to one weapon attack."))
    }

    @Test
    fun renderSubclass_displaysProgressionFeaturesAndSpells() {
        val html = renderer.render(
            ruleDetail(
                resource = ruleResource(type = ResourceType.SUBCLASS),
                rawJson = """
                {
                  "name": "Oathbound",
                  "class": { "name": "Warden", "id": "custom:classes:warden-42" },
                  "subclass_flavor": "Warden Calling",
                  "desc": ["Oathbound wardens swear themselves to a charge."],
                  "_levels": [
                    {
                      "level": 3,
                      "features": [{ "name": "Sacred Charge" }]
                    }
                  ],
                  "_features": [
                    {
                      "name": "Sacred Charge",
                      "level": 3,
                      "desc": ["You protect your chosen ally."]
                    }
                  ],
                  "spells": [
                    {
                      "spell": { "name": "Shield", "url": "/api/spells/shield" },
                      "prerequisites": [
                        { "type": "level", "name": "Subclass Level 3" },
                        { "type": "feature", "name": "Oathbound Spells" }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(html.contains("Warden Calling"))
        assertTrue(html.contains("custom%3Aclasses%3Awarden-42"))
        assertTrue(html.contains("Oathbound wardens swear themselves to a charge."))
        assertTrue(html.contains("Sacred Charge"))
        assertTrue(html.contains("You protect your chosen ally."))
        assertTrue(html.contains("official%3Aspells%3Ashield"))
    }
}
