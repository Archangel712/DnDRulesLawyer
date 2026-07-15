package com.example.dnd_ruleslawyer.presentation.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.dnd_ruleslawyer.MainActivity
import com.example.dnd_ruleslawyer.R
import com.example.dnd_ruleslawyer.data.repository.RulesRepository
import com.example.dnd_ruleslawyer.databinding.FragmentSettingsBinding
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.presentation.UIEntryPoint
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: RulesRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSettingsBinding.bind(view)
        repository = UIEntryPoint.rulesRepository(requireContext())

        setupInsets(view)
        setupBackNavigation()
        setupThemeToggle()
        setupReloadOfficialResources()
    }

    private fun setupInsets(rootView: View) {
        val initialLeftPadding = rootView.paddingLeft
        val initialTopPadding = rootView.paddingTop
        val initialRightPadding = rootView.paddingRight
        val initialBottomPadding = rootView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.updatePadding(
                left = initialLeftPadding + systemBars.left,
                top = initialTopPadding + systemBars.top,
                right = initialRightPadding + systemBars.right,
                bottom = initialBottomPadding + systemBars.bottom
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

    private fun setupThemeToggle() {
        binding.darkModeSwitch.isChecked = AppThemePreferences.isDarkModeEnabled(requireContext())
        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppThemePreferences.setDarkModeEnabled(requireContext(), isChecked)
        }
    }

    private fun setupReloadOfficialResources() {
        binding.reloadOfficialResourcesButton.setOnClickListener {
            reloadOfficialResources()
        }
    }

    private fun reloadOfficialResources() {
        binding.reloadOfficialResourcesButton.isEnabled = false
        (requireActivity() as MainActivity).showLoadingOverlay()

        viewLifecycleOwner.lifecycleScope.launch {
            val failedTypes = ResourceType.entries
                .filter { type -> type.syncByDefault }
                .mapNotNull { type ->
                    runCatching {
                        repository.syncOfficialResources(type)
                    }.exceptionOrNull()
                }

            binding.reloadOfficialResourcesButton.isEnabled = true
            (activity as? MainActivity)?.hideLoadingOverlay()

            Toast.makeText(
                requireContext(),
                if (failedTypes.isEmpty()) {
                    R.string.settings_reload_official_resources_done
                } else {
                    R.string.settings_reload_official_resources_failed
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
