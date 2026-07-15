package com.example.dnd_ruleslawyer.presentation.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableEditorCodecTest {
    @Test
    fun parseRows_readsPipeSeparatedRowsAndPadsMissingCells() {
        val rows = TableEditorCodec.parseRows(
            text = "1 | +2 | Feature A\n2 | +2",
            fieldCount = 3,
            blockRows = false
        )

        assertEquals(listOf("1", "+2", "Feature A"), rows[0].values)
        assertEquals(listOf("2", "+2", ""), rows[1].values)
    }

    @Test
    fun encodeRows_preservesPipeSeparatedContract() {
        val encoded = TableEditorCodec.encodeRows(
            rows = listOf(
                TableRowState(listOf("1", "+2", "Feature A")),
                TableRowState(listOf("", "", ""))
            ),
            blockRows = false
        )

        assertEquals("1 | +2 | Feature A", encoded)
    }

    @Test
    fun parseAndEncodeRows_preservesBlockRowsForLongDescriptions() {
        val rows = TableEditorCodec.parseRows(
            text = "Feature A | 1 | First paragraph\nSecond line\n\nFeature B | 2 | Other text",
            fieldCount = 3,
            blockRows = true
        )

        assertEquals("First paragraph\nSecond line", rows[0].values[2])
        assertEquals(
            "Feature A | 1 | First paragraph\nSecond line\n\nFeature B | 2 | Other text",
            TableEditorCodec.encodeRows(rows, blockRows = true)
        )
    }

    @Test
    fun normalizedCustomColumns_derivesStableUniqueKeysAndPreservesTypes() {
        val columns = TableEditorCodec.normalizedCustomColumns(
            listOf(
                CustomLevelColumnDraft("", "Pact Slots", "number"),
                CustomLevelColumnDraft("custom_2", "Pact Slots", "checkbox"),
                CustomLevelColumnDraft("slot_level", "Slot Level", "dice")
            )
        )

        assertEquals("pact_slots", columns[0].key)
        assertEquals("pact_slots_2", columns[1].key)
        assertEquals("slot_level", columns[2].key)
        assertEquals("checkbox", columns[1].type)
        assertEquals("dice", columns[2].type)
    }

    @Test
    fun booleanCellValue_acceptsCommonTruthValuesOnly() {
        assertTrue(TableEditorCodec.booleanCellValue("true"))
        assertTrue(TableEditorCodec.booleanCellValue("yes"))
        assertTrue(TableEditorCodec.booleanCellValue("1"))
        assertFalse(TableEditorCodec.booleanCellValue("false"))
        assertFalse(TableEditorCodec.booleanCellValue(""))
    }
}
