package com.example.dnd_ruleslawyer.presentation.create

import com.example.dnd_ruleslawyer.domain.model.ResourceType


data class EditableSectionDraft(val title: String, val body: String)
data class NamedTextDraft(val name: String, val text: String)
data class ResourceReferenceDraft(val name: String, val resourceId: String? = null)
data class CustomLevelColumnDraft(val key: String, val label: String, val type: String)

data class ResourceBasicsDraft(
    val type: ResourceType,
    val name: String,
    val description: String,
    val imageUrl: String? = null
)

sealed interface CustomResourceDraft {
    val basics: ResourceBasicsDraft
}

data class GenericResourceDraft(
    override val basics: ResourceBasicsDraft,
    val sections: List<EditableSectionDraft>
) : CustomResourceDraft

data class HomebreweryResourceDraft(
    override val basics: ResourceBasicsDraft,
    val shareUrl: String
) : CustomResourceDraft

data class SpellDraft(
    override val basics: ResourceBasicsDraft,
    val level: Int,
    val school: ResourceReferenceDraft,
    val castingTime: String,
    val range: String,
    val components: List<String>,
    val material: String,
    val ritual: Boolean,
    val concentration: Boolean,
    val duration: String,
    val attackType: String,
    val savingThrowAbility: String,
    val savingThrowSuccess: String,
    val damageType: ResourceReferenceDraft,
    val damageBySlotLevel: Map<Int, String>,
    val healingBySlotLevel: Map<Int, String>,
    val areaType: String,
    val areaSize: Int?,
    val classes: List<ResourceReferenceDraft>,
    val subclasses: List<ResourceReferenceDraft>,
    val description: List<String>,
    val higherLevel: List<String>
) : CustomResourceDraft

data class MonsterDraft(
    override val basics: ResourceBasicsDraft,
    val size: String,
    val type: String,
    val alignment: String,
    val armorClass: Int?,
    val hitPoints: Int?,
    val hitDice: String,
    val speed: Map<String, String>,
    val abilityScores: AbilityScoresDraft,
    val proficiencies: List<MonsterProficiencyDraft>,
    val damageVulnerabilities: List<String>,
    val damageResistances: List<String>,
    val damageImmunities: List<String>,
    val conditionImmunities: List<ResourceReferenceDraft>,
    val senses: Map<String, String>,
    val languages: String,
    val challengeRating: String,
    val xp: Int?,
    val actions: List<NamedTextDraft>,
    val traits: List<NamedTextDraft>,
    val reactions: List<NamedTextDraft>,
    val legendaryActions: List<NamedTextDraft>
) : CustomResourceDraft

data class ClassDraft(
    override val basics: ResourceBasicsDraft,
    val hitDie: Int,
    val savingThrows: List<String>,
    val proficiencies: List<String>,
    val proficiencyChoices: List<String>,
    val startingEquipment: List<EquipmentQuantityDraft>,
    val startingEquipmentOptions: List<String>,
    val subclasses: List<ResourceReferenceDraft>,
    val spellcastingInfo: List<NamedTextDraft>,
    val levels: List<ClassLevelDraft>,
    val features: List<ClassFeatureDraft>,
    val customLevelColumns: List<CustomLevelColumnDraft> = emptyList()
) : CustomResourceDraft

data class ConditionDraft(
    override val basics: ResourceBasicsDraft,
    val description: List<String>
) : CustomResourceDraft

data class FeatDraft(
    override val basics: ResourceBasicsDraft,
    val prerequisites: List<String>,
    val description: List<String>
) : CustomResourceDraft

data class MagicItemDraft(
    override val basics: ResourceBasicsDraft,
    val equipmentCategory: String,
    val rarity: String,
    val description: List<String>
) : CustomResourceDraft

data class EquipmentDraft(
    override val basics: ResourceBasicsDraft,
    val equipmentCategory: ResourceReferenceDraft,
    val gearCategory: String,
    val costQuantity: Int?,
    val costUnit: String,
    val weight: Int?,
    val description: List<String>,
    val weaponCategory: String,
    val weaponRange: String,
    val categoryRange: String,
    val damageDice: String,
    val damageType: ResourceReferenceDraft,
    val twoHandedDamageDice: String,
    val properties: List<ResourceReferenceDraft>,
    val rangeNormal: Int?,
    val rangeLong: Int?,
    val armorCategory: String,
    val armorBase: Int?,
    val armorDexBonus: Boolean,
    val armorMaxBonus: Int?,
    val strMinimum: Int?,
    val stealthDisadvantage: Boolean,
    val toolCategory: String,
    val contents: List<EquipmentQuantityDraft>
) : CustomResourceDraft

