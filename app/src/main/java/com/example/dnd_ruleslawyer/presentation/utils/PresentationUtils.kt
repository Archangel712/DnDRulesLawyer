package com.example.dnd_ruleslawyer.presentation.utils

import com.example.dnd_ruleslawyer.domain.model.RuleDetail
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.floor

fun Int?.ordinalSpellLevel(): String =
    when (this) {
        1 -> "1st-level"
        2 -> "2nd-level"
        3 -> "3rd-level"
        null -> ""
        else -> "${this}th-level"
    }

fun Int?.withModifier(): String {
    if (this == null) return "-"
    val modifier = floor((this - 10) / 2.0).toInt()
    val signed = if (modifier >= 0) "+$modifier" else "$modifier"
    return "$this ($signed)"
}

fun Int?.signedBonus(): String =
    this?.let { if (it >= 0) "+$it" else "$it" } ?: "-"

fun String?.escapeHtml(): String =
    this.orEmpty()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

fun String?.tableCellValue(): String =
    this?.takeIf { it.isNotBlank() } ?: "-"

fun RuleDetail.rawJsonObject(): JsonObject? {
    val rawJson = rawJson ?: return null

    return runCatching {
        JsonParser.parseString(rawJson)
            .takeIf { it.isJsonObject }
            ?.asJsonObject
    }.getOrNull()
}
