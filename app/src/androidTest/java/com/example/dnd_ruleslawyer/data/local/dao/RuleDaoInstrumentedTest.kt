package com.example.dnd_ruleslawyer.data.local.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dnd_ruleslawyer.data.local.database.DndRulesDatabase
import com.example.dnd_ruleslawyer.data.local.entity.RuleDetailEntity
import com.example.dnd_ruleslawyer.data.local.entity.RuleResourceEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleDaoInstrumentedTest {
    private lateinit var database: DndRulesDatabase
    private lateinit var resourceDao: RuleResourceDao
    private lateinit var detailDao: RuleDetailDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, DndRulesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        resourceDao = database.ruleResourceDao()
        detailDao = database.ruleDetailDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resourceDao_searchesFavoritesAndDeletesResources() = runBlocking {
        val fireball = resource("official:spells:fireball", "Fireball", "SPELL", isFavorite = true)
        val grapple = resource("custom:rules:grapple", "Grapple Ruling", "RULE")
        resourceDao.upsertAll(listOf(grapple, fireball))

        assertEquals(listOf(fireball.id), resourceDao.search("fire").map { it.id })
        assertEquals(listOf(fireball.id), resourceDao.getFavorites().map { it.id })
        assertEquals(1, resourceDao.countByType("SPELL"))
        assertEquals(1, resourceDao.deleteById(grapple.id))
        assertNull(resourceDao.getById(grapple.id))
    }

    @Test
    fun detailDao_storesDetailsAndCascadesWhenResourceIsDeleted() = runBlocking {
        val resource = resource("custom:rules:counterspell", "Counterspell Timing", "RULE")
        resourceDao.upsert(resource)
        detailDao.upsert(
            RuleDetailEntity(
                id = resource.id,
                resourceId = resource.id,
                sectionsJson = """[{"title":"Ruling","body":"Use the reaction.","order":0}]""",
                rawJson = null
            )
        )

        assertEquals(resource.id, detailDao.getByResourceId(resource.id)?.id)
        assertTrue(resourceDao.deleteById(resource.id) > 0)
        assertNull(detailDao.getByResourceId(resource.id))
    }

    private fun resource(
        id: String,
        name: String,
        type: String,
        isFavorite: Boolean = false
    ): RuleResourceEntity {
        return RuleResourceEntity(
            id = id,
            name = name,
            type = type,
            source = if (id.startsWith("official")) "OFFICIAL" else "CUSTOM",
            description = "$name description",
            apiUrl = null,
            isFavorite = isFavorite
        )
    }
}
