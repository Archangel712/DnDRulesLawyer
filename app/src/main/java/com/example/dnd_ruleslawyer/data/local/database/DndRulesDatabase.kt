package com.example.dnd_ruleslawyer.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.dnd_ruleslawyer.data.local.dao.RuleDetailDao
import com.example.dnd_ruleslawyer.data.local.dao.RuleResourceDao
import com.example.dnd_ruleslawyer.data.local.entity.RuleDetailEntity
import com.example.dnd_ruleslawyer.data.local.entity.RuleResourceEntity


@Database(
    entities = [RuleResourceEntity::class, RuleDetailEntity::class],
    version = 4,
    exportSchema = false
)
abstract class DndRulesDatabase : RoomDatabase() {
    abstract fun ruleResourceDao(): RuleResourceDao

    abstract fun ruleDetailDao(): RuleDetailDao
}
