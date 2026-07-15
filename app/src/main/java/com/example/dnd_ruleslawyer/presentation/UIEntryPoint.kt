package com.example.dnd_ruleslawyer.presentation

import android.content.Context
import android.content.Intent
import com.example.dnd_ruleslawyer.data.repository.RepositoryProvider
import com.example.dnd_ruleslawyer.data.repository.RulesRepository
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.domain.voice.SpeechToTextManager
import com.example.dnd_ruleslawyer.presentation.detail.RuleDetailActivity
import com.example.dnd_ruleslawyer.presentation.detail.RuleDetailHtmlRenderer
import com.example.dnd_ruleslawyer.presentation.search.RuleResourceAdapter

object UIEntryPoint {
    @Volatile
    private var rulesRepository: RulesRepository? = null

    fun rulesRepository(context: Context): RulesRepository {
        return rulesRepository ?: synchronized(this) {
            rulesRepository ?: RepositoryProvider.createRulesRepository(context.applicationContext)
                .also { rulesRepository = it }
        }
    }

    fun createRuleResultsAdapter(
        onRuleClicked: (RuleResource) -> Unit,
        onFavoriteClicked: ((RuleResource) -> Unit)? = null
    ): RuleResourceAdapter {
        return RuleResourceAdapter(onRuleClicked, onFavoriteClicked)
    }

    fun createRuleDetailIntent(context: Context, ruleId: String): Intent {
        return Intent(context, RuleDetailActivity::class.java).apply {
            putExtra(RuleDetailActivity.EXTRA_RULE_ID, ruleId)
        }
    }

    fun createRuleDetailRenderer(): RuleDetailHtmlRenderer {
        return RuleDetailHtmlRenderer()
    }

    fun createSpeechToTextManager(
        context: Context,
        onTextResult: (String) -> Unit,
        onListeningChanged: (Boolean) -> Unit,
        onError: (String) -> Unit
    ): SpeechToTextManager {
        return SpeechToTextManager(
            context = context,
            onTextResult = onTextResult,
            onListeningChanged = onListeningChanged,
            onError = onError
        )
    }

    fun replaceRulesRepositoryForTests(repository: RulesRepository) {
        rulesRepository = repository
    }

    fun clearRulesRepositoryForTests() {
        rulesRepository = null
    }
}
