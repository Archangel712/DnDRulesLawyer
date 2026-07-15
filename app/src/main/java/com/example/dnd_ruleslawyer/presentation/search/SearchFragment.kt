package com.example.dnd_ruleslawyer.presentation.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dnd_ruleslawyer.MainActivity
import com.example.dnd_ruleslawyer.R
import com.example.dnd_ruleslawyer.data.repository.RulesRepository
import com.example.dnd_ruleslawyer.databinding.FragmentSearchBinding
import com.example.dnd_ruleslawyer.domain.model.EquipmentSecondaryGroup
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSearchFilters
import com.example.dnd_ruleslawyer.domain.model.SearchSecondaryFilter
import com.example.dnd_ruleslawyer.domain.voice.SpeechToTextManager
import com.example.dnd_ruleslawyer.presentation.UIEntryPoint
import com.example.dnd_ruleslawyer.presentation.utils.updateVoiceListeningFeedback
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchFragment : Fragment(R.layout.fragment_search) {
    private lateinit var repository: RulesRepository
    private lateinit var speechToTextManager: SpeechToTextManager
    private lateinit var ruleAdapter: RuleResourceAdapter
    private var selectedFilterGroup: SearchFilterGroup? = null
    private var selectedSecondaryFilter: SearchSecondaryFilter? = null
    private var monsterChallengeRatings: List<Double> = emptyList()
    private var searchJob: Job? = null
    private var monsterChallengeRatingJob: Job? = null
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) speechToTextManager.startListening()
            else Toast.makeText(requireContext(), R.string.voice_permission_denied, Toast.LENGTH_SHORT).show()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSearchBinding.bind(view)
        repository = UIEntryPoint.rulesRepository(requireContext())

        setupInsets(view)
        setupSearchInput()
        setupSearchFilters()
        setupMonsterChallengeRatingSlider()
        setupResults()
        setupVoiceInput()
        syncInitialRuleResourcesIfNeeded()
        showSearchPrompt()
    }

    private fun setupInsets(rootView: View) {
        val initialLeftPadding = rootView.paddingLeft
        val initialTopPadding = rootView.paddingTop
        val initialRightPadding = rootView.paddingRight
        val initialBottomPadding = rootView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            view.updatePadding(
                left = initialLeftPadding + systemBars.left,
                top = initialTopPadding + systemBars.top,
                right = initialRightPadding + systemBars.right,
                bottom = initialBottomPadding + if (isKeyboardVisible) ime.bottom else systemBars.bottom
            )

            insets
        }
    }

    private fun syncInitialRuleResourcesIfNeeded() {
        viewLifecycleOwner.lifecycleScope.launch {
            val missingTypes = ResourceType.entries
                .filter { type -> type.syncByDefault }
                .filterNot { type ->
                    runCatching {
                        repository.hasRuleResources(type)
                    }.getOrDefault(false)
                }

            if (missingTypes.isEmpty()) {
                (activity as? MainActivity)?.hideLoadingOverlay()
                return@launch
            }

            missingTypes.forEach { type ->
                runCatching {
                    repository.syncOfficialResources(type)
                }
            }

            if (binding.queryInput.text.isBlank()) {
                refreshSearchResults()
            }

            (activity as? MainActivity)?.hideLoadingOverlay()
        }
    }

    private fun setupVoiceInput() {
        speechToTextManager = UIEntryPoint.createSpeechToTextManager(
            context = requireContext(),
            onTextResult = { text ->
                binding.queryInput.setText(text)
                binding.queryInput.setSelection(binding.queryInput.text.length)
                searchRules(text)
            },
            onListeningChanged = { isListening ->
                binding.voiceButton.updateVoiceListeningFeedback(isListening)
            },
            onError = { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        )

        binding.voiceButton.updateVoiceListeningFeedback(false)
        binding.voiceButton.setOnClickListener { startVoiceInput() }
    }

    private fun setupResults() {
        ruleAdapter = UIEntryPoint.createRuleResultsAdapter(
            onRuleClicked = { rule ->
                startActivity(UIEntryPoint.createRuleDetailIntent(requireContext(), rule.id))
            },
            onFavoriteClicked = { rule ->
                toggleFavorite(rule)
            }
        )

        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ruleAdapter
        }
    }

    private fun setupSearchFilters() {
        SearchFilterGroup.entries.forEach { filterGroup ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = getString(filterGroup.labelResId)
                isCheckable = true
                tag = filterGroup
            }

            chip.setOnCheckedChangeListener { button, isChecked ->
                val group = button.tag as SearchFilterGroup
                if (isChecked) {
                    selectedFilterGroup = group
                } else if (selectedFilterGroup == group) {
                    selectedFilterGroup = null
                }

                selectedSecondaryFilter = null
                monsterChallengeRatings = emptyList()
                renderSecondaryFilters()
                refreshSearchResults()
            }

            binding.searchFilterChipGroup.addView(chip)
        }
    }

    private fun setupMonsterChallengeRatingSlider() {
        binding.monsterChallengeRatingSlider.setLabelFormatter { value ->
            monsterChallengeRatings.getOrNull(value.toInt())?.challengeRatingLabel().orEmpty()
        }

        binding.monsterChallengeRatingSlider.addOnChangeListener { slider, _, fromUser ->
            if (!fromUser || monsterChallengeRatings.isEmpty()) return@addOnChangeListener
            val selectedIndexes = slider.values.map { value -> value.toInt() }.sorted()
            val min = monsterChallengeRatings.getOrNull(selectedIndexes.firstOrNull() ?: 0) ?: return@addOnChangeListener
            val max = monsterChallengeRatings.getOrNull(selectedIndexes.lastOrNull() ?: 0) ?: return@addOnChangeListener

            selectedSecondaryFilter = SearchSecondaryFilter.MonsterChallengeRatingRange(min, max)
            updateMonsterChallengeRatingLabel(min, max)
            searchRules(binding.queryInput.text.toString())
        }
    }

    private fun setupSearchInput() {
        binding.queryInput.setOnEditorActionListener { _, actionId, event ->
            val pressedEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_UP

            if (actionId == EditorInfo.IME_ACTION_SEARCH || pressedEnter) {
                submitSearchInput()
                true
            } else {
                false
            }
        }
    }

    private fun submitSearchInput() {
        hideKeyboard()
        binding.queryInput.clearFocus()
        refreshSearchResults()
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focusedView = requireActivity().currentFocus ?: binding.queryInput

        inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
    }

    private fun currentSearchFilters(): RuleSearchFilters {
        return RuleSearchFilters(
            resourceTypes = selectedFilterGroup?.resourceTypes.orEmpty()
        )
    }

    private fun startVoiceInput() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            speechToTextManager.startListening()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun refreshSearchResults() {
        if (selectedFilterGroup == SearchFilterGroup.MONSTERS) {
            refreshMonsterChallengeRatingsAndSearch(binding.queryInput.text.toString())
            return
        }

        searchRules(binding.queryInput.text.toString())
    }

    private fun searchRules(query: String) {
        val trimmedQuery = query.trim()
        val filters = currentSearchFilters()

        searchJob?.cancel()
        ruleAdapter.submitList(emptyList())

        if (trimmedQuery.isBlank() && filters.resourceTypes.isEmpty()) {
            showSearchPrompt()
            return
        }

        showSearchLoading()

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                repository.searchRules(trimmedQuery, filters, selectedSecondaryFilter)
            }

            result
                .onSuccess { rules ->
                    showSearchResults(rules)
                }
                .onFailure {
                    showSearchResults(emptyList(), showNoResults = false)
                    Toast.makeText(requireContext(), R.string.search_results_error, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun renderSecondaryFilters() {
        val primaryFilter = selectedFilterGroup
        binding.secondaryFilterChipGroup.removeAllViews()
        binding.secondaryFilterChipGroup.visibility = View.GONE
        binding.monsterChallengeRatingContainer.visibility = View.GONE

        if (primaryFilter == null) {
            binding.secondaryFiltersContainer.visibility = View.GONE
            return
        }

        binding.secondaryFiltersContainer.visibility = View.VISIBLE

        if (primaryFilter == SearchFilterGroup.MONSTERS) {
            binding.monsterChallengeRatingContainer.visibility = View.VISIBLE
            updateMonsterChallengeRatingLabel(null, null)
            return
        }

        binding.secondaryFilterChipGroup.visibility = View.VISIBLE
        secondaryChipSpecs(primaryFilter).forEach { spec ->
            addSecondaryFilterChip(spec)
        }
    }

    private fun addSecondaryFilterChip(spec: SecondaryChipSpec) {
        val chip = Chip(requireContext()).apply {
            id = View.generateViewId()
            text = spec.label
            isCheckable = true
            tag = spec.filter
        }

        chip.setOnCheckedChangeListener { button, isChecked ->
            selectedSecondaryFilter = if (isChecked) {
                button.tag as SearchSecondaryFilter
            } else if (selectedSecondaryFilter == button.tag) {
                null
            } else {
                selectedSecondaryFilter
            }

            searchRules(binding.queryInput.text.toString())
        }

        binding.secondaryFilterChipGroup.addView(chip)
    }

    private fun refreshMonsterChallengeRatingsAndSearch(query: String) {
        val trimmedQuery = query.trim()
        val filters = currentSearchFilters()

        if (filters.resourceTypes.isEmpty()) {
            searchRules(query)
            return
        }

        monsterChallengeRatingJob?.cancel()
        monsterChallengeRatingJob = viewLifecycleOwner.lifecycleScope.launch {
            val ratings = runCatching {
                repository.availableMonsterChallengeRatings(trimmedQuery, filters)
            }.getOrDefault(emptyList())

            monsterChallengeRatings = ratings
            configureMonsterChallengeRatingSlider(ratings)
            searchRules(query)
        }
    }

    private fun configureMonsterChallengeRatingSlider(ratings: List<Double>) {
        val slider = binding.monsterChallengeRatingSlider

        if (ratings.isEmpty()) {
            selectedSecondaryFilter = null
            slider.isEnabled = false
            slider.valueFrom = 0f
            slider.valueTo = 1f
            slider.values = listOf(0f, 1f)
            updateMonsterChallengeRatingLabel(null, null)
            return
        }

        val maxIndex = (ratings.size - 1).coerceAtLeast(1)
        slider.isEnabled = ratings.size > 1
        slider.valueFrom = 0f
        slider.valueTo = maxIndex.toFloat()
        slider.stepSize = 1f
        slider.values = listOf(0f, maxIndex.toFloat())

        val min = ratings.first()
        val max = ratings.last()
        selectedSecondaryFilter = SearchSecondaryFilter.MonsterChallengeRatingRange(min, max)
        updateMonsterChallengeRatingLabel(min, max)
    }

    private fun updateMonsterChallengeRatingLabel(min: Double?, max: Double?) {
        binding.monsterChallengeRatingLabel.text = if (min == null || max == null) {
            getString(R.string.filter_monster_cr_range)
        } else {
            getString(
                R.string.filter_monster_cr_range_value,
                min.challengeRatingLabel(),
                max.challengeRatingLabel()
            )
        }
    }

    private fun showSearchPrompt() {
        binding.searchLoadingIndicator.root.visibility = View.GONE
        binding.searchResultsRecyclerView.visibility = View.GONE
        binding.searchEmptyMessageText.setText(R.string.search_empty_prompt)
        binding.searchEmptyMessageText.visibility = View.VISIBLE
    }

    private fun showSearchLoading() {
        binding.searchResultsRecyclerView.visibility = View.GONE
        binding.searchEmptyMessageText.visibility = View.GONE
        binding.searchLoadingIndicator.root.visibility = View.VISIBLE
    }

    private fun showSearchResults(
        rules: List<RuleResource>,
        showNoResults: Boolean = rules.isEmpty()
    ) {
        binding.searchLoadingIndicator.root.visibility = View.GONE
        binding.searchResultsRecyclerView.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
        binding.searchEmptyMessageText.setText(R.string.search_no_results)
        binding.searchEmptyMessageText.visibility = if (showNoResults) View.VISIBLE else View.GONE
        ruleAdapter.submitList(rules)
    }

    private fun toggleFavorite(rule: RuleResource) {
        val newFavoriteState = !rule.isFavorite

        viewLifecycleOwner.lifecycleScope.launch {
            val updated = runCatching {
                repository.setFavorite(rule.id, newFavoriteState)
            }.getOrDefault(false)

            if (!updated) {
                Toast.makeText(requireContext(), R.string.favorite_update_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            ruleAdapter.submitList(
                ruleAdapter.currentList.map { item ->
                    if (item.id == rule.id) item.copy(isFavorite = newFavoriteState) else item
                }
            )
        }
    }

    override fun onDestroyView() {
        if (::speechToTextManager.isInitialized) {
            speechToTextManager.destroy()
        }
        searchJob?.cancel()
        monsterChallengeRatingJob?.cancel()
        binding.voiceButton.updateVoiceListeningFeedback(false)
        _binding = null
        super.onDestroyView()
    }

    private data class SecondaryChipSpec(
        val label: String,
        val filter: SearchSecondaryFilter
    )

    private fun secondaryChipSpecs(primaryFilter: SearchFilterGroup): List<SecondaryChipSpec> {
        return when (primaryFilter) {
            SearchFilterGroup.SPELLS -> spellSecondaryChipSpecs()
            SearchFilterGroup.CLASSES -> listOf(
                SecondaryChipSpec(getString(R.string.filter_class), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.CLASS))),
                SecondaryChipSpec(getString(R.string.filter_subclass), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.SUBCLASS)))
            )
            SearchFilterGroup.EQUIPMENT -> equipmentSecondaryChipSpecs()
            SearchFilterGroup.RACES -> listOf(
                SecondaryChipSpec(getString(R.string.filter_races), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.RACE))),
                SecondaryChipSpec(getString(R.string.filter_subrace), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.SUBRACE)))
            )
            SearchFilterGroup.RULES -> rulesSecondaryChipSpecs()
            SearchFilterGroup.MONSTERS -> emptyList()
        }
    }

    private fun spellSecondaryChipSpecs(): List<SecondaryChipSpec> {
        return listOf(
            SecondaryChipSpec(getString(R.string.filter_spell_cantrip), SearchSecondaryFilter.SpellLevel(0))
        ) + (1..9).map { level ->
            SecondaryChipSpec(getString(R.string.filter_spell_level, level), SearchSecondaryFilter.SpellLevel(level))
        } + SecondaryChipSpec(getString(R.string.filter_other), SearchSecondaryFilter.SpellLevel(null))
    }

    private fun equipmentSecondaryChipSpecs(): List<SecondaryChipSpec> =
        listOf(
            SecondaryChipSpec(getString(R.string.filter_equipment_weapons), SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.WEAPONS)),
            SecondaryChipSpec(getString(R.string.filter_equipment_armor), SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.ARMOR)),
            SecondaryChipSpec(getString(R.string.filter_equipment_adventuring_gear), SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.ADVENTURING_GEAR)),
            SecondaryChipSpec(getString(R.string.filter_equipment_tools), SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.TOOLS)),
            SecondaryChipSpec(getString(R.string.filter_equipment_mounts_vehicles), SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.MOUNTS_AND_VEHICLES)),
            SecondaryChipSpec(getString(R.string.filter_equipment_magic_items), SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.MAGIC_ITEMS)),
            SecondaryChipSpec(getString(R.string.filter_equipment_weapon_properties), SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.WEAPON_PROPERTIES)),
            SecondaryChipSpec(getString(R.string.filter_equipment_categories), SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.CATEGORIES)),
            SecondaryChipSpec(getString(R.string.filter_other), SearchSecondaryFilter.EquipmentGroup(EquipmentSecondaryGroup.OTHER))
        )

    private fun rulesSecondaryChipSpecs(): List<SecondaryChipSpec> =
        listOf(
            SecondaryChipSpec(getString(R.string.filter_rules), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.RULE))),
            SecondaryChipSpec(getString(R.string.filter_rule_sections), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.RULE_SECTION))),
            SecondaryChipSpec(getString(R.string.filter_languages), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.LANGUAGE))),
            SecondaryChipSpec(getString(R.string.filter_magic_schools), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.MAGIC_SCHOOL))),
            SecondaryChipSpec(getString(R.string.filter_conditions), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.CONDITION))),
            SecondaryChipSpec(getString(R.string.filter_damage_types), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.DAMAGE_TYPE))),
            SecondaryChipSpec(getString(R.string.filter_ability_scores), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.ABILITY_SCORE))),
            SecondaryChipSpec(getString(R.string.filter_alignments), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.ALIGNMENT))),
            SecondaryChipSpec(getString(R.string.filter_skills), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.SKILL))),
            SecondaryChipSpec(getString(R.string.filter_proficiencies), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.PROFICIENCY))),
            SecondaryChipSpec(getString(R.string.filter_feats), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.FEAT))),
            SecondaryChipSpec(getString(R.string.filter_features), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.FEATURE))),
            SecondaryChipSpec(getString(R.string.filter_traits), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.TRAIT))),
            SecondaryChipSpec(getString(R.string.filter_backgrounds), SearchSecondaryFilter.ResourceTypes(setOf(ResourceType.BACKGROUND))),
            SecondaryChipSpec(getString(R.string.filter_other), SearchSecondaryFilter.ResourceTypes(emptySet()))
        )

    private fun Double.challengeRatingLabel(): String {
        return when (this) {
            0.125 -> "1/8"
            0.25 -> "1/4"
            0.5 -> "1/2"
            else -> if (this % 1.0 == 0.0) toInt().toString() else toString()
        }
    }
}
