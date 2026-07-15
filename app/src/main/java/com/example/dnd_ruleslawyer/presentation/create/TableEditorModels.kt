package com.example.dnd_ruleslawyer.presentation.create

import android.text.InputType

enum class TableColumnType(val storageValue: String) {
    TEXT("text"),
    NUMBER("number"),
    CHECKBOX("checkbox"),
    DICE("dice");

    val inputType: Int
        get() = when (this) {
            TEXT -> InputType.TYPE_CLASS_TEXT
            NUMBER -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            CHECKBOX -> InputType.TYPE_CLASS_TEXT
            DICE -> InputType.TYPE_CLASS_TEXT
        }

    companion object {
        fun fromStorage(value: String?): TableColumnType =
            entries.firstOrNull { type -> type.storageValue == value } ?: TEXT
    }
}

data class TableColumnSpec(
    val key: String,
    val label: String,
    val type: TableColumnType = TableColumnType.TEXT,
    val inputType: Int = type.inputType,
    val fixed: Boolean = true
)

data class TableRowState(
    val values: List<String>
)

data class TableEditorResult(
    val encoded: String,
    val display: String,
    val customColumns: List<CustomLevelColumnDraft>
)
