package com.example.dnd_ruleslawyer.data.local.database

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    fun create(context: Context): DndRulesDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            DndRulesDatabase::class.java,
            "dnd_rules_database"
        ).fallbackToDestructiveMigrationFrom(true).build()
    }
}
