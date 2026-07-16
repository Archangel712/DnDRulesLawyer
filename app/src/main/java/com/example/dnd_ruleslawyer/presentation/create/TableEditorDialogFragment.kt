package com.example.dnd_ruleslawyer.presentation.create

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.dnd_ruleslawyer.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class TableEditorDialogFragment : DialogFragment() {
    private val requestKey: String by lazy { requireArguments().getString(ARG_REQUEST_KEY).orEmpty() }
    private val titleText: String by lazy { requireArguments().getString(ARG_TITLE).orEmpty() }
    private val blockRows: Boolean by lazy { requireArguments().getBoolean(ARG_BLOCK_ROWS) }
    private val customColumnsAllowed: Boolean by lazy { requireArguments().getBoolean(ARG_CUSTOM_COLUMNS_ALLOWED) }
    private lateinit var root: LinearLayout
    private lateinit var rowsContainer: LinearLayout
    private lateinit var headerContainer: LinearLayout
    private val rows = mutableListOf<TableRowState>()
    private val customColumns = mutableListOf<CustomLevelColumnDraft>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_DnD_RulesLawyer)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        Dialog(requireContext(), theme).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val fixedColumns = fixedColumns()
        customColumns += requireArguments().customColumnsFromArgs()
        val allColumns = fixedColumns + customColumns.toSpecs()
        rows += TableEditorCodec.parseRows(
            text = requireArguments().getString(ARG_ENCODED).orEmpty(),
            fieldCount = allColumns.size,
            blockRows = blockRows
        ).ifEmpty { listOf(TableRowState(List(allColumns.size) { "" })) }

        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurface))
        }
        root.addView(toolbar())

        val horizontalScroll = HorizontalScrollView(requireContext()).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val tableLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        headerContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainer))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        rowsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        tableLayout.addView(headerContainer)
        tableLayout.addView(ScrollView(requireContext()).apply {
            addView(rowsContainer)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        })
        horizontalScroll.addView(tableLayout)
        root.addView(horizontalScroll)

        renderTable()
        return root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onDestroy() {
        callbacks.remove(requestKey)
        super.onDestroy()
    }

    private fun toolbar(): View =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(themeColor(com.google.android.material.R.attr.colorPrimary))

            val initialTopPadding = paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val safeTop = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() or
                        WindowInsetsCompat.Type.displayCutout()
                ).top
                view.updatePadding(top = initialTopPadding + safeTop)
                insets
            }
            ViewCompat.requestApplyInsets(this)

            addView(toolbarButton(text = "X", contentDescription = getString(android.R.string.cancel)) {
                dismiss()
            })
            addView(TextView(requireContext()).apply {
                text = titleText
                textSize = 18f
                setTextColor(themeColor(com.google.android.material.R.attr.colorOnPrimary))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(8), 0, dp(8), 0)
                }
            })
            if (customColumnsAllowed) {
                addView(toolbarButton(text = getString(R.string.create_structured_columns), contentDescription = getString(R.string.create_structured_columns)) {
                    showColumnsDialog()
                })
            }
            addView(toolbarButton(text = "+", contentDescription = getString(R.string.create_structured_add_row)) {
                rows += TableRowState(List(allColumns().size) { "" })
                renderTable()
            })
            addView(toolbarButton(text = getString(android.R.string.ok), contentDescription = getString(android.R.string.ok)) {
                applyResult()
            })
        }

    private fun toolbarButton(text: String, contentDescription: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(requireContext()).apply {
            this.text = text
            this.contentDescription = contentDescription
            isAllCaps = false
            minWidth = dp(40)
            minHeight = dp(40)
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(themeColor(com.google.android.material.R.attr.colorPrimary))
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnPrimary))
            iconTint = ColorStateList.valueOf(themeColor(com.google.android.material.R.attr.colorOnPrimary))
            setOnClickListener { onClick() }
        }

    private fun renderTable() {
        val columns = allColumns()
        rows.replaceAll { row -> row.withSize(columns.size) }

        headerContainer.removeAllViews()
        columns.forEach { column ->
            headerContainer.addView(TextView(requireContext()).apply {
                text = column.label
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
                setPadding(dp(8), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(columnWidth(column), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        }
        headerContainer.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), 1)
        })

        rowsContainer.removeAllViews()
        rows.forEachIndexed { rowIndex, row ->
            rowsContainer.addView(rowView(rowIndex, row, columns))
        }
    }

    private fun rowView(rowIndex: Int, row: TableRowState, columns: List<TableColumnSpec>): View =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(
                if (rowIndex % 2 == 0) {
                    themeColor(com.google.android.material.R.attr.colorSurface)
                } else {
                    themeColor(com.google.android.material.R.attr.colorSurfaceContainer)
                }
            )

            columns.forEachIndexed { columnIndex, column ->
                addView(cellView(rowIndex, columnIndex, column, row.values.getOrNull(columnIndex).orEmpty()))
            }
            addView(MaterialButton(requireContext()).apply {
                icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete_24)
                iconTint = ColorStateList.valueOf(themeColor(com.google.android.material.R.attr.colorPrimary))
                backgroundTintList = ColorStateList.valueOf(themeColor(com.google.android.material.R.attr.colorSurface))
                contentDescription = getString(R.string.create_structured_remove_row)
                minWidth = dp(40)
                minHeight = dp(40)
                insetTop = 0
                insetBottom = 0
                text = ""
                setOnClickListener { removeRowWithUndo(rowIndex) }
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(44))
            })
        }

    private fun cellView(rowIndex: Int, columnIndex: Int, column: TableColumnSpec, value: String): View {
        if (column.type == TableColumnType.CHECKBOX) {
            return CheckBox(requireContext()).apply {
                isChecked = TableEditorCodec.booleanCellValue(value)
                setOnCheckedChangeListener { _, isChecked ->
                    rows[rowIndex] = rows[rowIndex].updated(columnIndex, isChecked.toString())
                }
                layoutParams = LinearLayout.LayoutParams(columnWidth(column), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        }

        return TextView(requireContext()).apply {
            text = value.ifBlank { "-" }
            textSize = 15f
            minHeight = dp(44)
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
            setPadding(dp(8), 0, dp(8), 0)
            maxLines = if (column.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) 2 else 1
            setOnClickListener { showCellEditor(rowIndex, columnIndex, column) }
            layoutParams = LinearLayout.LayoutParams(columnWidth(column), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun showCellEditor(rowIndex: Int, columnIndex: Int, column: TableColumnSpec) {
        val input = EditText(requireContext()).apply {
            hint = column.label
            inputType = column.inputType
            setText(rows[rowIndex].values.getOrNull(columnIndex).orEmpty())
            if (column.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) {
                minLines = 4
                gravity = Gravity.TOP
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(column.label)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                rows[rowIndex] = rows[rowIndex].updated(columnIndex, input.text.toString())
                renderTable()
            }
            .show()
    }

    private fun removeRowWithUndo(rowIndex: Int) {
        val removed = rows.removeAt(rowIndex)
        renderTable()
        Snackbar.make(root, R.string.create_structured_row_removed, Snackbar.LENGTH_LONG)
            .setAction(R.string.create_structured_undo) {
                rows.add(rowIndex.coerceAtMost(rows.size), removed)
                renderTable()
            }
            .show()
    }

    private fun showColumnsDialog() {
        val originalColumns = customColumns.toList()
        val workingColumns = customColumns.toMutableList()
        val list = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        val rows = mutableListOf<Pair<EditText, android.widget.Spinner>>()

        fun captureWorkingColumns() {
            rows.forEachIndexed { rowIndex, (name, type) ->
                if (rowIndex < workingColumns.size) {
                    workingColumns[rowIndex] = workingColumns[rowIndex].copy(
                        label = name.text.toString(),
                        type = TableColumnType.entries[type.selectedItemPosition].storageValue
                    )
                }
            }
        }

        fun renderColumnRows() {
            list.removeAllViews()
            rows.clear()
            workingColumns.forEachIndexed { index, column ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val nameInput = EditText(requireContext()).apply {
                    hint = getString(R.string.create_structured_column_name)
                    setText(column.label)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val typeInput = android.widget.Spinner(requireContext()).apply {
                    adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        TableColumnType.entries.map { it.storageValue.replaceFirstChar { char -> char.uppercase() } }
                    )
                    setSelection(TableColumnType.entries.indexOf(TableColumnType.fromStorage(column.type)))
                    layoutParams = LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val deleteButton = MaterialButton(requireContext()).apply {
                    icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete_24)
                    text = ""
                    contentDescription = getString(R.string.create_structured_remove_column)
                    minWidth = dp(40)
                    setOnClickListener {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.create_structured_remove_column)
                            .setMessage(R.string.create_structured_remove_column_confirm)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                captureWorkingColumns()
                                workingColumns.removeAt(index)
                                renderColumnRows()
                            }
                            .show()
                    }
                }
                row.addView(nameInput)
                row.addView(typeInput)
                row.addView(deleteButton)
                rows += nameInput to typeInput
                list.addView(row)
            }
        }

        renderColumnRows()

        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(MaterialButton(requireContext()).apply {
                text = getString(R.string.create_structured_add_column)
                isAllCaps = false
                setOnClickListener {
                    captureWorkingColumns()
                    workingColumns += CustomLevelColumnDraft("", getString(R.string.create_structured_custom_column_default, workingColumns.size + 1), TableColumnType.TEXT.storageValue)
                    renderColumnRows()
                }
            })
            addView(ScrollView(requireContext()).apply { addView(list) })
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.create_structured_columns)
            .setView(wrapper)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                captureWorkingColumns()
                val previousRows = this.rows.toList()
                val fixedCount = fixedColumns().size
                val normalizedColumns = TableEditorCodec.normalizedCustomColumns(workingColumns)
                customColumns.clear()
                customColumns += normalizedColumns
                this.rows.clear()
                this.rows += previousRows.map { row ->
                    val fixedValues = List(fixedCount) { index -> row.values.getOrNull(index).orEmpty() }
                    val customValues = workingColumns.map { column ->
                        val oldIndex = originalColumns.indexOfFirst { oldColumn ->
                            oldColumn.key.isNotBlank() && oldColumn.key == column.key
                        }
                        if (oldIndex >= 0) row.values.getOrNull(fixedCount + oldIndex).orEmpty() else ""
                    }
                    TableRowState(fixedValues + customValues)
                }
                renderTable()
            }
            .show()
    }

    private fun applyResult() {
        val finalRows = rows.filter { row -> row.values.any { value -> value.isNotBlank() } }
        val finalColumns = TableEditorCodec.normalizedCustomColumns(customColumns)
        val allColumns = fixedColumns() + finalColumns.toSpecs()
        callbacks.remove(requestKey)?.invoke(
            TableEditorResult(
                encoded = TableEditorCodec.encodeRows(finalRows, blockRows),
                display = TableEditorCodec.displayRows(finalRows, allColumns),
                customColumns = finalColumns
            )
        )
        dismiss()
    }

    private fun allColumns(): List<TableColumnSpec> =
        fixedColumns() + customColumns.toSpecs()

    private fun fixedColumns(): List<TableColumnSpec> {
        val labels = requireArguments().getStringArrayList(ARG_COLUMN_LABELS).orEmpty()
        val types = requireArguments().getStringArrayList(ARG_COLUMN_TYPES).orEmpty()
        val inputTypes = requireArguments().getIntegerArrayList(ARG_COLUMN_INPUT_TYPES).orEmpty()
        return labels.mapIndexed { index, label ->
            val type = TableColumnType.fromStorage(types.getOrNull(index))
            TableColumnSpec(
                key = "fixed_$index",
                label = label,
                type = type,
                inputType = inputTypes.getOrNull(index) ?: type.inputType,
                fixed = true
            )
        }
    }

    private fun List<CustomLevelColumnDraft>.toSpecs(): List<TableColumnSpec> =
        map { column ->
            TableColumnSpec(
                key = column.key,
                label = column.label,
                type = TableColumnType.fromStorage(column.type),
                inputType = TableColumnType.fromStorage(column.type).inputType,
                fixed = false
            )
        }

    private fun TableRowState.withSize(size: Int): TableRowState =
        TableRowState(List(size) { index -> values.getOrNull(index).orEmpty() })

    private fun TableRowState.updated(index: Int, value: String): TableRowState =
        TableRowState(values.toMutableList().apply {
            while (size <= index) add("")
            set(index, value)
        })

    private fun columnWidth(column: TableColumnSpec): Int =
        when (column.type) {
            TableColumnType.NUMBER, TableColumnType.CHECKBOX -> dp(96)
            TableColumnType.DICE -> dp(112)
            TableColumnType.TEXT -> dp(180)
        }

    private fun Bundle.customColumnsFromArgs(): List<CustomLevelColumnDraft> {
        val keys = getStringArrayList(ARG_CUSTOM_COLUMN_KEYS).orEmpty()
        val labels = getStringArrayList(ARG_CUSTOM_COLUMN_LABELS).orEmpty()
        val types = getStringArrayList(ARG_CUSTOM_COLUMN_TYPES).orEmpty()
        return labels.mapIndexed { index, label ->
            CustomLevelColumnDraft(
                key = keys.getOrNull(index).orEmpty(),
                label = label,
                type = types.getOrNull(index).orEmpty()
            )
        }
    }

    private fun themeColor(attr: Int): Int =
        MaterialColors.getColor(requireContext(), attr, 0)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_TITLE = "title"
        private const val ARG_ENCODED = "encoded"
        private const val ARG_BLOCK_ROWS = "block_rows"
        private const val ARG_CUSTOM_COLUMNS_ALLOWED = "custom_columns_allowed"
        private const val ARG_COLUMN_LABELS = "column_labels"
        private const val ARG_COLUMN_TYPES = "column_types"
        private const val ARG_COLUMN_INPUT_TYPES = "column_input_types"
        private const val ARG_CUSTOM_COLUMN_KEYS = "custom_column_keys"
        private const val ARG_CUSTOM_COLUMN_LABELS = "custom_column_labels"
        private const val ARG_CUSTOM_COLUMN_TYPES = "custom_column_types"
        private val callbacks = mutableMapOf<String, (TableEditorResult) -> Unit>()

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            encoded: String,
            columns: List<TableColumnSpec>,
            blockRows: Boolean,
            customColumnsAllowed: Boolean,
            customColumns: List<CustomLevelColumnDraft>,
            onResult: (TableEditorResult) -> Unit
        ) {
            val requestKey = "table_editor_${System.nanoTime()}"
            callbacks[requestKey] = onResult
            TableEditorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_REQUEST_KEY, requestKey)
                    putString(ARG_TITLE, title)
                    putString(ARG_ENCODED, encoded)
                    putBoolean(ARG_BLOCK_ROWS, blockRows)
                    putBoolean(ARG_CUSTOM_COLUMNS_ALLOWED, customColumnsAllowed)
                    putStringArrayList(ARG_COLUMN_LABELS, ArrayList(columns.map { it.label }))
                    putStringArrayList(ARG_COLUMN_TYPES, ArrayList(columns.map { it.type.storageValue }))
                    putIntegerArrayList(ARG_COLUMN_INPUT_TYPES, ArrayList(columns.map { it.inputType }))
                    putStringArrayList(ARG_CUSTOM_COLUMN_KEYS, ArrayList(customColumns.map { it.key }))
                    putStringArrayList(ARG_CUSTOM_COLUMN_LABELS, ArrayList(customColumns.map { it.label }))
                    putStringArrayList(ARG_CUSTOM_COLUMN_TYPES, ArrayList(customColumns.map { it.type }))
                }
            }.show(fragmentManager, "table_editor_$requestKey")
        }
    }
}
