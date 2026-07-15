package com.example.dnd_ruleslawyer.presentation.create

import android.content.res.ColorStateList
import android.os.Bundle
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.InputType
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.dnd_ruleslawyer.MainActivity
import com.example.dnd_ruleslawyer.R
import com.example.dnd_ruleslawyer.core.json.array
import com.example.dnd_ruleslawyer.core.json.arrayStrings
import com.example.dnd_ruleslawyer.core.json.boolean
import com.example.dnd_ruleslawyer.core.json.int
import com.example.dnd_ruleslawyer.core.json.namedDescriptionList
import com.example.dnd_ruleslawyer.core.json.obj
import com.example.dnd_ruleslawyer.core.json.referenceNameList
import com.example.dnd_ruleslawyer.core.json.string
import com.example.dnd_ruleslawyer.core.json.stringOrNumber
import com.example.dnd_ruleslawyer.data.repository.RulesRepository
import com.example.dnd_ruleslawyer.databinding.FragmentCreateResourceBinding
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSearchFilters
import com.example.dnd_ruleslawyer.domain.model.RuleSource
import com.example.dnd_ruleslawyer.presentation.UIEntryPoint
import com.example.dnd_ruleslawyer.presentation.search.displayName
import com.example.dnd_ruleslawyer.presentation.utils.rawJsonObject
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

private data class StructuredRowField(
    @param:StringRes val labelResId: Int? = null,
    val inputType: Int = InputType.TYPE_CLASS_TEXT,
    val label: String? = null,
    val columnType: StructuredColumnType = StructuredColumnType.TEXT
)

private data class StructuredRowsState(
    val encoded: String,
    val display: String,
    val customColumns: List<CustomLevelColumnDraft> = emptyList()
)

private data class ReferenceInputState(val namesText: String)

private enum class StructuredColumnType(val storageValue: String) {
    TEXT("text"),
    NUMBER("number"),
    CHECKBOX("checkbox"),
    DICE("dice");

    val inputType: Int
        get() = when (this) {
            TEXT -> InputType.TYPE_CLASS_TEXT
            NUMBER -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            CHECKBOX -> InputType.TYPE_CLASS_TEXT
            DICE -> InputType.TYPE_CLASS_TEXT
        }

    companion object {
        fun fromStorage(value: String?): StructuredColumnType =
            entries.firstOrNull { type -> type.storageValue == value } ?: TEXT
    }
}

private enum class SimpleListMode {
    COMMA,
    LINES
}

class CreateResourceFragment: Fragment(R.layout.fragment_create_resource) {
    private var _binding: FragmentCreateResourceBinding? = null
    private val binding get() = _binding!!
    private val resourceTypes = ResourceType.entries.toList()

    private lateinit var repository: RulesRepository
    private val detailFactory = CustomResourceDetailFactory()
    private val foundationResourceTypes = setOf(
        ResourceType.MAGIC_SCHOOL,
        ResourceType.DAMAGE_TYPE,
        ResourceType.ABILITY_SCORE,
        ResourceType.ALIGNMENT,
        ResourceType.LANGUAGE,
        ResourceType.WEAPON_PROPERTY,
        ResourceType.EQUIPMENT_CATEGORY,
        ResourceType.SKILL,
        ResourceType.PROFICIENCY
    )
    private var linkedSpellSchool: ResourceReferenceDraft? = null
    private var linkedSpellDamageType: ResourceReferenceDraft? = null
    private val linkedSpellClasses = linkedMapOf<String, ResourceReferenceDraft>()
    private val linkedSpellSubclasses = linkedMapOf<String, ResourceReferenceDraft>()
    private val linkedMonsterConditionImmunities = linkedMapOf<String, ResourceReferenceDraft>()
    private val linkedRaceTraits = linkedMapOf<String, ResourceReferenceDraft>()
    private val linkedRaceSubraces = linkedMapOf<String, ResourceReferenceDraft>()
    private var linkedSubraceParentRace: ResourceReferenceDraft? = null
    private val linkedSubraceTraits = linkedMapOf<String, ResourceReferenceDraft>()
    private var linkedFeatureClass: ResourceReferenceDraft? = null
    private var linkedFeatureSubclass: ResourceReferenceDraft? = null
    private val linkedClassSubclasses = linkedMapOf<String, ResourceReferenceDraft>()
    private var linkedSubclassClass: ResourceReferenceDraft? = null
    private var linkedEquipmentCategory: ResourceReferenceDraft? = null
    private var linkedEquipmentDamageType: ResourceReferenceDraft? = null
    private val linkedEquipmentProperties = linkedMapOf<String, ResourceReferenceDraft>()
    private val linkedBackgroundProficiencies = linkedMapOf<String, ResourceReferenceDraft>()
    private var linkedRuleSectionParentRule: ResourceReferenceDraft? = null
    private val linkedRuleSectionSubsections = linkedMapOf<String, ResourceReferenceDraft>()
    private val linkedRuleSubsections = linkedMapOf<String, ResourceReferenceDraft>()
    private val editingResourceId: String?
        get() = arguments?.getString(ARG_EDIT_RESOURCE_ID)

    private val isHomebreweryMode: Boolean
        get() = binding.createModeTabs.selectedTabPosition == MODE_HOMEBREWERY

    private val selectedResourceType: ResourceType
        get() = resourceTypes[binding.resourceTypeSpinner.selectedItemPosition]

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentCreateResourceBinding.bind(view)
        repository = UIEntryPoint.rulesRepository(requireContext())

        setupBackNavigation()
        setupInputs()
        loadEditingResourceIfNeeded()

