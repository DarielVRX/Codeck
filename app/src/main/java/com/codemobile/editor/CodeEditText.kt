package com.codemobile.editor

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
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

    /** Cuando está activo (toggle "Select" de la barra de controles), un tap
     *  sobre el texto extiende la selección hasta el punto tocado en vez de
     *  simplemente mover el cursor ahí. */
    var selectModeActive: Boolean = false

    private var highlighter = SyntaxHighlighter(Lang.PLAIN)
    private var selfEditing = false

    /** Posiciones (en el texto actual) de cierres autocompletados aún no "tocados" por el usuario.
     *  Se usan para decidir si un backspace sobre el abridor debe arrastrar también el cierre,
     *  y si escribir manualmente el cierre debe sobrescribir en vez de duplicar. */
    private val autoClosedPositions = sortedSetOf<Int>()

    private fun shiftAutoClosedPositions(fromIndex: Int, delta: Int) {
        if (delta == 0) return
        val updated = sortedSetOf<Int>()
        for (p in autoClosedPositions) {
            updated.add(if (p >= fromIndex) (p + delta).coerceAtLeast(0) else p)
        }
        autoClosedPositions.clear()
        autoClosedPositions.addAll(updated)
    }

    private val watcher = object : TextWatcher {
        private var insertedOpener: Char? = null
        private var insertStart: Int = -1
        private var deletedOpenerInfo: Pair<Int, Char>? = null // (posición donde quedó el cierre, carácter borrado)
        private var typedManualCloser: Pair<Int, Char>? = null // (posición, carácter) cuando el usuario tipea un cierre que coincide con uno autocompletado

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            if (selfEditing || s == null) return
            // Detecta borrado de un solo carácter que sea un "abridor" con su cierre
            // autocompletado todavía intacto justo después, para arrastrar el cierre también.
            if (count == 1 && after == 0) {
                val deleted = s[start]
                if (OpenFile.closerFor(deleted) != null && autoClosedPositions.contains(start + 1)) {
                    deletedOpenerInfo = start to deleted
                }
            }
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (selfEditing || s == null) return
            // Detecta inserción de un solo carácter "abridor" para autocompletar su cierre.
            if (count == 1 && before == 0) {
                val inserted = s[start]
                val closer = OpenFile.closerFor(inserted)
                if (closer != null) {
                    insertedOpener = inserted
                    insertStart = start
                } else if (isCloserChar(inserted) && autoClosedPositions.contains(start)) {
                    // El usuario tipeó manualmente un cierre que ya estaba autocompletado
                    // justo en esa posición: en vez de duplicarlo, se sobrescribe (se mueve el cursor).
                    typedManualCloser = start to inserted
                }
            }
            // Detecta Enter para aplicar autoindentación.
            if (count == 1 && before == 0 && s[start] == '\n') {
                handleNewLine(start)
            }
        }

        override fun afterTextChanged(editable: Editable?) {
            if (selfEditing || editable == null) return

            val manualCloser = typedManualCloser
            if (manualCloser != null) {
                typedManualCloser = null
                val (pos, _) = manualCloser
                selfEditing = true
                // El carácter ya se insertó en `pos`; lo quitamos y solo movemos el cursor,
                // porque el cierre autocompletado que ya estaba ahí cumple la misma función.
                editable.delete(pos, pos + 1)
                setSelection(pos + 1)
                selfEditing = false
                autoClosedPositions.remove(pos)
                openFile?.popCloser()
                onPendingCloserChanged?.invoke(openFile?.peekCloser(), openFile?.pendingCount() ?: 0)
                rehighlightCurrentLine(editable)
                openFile?.isDirty = true
                return
            }

            val deletedInfo = deletedOpenerInfo
            if (deletedInfo != null) {
                deletedOpenerInfo = null
                val (openerPos, _) = deletedInfo
                // Tras el backspace del abridor, el cierre autocompletado quedó en openerPos.
                if (openerPos < editable.length && autoClosedPositions.contains(openerPos)) {
                    selfEditing = true
                    editable.delete(openerPos, openerPos + 1)
                    setSelection(openerPos)
                    selfEditing = false
                    autoClosedPositions.remove(openerPos)
                    shiftAutoClosedPositions(openerPos, -1)
                    openFile?.popCloser()
                    onPendingCloserChanged?.invoke(openFile?.peekCloser(), openFile?.pendingCount() ?: 0)
                }
            }

            val opener = insertedOpener
            if (opener != null) {
                insertedOpener = null
                val closer = OpenFile.closerFor(opener)!!
                selfEditing = true
                val pos = insertStart + 1
                editable.insert(pos, closer.toString())
                setSelection(pos)
                selfEditing = false
                shiftAutoClosedPositions(pos, 1)
                autoClosedPositions.add(pos)
                openFile?.pushCloser(closer)
                onPendingCloserChanged?.invoke(openFile?.peekCloser(), openFile?.pendingCount() ?: 0)
            }
            rehighlightCurrentLine(editable)
            openFile?.isDirty = true
        }
    }

    private fun isCloserChar(c: Char): Boolean = c == '}' || c == ')' || c == ']' || c == '"' || c == '\''

    init {
        addTextChangedListener(watcher)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (selectModeActive && event.action == MotionEvent.ACTION_UP) {
            val offset = getOffsetForPosition(event.x, event.y)
            val anchor = if (selectionEnd >= 0) selectionEnd else offset
            setSelection(minOf(anchor, offset), maxOf(anchor, offset))
            return true
        }
        return super.onTouchEvent(event)
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
            shiftAutoClosedPositions(insertPos, indent.length)
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
        shiftAutoClosedPositions(pos, 1)
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
            shiftAutoClosedPositions(lineStart, unit.length)
        } else {
            var removed = 0
            while (removed < unit.length && lineStart + removed < full.length &&
                (full[lineStart + removed] == ' ' || full[lineStart + removed] == '\t')
            ) removed++
            if (removed > 0) {
                text.delete(lineStart, lineStart + removed)
                setSelection((cursor - removed).coerceAtLeast(lineStart))
                shiftAutoClosedPositions(lineStart + removed, -removed)
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
