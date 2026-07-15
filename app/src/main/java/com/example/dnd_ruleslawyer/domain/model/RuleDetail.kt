package com.example.dnd_ruleslawyer.domain.model

data class RuleDetail(
    val id: String,
    val resource: RuleResource,
    val sections: List<RuleSection>,
    val rawJson: String? = null
)
