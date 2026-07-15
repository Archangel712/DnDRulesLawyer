package com.example.dnd_ruleslawyer.presentation

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.dnd_ruleslawyer.MainActivity
import com.example.dnd_ruleslawyer.R
import com.example.dnd_ruleslawyer.data.repository.RulesRepository
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.model.RuleSearchFilters
import com.example.dnd_ruleslawyer.domain.model.RuleSection
import com.example.dnd_ruleslawyer.domain.model.RuleSource
import com.example.dnd_ruleslawyer.domain.model.SearchSecondaryFilter
import com.example.dnd_ruleslawyer.domain.model.challengeRating
import com.example.dnd_ruleslawyer.domain.model.matches
import com.example.dnd_ruleslawyer.presentation.detail.RuleDetailActivity
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not

class MainActivitySearchFlowTest {

    @After
    fun tearDown() {
        UIEntryPoint.clearRulesRepositoryForTests()
    }

    @Test
    fun searchFlow_displaysMatchingResults() {
        UIEntryPoint.replaceRulesRepositoryForTests(
            FakeRulesRepository(
                resources = listOf(
                    RuleResource(
                        id = "official:spells:fireball",
                        name = "Fireball",
                        type = ResourceType.SPELL,
                        source = RuleSource.OFFICIAL,
                        description = "A bright streak flashes.",
                        apiUrl = "/api/spells/fireball"
                    )
                )
            )
        )

        launchMainActivity().use {
            onView(withId(R.id.queryInput)).perform(replaceText("fire"), submitSearchAction())
            onView(withText("Fireball")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun startupLoadingOverlay_blocksNavigationUntilInitialSyncFinishes() {
        val repository = FakeRulesRepository(
            resources = emptyList(),
            hasResources = false,
            syncDelayMillis = 1_000
        )
        UIEntryPoint.replaceRulesRepositoryForTests(repository)

        launchMainActivity().use { scenario ->
            onView(withId(R.id.mainLoadingOverlay)).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                val drawer = activity.findViewById<DrawerLayout>(R.id.mainDrawer)
                assertEquals(
                    DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                    drawer.getDrawerLockMode(GravityCompat.START)
                )
            }
        }
    }

    @Test
    fun searchFlow_displaysPromptWithoutSearchingUntilQueryIsSubmitted() {
        val repository = FakeRulesRepository(
            resources = listOf(
                RuleResource(
                    id = "official:spells:fireball",
                    name = "Fireball",
                    type = ResourceType.SPELL,
                    source = RuleSource.OFFICIAL,
                    description = "A bright streak flashes.",
                    apiUrl = "/api/spells/fireball"
                )
            )
        )
        UIEntryPoint.replaceRulesRepositoryForTests(repository)

        launchMainActivity().use {
            onView(withText(R.string.search_empty_prompt)).check(matches(isDisplayed()))
            onView(withText("Fireball")).check(doesNotExist())
            assertEquals(emptyList<String>(), repository.searchQueries)

            onView(withId(R.id.queryInput)).perform(replaceText("missing"), submitSearchAction())
            onView(withText(R.string.search_no_results)).check(matches(isDisplayed()))
            onView(withText("Fireball")).check(doesNotExist())

            onView(withId(R.id.queryInput)).perform(replaceText(""), submitSearchAction())
            onView(withText(R.string.search_empty_prompt)).check(matches(isDisplayed()))
            onView(withText("Fireball")).check(doesNotExist())
            assertEquals(listOf("missing"), repository.searchQueries)
        }
    }

    @Test
    fun searchFlow_emptyQueryFilterShowsMatchingResourceTypes() {
        val repository = FakeRulesRepository(
            resources = listOf(
                RuleResource(
                    id = "official:spells:fireball",
                    name = "Fireball",
                    type = ResourceType.SPELL,
                    source = RuleSource.OFFICIAL,
                    description = "A bright streak flashes.",
                    apiUrl = "/api/spells/fireball"
                ),
                RuleResource(
                    id = "official:classes:wizard",
                    name = "Wizard",
                    type = ResourceType.CLASS,
                    source = RuleSource.OFFICIAL,
                    description = "A scholarly magic-user.",
                    apiUrl = "/api/classes/wizard"
                ),
                RuleResource(
                    id = "official:races:elf",
                    name = "Elf",
                    type = ResourceType.RACE,
                    source = RuleSource.OFFICIAL,
                    description = "A graceful ancestry.",
                    apiUrl = "/api/races/elf"
                )
            )
        )
        UIEntryPoint.replaceRulesRepositoryForTests(repository)

        launchMainActivity().use {
            onView(withText(R.string.search_empty_prompt)).check(matches(isDisplayed()))
            onView(withText("Fireball")).check(doesNotExist())
            onView(withText("Wizard")).check(doesNotExist())
            onView(withText("Elf")).check(doesNotExist())
            assertEquals(emptyList<String>(), repository.searchQueries)

            onView(withText(R.string.filter_classes)).perform(click())

            onView(withText("Wizard")).check(matches(isDisplayed()))
            onView(withText("Fireball")).check(doesNotExist())
            onView(withText("Elf")).check(doesNotExist())
            assertEquals(listOf(""), repository.searchQueries)
        }
    }

    @Test
    fun searchFlow_primaryFiltersAreExclusiveAndSecondaryFiltersReset() {
        UIEntryPoint.replaceRulesRepositoryForTests(
            FakeRulesRepository(
                resources = listOf(
                    RuleResource(
                        id = "official:spells:fireball",
                        name = "Fireball",
                        type = ResourceType.SPELL,
                        source = RuleSource.OFFICIAL,
                        description = "A bright streak flashes.",
                        apiUrl = "/api/spells/fireball"
                    ),
                    RuleResource(
                        id = "official:classes:wizard",
                        name = "Wizard",
                        type = ResourceType.CLASS,
                        source = RuleSource.OFFICIAL,
                        description = "A scholarly magic-user.",
                        apiUrl = "/api/classes/wizard"
                    )
                ),
                rawDetails = mapOf(
                    "official:spells:fireball" to """{ "level": 3 }"""
                )
            )
        )

        launchMainActivity().use {
            onView(withText(R.string.filter_spells)).perform(click())
            onView(withText(R.string.filter_spell_cantrip)).check(matches(isDisplayed()))
            onView(withText("Level 3")).perform(click())
            onView(withText("Fireball")).check(matches(isDisplayed()))

            onView(withText(R.string.filter_classes)).perform(click())

            onView(withText(R.string.filter_spells)).check(matches(not(isChecked())))
            onView(withText(R.string.filter_class)).check(matches(isDisplayed()))
            onView(withText(R.string.filter_spell_cantrip)).check(doesNotExist())
        }
    }

    @Test
    fun searchFlow_monsterPrimaryShowsChallengeRatingSlider() {
        UIEntryPoint.replaceRulesRepositoryForTests(
            FakeRulesRepository(
                resources = listOf(
                    RuleResource(
                        id = "official:monsters:goblin",
                        name = "Goblin",
                        type = ResourceType.MONSTER,
                        source = RuleSource.OFFICIAL,
                        description = "A small cunning monster.",
                        apiUrl = "/api/monsters/goblin"
                    )
                ),
                rawDetails = mapOf(
                    "official:monsters:goblin" to """{ "challenge_rating": "1/4" }"""
                )
            )
        )

        launchMainActivity().use {
            onView(withText(R.string.filter_monsters)).perform(click())
            onView(withId(R.id.monsterChallengeRatingSlider)).check(matches(isDisplayed()))
            onView(withText("Goblin")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun searchFlow_replacesResultsWithLoadingIndicatorWhileSearching() {
        UIEntryPoint.replaceRulesRepositoryForTests(
            FakeRulesRepository(
                resources = listOf(
                    RuleResource(
                        id = "official:spells:fireball",
                        name = "Fireball",
                        type = ResourceType.SPELL,
                        source = RuleSource.OFFICIAL,
                        description = "A bright streak flashes.",
                        apiUrl = "/api/spells/fireball"
                    ),
                    RuleResource(
                        id = "official:classes:wizard",
                        name = "Wizard",
                        type = ResourceType.CLASS,
                        source = RuleSource.OFFICIAL,
                        description = "A scholarly magic-user.",
                        apiUrl = "/api/classes/wizard"
                    )
                ),
                searchDelayMillis = 1_000
            )
        )

        launchMainActivity().use {
            onView(withId(R.id.queryInput)).perform(replaceText("fire"), submitSearchAction())
            onView(withId(R.id.main)).perform(waitForMainThreadAction(1_250))
            onView(withText("Fireball")).check(matches(isDisplayed()))

            onView(withId(R.id.queryInput)).perform(replaceText("wizard"), submitSearchAction())
            onView(withId(R.id.searchLoadingIndicator)).check(matches(isDisplayed()))
            onView(withId(R.id.searchResultsRecyclerView)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun searchResultFavoriteButton_addsResourceToFavorites() {
        UIEntryPoint.replaceRulesRepositoryForTests(
            FakeRulesRepository(
                resources = listOf(
                    RuleResource(
                        id = "official:spells:fireball",
                        name = "Fireball",
                        type = ResourceType.SPELL,
                        source = RuleSource.OFFICIAL,
                        description = "A bright streak flashes.",
                        apiUrl = "/api/spells/fireball"
                    )
                )
            )
        )

        launchMainActivity().use {
            onView(withId(R.id.queryInput)).perform(replaceText("fire"), submitSearchAction())
            onView(withText("Fireball")).check(matches(isDisplayed()))

            onView(withId(R.id.favoriteButton)).perform(click())
            openFavoritesFromDrawer()

            onView(withText("Fireball")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun favoritesFlow_addsResourceFromCompactSearchAndRemovesIt() {
        UIEntryPoint.replaceRulesRepositoryForTests(
            FakeRulesRepository(
                resources = listOf(
                    RuleResource(
                        id = "official:spells:fireball",
                        name = "Fireball",
                        type = ResourceType.SPELL,
                        source = RuleSource.OFFICIAL,
                        description = "A bright streak flashes.",
                        apiUrl = "/api/spells/fireball"
                    )
                )
            )
        )

        launchMainActivity().use {
            openFavoritesFromDrawer()
            onView(
                allOf(
                    withText("Fireball"),
                    isDescendantOfA(withId(R.id.favoritesRecyclerView))
                )
            ).check(doesNotExist())

            onView(withId(R.id.addFavoriteButton)).perform(click())
            onView(withId(R.id.addFavoriteQueryInput)).perform(replaceText("fire"), submitSearchAction())
            onView(
                allOf(
                    withId(R.id.resourceActionButton),
                    isDescendantOfA(withId(R.id.addFavoriteResultsRecyclerView))
                )
            ).perform(click())

            onView(
                allOf(
                    withText("Fireball"),
                    isDescendantOfA(withId(R.id.favoritesRecyclerView))
                )
            ).check(matches(isDisplayed()))

            onView(
                allOf(
                    withId(R.id.resourceActionButton),
                    isDescendantOfA(withId(R.id.favoritesRecyclerView))
                )
            ).perform(click())
            onView(
                allOf(
                    withText("Fireball"),
                    isDescendantOfA(withId(R.id.favoritesRecyclerView))
                )
            ).check(doesNotExist())
        }
    }

    @Test
    fun favoritesFilters_narrowVisibleFavoritesByResourceType() {
        UIEntryPoint.replaceRulesRepositoryForTests(
            FakeRulesRepository(
                resources = listOf(
                    RuleResource(
                        id = "official:spells:fireball",
                        name = "Fireball",
                        type = ResourceType.SPELL,
                        source = RuleSource.OFFICIAL,
                        description = "A bright streak flashes.",
                        apiUrl = "/api/spells/fireball",
                        isFavorite = true
                    ),
                    RuleResource(
                        id = "official:classes:wizard",
                        name = "Wizard",
                        type = ResourceType.CLASS,
                        source = RuleSource.OFFICIAL,
                        description = "A scholarly magic-user.",
                        apiUrl = "/api/classes/wizard",
                        isFavorite = true
                    )
                )
            )
        )

        launchMainActivity().use {
            openFavoritesFromDrawer()
            onView(withText("Fireball")).check(matches(isDisplayed()))
            onView(withText("Wizard")).check(matches(isDisplayed()))

            onView(withText(R.string.filter_spells)).perform(click())

            onView(withText("Fireball")).check(matches(isDisplayed()))
            onView(withText("Wizard")).check(doesNotExist())
        }
    }

    @Test
    fun settingsReloadButton_refreshesDefaultOfficialResourceTypes() {
        val repository = FakeRulesRepository(resources = emptyList())
        UIEntryPoint.replaceRulesRepositoryForTests(repository)

        launchMainActivity().use {
            openSettingsFromDrawer()
            onView(withId(R.id.settingsTitleText)).check(matches(isDisplayed()))

            onView(withId(R.id.reloadOfficialResourcesButton)).perform(click(), waitForMainThreadAction())

            assertEquals(
                ResourceType.entries.filter { type -> type.syncByDefault }.toSet(),
                repository.syncedTypes.toSet()
            )
        }
    }

    @Test
    fun settingsReloadButton_showsLoadingOverlayWhileSyncing() {
        val repository = FakeRulesRepository(
            resources = emptyList(),
            syncDelayMillis = 1_000
        )
        UIEntryPoint.replaceRulesRepositoryForTests(repository)

        launchMainActivity().use { scenario ->
            openSettingsFromDrawer()
            onView(withId(R.id.reloadOfficialResourcesButton)).perform(click())

            onView(withId(R.id.mainLoadingOverlay)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val drawer = activity.findViewById<DrawerLayout>(R.id.mainDrawer)
                assertEquals(
                    DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                    drawer.getDrawerLockMode(GravityCompat.START)
                )
            }
        }
    }

    @Test
    fun detailActivity_showsLoadingOverlayWhileDetailLoads() {
        UIEntryPoint.replaceRulesRepositoryForTests(
            FakeRulesRepository(
                resources = listOf(
                    RuleResource(
                        id = "official:spells:fireball",
                        name = "Fireball",
                        type = ResourceType.SPELL,
                        source = RuleSource.OFFICIAL,
                        description = "A bright streak flashes.",
                        apiUrl = "/api/spells/fireball"
                    )
                ),
                detailDelayMillis = 1_000
            )
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, RuleDetailActivity::class.java).apply {
            putExtra(RuleDetailActivity.EXTRA_RULE_ID, "official:spells:fireball")
        }

        ActivityScenario.launch<RuleDetailActivity>(intent).use {
            onView(withId(R.id.detailLoadingOverlay)).check(matches(isDisplayed()))
        }
    }

    private fun openFavoritesFromDrawer() {
        waitForView(allOf(withId(R.id.mainLoadingOverlay), not(isDisplayed())))
        onView(withId(R.id.mainDrawer)).perform(openDrawerAction())
        onView(
            allOf(
                withText(R.string.nav_favorites),
                isDescendantOfA(withId(R.id.mainNavigationView))
            )
        ).perform(click())
        waitForView(withId(R.id.addFavoriteButton))
    }

    private fun openSettingsFromDrawer() {
        waitForView(allOf(withId(R.id.mainLoadingOverlay), not(isDisplayed())))
        onView(withId(R.id.mainDrawer)).perform(openDrawerAction())
        onView(
            allOf(
                withText(R.string.nav_settings),
                isDescendantOfA(withId(R.id.settingsNavigationView))
            )
        ).perform(click())
        waitForView(withId(R.id.settingsTitleText))
    }

    private fun launchMainActivity(): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = android.os.SystemClock.uptimeMillis() + 30_000

        do {
            var hasWindowFocus = false
            scenario.onActivity { activity ->
                hasWindowFocus = activity.hasWindowFocus()
            }
            if (hasWindowFocus) return scenario

            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(100)
        } while (android.os.SystemClock.uptimeMillis() < deadline)

        scenario.close()
        throw AssertionError("MainActivity did not receive window focus within 30 seconds")
    }

    private fun waitForView(matcher: Matcher<View>, timeoutMillis: Long = 5_000) {
        onView(isRoot()).perform(
            object : ViewAction {
                override fun getConstraints(): Matcher<View> = isRoot()

                override fun getDescription(): String = "wait up to $timeoutMillis ms for $matcher"

                override fun perform(uiController: UiController, view: View) {
                    val deadline = android.os.SystemClock.uptimeMillis() + timeoutMillis
                    do {
                        if (view.anyDescendantOrSelf(matcher::matches)) return
                        uiController.loopMainThreadForAtLeast(50)
                    } while (android.os.SystemClock.uptimeMillis() < deadline)

                    throw AssertionError("Timed out waiting for view matching $matcher")
                }
            }
        )
    }

    private fun View.anyDescendantOrSelf(predicate: (View) -> Boolean): Boolean {
        if (predicate(this)) return true
        if (this !is ViewGroup) return false

        return (0 until childCount).any { index ->
            getChildAt(index).anyDescendantOrSelf(predicate)
        }
    }

    private fun openDrawerAction(): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(DrawerLayout::class.java)

            override fun getDescription(): String = "open drawer"

            override fun perform(uiController: UiController, view: View) {
                (view as DrawerLayout).openDrawer(GravityCompat.START, false)
                uiController.loopMainThreadUntilIdle()
            }
        }
    }

    private fun submitSearchAction(): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(TextView::class.java)

            override fun getDescription(): String = "submit search editor action"

            override fun perform(uiController: UiController, view: View) {
                (view as TextView).onEditorAction(EditorInfo.IME_ACTION_SEARCH)
                uiController.loopMainThreadForAtLeast(250)
            }
        }
    }

    private fun waitForMainThreadAction(millis: Long = 250): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

            override fun getDescription(): String = "wait for main thread"

            override fun perform(uiController: UiController, view: View) {
                uiController.loopMainThreadForAtLeast(millis)
            }
        }
    }

    private class FakeRulesRepository(
        resources: List<RuleResource>,
        private val searchDelayMillis: Long = 0,
        private val rawDetails: Map<String, String> = emptyMap(),
        private val hasResources: Boolean = true,
        private val syncDelayMillis: Long = 0,
        private val detailDelayMillis: Long = 0
    ) : RulesRepository {
        private val resourcesById = resources.associateBy { resource -> resource.id }.toMutableMap()
        val syncedTypes = mutableListOf<ResourceType>()
        val searchQueries = mutableListOf<String>()

        override suspend fun searchRules(query: String, filters: RuleSearchFilters): List<RuleResource> {
            searchQueries += query

            if (searchDelayMillis > 0) {
                delay(searchDelayMillis)
            }

            return resourcesById.values.filter { resource ->
                filters.matches(resource) &&
                    (resource.name.contains(query, ignoreCase = true) ||
                        resource.description.contains(query, ignoreCase = true))
            }
        }

        override suspend fun searchRules(
            query: String,
            filters: RuleSearchFilters,
            secondaryFilter: SearchSecondaryFilter?
        ): List<RuleResource> {
            val primaryResults = searchRules(query, filters)
            if (secondaryFilter == null) return primaryResults

            return primaryResults.filter { resource ->
                secondaryFilter.matches(resource, getRuleDetail(resource.id))
            }
        }

        override suspend fun availableMonsterChallengeRatings(
            query: String,
            filters: RuleSearchFilters
        ): List<Double> {
            return searchRules(query, filters)
                .filter { resource -> resource.type == ResourceType.MONSTER }
                .mapNotNull { resource -> getRuleDetail(resource.id).challengeRating() }
                .distinct()
                .sorted()
        }

        override suspend fun syncOfficialResources(type: ResourceType) {
            if (syncDelayMillis > 0) {
                delay(syncDelayMillis)
            }
            syncedTypes += type
        }

        override suspend fun addCustomRule(rule: RuleResource) = Unit

        override suspend fun addCustomRule(detail: RuleDetail) = Unit

        override suspend fun editCustomRule(rule: RuleResource): Boolean = false

        override suspend fun editCustomRule(detail: RuleDetail): Boolean = false

        override suspend fun deleteCustomRule(id: String): Boolean = false

        override suspend fun addLocalRule(detail: RuleDetail) = Unit

        override suspend fun editLocalRule(detail: RuleDetail): Boolean = false

        override suspend fun deleteLocalRule(id: String): Boolean = false

        override suspend fun setFavorite(id: String, isFavorite: Boolean): Boolean {
            val resource = resourcesById[id] ?: return false
            resourcesById[id] = resource.copy(isFavorite = isFavorite)
            return true
        }

        override suspend fun getFavoriteRules(): List<RuleResource> {
            return resourcesById.values
                .filter { it.isFavorite }
                .sortedBy { it.name }
        }

        override suspend fun getRuleResource(id: String): RuleResource? = resourcesById[id]

        override suspend fun getRuleDetail(id: String): RuleDetail? {
            if (detailDelayMillis > 0) {
                delay(detailDelayMillis)
            }
            val resource = getRuleResource(id) ?: return null
            return RuleDetail(
                id = resource.id,
                resource = resource,
                sections = listOf(RuleSection("Description", resource.description, 0)),
                rawJson = rawDetails[id]
            )
        }

        override suspend fun hasRuleResources(type: ResourceType): Boolean = hasResources
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun stabilizeEmulatorSystemServices() {
            runCatching {
                val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
                uiAutomation.executeShellCommand("cmd bluetooth_manager disable").close()
            }
        }
    }
}
