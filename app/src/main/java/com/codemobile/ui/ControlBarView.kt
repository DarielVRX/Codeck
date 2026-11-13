package com.codemobile.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.codemobile.R
import com.codemobile.editor.CodeEditText

/**
 * Barra de controles: SIEMPRE se coloca debajo del teclado del sistema en el
 * layout del Activity (ver activity_editor.xml), nunca encima.
 *
 * - Grupos de símbolos ({ } ▾, " ' ▾): al tocarlos, expanden expandPanel hacia
 *   arriba (entre el teclado y esta barra), sin tapar la línea del cursor.
 * - pendingCloserCell: celda dedicada que muestra el tope de la pila de cierres
 *   pendientes del archivo activo (ver OpenFile.pendingClosers). Tocarla inserta
 *   ese cierre y revela el siguiente de la pila. Si hay más de uno pendiente,
 *   muestra un contador pequeño superpuesto.
 * - selectToggle: activa el modo selección (cualquier tap extiende la selección
 *   en vez de mover el cursor).
 */
class ControlBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private lateinit var expandPanel: LinearLayout
    private lateinit var expandPanelSymbols: LinearLayout
    private lateinit var symGroupBrackets: TextView
    private lateinit var symGroupQuotes: TextView
    private lateinit var symGroupPunctuation: TextView
    private lateinit var pendingCloserCell: TextView
    private lateinit var tabKey: TextView
    private lateinit var selectToggle: TextView
    private lateinit var rotateButton: TextView
    private lateinit var arrowUp: TextView
    private lateinit var arrowDown: TextView
    private lateinit var arrowLeft: TextView
    private lateinit var arrowRight: TextView

    var target: CodeEditText? = null
    var selectModeActive: Boolean = false
        private set
    var onRotateClick: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_control_bar, this, true)
        bindViews()
        setupSymbolGroups()
        setupArrows()
        setupSelectToggle()
        setupPendingCloserCell()
        setupRotateButton()
        setupTabKey()
    }

    /** Tap inserta un tab real (\t); long-press desindenta la línea actual
     *  (mismo efecto que el gesto de pinch-in, para quien no use gestos). */
    private fun setupTabKey() {
        tabKey.setOnClickListener { insertAtCursor("\t") }
        tabKey.setOnLongClickListener {
            target?.indentCurrentLine(increase = false)
            true
        }
    }

    private fun setupRotateButton() {
        rotateButton.setOnClickListener { onRotateClick?.invoke() }
    }

    private fun bindViews() {
        expandPanel = findViewById(R.id.expandPanel)
        expandPanelSymbols = findViewById(R.id.expandPanelSymbols)
        symGroupBrackets = findViewById(R.id.symGroupBrackets)
        symGroupQuotes = findViewById(R.id.symGroupQuotes)
        symGroupPunctuation = findViewById(R.id.symGroupPunctuation)
        pendingCloserCell = findViewById(R.id.pendingCloserCell)
        tabKey = findViewById(R.id.tabKey)
        selectToggle = findViewById(R.id.selectToggle)
        rotateButton = findViewById(R.id.rotateButton)
        arrowUp = findViewById(R.id.arrowUp)
        arrowDown = findViewById(R.id.arrowDown)
        arrowLeft = findViewById(R.id.arrowLeft)
        arrowRight = findViewById(R.id.arrowRight)
    }

    private fun setupSymbolGroups() {
        symGroupBrackets.setOnClickListener {
            showExpandPanel(listOf('{', '}', '[', ']', '(', ')'))
        }
        symGroupQuotes.setOnClickListener {
            showExpandPanel(listOf('"', '\'', '`', '_', '-'))
        }
        symGroupPunctuation.setOnClickListener {
            showExpandPanel(listOf('=', ';', ':', '.', ',', '|', '&'))
        }
    }

    private fun showExpandPanel(symbols: List<Char>) {
        expandPanelSymbols.removeAllViews()
        for (sym in symbols) {
            val tv = TextView(context)
            tv.text = sym.toString()
            tv.textSize = 15f
            tv.setTextColor(resources.getColor(R.color.text_primary, context.theme))
            tv.setBackgroundResource(R.drawable.bg_symbol_group)
            tv.gravity = android.view.Gravity.CENTER
            val size = (44 * resources.displayMetrics.density).toInt()
            val margin = (6 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = margin
            tv.layoutParams = lp
            tv.setOnClickListener { insertAtCursor(sym.toString()) }
            expandPanelSymbols.addView(tv)
        }
        expandPanel.visibility = if (expandPanel.visibility == android.view.View.VISIBLE) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }
    }

    private fun insertAtCursor(s: String) {
        val editText = target ?: return
        val text = editText.text ?: return
        val start = editText.selectionStart.coerceIn(0, text.length)
        text.insert(start, s)
        editText.setSelection(start + s.length)
    }

    private fun setupArrows() {
        arrowUp.setOnClickListener { moveCursor(vertical = -1) }
        arrowDown.setOnClickListener { moveCursor(vertical = 1) }
        arrowLeft.setOnClickListener { moveCursor(horizontal = -1) }
        arrowRight.setOnClickListener { moveCursor(horizontal = 1) }
    }

    private fun moveCursor(horizontal: Int = 0, vertical: Int = 0) {
        val editText = target ?: return
        val layout = editText.layout ?: return
        val text = editText.text ?: return
        val cursor = editText.selectionStart.coerceIn(0, text.length)

        val newPos: Int = if (vertical != 0) {
            val line = layout.getLineForOffset(cursor)
            val targetLine = (line + vertical).coerceIn(0, layout.lineCount - 1)
            val col = cursor - layout.getLineStart(line)
            (layout.getLineStart(targetLine) + col).coerceAtMost(layout.getLineEnd(targetLine))
        } else {
            (cursor + horizontal).coerceIn(0, text.length)
        }

        if (selectModeActive) {
            if (editText.selectionAnchor < 0) editText.selectionAnchor = cursor
            val anchor = editText.selectionAnchor
            editText.setSelection(minOf(anchor, newPos), maxOf(anchor, newPos))
        } else {
            editText.setSelection(newPos)
        }
    }

    private fun setupSelectToggle() {
        selectToggle.setOnClickListener {
            selectModeActive = !selectModeActive
            target?.selectModeActive = selectModeActive
            selectToggle.isSelected = selectModeActive
            selectToggle.setBackgroundResource(
                if (selectModeActive) R.drawable.bg_pill_filled else R.drawable.bg_pill_outline
            )
            selectToggle.setTextColor(
                resources.getColor(
                    if (selectModeActive) R.color.bg else R.color.accent,
                    context.theme
                )
            )
        }
    }

    private fun setupPendingCloserCell() {
        pendingCloserCell.setOnClickListener {
            target?.insertPendingCloser()
        }
    }

    /** Llamado desde CodeEditText cada vez que cambia el tope o el tamaño de la pila. */
    fun updatePendingCloser(topCloser: Char?, count: Int) {
        if (topCloser == null) {
            pendingCloserCell.visibility = android.view.View.INVISIBLE
            pendingCloserCell.text = ""
        } else {
            pendingCloserCell.visibility = android.view.View.VISIBLE
            pendingCloserCell.text = if (count > 1) "$topCloser·$count" else topCloser.toString()
        }
    }
}
