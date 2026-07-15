package com.example.dnd_ruleslawyer.core.json

import com.google.gson.JsonArray
import com.google.gson.JsonObject

fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

fun JsonObject.int(name: String): Int? =
    get(name)?.takeIf { !it.isJsonNull }?.asInt

fun JsonObject.boolean(name: String): Boolean? =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asBoolean

fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

fun JsonObject.array(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

fun JsonArray.objects(): List<JsonObject> =
    mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }

fun JsonObject.arrayStrings(name: String): List<String> =
    get(name)
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { item -> item.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString }
        ?: emptyList()

fun JsonObject.textList(name: String): List<String> {
    val value = get(name)?.takeIf { !it.isJsonNull } ?: return emptyList()

    return when {
        value.isJsonArray -> value.asJsonArray.mapNotNull { item ->
            item.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
        }
        value.isJsonPrimitive -> listOf(value.asString)
        else -> emptyList()
    }
}

fun JsonObject.namedDescriptionList(name: String): List<Pair<String, String>> =
    get(name)
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val itemName = obj.string("name") ?: return@mapNotNull null
            val desc = obj.string("desc") ?: return@mapNotNull null

            itemName to desc
        }
        ?: emptyList()

fun JsonObject.spellReferenceList(name: String): List<String> =
    get(name)
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { item ->
            item.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.obj("spell")
                ?.string("name")
        }
        ?: emptyList()

fun JsonObject.choiceDescriptions(name: String): List<String> =
    get(name)
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { item ->
            item.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.string("desc")
        }
        ?: emptyList()

fun JsonObject.equipmentQuantityList(name: String): List<String> =
    get(name)
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val equipmentName = obj.obj("equipment")?.string("name") ?: return@mapNotNull null
            val quantity = obj.int("quantity") ?: 1

            "${quantity}x $equipmentName"
        }
        ?: emptyList()

fun JsonObject.namedTextList(name: String): List<Pair<String, String>> =
    get(name)
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val itemName = obj.string("name") ?: return@mapNotNull null
            val desc = obj.textList("desc")
                .joinToString("\n\n")
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            itemName to desc
        }
        ?: emptyList()

fun JsonObject.referenceNameList(name: String): List<String> =
    get(name)
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { item ->
            item.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.string("name")
        }
        ?: emptyList()

fun JsonObject.stringOrNumber(name: String): String? =
    get(name)
        ?.takeIf { !it.isJsonNull }
        ?.asJsonPrimitive
        ?.toString()
        ?.trim('"')
