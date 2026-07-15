package com.example.dnd_ruleslawyer.presentation.favorites

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.example.dnd_ruleslawyer.databinding.FragmentFavoritesBinding
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSearchFilters
import com.example.dnd_ruleslawyer.domain.voice.SpeechToTextManager
import com.example.dnd_ruleslawyer.presentation.UIEntryPoint
import com.example.dnd_ruleslawyer.presentation.search.SearchFilterGroup
import com.example.dnd_ruleslawyer.presentation.utils.updateVoiceListeningFeedback
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment(R.layout.fragment_favorites) {
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: RulesRepository
    private lateinit var speechToTextManager: SpeechToTextManager
    private lateinit var favoritesAdapter: CompactRuleResourceAdapter
    private lateinit var addFavoriteAdapter: CompactRuleResourceAdapter
    private val selectedFilterGroups = linkedSetOf<SearchFilterGroup>()

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) speechToTextManager.startListening()
            else Toast.makeText(requireContext(), R.string.voice_permission_denied, Toast.LENGTH_SHORT).show()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentFavoritesBinding.bind(view)
        repository = UIEntryPoint.rulesRepository(requireContext())

        setupInsets(view)
        setupBackNavigation()
        setupFavoriteList()
        setupAddFavoriteSearch()
        setupSearchFilters()
        setupVoiceInput()
        refreshFavorites()
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

    private fun setupFavoriteList() {
        favoritesAdapter = CompactRuleResourceAdapter(
            actionIconResId = R.drawable.ic_star_24,
            actionContentDescriptionResId = R.string.favorite_remove,
            onRuleClicked = { rule ->
                startActivity(UIEntryPoint.createRuleDetailIntent(requireContext(), rule.id))
            },
            onActionClicked = { rule ->
                removeFavorite(rule)
            }
        )

        binding.favoritesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = favoritesAdapter
        }
    }

    private fun setupAddFavoriteSearch() {
        addFavoriteAdapter = CompactRuleResourceAdapter(
            actionIconResId = R.drawable.ic_add_24,
            actionContentDescriptionResId = R.string.favorite_add,
            onRuleClicked = null,
            onActionClicked = { rule ->
                addFavorite(rule)
            }
        )

        binding.addFavoriteResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = addFavoriteAdapter
        }

        binding.addFavoriteButton.setOnClickListener {
            val showingSearch = binding.addFavoriteContainer.visibility == View.VISIBLE
            binding.addFavoriteContainer.visibility = if (showingSearch) View.GONE else View.VISIBLE
            if (!showingSearch) {
                binding.addFavoriteQueryInput.requestFocus()
            }
        }

        binding.addFavoriteQueryInput.setOnEditorActionListener { _, actionId, event ->
            val pressedEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_UP

            if (actionId == EditorInfo.IME_ACTION_SEARCH || pressedEnter) {
                submitAddFavoriteSearch()
                true
            } else {
                false
            }
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
                if (isChecked) selectedFilterGroups += group else selectedFilterGroups -= group

                refreshFavorites()
                if (binding.addFavoriteQueryInput.text.isNotBlank()) {
                    searchAddFavoriteResults(binding.addFavoriteQueryInput.text.toString())
                }
            }

            binding.favoritesFilterChipGroup.addView(chip)
        }
    }

    private fun setupVoiceInput() {
        speechToTextManager = UIEntryPoint.createSpeechToTextManager(
            context = requireContext(),
            onTextResult = { text ->
                binding.addFavoriteContainer.visibility = View.VISIBLE
                binding.addFavoriteQueryInput.setText(text)
                binding.addFavoriteQueryInput.setSelection(binding.addFavoriteQueryInput.text.length)
                searchAddFavoriteResults(text)
            },
            onListeningChanged = { isListening ->
                binding.addFavoriteVoiceButton.updateVoiceListeningFeedback(isListening)
            },
            onError = { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        )

        binding.addFavoriteVoiceButton.updateVoiceListeningFeedback(false)
        binding.addFavoriteVoiceButton.setOnClickListener { startVoiceInput() }
    }

    private fun refreshFavorites() {
        viewLifecycleOwner.lifecycleScope.launch {
            val favoriteRules = runCatching {
                repository.getFavoriteRules()
            }.getOrElse {
                emptyList()
            }

            val filters = currentSearchFilters()
            val filteredRules = favoriteRules.filter { rule -> filters.matches(rule) }

            favoritesAdapter.submitList(filteredRules)
        }
    }

    private fun submitAddFavoriteSearch() {
        hideKeyboard()
        binding.addFavoriteQueryInput.clearFocus()
        searchAddFavoriteResults(binding.addFavoriteQueryInput.text.toString())
    }

    private fun searchAddFavoriteResults(query: String) {
        val trimmedQuery = query.trim()

        if (trimmedQuery.isBlank()) {
            addFavoriteAdapter.submitList(emptyList())
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val rules = runCatching {
                repository.searchRules(trimmedQuery, currentSearchFilters())
                    .filterNot { rule -> rule.isFavorite }
            }.getOrElse {
                Toast.makeText(requireContext(), R.string.search_results_error, Toast.LENGTH_SHORT).show()
                emptyList()
            }

            addFavoriteAdapter.submitList(rules)
        }
    }

    private fun addFavorite(rule: RuleResource) {
        viewLifecycleOwner.lifecycleScope.launch {
            val updated = runCatching {
                repository.setFavorite(rule.id, true)
            }.getOrDefault(false)

            if (!updated) {
                Toast.makeText(requireContext(), R.string.favorite_update_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            Toast.makeText(requireContext(), R.string.favorite_added, Toast.LENGTH_SHORT).show()
            binding.addFavoriteContainer.visibility = View.GONE
            binding.addFavoriteQueryInput.text?.clear()
            addFavoriteAdapter.submitList(emptyList())
            refreshFavorites()
        }
    }

    private fun removeFavorite(rule: RuleResource) {
        viewLifecycleOwner.lifecycleScope.launch {
            val updated = runCatching {
                repository.setFavorite(rule.id, false)
            }.getOrDefault(false)

            if (!updated) {
                Toast.makeText(requireContext(), R.string.favorite_update_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            Toast.makeText(requireContext(), R.string.favorite_removed, Toast.LENGTH_SHORT).show()
            refreshFavorites()
        }
    }

    private fun currentSearchFilters(): RuleSearchFilters {
        return RuleSearchFilters(
            resourceTypes = SearchFilterGroup.resourceTypesFor(selectedFilterGroups)
        )
    }

    private fun startVoiceInput() {
        binding.addFavoriteContainer.visibility = View.VISIBLE

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

    private fun hideKeyboard() {
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focusedView = requireActivity().currentFocus ?: binding.addFavoriteQueryInput

        inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
    }

    override fun onDestroyView() {
        if (::speechToTextManager.isInitialized) {
            speechToTextManager.destroy()
        }
        binding.addFavoriteVoiceButton.updateVoiceListeningFeedback(false)
        _binding = null
        super.onDestroyView()
    }
}
