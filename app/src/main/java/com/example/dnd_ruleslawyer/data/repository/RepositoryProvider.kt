package com.example.dnd_ruleslawyer.data.repository

import android.content.Context
import com.example.dnd_ruleslawyer.data.local.database.DatabaseProvider
import com.example.dnd_ruleslawyer.data.local.mapper.LocalRuleDetailMapper
import com.example.dnd_ruleslawyer.data.local.mapper.LocalRuleMapper
import com.example.dnd_ruleslawyer.data.remote.api.RetrofitProvider
import com.example.dnd_ruleslawyer.data.remote.mapper.RemoteRuleDetailMapper
import com.example.dnd_ruleslawyer.data.remote.mapper.RemoteRuleMapper

object RepositoryProvider {
    fun createRulesRepository(context: Context): RulesRepository {
        val database = DatabaseProvider.create(context)

        return DefaultRulesRepository(
            api = RetrofitProvider.api,
            dao = database.ruleResourceDao(),
            localMapper = LocalRuleMapper(),
            remoteMapper = RemoteRuleMapper(),
            detailDao = database.ruleDetailDao(),
            detailMapper = LocalRuleDetailMapper(),
            remoteDetailMapper = RemoteRuleDetailMapper()
        )
    }
}