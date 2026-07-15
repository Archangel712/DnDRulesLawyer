package com.example.dnd_ruleslawyer.presentation.create

object TableEditorCodec {
    fun parseRows(text: String, fieldCount: Int, blockRows: Boolean): List<TableRowState> {
        val rawRows = if (blockRows) {
            text.split(Regex("\\n\\s*\\n"))
                .map { value -> value.trim() }
                .filter { value -> value.isNotBlank() }
        } else {
            text.lines().map { line -> line.trim() }.filter { line -> line.isNotBlank() }
        }

        return rawRows.map { row ->
            val parts = row.split("|", limit = fieldCount).map { part -> part.trim() }
            TableRowState(List(fieldCount) { index -> parts.getOrNull(index).orEmpty() })
        }
    }

    fun encodeRows(rows: List<TableRowState>, blockRows: Boolean): String =
        rows
            .map { row -> row.values.map { value -> value.trim() } }
            .filter { row -> row.any { value -> value.isNotBlank() } }
            .joinToString(if (blockRows) "\n\n" else "\n") { row ->
                row.joinToString(" | ")
            }

    fun displayRows(rows: List<TableRowState>, columns: List<TableColumnSpec>): String =
        rows
            .map { row -> row.values }
            .filter { row -> row.any { value -> value.isNotBlank() } }
            .joinToString("\n") { row ->
                columns.mapIndexedNotNull { index, column ->
                    row.getOrNull(index)
                        ?.takeIf { value -> value.isNotBlank() }
                        ?.let { value -> "${column.label}: $value" }
                }.joinToString(", ")
            }

    fun normalizedCustomColumns(columns: List<CustomLevelColumnDraft>): List<CustomLevelColumnDraft> {
        val usedKeys = mutableSetOf<String>()
        return columns.mapIndexedNotNull { index, column ->
            val label = column.label.trim().ifBlank { return@mapIndexedNotNull null }
            val baseKey = label.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "custom_${index + 1}" }
            var key = column.key.takeUnless { it.isBlank() || it.matches(Regex("custom_\\d+")) } ?: baseKey
            key = key.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { baseKey }
            var suffix = 2
            while (key in usedKeys) {
                key = "${baseKey}_${suffix++}"
            }
            usedKeys += key
            column.copy(key = key, label = label)
        }
    }

    fun booleanCellValue(value: String): Boolean =
        value.trim().lowercase() in setOf("true", "yes", "y", "1", "checked", "on")
}
