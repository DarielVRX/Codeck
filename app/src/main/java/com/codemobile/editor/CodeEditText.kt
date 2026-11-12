package com.codemobile.editor

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import com.codemobile.files.FileRepository.Lang
import com.codemobile.files.OpenFile

/**
 * Editor de código: AppCompatEditText con autocompletado de pares (vía la pila de
 * OpenFile), autoindentación básica y resaltado de sintaxis por regex.
 *
 * Los gestos (pinch para indentar, long-press en margen derecho para seleccionar
 * línea) se manejan en el contenedor EditorPaneView, que envuelve a esta vista y
 * tiene acceso al layout completo (gutter + margen) — aquí solo vive la lógica de
 * texto en sí.
 */
class CodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    var lang: Lang = Lang.PLAIN
        set(value) {
            field = value
            highlighter = SyntaxHighlighter(value)
        }

    var openFile: OpenFile? = null
    var onPendingCloserChanged: ((Char?, Int) -> Unit)? = null

    private var highlighter = SyntaxHighlighter(Lang.PLAIN)
    private var selfEditing = false

    private val watcher = object : TextWatcher {
        private var insertedOpener: Char? = null
        private var insertStart: Int = -1

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (selfEditing || s == null) return
            // Detecta inserción de un solo carácter "abridor" para autocompletar su cierre.
            if (count == 1 && before == 0) {
                val inserted = s[start]
                val closer = OpenFile.closerFor(inserted)
                if (closer != null) {
                    insertedOpener = inserted
                    insertStart = start
                }
            }
            // Detecta Enter para aplicar autoindentación.
            if (count == 1 && before == 0 && s[start] == '\n') {
                handleNewLine(start)
            }
        }

        override fun afterTextChanged(editable: Editable?) {
            if (selfEditing || editable == null) return
            val opener = insertedOpener
            if (opener != null) {
                insertedOpener = null
                val closer = OpenFile.closerFor(opener)!!
                selfEditing = true
                val pos = insertStart + 1
                editable.insert(pos, closer.toString())
                setSelection(pos)
                selfEditing = false
                openFile?.pushCloser(closer)
                onPendingCloserChanged?.invoke(openFile?.peekCloser(), openFile?.pendingCount() ?: 0)
            }
            rehighlightCurrentLine(editable)
            openFile?.isDirty = true
        }
    }

    init {
        addTextChangedListener(watcher)
    }

    private fun handleNewLine(newlineIndex: Int) {
        val text = text ?: return
        val full = text.toString()
        val lineStart = full.lastIndexOf('\n', newlineIndex - 1).let { if (it == -1) 0 else it + 1 }
        val previousLine = full.substring(lineStart, newlineIndex)
        val indent = AutoIndent.indentForNewLine(previousLine, lang)
        if (indent.isNotEmpty()) {
            selfEditing = true
            val insertPos = newlineIndex + 1
            text.insert(insertPos, indent)
            setSelection(insertPos + indent.length)
            selfEditing = false
        }
    }

    private fun rehighlightCurrentLine(editable: Editable) {
        val full = editable.toString()
        val cursor = selectionStart.coerceIn(0, full.length)
        val lineStart = full.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        var lineEnd = full.indexOf('\n', cursor)
        if (lineEnd == -1) lineEnd = full.length
        highlighter.highlightLine(editable, lineStart, lineEnd)
    }

    /** Inserta el cierre pendiente del tope de la pila (llamado por la celda dedicada). */
    fun insertPendingCloser() {
        val of = openFile ?: return
        val closer = of.popCloser() ?: return
        val text = text ?: return
        val pos = selectionStart.coerceIn(0, text.length)
        selfEditing = true
        text.insert(pos, closer.toString())
        setSelection(pos + 1)
        selfEditing = false
        onPendingCloserChanged?.invoke(of.peekCloser(), of.pendingCount())
    }

    /** Indenta o desindenta la línea donde está el cursor (usado por el gesto de pinch). */
    fun indentCurrentLine(increase: Boolean) {
        val text = text ?: return
        val full = text.toString()
        val cursor = selectionStart.coerceIn(0, full.length)
        val lineStart = full.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val unit = AutoIndent.indentUnitFor(lang)

        selfEditing = true
        if (increase) {
            text.insert(lineStart, unit)
            setSelection((cursor + unit.length).coerceAtMost(text.length))
        } else {
            var removed = 0
            while (removed < unit.length && lineStart + removed < full.length &&
                (full[lineStart + removed] == ' ' || full[lineStart + removed] == '\t')
            ) removed++
            if (removed > 0) {
                text.delete(lineStart, lineStart + removed)
                setSelection((cursor - removed).coerceAtLeast(lineStart))
            }
        }
        selfEditing = false
        rehighlightCurrentLine(text)
    }

    /** Selecciona la línea completa que contiene [charIndex] (usado por long-press en el margen). */
    fun selectLineAt(charIndex: Int) {
        val full = text?.toString() ?: return
        val idx = charIndex.coerceIn(0, full.length)
        val lineStart = full.lastIndexOf('\n', (idx - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        var lineEnd = full.indexOf('\n', idx)
        if (lineEnd == -1) lineEnd = full.length
        setSelection(lineStart, lineEnd)
    }

    /** Extiende la selección de línea actual para incluir hasta [charIndex] (drag del long-press). */
    fun extendLineSelectionTo(anchorIndex: Int, charIndex: Int) {
        val full = text?.toString() ?: return
        val a = anchorIndex.coerceIn(0, full.length)
        val b = charIndex.coerceIn(0, full.length)
        val from = minOf(a, b)
        val to = maxOf(a, b)
        val lineStart = full.lastIndexOf('\n', (from - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        var lineEnd = full.indexOf('\n', to)
        if (lineEnd == -1) lineEnd = full.length
        setSelection(lineStart, lineEnd)
    }
}