        binding.saveResourceButton.setOnClickListener {
            if (!validateInputs()) return@setOnClickListener

            val detail = buildDetailForSave()

            viewLifecycleOwner.lifecycleScope.launch {
                val editId = editingResourceId
                val saved = if (editId == null) {
                    repository.addLocalRule(detail)
                    true
                } else {
                    repository.editLocalRule(detail)
                }

                if (!saved) {
                    Toast.makeText(requireContext(), R.string.edit_resource_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                (requireActivity() as MainActivity).showSearch()
                startActivity(UIEntryPoint.createRuleDetailIntent(requireContext(), detail.id))
            }
        }
    }

    private fun loadEditingResourceIfNeeded() {
        val editId = editingResourceId ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val detail = repository.getRuleDetail(editId)
            if (detail?.resource?.source != RuleSource.CUSTOM) {
                Toast.makeText(requireContext(), R.string.edit_resource_failed, Toast.LENGTH_SHORT).show()
                (requireActivity() as MainActivity).showSearch()
                return@launch
            }

            populateFromDetail(detail)
        }
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    (requireActivity() as MainActivity).showSearch()
                }
            }
        )
    }

    private fun setupInputs() {
        binding.resourceTypeSpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                resourceTypes.map { it.displayName() }
        )

        binding.createModeTabs.getTabAt(MODE_CUSTOM)?.select()
        binding.createModeTabs.visibility = if (editingResourceId == null) View.VISIBLE else View.GONE
        binding.createModeTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateModeVisibility()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        binding.resourceTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                updateModeVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        updateModeVisibility()
        setupSpellReferenceLinking()
        setupMonsterReferenceLinking()
        setupRaceReferenceLinking()
        setupFeatureReferenceLinking()
        setupClassReferenceLinking()
        setupEquipmentReferenceLinking()
        setupBackgroundReferenceLinking()
        setupRuleReferenceLinking()
        setupReferenceFieldTriggers()
        setupSpellComponentInputs()
        setupChoiceEditors()
        setupStructuredEditors()
        setupListEditors()
        setupKeyValueEditors()
        setupNamedTextEditors()
        setupAbilityScoreEditor()
    }

    private fun setupSpellComponentInputs() {
        val updateMaterialVisibility = {
            binding.spellMaterialInput.visibility =
                if (binding.spellMaterialComponentInput.isChecked) View.VISIBLE else View.GONE
        }

        binding.spellMaterialComponentInput.setOnCheckedChangeListener { _, _ -> updateMaterialVisibility() }
        updateMaterialVisibility()
    }

    private fun spellComponentsFromInputs(): List<String> =
        buildList {
            if (binding.spellVerbalInput.isChecked) add("V")
            if (binding.spellSomaticInput.isChecked) add("S")
            if (binding.spellMaterialComponentInput.isChecked) add("M")
        }

    private fun setupReferenceFieldTriggers() {
        listOf(
            binding.spellSchoolInput to binding.spellSchoolLinkButton,
            binding.spellDamageTypeInput to binding.spellDamageTypeLinkButton,
            binding.spellClassesInput to binding.spellClassesLinkButton,
            binding.spellSubclassesInput to binding.spellSubclassesLinkButton,
            binding.monsterConditionImmunitiesInput to binding.monsterConditionImmunitiesLinkButton,
            binding.raceTraitsInput to binding.raceTraitsLinkButton,
            binding.raceSubracesInput to binding.raceSubracesLinkButton,
            binding.subraceParentRaceInput to binding.subraceParentRaceLinkButton,
            binding.subraceTraitsInput to binding.subraceTraitsLinkButton,
            binding.featureClassInput to binding.featureClassLinkButton,
            binding.featureSubclassInput to binding.featureSubclassLinkButton,
            binding.classSubclassesInput to binding.classSubclassesLinkButton,
            binding.subclassClassInput to binding.subclassClassLinkButton,
            binding.equipmentCategoryInput to binding.equipmentCategoryLinkButton,
            binding.equipmentDamageTypeInput to binding.equipmentDamageTypeLinkButton,
            binding.equipmentPropertiesInput to binding.equipmentPropertiesLinkButton,
            binding.backgroundProficienciesInput to binding.backgroundProficienciesLinkButton,
            binding.ruleSectionParentRuleInput to binding.ruleSectionParentRuleLinkButton,
            binding.ruleSectionSubsectionsInput to binding.ruleSectionSubsectionsLinkButton,
            binding.ruleSubsectionsInput to binding.ruleSubsectionsLinkButton
        ).forEach { (input, button) ->
            input.isFocusable = false
            input.isCursorVisible = false
            input.inputType = InputType.TYPE_NULL
            input.setOnClickListener { button.performClick() }
        }
    }

    private fun setupStructuredEditors() {
        val quantityItemFields = listOf(
            StructuredRowField(R.string.create_structured_quantity, InputType.TYPE_CLASS_NUMBER),
            StructuredRowField(R.string.create_structured_item)
        )
        val levelValueFields = listOf(
            StructuredRowField(R.string.create_structured_level, InputType.TYPE_CLASS_NUMBER),
            StructuredRowField(R.string.create_structured_value)
        )
        val nameBonusFields = listOf(
            StructuredRowField(R.string.create_structured_name),
            StructuredRowField(R.string.create_structured_bonus, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
        )
        val abilityBonusFields = listOf(
            StructuredRowField(R.string.create_structured_ability),
            StructuredRowField(R.string.create_structured_bonus, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
        )
        val classLevelFields = listOf(
            StructuredRowField(R.string.create_structured_level, InputType.TYPE_CLASS_NUMBER),
            StructuredRowField(R.string.create_structured_proficiency_bonus, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED),
            StructuredRowField(R.string.create_structured_features)
        )
        val subclassLevelFields = listOf(
            StructuredRowField(R.string.create_structured_level, InputType.TYPE_CLASS_NUMBER),
            StructuredRowField(R.string.create_structured_features)
        )
        val featureFields = listOf(
            StructuredRowField(R.string.create_structured_name),
            StructuredRowField(R.string.create_structured_level, InputType.TYPE_CLASS_NUMBER),
            StructuredRowField(R.string.create_structured_description, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        )
        val subclassSpellFields = listOf(
            StructuredRowField(R.string.create_structured_level, InputType.TYPE_CLASS_NUMBER),
            StructuredRowField(R.string.create_structured_spell),
            StructuredRowField(R.string.create_structured_group)
        )

        setupStructuredRowsEditor(binding.spellDamageScalingInput, R.string.create_spell_damage_scaling_editor, levelValueFields)
        setupStructuredRowsEditor(binding.spellHealingScalingInput, R.string.create_spell_healing_scaling_editor, levelValueFields)
        setupStructuredRowsEditor(binding.monsterProficienciesInput, R.string.create_monster_proficiencies_editor, nameBonusFields)
        setupStructuredRowsEditor(binding.classStartingEquipmentInput, R.string.create_class_starting_equipment_editor, quantityItemFields)
        setupStructuredRowsEditor(
            binding.classLevelsInput,
            R.string.create_class_levels_editor,
            classLevelFields,
            customColumnsAllowed = true
        )
        setupStructuredRowsEditor(binding.classFeaturesInput, R.string.create_class_features_editor, featureFields, blockRows = true)
        setupStructuredRowsEditor(
            binding.subclassLevelsInput,
            R.string.create_subclass_levels_editor,
            subclassLevelFields,
            customColumnsAllowed = true
        )
        setupStructuredRowsEditor(binding.subclassFeaturesInput, R.string.create_subclass_features_editor, featureFields, blockRows = true)
        setupStructuredRowsEditor(binding.subclassSpellsInput, R.string.create_subclass_spells_editor, subclassSpellFields)
        setupStructuredRowsEditor(binding.equipmentContentsInput, R.string.create_equipment_contents_editor, quantityItemFields)
        setupStructuredRowsEditor(binding.backgroundStartingEquipmentInput, R.string.create_background_starting_equipment_editor, quantityItemFields)
        setupStructuredRowsEditor(binding.raceAbilityBonusesInput, R.string.create_race_ability_bonuses_editor, abilityBonusFields)
        setupStructuredRowsEditor(binding.subraceAbilityBonusesInput, R.string.create_subrace_ability_bonuses_editor, abilityBonusFields)
    }

    private fun setupListEditors() {
        setupSimpleListEditor(binding.monsterDamageVulnerabilitiesInput, R.string.create_monster_damage_vulnerabilities_editor, SimpleListMode.COMMA)
        setupSimpleListEditor(binding.monsterDamageResistancesInput, R.string.create_monster_damage_resistances_editor, SimpleListMode.COMMA)
        setupSimpleListEditor(binding.monsterDamageImmunitiesInput, R.string.create_monster_damage_immunities_editor, SimpleListMode.COMMA)
        setupSimpleListEditor(binding.classSavingThrowsInput, R.string.create_class_saving_throws_editor, SimpleListMode.COMMA)
        setupSimpleListEditor(binding.classProficienciesInput, R.string.create_class_proficiencies_editor, SimpleListMode.COMMA)
        setupSimpleListEditor(binding.classProficiencyChoicesInput, R.string.create_class_proficiency_choices_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.classStartingEquipmentOptionsInput, R.string.create_class_starting_equipment_options_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.featurePrerequisitesInput, R.string.create_feature_prerequisites_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.featPrerequisitesInput, R.string.create_feat_prerequisites_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.backgroundLanguageOptionsInput, R.string.create_background_language_options_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.backgroundStartingEquipmentOptionsInput, R.string.create_background_starting_equipment_options_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.backgroundPersonalityTraitsInput, R.string.create_background_personality_traits_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.backgroundIdealsInput, R.string.create_background_ideals_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.backgroundBondsInput, R.string.create_background_bonds_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.backgroundFlawsInput, R.string.create_background_flaws_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.traitProficienciesInput, R.string.create_trait_proficiencies_editor, SimpleListMode.COMMA)
        setupSimpleListEditor(binding.traitChoicesInput, R.string.create_trait_choices_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.raceAbilityChoicesInput, R.string.create_race_ability_choices_editor, SimpleListMode.LINES)
        setupSimpleListEditor(binding.raceLanguageChoicesInput, R.string.create_race_language_choices_editor, SimpleListMode.LINES)
    }

    private fun setupKeyValueEditors() {
        setupKeyValueEditor(binding.monsterSpeedInput, R.string.create_monster_speed_editor)
        setupKeyValueEditor(binding.monsterSensesInput, R.string.create_monster_senses_editor)
    }

    private fun setupNamedTextEditors() {
        setupNamedTextEditor(binding.monsterTraitsInput, R.string.create_monster_traits_editor)
        setupNamedTextEditor(binding.monsterActionsInput, R.string.create_monster_actions_editor)
        setupNamedTextEditor(binding.monsterReactionsInput, R.string.create_monster_reactions_editor)
        setupNamedTextEditor(binding.monsterLegendaryActionsInput, R.string.create_monster_legendary_actions_editor)
        setupNamedTextEditor(binding.classSpellcastingInfoInput, R.string.create_class_spellcasting_info_editor)
        setupNamedTextEditor(binding.ruleSectionsInput, R.string.create_rule_sections_editor)
        setupNamedTextEditor(binding.traitSubtraitsInput, R.string.create_trait_subtraits_editor)
        setupNamedTextEditor(binding.raceTraitDetailsInput, R.string.create_race_trait_details_editor)
        setupNamedTextEditor(binding.subraceTraitDetailsInput, R.string.create_subrace_trait_details_editor)
    }

    private fun setupAbilityScoreEditor() {
        binding.monsterAbilityScoresInput.isFocusable = false
        binding.monsterAbilityScoresInput.isCursorVisible = false
        binding.monsterAbilityScoresInput.inputType = InputType.TYPE_NULL
        binding.monsterAbilityScoresInput.setOnClickListener {
            showAbilityScoresDialog(binding.monsterAbilityScoresInput)
        }
    }

    private fun setupChoiceEditors() {
        setupChoiceEditor(binding.spellAttackTypeInput, R.string.create_spell_attack_type_editor, listOf("melee", "ranged"))
        setupChoiceEditor(binding.spellSaveAbilityInput, R.string.create_spell_save_ability_editor, abilityAbbreviations())
        setupChoiceEditor(binding.spellSaveSuccessInput, R.string.create_spell_save_success_editor, listOf("none", "half", "other"))
        setupChoiceEditor(binding.spellAreaTypeInput, R.string.create_spell_area_type_editor, listOf("cone", "cube", "cylinder", "line", "sphere"))
        setupChoiceEditor(binding.monsterSizeInput, R.string.create_monster_size_editor, listOf("Tiny", "Small", "Medium", "Large", "Huge", "Gargantuan"))
        setupChoiceEditor(binding.monsterAlignmentInput, R.string.create_monster_alignment_editor, alignmentOptions())
        setupChoiceEditor(binding.raceSizeInput, R.string.create_race_size_editor, listOf("Small", "Medium"))
        setupChoiceEditor(binding.equipmentCostUnitInput, R.string.create_equipment_cost_unit_editor, listOf("cp", "sp", "ep", "gp", "pp"))
        setupChoiceEditor(binding.equipmentWeaponCategoryInput, R.string.create_equipment_weapon_category_editor, listOf("Simple", "Martial"))
        setupChoiceEditor(binding.equipmentWeaponRangeInput, R.string.create_equipment_weapon_range_editor, listOf("Melee", "Ranged"))
        setupChoiceEditor(binding.equipmentArmorCategoryInput, R.string.create_equipment_armor_category_editor, listOf("Light", "Medium", "Heavy", "Shield"))
    }

    private fun setupSpellReferenceLinking() {
        binding.spellSchoolLinkButton.setOnClickListener {
            showSingleResourceToggleDialog(
                titleResId = R.string.create_reference_link_school,
                allowedTypes = setOf(ResourceType.MAGIC_SCHOOL),
                input = binding.spellSchoolInput,
                linkedReference = linkedSpellSchool
            ) { reference ->
                linkedSpellSchool = reference
            }
        }

        binding.spellDamageTypeLinkButton.setOnClickListener {
            showSingleResourceToggleDialog(
                titleResId = R.string.create_reference_link_damage_type,
                allowedTypes = setOf(ResourceType.DAMAGE_TYPE),
                input = binding.spellDamageTypeInput,
                linkedReference = linkedSpellDamageType
            ) { reference ->
                linkedSpellDamageType = reference
            }
        }

        binding.spellClassesLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_classes,
                allowedTypes = setOf(ResourceType.CLASS),
                input = binding.spellClassesInput,
                linkedReferences = linkedSpellClasses
            ) { references ->
                linkedSpellClasses.clear()
                linkedSpellClasses.putAll(references)
            }
        }

        binding.spellSubclassesLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_subclasses,
                allowedTypes = setOf(ResourceType.SUBCLASS),
                input = binding.spellSubclassesInput,
                linkedReferences = linkedSpellSubclasses
            ) { references ->
                linkedSpellSubclasses.clear()
                linkedSpellSubclasses.putAll(references)
            }
        }
    }

    private fun setupMonsterReferenceLinking() {
        binding.monsterConditionImmunitiesLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_condition_immunities,
                allowedTypes = setOf(ResourceType.CONDITION),
                input = binding.monsterConditionImmunitiesInput,
                linkedReferences = linkedMonsterConditionImmunities
            ) { references ->
                linkedMonsterConditionImmunities.clear()
                linkedMonsterConditionImmunities.putAll(references)
            }
        }
    }

    private fun setupRaceReferenceLinking() {
        binding.raceTraitsLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_traits,
                allowedTypes = setOf(ResourceType.TRAIT),
                input = binding.raceTraitsInput,
                linkedReferences = linkedRaceTraits
            ) { references ->
                linkedRaceTraits.clear()
                linkedRaceTraits.putAll(references)
            }
        }

        binding.raceSubracesLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_subrace_list,
                allowedTypes = setOf(ResourceType.SUBRACE),
                input = binding.raceSubracesInput,
                linkedReferences = linkedRaceSubraces
            ) { references ->
                linkedRaceSubraces.clear()
                linkedRaceSubraces.putAll(references)
            }
        }

        binding.subraceParentRaceLinkButton.setOnClickListener {
            showSingleResourceToggleDialog(
                titleResId = R.string.create_reference_link_parent_race,
                allowedTypes = setOf(ResourceType.RACE),
                input = binding.subraceParentRaceInput,
                linkedReference = linkedSubraceParentRace
            ) { reference ->
                linkedSubraceParentRace = reference
            }
        }

        binding.subraceTraitsLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_traits,
                allowedTypes = setOf(ResourceType.TRAIT),
                input = binding.subraceTraitsInput,
                linkedReferences = linkedSubraceTraits
            ) { references ->
                linkedSubraceTraits.clear()
                linkedSubraceTraits.putAll(references)
            }
        }
    }

    private fun setupFeatureReferenceLinking() {
        binding.featureClassLinkButton.setOnClickListener {
            showSingleResourceToggleDialog(
                titleResId = R.string.create_reference_link_feature_class,
                allowedTypes = setOf(ResourceType.CLASS),
                input = binding.featureClassInput,
                linkedReference = linkedFeatureClass
            ) { reference ->
                linkedFeatureClass = reference
            }
        }

        binding.featureSubclassLinkButton.setOnClickListener {
            showSingleResourceToggleDialog(
                titleResId = R.string.create_reference_link_feature_subclass,
                allowedTypes = setOf(ResourceType.SUBCLASS),
                input = binding.featureSubclassInput,
                linkedReference = linkedFeatureSubclass
            ) { reference ->
                linkedFeatureSubclass = reference
            }
        }
    }

    private fun setupClassReferenceLinking() {
        binding.classSubclassesLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_class_subclasses,
                allowedTypes = setOf(ResourceType.SUBCLASS),
                input = binding.classSubclassesInput,
                linkedReferences = linkedClassSubclasses
            ) { references ->
                linkedClassSubclasses.clear()
                linkedClassSubclasses.putAll(references)
            }
        }

        binding.subclassClassLinkButton.setOnClickListener {
            showSingleResourceToggleDialog(
                titleResId = R.string.create_reference_link_subclass_class,
                allowedTypes = setOf(ResourceType.CLASS),
                input = binding.subclassClassInput,
                linkedReference = linkedSubclassClass
            ) { reference ->
                linkedSubclassClass = reference
            }
        }
    }

    private fun setupEquipmentReferenceLinking() {
        binding.equipmentCategoryLinkButton.setOnClickListener {
            showSingleResourceToggleDialog(
                titleResId = R.string.create_reference_link_equipment_category,
                allowedTypes = setOf(ResourceType.EQUIPMENT_CATEGORY),
                input = binding.equipmentCategoryInput,
                linkedReference = linkedEquipmentCategory
            ) { reference ->
                linkedEquipmentCategory = reference
            }
        }

        binding.equipmentDamageTypeLinkButton.setOnClickListener {
            showSingleResourceToggleDialog(
                titleResId = R.string.create_reference_link_damage_type,
                allowedTypes = setOf(ResourceType.DAMAGE_TYPE),
                input = binding.equipmentDamageTypeInput,
                linkedReference = linkedEquipmentDamageType
            ) { reference ->
                linkedEquipmentDamageType = reference
            }
        }

        binding.equipmentPropertiesLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_weapon_properties,
                allowedTypes = setOf(ResourceType.WEAPON_PROPERTY),
                input = binding.equipmentPropertiesInput,
                linkedReferences = linkedEquipmentProperties
            ) { references ->
                linkedEquipmentProperties.clear()
                linkedEquipmentProperties.putAll(references)
            }
        }
    }

    private fun setupBackgroundReferenceLinking() {
        binding.backgroundProficienciesLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_background_proficiencies,
                allowedTypes = setOf(ResourceType.PROFICIENCY),
                input = binding.backgroundProficienciesInput,
                linkedReferences = linkedBackgroundProficiencies
            ) { references ->
                linkedBackgroundProficiencies.clear()
                linkedBackgroundProficiencies.putAll(references)
            }
        }
    }

    private fun setupRuleReferenceLinking() {
        binding.ruleSectionParentRuleLinkButton.setOnClickListener {
            showSingleResourceToggleDialog(
                titleResId = R.string.create_reference_link_parent_rule,
                allowedTypes = setOf(ResourceType.RULE),
                input = binding.ruleSectionParentRuleInput,
                linkedReference = linkedRuleSectionParentRule
            ) { reference ->
                linkedRuleSectionParentRule = reference
            }
        }

        binding.ruleSectionSubsectionsLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_rule_subsections,
                allowedTypes = setOf(ResourceType.RULE_SECTION),
                input = binding.ruleSectionSubsectionsInput,
                linkedReferences = linkedRuleSectionSubsections
            ) { references ->
                linkedRuleSectionSubsections.clear()
                linkedRuleSectionSubsections.putAll(references)
            }
        }

        binding.ruleSubsectionsLinkButton.setOnClickListener {
            showMultiResourceToggleDialog(
                titleResId = R.string.create_reference_link_rule_subsections,
                allowedTypes = setOf(ResourceType.RULE_SECTION),
                input = binding.ruleSubsectionsInput,
                linkedReferences = linkedRuleSubsections
            ) { references ->
                linkedRuleSubsections.clear()
                linkedRuleSubsections.putAll(references)
            }
        }
    }

    private fun setupStructuredRowsEditor(
        input: EditText,
        @StringRes titleResId: Int,
        fields: List<StructuredRowField>,
        blockRows: Boolean = false,
        customColumnsAllowed: Boolean = false
    ) {
        input.isFocusable = false
        input.isCursorVisible = false
        input.inputType = InputType.TYPE_NULL
        input.setOnClickListener {
            showTableEditor(input, titleResId, fields, blockRows, customColumnsAllowed)
        }
    }

    private fun showTableEditor(
        input: EditText,
        @StringRes titleResId: Int,
        fields: List<StructuredRowField>,
        blockRows: Boolean,
        customColumnsAllowed: Boolean
    ) {
        TableEditorDialogFragment.show(
            fragmentManager = childFragmentManager,
            title = getString(titleResId),
            encoded = input.structuredValue(),
            columns = fields.mapIndexed { index, field -> field.toTableColumnSpec(index) },
            blockRows = blockRows,
            customColumnsAllowed = customColumnsAllowed,
            customColumns = input.structuredCustomColumns()
        ) { result ->
            input.setStructuredValue(
                encoded = result.encoded,
                display = result.display,
                customColumns = result.customColumns
            )
        }
    }

    private fun dialogPrimaryButton(text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(requireContext()).apply {
            this.text = text
            isAllCaps = false
            minHeight = dp(40)
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(dialogThemeColor(com.google.android.material.R.attr.colorPrimary))
            setTextColor(dialogThemeColor(com.google.android.material.R.attr.colorOnPrimary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(4), 0, dp(8))
            }
            setOnClickListener { onClick() }
        }

    private fun dialogSecondaryButton(text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(requireContext()).apply {
            this.text = text
            isAllCaps = false
            minHeight = dp(36)
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(dialogThemeColor(com.google.android.material.R.attr.colorSurface))
            strokeColor = ColorStateList.valueOf(dialogThemeColor(com.google.android.material.R.attr.colorOutline))
            strokeWidth = dp(1)
            setTextColor(dialogThemeColor(com.google.android.material.R.attr.colorPrimary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(4), 0, dp(8))
            }
            setOnClickListener { onClick() }
        }

    private fun dialogRowsScroll(content: View, heightDp: Int = 320): ScrollView =
        ScrollView(requireContext()).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * resources.displayMetrics.density).toInt()
            )
        }

    private fun dialogThemeColor(attr: Int): Int =
        MaterialColors.getColor(requireContext(), attr, 0)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun setupSimpleListEditor(input: EditText, @StringRes titleResId: Int, mode: SimpleListMode) {
        input.isFocusable = false
        input.isCursorVisible = false
        input.inputType = InputType.TYPE_NULL
        input.setOnClickListener { showSimpleListDialog(input, titleResId, mode) }
    }

    private fun showSimpleListDialog(input: EditText, @StringRes titleResId: Int, mode: SimpleListMode) {
        val values = input.structuredValue()
            .toSimpleList(mode)
            .ifEmpty { listOf("") }
            .toMutableList()
        val rowInputs = mutableListOf<EditText>()
        val rowsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun captureRows() {
            rowInputs.forEachIndexed { index, editText ->
                values[index] = editText.text.toString()
            }
        }

        fun renderRows() {
            rowsContainer.removeAllViews()
            rowInputs.clear()
            values.forEachIndexed { index, value ->
                val rowLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 8, 0, 12)
                }
                val valueInput = EditText(requireContext()).apply {
                    hint = getString(R.string.create_structured_value)
                    setText(value)
                }
                rowLayout.addView(valueInput)
                rowLayout.addView(
                    dialogSecondaryButton(getString(R.string.create_structured_remove_row)) {
                        values.removeAt(index)
                        if (values.isEmpty()) values += ""
                        renderRows()
                    }
                )
                rowInputs += valueInput
                rowsContainer.addView(rowLayout)
            }
        }

        renderRows()

        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(
                dialogPrimaryButton(getString(R.string.create_structured_add_row)) {
                    captureRows()
                    values += ""
                    renderRows()
                }
            )
            addView(dialogRowsScroll(rowsContainer))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleResId)
            .setView(dialogLayout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val finalValues = rowInputs
                    .map { editText -> editText.text.toString().trim() }
                    .filter { value -> value.isNotBlank() }
                input.setStructuredValue(
                    encoded = finalValues.encodeSimpleList(mode),
                    display = displayReferenceMiniItems(finalValues)
                )
            }
            .show()
    }

    private fun setupKeyValueEditor(input: EditText, @StringRes titleResId: Int) {
        input.isFocusable = false
        input.isCursorVisible = false
        input.inputType = InputType.TYPE_NULL
        input.setOnClickListener { showKeyValueDialog(input, titleResId) }
    }

    private fun showKeyValueDialog(input: EditText, @StringRes titleResId: Int) {
        val rows = input.structuredValue()
            .keyValuePairs()
            .map { (key, value) -> mutableListOf(key, value) }
            .ifEmpty { listOf(mutableListOf("", "")) }
            .toMutableList()
        val rowInputs = mutableListOf<Pair<EditText, EditText>>()
        val rowsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun captureRows() {
            rowInputs.forEachIndexed { index, (keyInput, valueInput) ->
                rows[index] = mutableListOf(keyInput.text.toString(), valueInput.text.toString())
            }
        }

        fun renderRows() {
            rowsContainer.removeAllViews()
            rowInputs.clear()
            rows.forEachIndexed { index, row ->
                val rowLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 8, 0, 12)
                }
                val keyInput = EditText(requireContext()).apply {
                    hint = getString(R.string.create_structured_name)
                    setText(row.getOrNull(0).orEmpty())
                }
                val valueInput = EditText(requireContext()).apply {
                    hint = getString(R.string.create_structured_value)
                    setText(row.getOrNull(1).orEmpty())
                }
                rowLayout.addView(keyInput)
                rowLayout.addView(valueInput)
                rowLayout.addView(
                    dialogSecondaryButton(getString(R.string.create_structured_remove_row)) {
                        rows.removeAt(index)
                        if (rows.isEmpty()) rows += mutableListOf("", "")
                        renderRows()
                    }
                )
                rowInputs += keyInput to valueInput
                rowsContainer.addView(rowLayout)
            }
        }

        renderRows()

        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(
                dialogPrimaryButton(getString(R.string.create_structured_add_row)) {
                    captureRows()
                    rows += mutableListOf("", "")
                    renderRows()
                }
            )
            addView(dialogRowsScroll(rowsContainer))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleResId)
            .setView(dialogLayout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val finalRows = rowInputs
                    .map { (keyInput, valueInput) -> keyInput.text.toString().trim() to valueInput.text.toString().trim() }
                    .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
                input.setStructuredValue(
                    encoded = finalRows.joinToString(", ") { (key, value) -> "$key=$value" },
                    display = finalRows.joinToString("\n") { (key, value) -> "$key: $value" }
                )
            }
            .show()
    }

    private fun setupNamedTextEditor(input: EditText, @StringRes titleResId: Int) {
        input.isFocusable = false
        input.isCursorVisible = false
        input.inputType = InputType.TYPE_NULL
        input.setOnClickListener { showNamedTextDialog(input, titleResId) }
    }

    private fun showNamedTextDialog(input: EditText, @StringRes titleResId: Int) {
        val rows = input.structuredValue()
            .namedTextBlocks()
            .map { item -> mutableListOf(item.name, item.text) }
            .ifEmpty { listOf(mutableListOf("", "")) }
            .toMutableList()
        val rowInputs = mutableListOf<Pair<EditText, EditText>>()
        val rowsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun captureRows() {
            rowInputs.forEachIndexed { index, (nameInput, textInput) ->
                rows[index] = mutableListOf(nameInput.text.toString(), textInput.text.toString())
            }
        }

        fun renderRows() {
            rowsContainer.removeAllViews()
            rowInputs.clear()
            rows.forEachIndexed { index, row ->
                val rowLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 8, 0, 16)
                }
                val nameInput = EditText(requireContext()).apply {
                    hint = getString(R.string.create_structured_name)
                    setText(row.getOrNull(0).orEmpty())
                }
                val textInput = EditText(requireContext()).apply {
                    hint = getString(R.string.create_structured_description)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    minLines = 3
                    gravity = android.view.Gravity.TOP
                    setText(row.getOrNull(1).orEmpty())
                }
                rowLayout.addView(nameInput)
                rowLayout.addView(textInput)
                rowLayout.addView(
                    dialogSecondaryButton(getString(R.string.create_structured_remove_row)) {
                        rows.removeAt(index)
                        if (rows.isEmpty()) rows += mutableListOf("", "")
                        renderRows()
                    }
                )
                rowInputs += nameInput to textInput
                rowsContainer.addView(rowLayout)
            }
        }

        renderRows()

        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(
                dialogPrimaryButton(getString(R.string.create_structured_add_row)) {
                    captureRows()
                    rows += mutableListOf("", "")
                    renderRows()
                }
            )
            addView(dialogRowsScroll(rowsContainer, heightDp = 360))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleResId)
            .setView(dialogLayout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val finalRows = rowInputs
                    .map { (nameInput, textInput) -> nameInput.text.toString().trim() to textInput.text.toString().trim() }
                    .filter { (name, text) -> name.isNotBlank() || text.isNotBlank() }
                input.setStructuredValue(
                    encoded = finalRows.joinToString("\n\n") { (name, text) -> "$name: $text" },
                    display = finalRows.joinToString("\n") { (name, text) ->
                        if (text.isBlank()) name else "$name: ${text.lineSequence().firstOrNull().orEmpty()}"
                    }
                )
            }
            .show()
    }

    private fun setupChoiceEditor(input: EditText, @StringRes titleResId: Int, choices: List<String>) {
        input.isFocusable = false
        input.isCursorVisible = false
        input.inputType = InputType.TYPE_NULL
        input.setOnClickListener { showChoiceDialog(input, titleResId, choices) }
    }

    private fun showChoiceDialog(input: EditText, @StringRes titleResId: Int, choices: List<String>) {
        val context = requireContext()
        val selected = input.structuredValue().ifBlank { input.text.toString() }
        val customInput = EditText(context).apply {
            hint = getString(R.string.create_reference_custom_entry_hint)
            setText(selected.takeUnless { value -> choices.any { choice -> choice.equals(value, ignoreCase = true) } }.orEmpty())
        }
        val listView = ListView(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_list_item_single_choice, choices)
            choiceMode = ListView.CHOICE_MODE_SINGLE
            val selectedIndex = choices.indexOfFirst { choice -> choice.equals(selected, ignoreCase = true) }
            if (selectedIndex >= 0) setItemChecked(selectedIndex, true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (280 * resources.displayMetrics.density).toInt()
            )
        }
        listView.setOnItemClickListener { _, _, _, _ -> customInput.setText("") }

        MaterialAlertDialogBuilder(context)
            .setTitle(titleResId)
            .setView(createReferenceDialogLayout(customInput, listView))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val checkedPosition = listView.checkedItemPosition
                val choice = customInput.text.toString().trim()
                    .takeIf { value -> value.isNotBlank() }
                    ?: choices.getOrNull(checkedPosition).orEmpty()
                input.setStructuredValue(choice, choice)
            }
            .show()
    }

    private fun showAbilityScoresDialog(input: EditText) {
        val currentScores = input.structuredValue()
            .commaSeparatedValues()
            .mapNotNull { value -> value.toIntOrNull() }
        val labels = abilityAbbreviations()
        val scoreInputs = labels.mapIndexed { index, label ->
            EditText(requireContext()).apply {
                hint = label
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(currentScores.getOrNull(index)?.toString().orEmpty())
            }
        }
        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            scoreInputs.forEach { scoreInput -> addView(scoreInput) }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.create_monster_abilities_editor)
            .setView(dialogLayout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val values = scoreInputs.map { scoreInput -> scoreInput.text.toString().trim() }
                input.setStructuredValue(
                    encoded = values.joinToString(", "),
                    display = labels.zip(values).joinToString(", ") { (label, value) -> "$label $value" }
                )
            }
            .show()
    }

    private fun buildDraft(): CustomResourceDraft {
        val basics = ResourceBasicsDraft(
            type = resourceTypes[binding.resourceTypeSpinner.selectedItemPosition],
            name = binding.resourceNameInput.text.toString(),
            description = binding.resourceDescriptionInput.text.toString()
        )

        return if (isHomebreweryMode) {
            HomebreweryResourceDraft(
                basics = basics,
                shareUrl = binding.homebreweryUrlInput.text.toString()
            )
        } else if (basics.type == ResourceType.SPELL) {
            SpellDraft(
                basics = basics,
                level = binding.spellLevelInput.text.toString().toIntOrNull() ?: 0,
                school = singleReference(
                    name = binding.spellSchoolInput.referenceNamesText(),
                    linkedReference = linkedSpellSchool
                ),
                castingTime = binding.spellCastingTimeInput.text.toString(),
                range = binding.spellRangeInput.text.toString(),
                components = spellComponentsFromInputs(),
                material = binding.spellMaterialInput.text.toString(),
                ritual = binding.spellRitualInput.isChecked,
                concentration = binding.spellConcentrationInput.isChecked,
                duration = binding.spellDurationInput.text.toString(),
                attackType = binding.spellAttackTypeInput.structuredValue(),
                savingThrowAbility = binding.spellSaveAbilityInput.structuredValue(),
                savingThrowSuccess = binding.spellSaveSuccessInput.structuredValue(),
                damageType = singleReference(
                    name = binding.spellDamageTypeInput.referenceNamesText(),
                    linkedReference = linkedSpellDamageType
                ),
                damageBySlotLevel = binding.spellDamageScalingInput.structuredValue().slotLevelValues(),
                healingBySlotLevel = binding.spellHealingScalingInput.structuredValue().slotLevelValues(),
                areaType = binding.spellAreaTypeInput.structuredValue(),
                areaSize = binding.spellAreaSizeInput.text.toString().toIntOrNull(),
                classes = multiReferences(binding.spellClassesInput.referenceNamesText(), linkedSpellClasses),
                subclasses = multiReferences(binding.spellSubclassesInput.referenceNamesText(), linkedSpellSubclasses),
                description = binding.spellDescriptionInput.text.toString().paragraphs(),
                higherLevel = binding.spellHigherLevelInput.text.toString().paragraphs()
            )
        } else if (basics.type == ResourceType.MONSTER) {
            MonsterDraft(
                basics = basics.copy(imageUrl = binding.monsterImageUrlInput.text.toString().takeIf { it.isNotBlank() }),
                size = binding.monsterSizeInput.structuredValue(),
                type = binding.monsterTypeInput.text.toString(),
                alignment = binding.monsterAlignmentInput.structuredValue(),
                armorClass = binding.monsterArmorClassInput.text.toString().toIntOrNull(),
                hitPoints = binding.monsterHitPointsInput.text.toString().toIntOrNull(),
                hitDice = binding.monsterHitDiceInput.text.toString(),
                speed = binding.monsterSpeedInput.structuredValue().keyValuePairs(),
                abilityScores = binding.monsterAbilityScoresInput.structuredValue().abilityScores(),
                proficiencies = binding.monsterProficienciesInput.structuredValue().monsterProficiencies(),
                damageVulnerabilities = binding.monsterDamageVulnerabilitiesInput.structuredValue().commaSeparatedValues(),
                damageResistances = binding.monsterDamageResistancesInput.structuredValue().commaSeparatedValues(),
                damageImmunities = binding.monsterDamageImmunitiesInput.structuredValue().commaSeparatedValues(),
                conditionImmunities = multiReferences(
                    binding.monsterConditionImmunitiesInput.referenceNamesText(),
                    linkedMonsterConditionImmunities
                ),
                senses = binding.monsterSensesInput.structuredValue().keyValuePairs(),
                languages = binding.monsterLanguagesInput.text.toString(),
                challengeRating = binding.monsterChallengeRatingInput.text.toString(),
                xp = binding.monsterXpInput.text.toString().toIntOrNull(),
                traits = binding.monsterTraitsInput.structuredValue().namedTextBlocks(),
                actions = binding.monsterActionsInput.structuredValue().namedTextBlocks(),
                reactions = binding.monsterReactionsInput.structuredValue().namedTextBlocks(),
                legendaryActions = binding.monsterLegendaryActionsInput.structuredValue().namedTextBlocks()
            )
        } else if (basics.type == ResourceType.CLASS) {
            ClassDraft(
                basics = basics,
                hitDie = binding.classHitDieInput.text.toString().toIntOrNull() ?: 0,
                savingThrows = binding.classSavingThrowsInput.structuredValue().commaSeparatedValues(),
                proficiencies = binding.classProficienciesInput.structuredValue().commaSeparatedValues(),
                proficiencyChoices = binding.classProficiencyChoicesInput.structuredValue().nonBlankLines(),
                startingEquipment = binding.classStartingEquipmentInput.structuredValue().equipmentQuantities(),
                startingEquipmentOptions = binding.classStartingEquipmentOptionsInput.structuredValue().nonBlankLines(),
                subclasses = multiReferences(binding.classSubclassesInput.referenceNamesText(), linkedClassSubclasses),
                spellcastingInfo = binding.classSpellcastingInfoInput.structuredValue().namedTextBlocks(),
                levels = binding.classLevelsInput.classLevelsFromInput(),
                features = binding.classFeaturesInput.structuredValue().classFeatures(),
                customLevelColumns = binding.classLevelsInput.structuredCustomColumns()
            )
        } else if (basics.type == ResourceType.SUBCLASS) {
            SubclassDraft(
                basics = basics,
                parentClass = singleReference(
                    name = binding.subclassClassInput.referenceNamesText(),
                    linkedReference = linkedSubclassClass
                ),
                flavor = binding.subclassFlavorInput.text.toString(),
                description = binding.subclassDescriptionInput.text.toString().paragraphs(),
                levels = binding.subclassLevelsInput.subclassLevelsFromInput(),
                features = binding.subclassFeaturesInput.structuredValue().classFeatures(),
                spells = binding.subclassSpellsInput.structuredValue().subclassSpells(),
                customLevelColumns = binding.subclassLevelsInput.structuredCustomColumns()
            )
        } else if (basics.type == ResourceType.FEATURE) {
            FeatureDraft(
                basics = basics,
                level = binding.featureLevelInput.text.toString().toIntOrNull(),
                parentClass = singleReference(
                    name = binding.featureClassInput.referenceNamesText(),
                    linkedReference = linkedFeatureClass
                ),
                parentSubclass = singleReference(
                    name = binding.featureSubclassInput.referenceNamesText(),
                    linkedReference = linkedFeatureSubclass
                ),
                prerequisites = binding.featurePrerequisitesInput.structuredValue().nonBlankLines(),
                description = binding.featureDescriptionInput.text.toString().paragraphs()
            )
        } else if (basics.type == ResourceType.CONDITION) {
            ConditionDraft(
                basics = basics,
                description = binding.conditionDescriptionInput.text.toString().paragraphs()
            )
        } else if (basics.type == ResourceType.FEAT) {
            FeatDraft(
                basics = basics,
                prerequisites = binding.featPrerequisitesInput.structuredValue().nonBlankLines(),
                description = binding.featDescriptionInput.text.toString().paragraphs()
            )
        } else if (basics.type == ResourceType.MAGIC_ITEM) {
            MagicItemDraft(
                basics = basics,
                equipmentCategory = binding.magicItemCategoryInput.text.toString(),
                rarity = binding.magicItemRarityInput.text.toString(),
                description = binding.magicItemDescriptionInput.text.toString().paragraphs()
            )
        } else if (basics.type == ResourceType.EQUIPMENT) {
            EquipmentDraft(
                basics = basics,
                equipmentCategory = singleReference(
                    name = binding.equipmentCategoryInput.referenceNamesText(),
                    linkedReference = linkedEquipmentCategory
                ),
                gearCategory = binding.equipmentGearCategoryInput.text.toString(),
                costQuantity = binding.equipmentCostQuantityInput.text.toString().toIntOrNull(),
                costUnit = binding.equipmentCostUnitInput.structuredValue(),
                weight = binding.equipmentWeightInput.text.toString().toIntOrNull(),
                description = binding.equipmentDescriptionInput.text.toString().paragraphs(),
                weaponCategory = binding.equipmentWeaponCategoryInput.structuredValue(),
                weaponRange = binding.equipmentWeaponRangeInput.structuredValue(),
                categoryRange = binding.equipmentCategoryRangeInput.text.toString(),
                damageDice = binding.equipmentDamageDiceInput.text.toString(),
                damageType = singleReference(
                    name = binding.equipmentDamageTypeInput.referenceNamesText(),
                    linkedReference = linkedEquipmentDamageType
                ),
                twoHandedDamageDice = binding.equipmentTwoHandedDamageDiceInput.text.toString(),
                properties = multiReferences(binding.equipmentPropertiesInput.referenceNamesText(), linkedEquipmentProperties),
                rangeNormal = binding.equipmentRangeNormalInput.text.toString().toIntOrNull(),
                rangeLong = binding.equipmentRangeLongInput.text.toString().toIntOrNull(),
                armorCategory = binding.equipmentArmorCategoryInput.structuredValue(),
                armorBase = binding.equipmentArmorBaseInput.text.toString().toIntOrNull(),
                armorDexBonus = binding.equipmentArmorDexBonusInput.isChecked,
                armorMaxBonus = binding.equipmentArmorMaxBonusInput.text.toString().toIntOrNull(),
                strMinimum = binding.equipmentStrMinimumInput.text.toString().toIntOrNull(),
                stealthDisadvantage = binding.equipmentStealthDisadvantageInput.isChecked,
                toolCategory = binding.equipmentToolCategoryInput.text.toString(),
                contents = binding.equipmentContentsInput.structuredValue().equipmentQuantities()
            )
        } else if (basics.type == ResourceType.BACKGROUND) {
            BackgroundDraft(
                basics = basics,
                startingProficiencies = multiReferences(
                    binding.backgroundProficienciesInput.referenceNamesText(),
                    linkedBackgroundProficiencies
                ),
                languageOptions = binding.backgroundLanguageOptionsInput.structuredValue().nonBlankLines(),
                startingEquipment = binding.backgroundStartingEquipmentInput.structuredValue().equipmentQuantities(),
                startingEquipmentOptions = binding.backgroundStartingEquipmentOptionsInput.structuredValue().nonBlankLines(),
                feature = NamedTextDraft(
                    name = binding.backgroundFeatureNameInput.text.toString(),
                    text = binding.backgroundFeatureDescriptionInput.text.toString()
                ),
                personalityTraits = binding.backgroundPersonalityTraitsInput.structuredValue().nonBlankLines(),
                ideals = binding.backgroundIdealsInput.structuredValue().nonBlankLines(),
                bonds = binding.backgroundBondsInput.structuredValue().nonBlankLines(),
                flaws = binding.backgroundFlawsInput.structuredValue().nonBlankLines()
            )
        } else if (basics.type == ResourceType.RULE_SECTION) {
            RuleSectionDraft(
                basics = basics,
                parentRule = singleReference(
                    name = binding.ruleSectionParentRuleInput.referenceNamesText(),
                    linkedReference = linkedRuleSectionParentRule
                ),
                description = binding.ruleSectionDescriptionInput.text.toString().paragraphs(),
                subsections = multiReferences(binding.ruleSectionSubsectionsInput.referenceNamesText(), linkedRuleSectionSubsections)
            )
        } else if (basics.type == ResourceType.RULE) {
            RuleDraft(
                basics = basics,
                description = binding.ruleDescriptionInput.text.toString().paragraphs(),
                subsectionReferences = multiReferences(binding.ruleSubsectionsInput.referenceNamesText(), linkedRuleSubsections),
                sections = binding.ruleSectionsInput.structuredValue().namedTextBlocks()
            )
        } else if (basics.type in foundationResourceTypes) {
            FoundationResourceDraft(
                basics = basics,
                description = binding.foundationDescriptionInput.text.toString().paragraphs()
            )
        } else if (basics.type == ResourceType.TRAIT) {
            TraitDraft(
                basics = basics,
                description = binding.traitDescriptionInput.text.toString().paragraphs(),
                proficiencies = binding.traitProficienciesInput.structuredValue().commaSeparatedValues(),
                choices = binding.traitChoicesInput.structuredValue().nonBlankLines(),
                subtraits = binding.traitSubtraitsInput.structuredValue().namedTextBlocks()
            )
        } else if (basics.type == ResourceType.RACE) {
            RaceDraft(
                basics = basics,
                speed = binding.raceSpeedInput.text.toString().toIntOrNull(),
                abilityBonuses = binding.raceAbilityBonusesInput.structuredValue().abilityBonuses(),
                alignment = binding.raceAlignmentInput.text.toString(),
                age = binding.raceAgeInput.text.toString(),
                size = binding.raceSizeInput.structuredValue(),
                sizeDescription = binding.raceSizeDescriptionInput.text.toString(),
                languageDescription = binding.raceLanguageDescriptionInput.text.toString(),
                abilityChoices = binding.raceAbilityChoicesInput.structuredValue().nonBlankLines(),
                languageChoices = binding.raceLanguageChoicesInput.structuredValue().nonBlankLines(),
                traitReferences = multiReferences(binding.raceTraitsInput.referenceNamesText(), linkedRaceTraits),
                traits = binding.raceTraitDetailsInput.structuredValue().namedTextBlocks(),
                subraces = multiReferences(binding.raceSubracesInput.referenceNamesText(), linkedRaceSubraces)
            )
        } else if (basics.type == ResourceType.SUBRACE) {
            SubraceDraft(
                basics = basics,
                parentRace = singleReference(
                    name = binding.subraceParentRaceInput.referenceNamesText(),
                    linkedReference = linkedSubraceParentRace
                ),
                description = binding.subraceDescriptionInput.text.toString().paragraphs(),
                abilityBonuses = binding.subraceAbilityBonusesInput.structuredValue().abilityBonuses(),
                traitReferences = multiReferences(binding.subraceTraitsInput.referenceNamesText(), linkedSubraceTraits),
                traits = binding.subraceTraitDetailsInput.structuredValue().namedTextBlocks()
            )
        } else {
            GenericResourceDraft(
                basics = basics,
                sections = listOf(
                    EditableSectionDraft(
                        title = "Details",
                        body = binding.genericBodyInput.text.toString()
                    )
                )
            )
        }
    }

    private fun buildDetailForSave(): RuleDetail {
        val builtDetail = detailFactory.build(buildDraft())
        val editId = editingResourceId ?: return builtDetail

        val editedResource = builtDetail.resource.copy(
            id = editId,
            source = RuleSource.CUSTOM
        )

        return builtDetail.copy(
            id = editId,
            resource = editedResource
        )
    }

    private fun populateFromDetail(detail: RuleDetail) {
        val resource = detail.resource
        binding.createModeTabs.getTabAt(MODE_CUSTOM)?.select()
        binding.createModeTabs.visibility = View.GONE
        binding.resourceTypeSpinner.setSelection(resourceTypes.indexOf(resource.type).coerceAtLeast(0))
        binding.resourceNameInput.setText(resource.name)
        binding.resourceDescriptionInput.setText(resource.description)
        updateModeVisibility()

        when (resource.type) {
            ResourceType.SPELL -> populateSpellFields(detail.rawJsonObject())
            ResourceType.MONSTER -> populateMonsterFields(detail.rawJsonObject())
            ResourceType.CLASS -> populateClassFields(detail.rawJsonObject())
            ResourceType.SUBCLASS -> populateSubclassFields(detail.rawJsonObject())
            ResourceType.FEATURE -> populateFeatureFields(detail.rawJsonObject())
            ResourceType.CONDITION -> populateConditionFields(detail.rawJsonObject())
            ResourceType.FEAT -> populateFeatFields(detail.rawJsonObject())
            ResourceType.MAGIC_ITEM -> populateMagicItemFields(detail.rawJsonObject())
            ResourceType.EQUIPMENT -> populateEquipmentFields(detail.rawJsonObject())
            ResourceType.BACKGROUND -> populateBackgroundFields(detail.rawJsonObject())
            ResourceType.RULE_SECTION -> populateRuleSectionFields(detail.rawJsonObject())
            ResourceType.RULE -> populateRuleFields(detail.rawJsonObject(), detail)
            in foundationResourceTypes -> populateFoundationFields(detail)
            ResourceType.TRAIT -> populateTraitFields(detail.rawJsonObject())
            ResourceType.RACE -> populateRaceFields(detail.rawJsonObject())
            ResourceType.SUBRACE -> populateSubraceFields(detail.rawJsonObject())
            else -> binding.genericBodyInput.setText(
                detail.sections
                    .sortedBy { section -> section.order }
                    .joinToString("\n\n") { section -> section.body }
            )
        }
    }

    private fun populateSpellFields(json: JsonObject?) {
        if (json == null) return

        linkedSpellSchool = json.obj("school")?.toReferenceDraft(ResourceType.MAGIC_SCHOOL.endpoint)
        linkedSpellDamageType = json.obj("damage")
            ?.obj("damage_type")
            ?.toReferenceDraft(ResourceType.DAMAGE_TYPE.endpoint)
        linkedSpellClasses.clear()
        linkedSpellClasses.putAll(json.referenceDrafts("classes", ResourceType.CLASS.endpoint).toReferenceMap())
        linkedSpellSubclasses.clear()
        linkedSpellSubclasses.putAll(json.referenceDrafts("subclasses", ResourceType.SUBCLASS.endpoint).toReferenceMap())

        binding.spellLevelInput.setText(json.int("level")?.toString().orEmpty())
        binding.spellSchoolInput.setReferenceInputValue(json.obj("school")?.string("name").orEmpty())
        binding.spellCastingTimeInput.setText(json.string("casting_time").orEmpty())
        binding.spellRangeInput.setText(json.string("range").orEmpty())
        val components = json.arrayStrings("components").map { component -> component.uppercase() }
        binding.spellComponentsInput.setText(components.joinToString(", "))
        binding.spellVerbalInput.isChecked = "V" in components
        binding.spellSomaticInput.isChecked = "S" in components
        binding.spellMaterialComponentInput.isChecked = "M" in components
        binding.spellMaterialInput.visibility =
            if (binding.spellMaterialComponentInput.isChecked) View.VISIBLE else View.GONE
        binding.spellMaterialInput.setText(json.string("material").orEmpty())
        binding.spellRitualInput.isChecked = json.boolean("ritual") ?: false
        binding.spellConcentrationInput.isChecked = json.boolean("concentration") ?: false
        binding.spellDurationInput.setText(json.string("duration").orEmpty())
        binding.spellAttackTypeInput.setText(json.string("attack_type").orEmpty())
        binding.spellSaveAbilityInput.setText(json.obj("dc")?.obj("dc_type")?.string("name").orEmpty())
        binding.spellSaveSuccessInput.setText(json.obj("dc")?.string("dc_success").orEmpty())
        binding.spellDamageTypeInput.setReferenceInputValue(json.obj("damage")?.obj("damage_type")?.string("name").orEmpty())
        binding.spellDamageScalingInput.setText(json.obj("damage")?.obj("damage_at_slot_level").slotLevelInputText())
        binding.spellHealingScalingInput.setText(json.obj("heal_at_slot_level").slotLevelInputText())
        binding.spellAreaTypeInput.setText(json.obj("area_of_effect")?.string("type").orEmpty())
        binding.spellAreaSizeInput.setText(json.obj("area_of_effect")?.int("size")?.toString().orEmpty())
        binding.spellClassesInput.setReferenceInputValue(json.referenceNameList("classes"))
        binding.spellSubclassesInput.setReferenceInputValue(json.referenceNameList("subclasses"))
        binding.spellDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
        binding.spellHigherLevelInput.setText(json.arrayStrings("higher_level").joinToString("\n\n"))
    }

    private fun populateMonsterFields(json: JsonObject?) {
        if (json == null) return

        linkedMonsterConditionImmunities.clear()
        linkedMonsterConditionImmunities.putAll(
            json.referenceDrafts("condition_immunities", ResourceType.CONDITION.endpoint).toReferenceMap()
        )

        val armorClass = json.get("armor_class")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.firstOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.int("value")

        binding.monsterImageUrlInput.setText(json.string("image").orEmpty())
        binding.monsterSizeInput.setText(json.string("size").orEmpty())
        binding.monsterTypeInput.setText(json.string("type").orEmpty())
        binding.monsterAlignmentInput.setText(json.string("alignment").orEmpty())
        binding.monsterArmorClassInput.setText(armorClass?.toString().orEmpty())
        binding.monsterHitPointsInput.setText(json.int("hit_points")?.toString().orEmpty())
        binding.monsterHitDiceInput.setText(json.string("hit_dice").orEmpty())
        binding.monsterSpeedInput.setText(
            json.obj("speed")
                ?.entrySet()
                ?.joinToString(", ") { entry -> "${entry.key}=${entry.value.asString}" }
                .orEmpty()
        )
        binding.monsterAbilityScoresInput.setText(
            listOf(
                json.int("strength"),
                json.int("dexterity"),
                json.int("constitution"),
                json.int("intelligence"),
                json.int("wisdom"),
                json.int("charisma")
            ).joinToString(", ") { value -> value?.toString().orEmpty() }
        )
        binding.monsterProficienciesInput.setText(json.monsterProficiencyInputText())
        binding.monsterDamageVulnerabilitiesInput.setText(json.arrayStrings("damage_vulnerabilities").joinToString(", "))
        binding.monsterDamageResistancesInput.setText(json.arrayStrings("damage_resistances").joinToString(", "))
        binding.monsterDamageImmunitiesInput.setText(json.arrayStrings("damage_immunities").joinToString(", "))
        binding.monsterConditionImmunitiesInput.setReferenceInputValue(json.referenceNameList("condition_immunities"))
        binding.monsterSensesInput.setText(
            json.obj("senses")
                ?.entrySet()
                ?.joinToString(", ") { entry -> "${entry.key}=${entry.value.asString}" }
                .orEmpty()
        )
        binding.monsterLanguagesInput.setText(json.string("languages").orEmpty())
        binding.monsterChallengeRatingInput.setText(json.stringOrNumber("challenge_rating").orEmpty())
        binding.monsterXpInput.setText(json.int("xp")?.toString().orEmpty())
        binding.monsterTraitsInput.setText(json.namedDescriptionList("special_abilities").toInputBlocks())
        binding.monsterActionsInput.setText(json.namedDescriptionList("actions").toInputBlocks())
        binding.monsterReactionsInput.setText(json.namedDescriptionList("reactions").toInputBlocks())
        binding.monsterLegendaryActionsInput.setText(json.namedDescriptionList("legendary_actions").toInputBlocks())
    }

    private fun populateClassFields(json: JsonObject?) {
        if (json == null) return

        linkedClassSubclasses.clear()
        linkedClassSubclasses.putAll(json.referenceDrafts("subclasses", ResourceType.SUBCLASS.endpoint).toReferenceMap())

        binding.classHitDieInput.setText(json.int("hit_die")?.toString().orEmpty())
        binding.classSavingThrowsInput.setText(json.referenceNameList("saving_throws").joinToString(", "))
        binding.classProficienciesInput.setText(json.referenceNameList("proficiencies").joinToString(", "))
        binding.classProficiencyChoicesInput.setText(json.descriptionArrayInputText("proficiency_choices"))
        binding.classStartingEquipmentInput.setText(json.startingEquipmentInputText())
        binding.classStartingEquipmentOptionsInput.setText(json.descriptionArrayInputText("starting_equipment_options"))
        binding.classSubclassesInput.setReferenceInputValue(json.referenceNameList("subclasses"))
        binding.classSpellcastingInfoInput.setText(json.obj("spellcasting")?.namedTextArrayInputBlocks("info").orEmpty())
        val classLevelColumns = json.customLevelColumns()
        val classLevelRows = json.classLevelInputText(classLevelColumns)
        binding.classLevelsInput.setStructuredValue(
            encoded = classLevelRows,
            display = displayStructuredRows(
                parseStructuredRows(classLevelRows, 3 + classLevelColumns.size, blockRows = false),
                classLevelDisplayFields(classLevelColumns)
            ),
            customColumns = classLevelColumns
        )
        binding.classFeaturesInput.setText(
            json.array("_features")
                ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
                ?.joinToString("\n\n") { feature ->
                    val description = feature.arrayStrings("desc").joinToString("\n")
                    "${feature.string("name").orEmpty()} | ${feature.int("level").orEmptyText()} | $description"
                }
                .orEmpty()
        )
    }

    private fun populateSubclassFields(json: JsonObject?) {
        if (json == null) return

        linkedSubclassClass = json.obj("class")?.toReferenceDraft(ResourceType.CLASS.endpoint)

        binding.subclassClassInput.setReferenceInputValue(json.obj("class")?.string("name").orEmpty())
        binding.subclassFlavorInput.setText(json.string("subclass_flavor").orEmpty())
        binding.subclassDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
        val subclassLevelColumns = json.customLevelColumns()
        val subclassLevelRows = json.subclassLevelInputText(subclassLevelColumns)
        binding.subclassLevelsInput.setStructuredValue(
            encoded = subclassLevelRows,
            display = displayStructuredRows(
                parseStructuredRows(subclassLevelRows, 2 + subclassLevelColumns.size, blockRows = false),
                subclassLevelDisplayFields(subclassLevelColumns)
            ),
            customColumns = subclassLevelColumns
        )
        binding.subclassFeaturesInput.setText(
            json.array("_features")
                ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
                ?.joinToString("\n\n") { feature ->
                    val description = feature.arrayStrings("desc").joinToString("\n")
                    "${feature.string("name").orEmpty()} | ${feature.int("level").orEmptyText()} | $description"
                }
                .orEmpty()
        )
        binding.subclassSpellsInput.setText(json.subclassSpellInputText())
    }

    private fun populateFeatureFields(json: JsonObject?) {
        if (json == null) return

        linkedFeatureClass = json.obj("class")?.toReferenceDraft(ResourceType.CLASS.endpoint)
        linkedFeatureSubclass = json.obj("subclass")?.toReferenceDraft(ResourceType.SUBCLASS.endpoint)

        binding.featureLevelInput.setText(json.int("level")?.toString().orEmpty())
        binding.featureClassInput.setReferenceInputValue(json.obj("class")?.string("name").orEmpty())
        binding.featureSubclassInput.setReferenceInputValue(json.obj("subclass")?.string("name").orEmpty())
        binding.featurePrerequisitesInput.setText(json.prerequisiteInputText())
        binding.featureDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
    }

    private fun populateConditionFields(json: JsonObject?) {
        if (json == null) return

        binding.conditionDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
    }

    private fun populateFeatFields(json: JsonObject?) {
        if (json == null) return

        binding.featPrerequisitesInput.setText(json.featPrerequisiteInputText())
        binding.featDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
    }

    private fun populateMagicItemFields(json: JsonObject?) {
        if (json == null) return

        binding.magicItemCategoryInput.setText(json.obj("equipment_category")?.string("name").orEmpty())
        binding.magicItemRarityInput.setText(json.obj("rarity")?.string("name").orEmpty())
        binding.magicItemDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
    }

    private fun populateEquipmentFields(json: JsonObject?) {
        if (json == null) return

        linkedEquipmentCategory = json.obj("equipment_category")?.toReferenceDraft(ResourceType.EQUIPMENT_CATEGORY.endpoint)
        linkedEquipmentDamageType = json.obj("damage")
            ?.obj("damage_type")
            ?.toReferenceDraft(ResourceType.DAMAGE_TYPE.endpoint)
            ?: json.obj("two_handed_damage")
                ?.obj("damage_type")
                ?.toReferenceDraft(ResourceType.DAMAGE_TYPE.endpoint)
        linkedEquipmentProperties.clear()
        linkedEquipmentProperties.putAll(json.referenceDrafts("properties", ResourceType.WEAPON_PROPERTY.endpoint).toReferenceMap())

        binding.equipmentCategoryInput.setReferenceInputValue(json.obj("equipment_category")?.string("name").orEmpty())
        binding.equipmentGearCategoryInput.setText(json.obj("gear_category")?.string("name").orEmpty())
        binding.equipmentCostQuantityInput.setText(json.obj("cost")?.int("quantity")?.toString().orEmpty())
        binding.equipmentCostUnitInput.setText(json.obj("cost")?.string("unit").orEmpty())
        binding.equipmentWeightInput.setText(json.int("weight")?.toString().orEmpty())
        binding.equipmentDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
        binding.equipmentWeaponCategoryInput.setText(json.string("weapon_category").orEmpty())
        binding.equipmentWeaponRangeInput.setText(json.string("weapon_range").orEmpty())
        binding.equipmentCategoryRangeInput.setText(json.string("category_range").orEmpty())
        binding.equipmentDamageDiceInput.setText(json.obj("damage")?.string("damage_dice").orEmpty())
        binding.equipmentDamageTypeInput.setReferenceInputValue(
            json.obj("damage")?.obj("damage_type")?.string("name")
                ?: json.obj("two_handed_damage")?.obj("damage_type")?.string("name")
                ?: ""
        )
        binding.equipmentTwoHandedDamageDiceInput.setText(json.obj("two_handed_damage")?.string("damage_dice").orEmpty())
        binding.equipmentPropertiesInput.setReferenceInputValue(json.referenceNameList("properties"))
        binding.equipmentRangeNormalInput.setText(json.obj("range")?.int("normal")?.toString().orEmpty())
        binding.equipmentRangeLongInput.setText(json.obj("range")?.int("long")?.toString().orEmpty())
        binding.equipmentArmorCategoryInput.setText(json.string("armor_category").orEmpty())
        binding.equipmentArmorBaseInput.setText(json.obj("armor_class")?.int("base")?.toString().orEmpty())
        binding.equipmentArmorDexBonusInput.isChecked = json.obj("armor_class")?.boolean("dex_bonus") ?: false
        binding.equipmentArmorMaxBonusInput.setText(json.obj("armor_class")?.int("max_bonus")?.toString().orEmpty())
        binding.equipmentStrMinimumInput.setText(json.int("str_minimum")?.toString().orEmpty())
        binding.equipmentStealthDisadvantageInput.isChecked = json.boolean("stealth_disadvantage") ?: false
        binding.equipmentToolCategoryInput.setText(json.string("tool_category").orEmpty())
        binding.equipmentContentsInput.setText(json.equipmentQuantityInputText("contents"))
    }

    private fun populateBackgroundFields(json: JsonObject?) {
        if (json == null) return

        linkedBackgroundProficiencies.clear()
        linkedBackgroundProficiencies.putAll(
            json.referenceDrafts("starting_proficiencies", ResourceType.PROFICIENCY.endpoint).toReferenceMap()
        )

        binding.backgroundProficienciesInput.setReferenceInputValue(json.referenceNameList("starting_proficiencies"))
        binding.backgroundLanguageOptionsInput.setText(json.backgroundLanguageOptionInputText())
        binding.backgroundStartingEquipmentInput.setText(json.equipmentQuantityInputText("starting_equipment"))
        binding.backgroundStartingEquipmentOptionsInput.setText(json.descriptionArrayInputText("starting_equipment_options"))
        binding.backgroundFeatureNameInput.setText(json.obj("feature")?.string("name").orEmpty())
        binding.backgroundFeatureDescriptionInput.setText(json.obj("feature")?.arrayStrings("desc")?.joinToString("\n\n").orEmpty())
        binding.backgroundPersonalityTraitsInput.setText(json.choiceOptionsInputText("personality_traits"))
        binding.backgroundIdealsInput.setText(json.choiceOptionsInputText("ideals"))
        binding.backgroundBondsInput.setText(json.choiceOptionsInputText("bonds"))
        binding.backgroundFlawsInput.setText(json.choiceOptionsInputText("flaws"))
    }

    private fun populateRuleSectionFields(json: JsonObject?) {
        if (json == null) return

        linkedRuleSectionParentRule = json.obj("rule")?.toReferenceDraft(ResourceType.RULE.endpoint)
        linkedRuleSectionSubsections.clear()
        linkedRuleSectionSubsections.putAll(json.referenceDrafts("subsections", ResourceType.RULE_SECTION.endpoint).toReferenceMap())

        binding.ruleSectionParentRuleInput.setReferenceInputValue(json.obj("rule")?.string("name").orEmpty())
        binding.ruleSectionDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
        binding.ruleSectionSubsectionsInput.setReferenceInputValue(
            json.linkedReferenceNames("subsections", ResourceType.RULE_SECTION.endpoint).commaSeparatedValues()
        )
    }

    private fun populateRuleFields(json: JsonObject?, detail: RuleDetail) {
        linkedRuleSubsections.clear()

        if (json == null) {
            binding.ruleSectionsInput.setText(
                detail.sections
                    .sortedBy { section -> section.order }
                    .joinToString("\n\n") { section -> "${section.title}: ${section.body}" }
            )
            return
        }

        linkedRuleSubsections.putAll(json.referenceDrafts("subsections", ResourceType.RULE_SECTION.endpoint).toReferenceMap())

        binding.ruleDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
        binding.ruleSubsectionsInput.setReferenceInputValue(
            json.linkedReferenceNames("subsections", ResourceType.RULE_SECTION.endpoint).commaSeparatedValues()
        )
        binding.ruleSectionsInput.setText(json.namedTextArrayInputBlocks("subsections"))
    }

    private fun populateFoundationFields(detail: RuleDetail) {
        val description = detail.rawJsonObject()
            ?.arrayStrings("desc")
            ?.joinToString("\n\n")
            ?.takeIf { it.isNotBlank() }
            ?: detail.sections
                .sortedBy { section -> section.order }
                .joinToString("\n\n") { section -> section.body }

        binding.foundationDescriptionInput.setText(description)
    }

    private fun populateTraitFields(json: JsonObject?) {
        if (json == null) return

        binding.traitDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
        binding.traitProficienciesInput.setText(json.referenceNameList("proficiencies").joinToString(", "))
        binding.traitChoicesInput.setText(json.traitChoiceInputText())
        binding.traitSubtraitsInput.setText(json.traitSubtraitInputText())
    }

    private fun populateRaceFields(json: JsonObject?) {
        if (json == null) return

        linkedRaceTraits.clear()
        linkedRaceTraits.putAll(json.referenceDrafts("_traits", ResourceType.TRAIT.endpoint).toReferenceMap())
        linkedRaceSubraces.clear()
        linkedRaceSubraces.putAll(json.referenceDrafts("subraces", ResourceType.SUBRACE.endpoint).toReferenceMap())

        binding.raceSpeedInput.setText(json.int("speed")?.toString().orEmpty())
        binding.raceAbilityBonusesInput.setText(json.abilityBonusInputText())
        binding.raceAlignmentInput.setText(json.string("alignment").orEmpty())
        binding.raceAgeInput.setText(json.string("age").orEmpty())
        binding.raceSizeInput.setText(json.string("size").orEmpty())
        binding.raceSizeDescriptionInput.setText(json.string("size_description").orEmpty())
        binding.raceLanguageDescriptionInput.setText(json.string("language_desc").orEmpty())
        binding.raceAbilityChoicesInput.setText(json.arrayStrings("_ability_bonus_choice_descriptions").joinToString("\n"))
        binding.raceLanguageChoicesInput.setText(json.arrayStrings("_language_choice_descriptions").joinToString("\n"))
        binding.raceTraitsInput.setReferenceInputValue(
            json.linkedReferenceNames("_traits", ResourceType.TRAIT.endpoint).commaSeparatedValues()
        )
        binding.raceTraitDetailsInput.setText(json.namedTextArrayInputBlocks("_traits"))
        binding.raceSubracesInput.setReferenceInputValue(json.referenceNameList("subraces"))
    }

    private fun populateSubraceFields(json: JsonObject?) {
        if (json == null) return

        linkedSubraceParentRace = json.obj("race")?.toReferenceDraft(ResourceType.RACE.endpoint)
        linkedSubraceTraits.clear()
        linkedSubraceTraits.putAll(json.referenceDrafts("_racial_traits", ResourceType.TRAIT.endpoint).toReferenceMap())

        binding.subraceParentRaceInput.setReferenceInputValue(json.obj("race")?.string("name").orEmpty())
        binding.subraceDescriptionInput.setText(json.arrayStrings("desc").joinToString("\n\n"))
        binding.subraceAbilityBonusesInput.setText(json.abilityBonusInputText())
        binding.subraceTraitsInput.setReferenceInputValue(
            json.linkedReferenceNames("_racial_traits", ResourceType.TRAIT.endpoint).commaSeparatedValues()
        )
        binding.subraceTraitDetailsInput.setText(json.namedTextArrayInputBlocks("_racial_traits"))
    }

    private fun updateModeVisibility() {
        val isManualSpell = !isHomebreweryMode && selectedResourceType == ResourceType.SPELL
        val isManualMonster = !isHomebreweryMode && selectedResourceType == ResourceType.MONSTER
        val isManualClass = !isHomebreweryMode && selectedResourceType == ResourceType.CLASS
        val isManualSubclass = !isHomebreweryMode && selectedResourceType == ResourceType.SUBCLASS
        val isManualFeature = !isHomebreweryMode && selectedResourceType == ResourceType.FEATURE
        val isManualCondition = !isHomebreweryMode && selectedResourceType == ResourceType.CONDITION
        val isManualFeat = !isHomebreweryMode && selectedResourceType == ResourceType.FEAT
        val isManualMagicItem = !isHomebreweryMode && selectedResourceType == ResourceType.MAGIC_ITEM
        val isManualEquipment = !isHomebreweryMode && selectedResourceType == ResourceType.EQUIPMENT
        val isManualBackground = !isHomebreweryMode && selectedResourceType == ResourceType.BACKGROUND
        val isManualRuleSection = !isHomebreweryMode && selectedResourceType == ResourceType.RULE_SECTION
        val isManualRule = !isHomebreweryMode && selectedResourceType == ResourceType.RULE
        val isManualFoundation = !isHomebreweryMode && selectedResourceType in foundationResourceTypes
        val isManualTrait = !isHomebreweryMode && selectedResourceType == ResourceType.TRAIT
        val isManualRace = !isHomebreweryMode && selectedResourceType == ResourceType.RACE
        val isManualSubrace = !isHomebreweryMode && selectedResourceType == ResourceType.SUBRACE

        binding.homebreweryUrlInput.visibility = if (isHomebreweryMode) View.VISIBLE else View.GONE
        binding.spellFieldsContainer.visibility = if (isManualSpell) View.VISIBLE else View.GONE
        binding.monsterFieldsContainer.visibility = if (isManualMonster) View.VISIBLE else View.GONE
        binding.classFieldsContainer.visibility = if (isManualClass) View.VISIBLE else View.GONE
        binding.subclassFieldsContainer.visibility = if (isManualSubclass) View.VISIBLE else View.GONE
        binding.featureFieldsContainer.visibility = if (isManualFeature) View.VISIBLE else View.GONE
        binding.conditionFieldsContainer.visibility = if (isManualCondition) View.VISIBLE else View.GONE
        binding.featFieldsContainer.visibility = if (isManualFeat) View.VISIBLE else View.GONE
        binding.magicItemFieldsContainer.visibility = if (isManualMagicItem) View.VISIBLE else View.GONE
        binding.equipmentFieldsContainer.visibility = if (isManualEquipment) View.VISIBLE else View.GONE
        binding.backgroundFieldsContainer.visibility = if (isManualBackground) View.VISIBLE else View.GONE
        binding.ruleSectionFieldsContainer.visibility = if (isManualRuleSection) View.VISIBLE else View.GONE
        binding.ruleFieldsContainer.visibility = if (isManualRule) View.VISIBLE else View.GONE
        binding.foundationFieldsContainer.visibility = if (isManualFoundation) View.VISIBLE else View.GONE
        binding.traitFieldsContainer.visibility = if (isManualTrait) View.VISIBLE else View.GONE
        binding.raceFieldsContainer.visibility = if (isManualRace) View.VISIBLE else View.GONE
        binding.subraceFieldsContainer.visibility = if (isManualSubrace) View.VISIBLE else View.GONE
        binding.genericBodyInput.visibility =
            if (
                !isHomebreweryMode &&
                !isManualSpell &&
                !isManualMonster &&
                !isManualClass &&
                !isManualSubclass &&
                !isManualFeature &&
                !isManualCondition &&
                !isManualFeat &&
                !isManualMagicItem &&
                !isManualEquipment &&
                !isManualBackground &&
                !isManualRuleSection &&
                !isManualRule &&
                !isManualFoundation &&
                !isManualTrait &&
                !isManualRace &&
                !isManualSubrace
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        fun requireText(view: EditText, messageResId: Int) {
            if (view.text.toString().isBlank()) {
                view.error = getString(messageResId)
                isValid = false
            } else {
                view.error = null
            }
        }

        requireText(binding.resourceNameInput, R.string.create_resource_name_required)

        if (isHomebreweryMode) {
            requireText(binding.homebreweryUrlInput, R.string.create_resource_homebrewery_url_required)
        } else if (selectedResourceType == ResourceType.SPELL) {
            val spellLevel = binding.spellLevelInput.text.toString().toIntOrNull()
            if (spellLevel == null || spellLevel !in 0..9) {
                binding.spellLevelInput.error = getString(R.string.create_spell_level_required)
                isValid = false
            } else {
                binding.spellLevelInput.error = null
            }

            requireText(binding.spellDescriptionInput, R.string.create_spell_description_required)
        } else if (selectedResourceType == ResourceType.MONSTER) {
            val scores = binding.monsterAbilityScoresInput.structuredValue()
                .commaSeparatedValues()
                .mapNotNull { it.toIntOrNull() }

            if (scores.size != 6) {
                binding.monsterAbilityScoresInput.error = getString(R.string.create_monster_abilities_required)
                isValid = false
            } else {
                binding.monsterAbilityScoresInput.error = null
            }
        } else if (selectedResourceType == ResourceType.CLASS) {
            val hitDie = binding.classHitDieInput.text.toString().toIntOrNull()
            if (hitDie == null || hitDie <= 0) {
                binding.classHitDieInput.error = getString(R.string.create_class_hit_die_required)
                isValid = false
            } else {
                binding.classHitDieInput.error = null
            }

            val levels = binding.classLevelsInput.classLevelsFromInput()
            if (levels.isEmpty()) {
                binding.classLevelsInput.error = getString(R.string.create_class_levels_required)
                isValid = false
            } else {
                binding.classLevelsInput.error = null
            }
        } else if (selectedResourceType == ResourceType.SUBCLASS) {
            requireText(binding.subclassDescriptionInput, R.string.create_subclass_description_required)
        } else if (selectedResourceType == ResourceType.FEATURE) {
            requireText(binding.featureDescriptionInput, R.string.create_feature_description_required)
        } else if (selectedResourceType == ResourceType.CONDITION) {
            requireText(binding.conditionDescriptionInput, R.string.create_condition_description_required)
        } else if (selectedResourceType == ResourceType.FEAT) {
            requireText(binding.featDescriptionInput, R.string.create_feat_description_required)
        } else if (selectedResourceType == ResourceType.MAGIC_ITEM) {
            requireText(binding.magicItemDescriptionInput, R.string.create_magic_item_description_required)
        } else if (selectedResourceType == ResourceType.EQUIPMENT) {
            val hasEquipmentDetail = listOf(
                binding.equipmentCategoryInput.text.toString(),
                binding.equipmentGearCategoryInput.text.toString(),
                binding.equipmentCostQuantityInput.text.toString(),
                binding.equipmentCostUnitInput.text.toString(),
                binding.equipmentWeightInput.text.toString(),
                binding.equipmentDescriptionInput.text.toString(),
                binding.equipmentWeaponCategoryInput.text.toString(),
                binding.equipmentWeaponRangeInput.text.toString(),
                binding.equipmentCategoryRangeInput.text.toString(),
                binding.equipmentDamageDiceInput.text.toString(),
                binding.equipmentDamageTypeInput.text.toString(),
                binding.equipmentTwoHandedDamageDiceInput.text.toString(),
                binding.equipmentPropertiesInput.text.toString(),
                binding.equipmentRangeNormalInput.text.toString(),
                binding.equipmentRangeLongInput.text.toString(),
                binding.equipmentArmorCategoryInput.text.toString(),
                binding.equipmentArmorBaseInput.text.toString(),
                binding.equipmentArmorMaxBonusInput.text.toString(),
                binding.equipmentStrMinimumInput.text.toString(),
                binding.equipmentToolCategoryInput.text.toString(),
                binding.equipmentContentsInput.text.toString()
            ).any { value -> value.isNotBlank() } ||
                binding.equipmentArmorDexBonusInput.isChecked ||
                binding.equipmentStealthDisadvantageInput.isChecked

            if (!hasEquipmentDetail) {
                binding.equipmentCategoryInput.error = getString(R.string.create_equipment_required)
                isValid = false
            } else {
                binding.equipmentCategoryInput.error = null
            }
        } else if (selectedResourceType == ResourceType.BACKGROUND) {
            val hasBackgroundDetail = listOf(
                binding.backgroundProficienciesInput.text.toString(),
                binding.backgroundLanguageOptionsInput.text.toString(),
                binding.backgroundStartingEquipmentInput.text.toString(),
                binding.backgroundStartingEquipmentOptionsInput.text.toString(),
                binding.backgroundFeatureNameInput.text.toString(),
                binding.backgroundFeatureDescriptionInput.text.toString(),
                binding.backgroundPersonalityTraitsInput.text.toString(),
                binding.backgroundIdealsInput.text.toString(),
                binding.backgroundBondsInput.text.toString(),
                binding.backgroundFlawsInput.text.toString()
            ).any { value -> value.isNotBlank() }

            if (!hasBackgroundDetail) {
                binding.backgroundFeatureDescriptionInput.error = getString(R.string.create_background_required)
                isValid = false
            } else {
                binding.backgroundFeatureDescriptionInput.error = null
            }
        } else if (selectedResourceType == ResourceType.RULE_SECTION) {
            requireText(binding.ruleSectionDescriptionInput, R.string.create_rule_section_description_required)
        } else if (selectedResourceType == ResourceType.RULE) {
            val hasRuleDetail = listOf(
                binding.ruleDescriptionInput.text.toString(),
                binding.ruleSubsectionsInput.text.toString(),
                binding.ruleSectionsInput.text.toString()
            ).any { value -> value.isNotBlank() }

            if (!hasRuleDetail) {
                binding.ruleDescriptionInput.error = getString(R.string.create_rule_required)
                isValid = false
            } else {
                binding.ruleDescriptionInput.error = null
            }
        } else if (selectedResourceType in foundationResourceTypes) {
            requireText(binding.foundationDescriptionInput, R.string.create_foundation_description_required)
        } else if (selectedResourceType == ResourceType.TRAIT) {
            requireText(binding.traitDescriptionInput, R.string.create_trait_description_required)
        } else if (selectedResourceType == ResourceType.RACE) {
            val hasRaceDetail = listOf(
                binding.raceSpeedInput.text.toString(),
                binding.raceAbilityBonusesInput.text.toString(),
                binding.raceAlignmentInput.text.toString(),
                binding.raceAgeInput.text.toString(),
                binding.raceSizeInput.text.toString(),
                binding.raceSizeDescriptionInput.text.toString(),
                binding.raceLanguageDescriptionInput.text.toString(),
                binding.raceAbilityChoicesInput.text.toString(),
                binding.raceLanguageChoicesInput.text.toString(),
                binding.raceTraitsInput.text.toString(),
                binding.raceTraitDetailsInput.text.toString(),
                binding.raceSubracesInput.text.toString()
            ).any { value -> value.isNotBlank() }

            if (!hasRaceDetail) {
                binding.raceSpeedInput.error = getString(R.string.create_race_required)
                isValid = false
            } else {
                binding.raceSpeedInput.error = null
            }
        } else if (selectedResourceType == ResourceType.SUBRACE) {
            val hasSubraceDetail = listOf(
                binding.subraceParentRaceInput.text.toString(),
                binding.subraceDescriptionInput.text.toString(),
                binding.subraceAbilityBonusesInput.text.toString(),
                binding.subraceTraitsInput.text.toString(),
                binding.subraceTraitDetailsInput.text.toString()
            ).any { value -> value.isNotBlank() }

            if (!hasSubraceDetail) {
                binding.subraceDescriptionInput.error = getString(R.string.create_subrace_required)
                isValid = false
            } else {
                binding.subraceDescriptionInput.error = null
            }
        } else {
            requireText(binding.genericBodyInput, R.string.create_resource_body_required)
        }

        if (!isValid) {
            Toast.makeText(requireContext(), R.string.create_resource_validation_error, Toast.LENGTH_SHORT).show()
        }

        return isValid
    }

    private fun String.commaSeparatedValues(): List<String> =
        split(",")
            .map { value -> value.trim() }
            .filter { value -> value.isNotBlank() }

    private fun String.toSimpleList(mode: SimpleListMode): List<String> =
        when (mode) {
            SimpleListMode.COMMA -> commaSeparatedValues()
            SimpleListMode.LINES -> nonBlankLines()
        }

    private fun List<String>.encodeSimpleList(mode: SimpleListMode): String =
        joinToString(
            separator = when (mode) {
                SimpleListMode.COMMA -> ", "
                SimpleListMode.LINES -> "\n"
            }
        )

    private fun abilityAbbreviations(): List<String> =
        listOf("STR", "DEX", "CON", "INT", "WIS", "CHA")

    private fun alignmentOptions(): List<String> =
        listOf(
            "lawful good",
            "neutral good",
            "chaotic good",
            "lawful neutral",
            "neutral",
            "chaotic neutral",
            "lawful evil",
            "neutral evil",
            "chaotic evil",
            "unaligned",
            "any alignment"
        )

    private fun EditText.structuredValue(): String =
        when (val state = tag) {
            is StructuredRowsState -> state.encoded.takeIf { value -> value.isNotBlank() } ?: text.toString()
            is String -> state.takeIf { value -> value.isNotBlank() } ?: text.toString()
            else -> text.toString()
        }

    private fun EditText.structuredCustomColumns(): List<CustomLevelColumnDraft> =
        (tag as? StructuredRowsState)?.customColumns ?: emptyList()

    private fun EditText.setStructuredValue(
        encoded: String,
        display: String,
        customColumns: List<CustomLevelColumnDraft> = emptyList()
    ) {
        tag = StructuredRowsState(encoded, display, customColumns)
        setText(display)
    }

    private fun StructuredRowField.displayLabel(): String =
        label ?: labelResId?.let { resId -> getString(resId) }.orEmpty()

    private fun StructuredRowField.toTableColumnSpec(index: Int): TableColumnSpec {
        val type = when {
            columnType != StructuredColumnType.TEXT -> TableColumnType.fromStorage(columnType.storageValue)
            inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER -> TableColumnType.NUMBER
            else -> TableColumnType.TEXT
        }
        return TableColumnSpec(
            key = "fixed_$index",
            label = displayLabel(),
            type = type,
            inputType = inputType,
            fixed = true
        )
    }

    private fun EditText.referenceNamesText(): String =
        (tag as? ReferenceInputState)?.namesText ?: text.toString()

    private fun EditText.setReferenceInputValue(names: List<String>) {
        val cleanNames = names.map { name -> name.trim() }.filter { name -> name.isNotBlank() }
        tag = ReferenceInputState(cleanNames.joinToString(", "))
        setText(displayReferenceMiniItems(cleanNames))
    }

    private fun EditText.setReferenceInputValue(name: String) {
        val cleanName = name.trim()
        tag = ReferenceInputState(cleanName)
        setText(cleanName)
    }

    private fun displayReferenceMiniItems(names: List<String>): String =
        names.joinToString("  ") { name -> "[$name]" }

    private fun parseStructuredRows(text: String, fieldCount: Int, blockRows: Boolean): List<List<String>> {
        val rawRows = if (blockRows) {
            text.paragraphs()
        } else {
            text.lines().map { line -> line.trim() }.filter { line -> line.isNotBlank() }
        }

        return rawRows.map { row ->
            val parts = row.split("|", limit = fieldCount).map { part -> part.trim() }
            List(fieldCount) { index -> parts.getOrNull(index).orEmpty() }
        }
    }

    private fun displayStructuredRows(rows: List<List<String>>, fields: List<StructuredRowField>): String =
        rows
            .filter { row -> row.any { value -> value.isNotBlank() } }
            .joinToString("\n") { row ->
                fields.mapIndexedNotNull { index, field ->
                    row.getOrNull(index)
                        ?.takeIf { value -> value.isNotBlank() }
                        ?.let { value -> "${field.displayLabel()}: $value" }
                }.joinToString(", ")
            }

    private fun String.nonBlankLines(): List<String> =
        lines()
            .map { value -> value.trim() }
            .filter { value -> value.isNotBlank() }

    private fun singleReference(name: String, linkedReference: ResourceReferenceDraft?): ResourceReferenceDraft {
        val trimmedName = name.trim()
        val linked = linkedReference?.takeIf { reference ->
            reference.name.normalizedReferenceKey() == trimmedName.normalizedReferenceKey()
        }

        return linked ?: ResourceReferenceDraft(trimmedName)
    }

    private fun multiReferences(
        namesText: String,
        linkedReferences: Map<String, ResourceReferenceDraft>
    ): List<ResourceReferenceDraft> {
        return namesText.commaSeparatedValues().map { name ->
            linkedReferences[name.normalizedReferenceKey()] ?: ResourceReferenceDraft(name)
        }
    }

    private fun showSingleResourceToggleDialog(
        @StringRes titleResId: Int,
        allowedTypes: Set<ResourceType>,
        input: EditText,
        linkedReference: ResourceReferenceDraft?,
        onApplied: (ResourceReferenceDraft?) -> Unit
    ) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val resources = loadLinkableResources(allowedTypes)
            val selectedIds = mutableSetOf<String>()
            val typedName = input.referenceNamesText().trim()

            linkedReference
                ?.resourceId
                ?.takeIf { id -> linkedReference.name.normalizedReferenceKey() == typedName.normalizedReferenceKey() }
                ?.let { id -> selectedIds += id }

            if (selectedIds.isEmpty()) {
                resources.preferredExactMatchFor(typedName)?.let { rule -> selectedIds += rule.id }
            }

            val adapter = ReferenceToggleAdapter(resources.prioritizeSelected(selectedIds), selectedIds)
            val listView = createReferenceToggleList(adapter)
            val customInput = EditText(context).apply {
                hint = getString(R.string.create_reference_custom_entry_hint)
                setText(typedName.takeUnless { resources.preferredExactMatchFor(it) != null }.orEmpty())
            }
            val dialogLayout = createReferenceDialogLayout(customInput, listView)

            listView.setOnItemClickListener { _, _, position, _ ->
                val selectedRule = adapter.getItem(position)
                val wasSelected = selectedRule.id in selectedIds
                selectedIds.clear()
                if (!wasSelected) selectedIds += selectedRule.id
                adapter.notifyDataSetChanged()
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(titleResId)
                .setView(dialogLayout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val selectedRule = resources.firstOrNull { rule -> rule.id in selectedIds }
                    val reference = selectedRule?.toReferenceDraft()
                        ?: customInput.text.toString().trim().takeIf { name -> name.isNotBlank() }?.let { name ->
                            ResourceReferenceDraft(name)
                        }

                    input.setReferenceInputValue(reference?.name.orEmpty())
                    onApplied(reference)
                }
                .show()
        }
    }

    private fun showMultiResourceToggleDialog(
        @StringRes titleResId: Int,
        allowedTypes: Set<ResourceType>,
        input: EditText,
        linkedReferences: Map<String, ResourceReferenceDraft>,
        onApplied: (Map<String, ResourceReferenceDraft>) -> Unit
    ) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val resources = loadLinkableResources(allowedTypes)
            val typedNames = input.referenceNamesText().commaSeparatedValues()
            val selectedIds = initialSelectedIdsForMultiValue(resources, typedNames, linkedReferences).toMutableSet()
            val adapter = ReferenceToggleAdapter(resources.prioritizeSelected(selectedIds), selectedIds)
            val listView = createReferenceToggleList(adapter)
            val customInput = EditText(context).apply {
                hint = getString(R.string.create_reference_custom_entries_hint)
                setText(
                    typedNames
                        .filter { name -> resources.preferredExactMatchFor(name)?.id !in selectedIds }
                        .joinToString(", ")
                )
            }
            val dialogLayout = createReferenceDialogLayout(customInput, listView)

            listView.setOnItemClickListener { _, _, position, _ ->
                val selectedRule = adapter.getItem(position)
                if (selectedRule.id in selectedIds) selectedIds -= selectedRule.id else selectedIds += selectedRule.id
                adapter.notifyDataSetChanged()
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(titleResId)
                .setView(dialogLayout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val selectedReferences = resources
                        .filter { rule -> rule.id in selectedIds }
                        .map { rule -> rule.toReferenceDraft() }
                    val finalNames = customInput.text.toString().commaSeparatedValues().toMutableList()

                    selectedReferences.forEach { reference ->
                        if (finalNames.none { name -> name.normalizedReferenceKey() == reference.name.normalizedReferenceKey() }) {
                            finalNames += reference.name
                        }
                    }

                    input.setReferenceInputValue(finalNames)
                    onApplied(selectedReferences.toReferenceMap())
                }
                .show()
        }
    }

    private suspend fun loadLinkableResources(allowedTypes: Set<ResourceType>): List<RuleResource> {
        val filters = RuleSearchFilters(resourceTypes = allowedTypes)
        return runCatching {
            repository.searchRules("", filters)
        }.getOrElse {
            Toast.makeText(requireContext(), R.string.search_results_error, Toast.LENGTH_SHORT).show()
            emptyList()
        }.sortedWith(
            compareBy<RuleResource> { rule -> rule.name.lowercase() }
                .thenBy { rule -> rule.source.name }
        )
    }

    private fun initialSelectedIdsForMultiValue(
        resources: List<RuleResource>,
        typedNames: List<String>,
        linkedReferences: Map<String, ResourceReferenceDraft>
    ): Set<String> {
        return typedNames.mapNotNull { name ->
            linkedReferences[name.normalizedReferenceKey()]?.resourceId
                ?: resources.preferredExactMatchFor(name)?.id
        }.toSet()
    }

    private fun List<RuleResource>.preferredExactMatchFor(name: String): RuleResource? {
        val normalizedName = name.normalizedReferenceKey()
        if (normalizedName.isBlank()) return null

        return filter { rule -> rule.name.normalizedReferenceKey() == normalizedName }
            .minWithOrNull(
                compareBy<RuleResource> { rule -> if (rule.source == RuleSource.OFFICIAL) 0 else 1 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { rule -> rule.name }
            )
    }

    private fun List<RuleResource>.prioritizeSelected(selectedIds: Set<String>): List<RuleResource> {
        return sortedWith(
            compareByDescending<RuleResource> { rule -> rule.id in selectedIds }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { rule -> rule.name }
                .thenBy { rule -> rule.source.name }
        )
    }

    private fun createReferenceToggleList(adapter: ReferenceToggleAdapter): ListView {
        return ListView(requireContext()).apply {
            this.adapter = adapter
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (360 * resources.displayMetrics.density).toInt()
            )
        }
    }

    private fun createReferenceDialogLayout(customInput: EditText, listView: ListView): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(customInput)
            addView(listView)
        }

    private fun RuleResource.toReferenceDraft(): ResourceReferenceDraft =
        ResourceReferenceDraft(name = name, resourceId = id)

    private fun List<ResourceReferenceDraft>.toReferenceMap(): Map<String, ResourceReferenceDraft> =
        filter { reference -> !reference.resourceId.isNullOrBlank() }
            .associateBy { reference -> reference.name.normalizedReferenceKey() }

    private fun JsonObject.referenceDrafts(name: String, fallbackEndpoint: String): List<ResourceReferenceDraft> =
        array(name)
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { reference -> reference.toReferenceDraft(fallbackEndpoint) }
            ?: emptyList()

    private fun JsonObject.toReferenceDraft(fallbackEndpoint: String): ResourceReferenceDraft? {
        val referenceName = string("name")?.takeIf { it.isNotBlank() } ?: return null
        return ResourceReferenceDraft(
            name = referenceName,
            resourceId = string("id") ?: officialResourceIdFromUrl(string("url"), fallbackEndpoint)
        )
    }

    private fun officialResourceIdFromUrl(url: String?, fallbackEndpoint: String): String? {
        if (url.isNullOrBlank()) return null
        val segments = url.trim().split("/").filter { segment -> segment.isNotBlank() }
        val index = segments.lastOrNull() ?: return null
        return "official:$fallbackEndpoint:$index"
    }

    private fun String.normalizedReferenceKey(): String =
        trim().lowercase()

    private inner class ReferenceToggleAdapter(
        private val rules: List<RuleResource>,
        private val selectedIds: Set<String>
    ) : BaseAdapter() {
        override fun getCount(): Int = rules.size

        override fun getItem(position: Int): RuleResource = rules[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView as? LinearLayout ?: LinearLayout(parent?.context ?: requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(16, 10, 16, 10)

                addView(CheckBox(context).apply {
                    isClickable = false
                    isFocusable = false
                })
                addView(TextView(context).apply {
                    textSize = 16f
                    setPadding(12, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }

            val checkbox = row.getChildAt(0) as CheckBox
            val textView = row.getChildAt(1) as TextView
            val rule = getItem(position)

            checkbox.isChecked = rule.id in selectedIds
            textView.text = rule.toggleLabel()

            return row
        }

        private fun RuleResource.toggleLabel(): SpannableString {
            val source = source.displayName()
            val label = "$name - $source"
            return SpannableString(label).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun String.paragraphs(): List<String> =
        split(Regex("\\n\\s*\\n"))
            .map { value -> value.trim() }
            .filter { value -> value.isNotBlank() }

    private fun String.keyValuePairs(): Map<String, String> =
        split(",")
            .mapNotNull { entry ->
                val parts = entry.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .toMap()

    private fun String.abilityBonuses(): List<AbilityBonusDraft> =
        lines()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 2).map { it.trim() }
                if (parts.size != 2) return@mapNotNull null

                val ability = parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val bonus = parts[1]
                    .removePrefix("+")
                    .toIntOrNull()
                    ?: return@mapNotNull null

                AbilityBonusDraft(ability, bonus)
            }

    private fun String.equipmentQuantities(): List<EquipmentQuantityDraft> =
        lines()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 2).map { it.trim() }
                if (parts.size != 2) return@mapNotNull null

                val quantity = parts[0].toIntOrNull() ?: return@mapNotNull null
                val name = parts[1].takeIf { it.isNotBlank() } ?: return@mapNotNull null

                EquipmentQuantityDraft(name, quantity)
            }

    private fun String.monsterProficiencies(): List<MonsterProficiencyDraft> =
        lines()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 2).map { it.trim() }
                if (parts.size != 2) return@mapNotNull null

                val name = parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val value = parts[1].removePrefix("+").toIntOrNull() ?: return@mapNotNull null

                MonsterProficiencyDraft(name, value)
            }

    private fun String.subclassLevels(): List<ClassLevelDraft> =
        lines()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 2).map { it.trim() }
                if (parts.size < 2) return@mapNotNull null

                val level = parts[0].toIntOrNull() ?: return@mapNotNull null
                val featureNames = parts[1].split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                ClassLevelDraft(
                    level = level,
                    proficiencyBonus = 0,
                    featureNames = featureNames
                )
            }

    private fun EditText.subclassLevelsFromInput(): List<ClassLevelDraft> {
        val customColumns = structuredCustomColumns()
        return structuredValue()
            .lines()
            .mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                if (parts.size < 2) return@mapNotNull null

                val level = parts[0].toIntOrNull() ?: return@mapNotNull null
                val featureNames = parts[1].split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val classSpecific = customColumns.mapIndexedNotNull { index, column ->
                    parts.getOrNull(index + 2)
                        ?.takeIf { value -> value.isNotBlank() }
                        ?.let { value -> column.key to value }
                }.toMap()

                ClassLevelDraft(
                    level = level,
                    proficiencyBonus = 0,
                    featureNames = featureNames,
                    classSpecific = classSpecific
                )
            }
    }

    private fun String.subclassSpells(): List<SubclassSpellDraft> =
        lines()
            .mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                if (parts.size < 2) return@mapNotNull null

                val level = parts[0].toIntOrNull()
                val spellName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val groupName = parts.getOrNull(2).orEmpty()

                SubclassSpellDraft(
                    level = level,
                    spell = ResourceReferenceDraft(spellName),
                    groupName = groupName
                )
            }

    private fun JsonObject.abilityBonusInputText(): String =
        array("ability_bonuses")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { bonus ->
                val ability = bonus.obj("ability_score")?.string("name") ?: return@mapNotNull null
                val value = bonus.int("bonus") ?: return@mapNotNull null
                "$ability | $value"
            }
            ?.joinToString("\n")
            .orEmpty()

    private fun JsonObject.monsterProficiencyInputText(): String =
        array("proficiencies")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { proficiency ->
                val name = proficiency.obj("proficiency")?.string("name") ?: return@mapNotNull null
                val value = proficiency.int("value") ?: return@mapNotNull null
                "$name | $value"
            }
            ?.joinToString("\n")
            .orEmpty()

    private fun JsonObject.linkedReferenceNames(name: String, fallbackEndpoint: String): String =
        referenceDrafts(name, fallbackEndpoint)
            .filter { reference -> !reference.resourceId.isNullOrBlank() }
            .joinToString(", ") { reference -> reference.name }

    private fun JsonObject.descriptionArrayInputText(name: String): String =
        array(name)
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { item -> item.string("desc")?.takeIf { it.isNotBlank() } }
            ?.joinToString("\n")
            .orEmpty()

    private fun JsonObject.startingEquipmentInputText(): String =
        equipmentQuantityInputText("starting_equipment")

    private fun JsonObject.equipmentQuantityInputText(name: String): String =
        array(name)
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { item ->
                val itemName = item.obj("equipment")?.string("name") ?: return@mapNotNull null
                val quantity = item.int("quantity") ?: 1
                "$quantity | $itemName"
            }
            ?.joinToString("\n")
            .orEmpty()

    private fun JsonObject.backgroundLanguageOptionInputText(): String =
        arrayStrings("_language_choice_descriptions")
            .takeIf { values -> values.isNotEmpty() }
            ?.joinToString("\n")
            ?: choiceOptionsInputText("language_options")

    private fun JsonObject.choiceOptionsInputText(name: String): String =
        obj(name)
            ?.obj("from")
            ?.array("options")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { option ->
                option.string("string")
                    ?: option.string("desc")
                    ?: option.obj("item")?.string("name")
                    ?: option.obj("choice")?.string("desc")
            }
            ?.joinToString("\n")
            .orEmpty()

    private fun JsonObject.subclassSpellInputText(): String =
        array("spells")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { item ->
                val spellName = item.obj("spell")?.string("name") ?: return@mapNotNull null
                val prerequisites = item.array("prerequisites")
                    ?.mapNotNull { prerequisite -> prerequisite.takeIf { it.isJsonObject }?.asJsonObject }
                    ?: emptyList()
                val level = prerequisites
                    .firstOrNull { prerequisite -> prerequisite.string("type") == "level" }
                    ?.string("name")
                    ?.substringAfterLast(" ")
                    ?.toIntOrNull()
                val groupName = prerequisites
                    .firstOrNull { prerequisite -> prerequisite.string("type") == "feature" }
                    ?.string("name")
                    .orEmpty()

                listOfNotNull(level?.toString(), spellName, groupName.takeIf { it.isNotBlank() })
                    .joinToString(" | ")
            }
            ?.joinToString("\n")
            .orEmpty()

    private fun JsonObject.customLevelColumns(): List<CustomLevelColumnDraft> {
        val configured = array("_custom_level_columns")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { column ->
                val key = column.string("key")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val label = column.string("label")?.takeIf { it.isNotBlank() } ?: key.toColumnLabel()
                val type = StructuredColumnType.fromStorage(column.string("type")).storageValue
                CustomLevelColumnDraft(key, label, type)
            }
            .orEmpty()

        if (configured.isNotEmpty()) return configured

        return array("_levels")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.flatMap { level -> level.obj("class_specific")?.entrySet()?.map { entry -> entry.key } ?: emptyList() }
            ?.distinct()
            ?.map { key -> CustomLevelColumnDraft(key, key.toColumnLabel(), StructuredColumnType.TEXT.storageValue) }
            .orEmpty()
    }

    private fun JsonObject.classLevelInputText(customColumns: List<CustomLevelColumnDraft>): String =
        array("_levels")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.joinToString("\n") { level ->
                val features = level.referenceNameList("features").joinToString(", ")
                val values = listOf(
                    level.int("level").orEmptyText(),
                    level.int("prof_bonus").orEmptyText(),
                    features
                ) + customColumns.map { column ->
                    level.obj("class_specific")?.stringOrNumber(column.key).orEmpty()
                }
                values.joinToString(" | ")
            }
            .orEmpty()

    private fun JsonObject.subclassLevelInputText(customColumns: List<CustomLevelColumnDraft>): String =
        array("_levels")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.joinToString("\n") { level ->
                val features = level.referenceNameList("features").joinToString(", ")
                val values = listOf(level.int("level").orEmptyText(), features) + customColumns.map { column ->
                    level.obj("class_specific")?.stringOrNumber(column.key).orEmpty()
                }
                values.joinToString(" | ")
            }
            .orEmpty()

    private fun classLevelDisplayFields(customColumns: List<CustomLevelColumnDraft>): List<StructuredRowField> =
        listOf(
            StructuredRowField(R.string.create_structured_level, InputType.TYPE_CLASS_NUMBER),
            StructuredRowField(R.string.create_structured_proficiency_bonus, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED),
            StructuredRowField(R.string.create_structured_features)
        ) + customColumns.map { column ->
            StructuredRowField(label = column.label, inputType = StructuredColumnType.fromStorage(column.type).inputType)
        }

    private fun subclassLevelDisplayFields(customColumns: List<CustomLevelColumnDraft>): List<StructuredRowField> =
        listOf(
            StructuredRowField(R.string.create_structured_level, InputType.TYPE_CLASS_NUMBER),
            StructuredRowField(R.string.create_structured_features)
        ) + customColumns.map { column ->
            StructuredRowField(label = column.label, inputType = StructuredColumnType.fromStorage(column.type).inputType)
        }

    private fun JsonObject.namedTextArrayInputBlocks(name: String): String =
        array(name)
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.filter { item -> item.string("id").isNullOrBlank() && item.string("url").isNullOrBlank() }
            ?.mapNotNull { item ->
                val itemName = item.string("name") ?: return@mapNotNull null
                val description = item.arrayStrings("desc")
                    .ifEmpty { item.string("desc")?.let { listOf(it) } ?: emptyList() }
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                "$itemName: $description"
            }
            ?.joinToString("\n\n")
            .orEmpty()

    private fun String.slotLevelValues(): Map<Int, String> =
        lines()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 2).map { it.trim() }
                if (parts.size != 2) return@mapNotNull null

                val level = parts[0].toIntOrNull() ?: return@mapNotNull null
                val value = parts[1].takeIf { it.isNotBlank() } ?: return@mapNotNull null

                level to value
            }
            .toMap()

    private fun JsonObject?.slotLevelInputText(): String =
        this
            ?.entrySet()
            ?.mapNotNull { entry ->
                val level = entry.key.toIntOrNull() ?: return@mapNotNull null
                val value = entry.value?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
                    ?: return@mapNotNull null

                level to value
            }
            ?.sortedBy { (level) -> level }
            ?.joinToString("\n") { (level, value) -> "$level | $value" }
            .orEmpty()

    private fun JsonObject.featPrerequisiteInputText(): String =
        prerequisiteInputText()

    private fun JsonObject.prerequisiteInputText(): String =
        array("prerequisites")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { prerequisite ->
                prerequisite.string("desc")
                    ?: prerequisite.string("name")
                    ?: prerequisite.obj("ability_score")
                        ?.string("name")
                        ?.let { ability -> prerequisite.int("minimum_score")?.let { minimum -> "$ability $minimum" } }
            }
            ?.joinToString("\n")
            .orEmpty()

    private fun JsonObject.traitChoiceInputText(): String {
        val customChoices = arrayStrings("_choice_descriptions")
        if (customChoices.isNotEmpty()) return customChoices.joinToString("\n")

        return obj("trait_specific")
            ?.entrySet()
            ?.mapNotNull { entry ->
                val option = entry.value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val choose = option.int("choose") ?: return@mapNotNull null
                val type = option.string("type")?.replace("_", " ") ?: entry.key.replace("_", " ")
                "Choose $choose $type."
            }
            ?.joinToString("\n")
            .orEmpty()
    }

    private fun JsonObject.traitSubtraitInputText(): String =
        array("_subtraits")
            ?.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { subtrait ->
                val name = subtrait.string("name") ?: return@mapNotNull null
                val description = subtrait.arrayStrings("desc").joinToString("\n").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull name
                "$name: $description"
            }
            ?.joinToString("\n\n")
            .orEmpty()

    private fun String.abilityScores(): AbilityScoresDraft {
        val scores = commaSeparatedValues().mapNotNull { it.toIntOrNull() }
        return AbilityScoresDraft(scores[0], scores[1], scores[2], scores[3], scores[4], scores[5])
    }

    private fun String.namedTextBlocks(): List<NamedTextDraft> =
        paragraphs().mapNotNull { block ->
            val parts = block.split(":", limit = 2)
            if (parts.size == 2) NamedTextDraft(parts[0].trim(), parts[1].trim()) else null
        }

    private fun String.classLevels(): List<ClassLevelDraft> =
        lines()
            .mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                if (parts.size < 3) return@mapNotNull null

                val level = parts[0].toIntOrNull() ?: return@mapNotNull null
                val proficiencyBonus = parts[1].toIntOrNull() ?: return@mapNotNull null
                val featureNames = parts[2].split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                ClassLevelDraft(
                    level = level,
                    proficiencyBonus = proficiencyBonus,
                    featureNames = featureNames
                )
            }

    private fun EditText.classLevelsFromInput(): List<ClassLevelDraft> {
        val customColumns = structuredCustomColumns()
        return structuredValue()
            .lines()
            .mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                if (parts.size < 3) return@mapNotNull null

                val level = parts[0].toIntOrNull() ?: return@mapNotNull null
                val proficiencyBonus = parts[1].toIntOrNull() ?: return@mapNotNull null
                val featureNames = parts[2].split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val classSpecific = customColumns.mapIndexedNotNull { index, column ->
                    parts.getOrNull(index + 3)
                        ?.takeIf { value -> value.isNotBlank() }
                        ?.let { value -> column.key to value }
                }.toMap()

                ClassLevelDraft(
                    level = level,
                    proficiencyBonus = proficiencyBonus,
                    featureNames = featureNames,
                    classSpecific = classSpecific
                )
            }
    }

    private fun String.classFeatures(): List<ClassFeatureDraft> =
        paragraphs()
            .mapNotNull { block ->
                val parts = block.split("|", limit = 3).map { it.trim() }
                if (parts.size < 3) return@mapNotNull null

                val level = parts[1].toIntOrNull() ?: return@mapNotNull null

                ClassFeatureDraft(
                    name = parts[0],
                    level = level,
                    description = listOf(parts[2])
                )
            }

    private fun List<Pair<String, String>>.toInputBlocks(): String =
        joinToString("\n\n") { (name, description) ->
            "$name: $description"
        }

    private fun Int?.orEmptyText(): String = this?.toString().orEmpty()

    private fun String.toColumnLabel(): String =
        split("_", "-")
            .filter { part -> part.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { char -> char.uppercase() } }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_EDIT_RESOURCE_ID = "arg_edit_resource_id"
        private const val MODE_CUSTOM = 0
        private const val MODE_HOMEBREWERY = 1

        fun newEditInstance(resourceId: String): CreateResourceFragment {
            return CreateResourceFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EDIT_RESOURCE_ID, resourceId)
                }
            }
        }
    }
}