data class BackgroundDraft(
    override val basics: ResourceBasicsDraft,
    val startingProficiencies: List<ResourceReferenceDraft>,
    val languageOptions: List<String>,
    val startingEquipment: List<EquipmentQuantityDraft>,
    val startingEquipmentOptions: List<String>,
    val feature: NamedTextDraft,
    val personalityTraits: List<String>,
    val ideals: List<String>,
    val bonds: List<String>,
    val flaws: List<String>
) : CustomResourceDraft

data class RuleSectionDraft(
    override val basics: ResourceBasicsDraft,
    val parentRule: ResourceReferenceDraft,
    val description: List<String>,
    val subsections: List<ResourceReferenceDraft>
) : CustomResourceDraft

data class RuleDraft(
    override val basics: ResourceBasicsDraft,
    val description: List<String>,
    val subsectionReferences: List<ResourceReferenceDraft>,
    val sections: List<NamedTextDraft>
) : CustomResourceDraft

data class FoundationResourceDraft(
    override val basics: ResourceBasicsDraft,
    val description: List<String>
) : CustomResourceDraft

data class TraitDraft(
    override val basics: ResourceBasicsDraft,
    val description: List<String>,
    val proficiencies: List<String>,
    val choices: List<String>,
    val subtraits: List<NamedTextDraft>
) : CustomResourceDraft

data class RaceDraft(
    override val basics: ResourceBasicsDraft,
    val speed: Int?,
    val abilityBonuses: List<AbilityBonusDraft>,
    val alignment: String,
    val age: String,
    val size: String,
    val sizeDescription: String,
    val languageDescription: String,
    val abilityChoices: List<String>,
    val languageChoices: List<String>,
    val traitReferences: List<ResourceReferenceDraft>,
    val traits: List<NamedTextDraft>,
    val subraces: List<ResourceReferenceDraft>
) : CustomResourceDraft

data class SubraceDraft(
    override val basics: ResourceBasicsDraft,
    val parentRace: ResourceReferenceDraft,
    val description: List<String>,
    val abilityBonuses: List<AbilityBonusDraft>,
    val traitReferences: List<ResourceReferenceDraft>,
    val traits: List<NamedTextDraft>
) : CustomResourceDraft

data class FeatureDraft(
    override val basics: ResourceBasicsDraft,
    val level: Int?,
    val parentClass: ResourceReferenceDraft,
    val parentSubclass: ResourceReferenceDraft,
    val prerequisites: List<String>,
    val description: List<String>
) : CustomResourceDraft

data class SubclassDraft(
    override val basics: ResourceBasicsDraft,
    val parentClass: ResourceReferenceDraft,
    val flavor: String,
    val description: List<String>,
    val levels: List<ClassLevelDraft>,
    val features: List<ClassFeatureDraft>,
    val spells: List<SubclassSpellDraft>,
    val customLevelColumns: List<CustomLevelColumnDraft> = emptyList()
) : CustomResourceDraft

data class AbilityScoresDraft(
    val strength: Int,
    val dexterity: Int,
    val constitution: Int,
    val intelligence: Int,
    val wisdom: Int,
    val charisma: Int
)

data class AbilityBonusDraft(
    val ability: String,
    val bonus: Int
)

data class MonsterProficiencyDraft(
    val name: String,
    val value: Int
)

data class EquipmentQuantityDraft(
    val name: String,
    val quantity: Int
)

data class SubclassSpellDraft(
    val level: Int?,
    val spell: ResourceReferenceDraft,
    val groupName: String
)

data class ClassLevelDraft(
    val level: Int,
    val proficiencyBonus: Int,
    val featureNames: List<String>,
    val spellcasting: Map<String, Int> = emptyMap(),
    val classSpecific: Map<String, String> = emptyMap()
)

data class ClassFeatureDraft(
    val name: String,
    val level: Int,
    val description: List<String>
)
