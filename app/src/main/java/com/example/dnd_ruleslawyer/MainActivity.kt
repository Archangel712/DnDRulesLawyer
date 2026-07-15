package com.example.dnd_ruleslawyer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.dnd_ruleslawyer.databinding.ActivityMainBinding
import com.example.dnd_ruleslawyer.presentation.create.CreateResourceFragment
import com.example.dnd_ruleslawyer.presentation.favorites.FavoritesFragment
import com.example.dnd_ruleslawyer.presentation.loading.LoadingOverlayController
import com.example.dnd_ruleslawyer.presentation.search.SearchFragment
import com.example.dnd_ruleslawyer.presentation.settings.AppThemePreferences
import com.example.dnd_ruleslawyer.presentation.settings.SettingsFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var loadingOverlayController: LoadingOverlayController

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applySavedTheme(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyToolbarStatusBarInset()
        loadingOverlayController = LoadingOverlayController(
            root = binding.mainLoadingOverlay.root,
            flavorTextView = binding.mainLoadingOverlay.loadingFlavorText,
            flavorTexts = resources.getStringArray(R.array.loading_flavor_texts).toList()
        )

        binding.mainToolbar.setNavigationOnClickListener {
            binding.mainDrawer.open()
        }

        binding.mainNavigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_search -> showSearch()
                R.id.nav_favorites -> showFavorites()
                R.id.nav_add_resource -> showAddResource()
            }

            binding.mainDrawer.close()
            true
        }

        binding.settingsNavigationView.setNavigationItemSelectedListener { item ->
            if (item.itemId == R.id.nav_settings) {
                showSettings()
            }

            binding.mainDrawer.close()
            true
        }

        if (savedInstanceState == null) {
            showLoadingOverlay()
            showSearch()
        }

        handleIntent(intent)
    }

    private fun applyToolbarStatusBarInset() {
        val toolbar = binding.mainToolbar
        val initialHeight = toolbar.layoutParams.height
        val initialTopPadding = toolbar.paddingTop

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val safeTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.displayCutout()
            ).top

            view.updatePadding(top = initialTopPadding + safeTop)
            view.layoutParams = view.layoutParams.apply {
                height = initialHeight + safeTop
            }

            insets
        }
        ViewCompat.requestApplyInsets(toolbar)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    fun showSearch() {
        showFragment(SearchFragment())
        binding.mainNavigationView.setCheckedItem(R.id.nav_search)
        clearSettingsNavigationSelection()
    }

    private fun showFavorites() {
        showFragment(FavoritesFragment())
        binding.mainNavigationView.setCheckedItem(R.id.nav_favorites)
        clearSettingsNavigationSelection()
    }

    private fun showAddResource() {
        showFragment(CreateResourceFragment())
        binding.mainNavigationView.setCheckedItem(R.id.nav_add_resource)
        clearSettingsNavigationSelection()
    }

    private fun showSettings() {
        showFragment(SettingsFragment())
        clearMainNavigationSelection()
        binding.settingsNavigationView.setCheckedItem(R.id.nav_settings)
    }

    fun showLoadingOverlay() {
        loadingOverlayController.show()
        binding.mainDrawer.close()
        binding.mainDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        binding.mainToolbar.navigationIcon = null
        binding.mainToolbar.setNavigationOnClickListener(null)
    }

    fun hideLoadingOverlay() {
        loadingOverlayController.hide()
        binding.mainDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        binding.mainToolbar.setNavigationIcon(R.drawable.ic_menu_24)
        binding.mainToolbar.setNavigationOnClickListener {
            binding.mainDrawer.open()
        }
    }

    private fun handleIntent(intent: Intent?) {
        val editResourceId = intent?.getStringExtra(EXTRA_EDIT_RESOURCE_ID)
        if (!editResourceId.isNullOrBlank()) {
            showFragment(CreateResourceFragment.newEditInstance(editResourceId))
            binding.mainNavigationView.setCheckedItem(R.id.nav_add_resource)
            clearSettingsNavigationSelection()
        } else if (intent?.getBooleanExtra(EXTRA_SHOW_SEARCH, false) == true) {
            showSearch()
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainFragmentContainer, fragment)
            .commit()
    }

    override fun onDestroy() {
        if (::loadingOverlayController.isInitialized) {
            loadingOverlayController.destroy()
        }
        super.onDestroy()
    }

    private fun clearMainNavigationSelection() {
        binding.mainNavigationView.menu.setGroupCheckable(0, true, false)
        binding.mainNavigationView.checkedItem?.isChecked = false
        binding.mainNavigationView.menu.setGroupCheckable(0, true, true)
    }

    private fun clearSettingsNavigationSelection() {
        binding.settingsNavigationView.menu.setGroupCheckable(0, true, false)
        binding.settingsNavigationView.checkedItem?.isChecked = false
        binding.settingsNavigationView.menu.setGroupCheckable(0, true, true)
    }

    companion object {
        const val EXTRA_SHOW_SEARCH = "extra_show_search"
        const val EXTRA_EDIT_RESOURCE_ID = "extra_edit_resource_id"
    }
}
