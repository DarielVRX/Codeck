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
     *  simplemente mover el cursor ahí. El punto de inicio queda fijo en
     *  [selectionAnchor] desde que se activa (o desde el primer toque/flecha),
     *  y cada toque o flecha posterior solo mueve el otro extremo: así la
     *  selección no "salta" usando selectionEnd como ancla cambiante. */
    var selectModeActive: Boolean = false
        set(value) {
            field = value
            selectionAnchor = if (value) selectionStart else -1
        }

    /** Extremo fijo de la selección mientras selectModeActive está activo, o -1
     *  si todavía no se fijó (se fija con el primer toque/flecha tras activar). */
    var selectionAnchor: Int = -1

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
        private var deletedCloserChar: Char? = null // cierre "suelto" borrado (no el caso de abridor+cierre intacto), para reconciliar simetría después

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            if (selfEditing || s == null) return
            textChangeInFlight = true
            // Detecta borrado de un solo carácter que sea un "abridor" con su cierre
            // autocompletado todavía intacto justo después, para arrastrar el cierre también.
            if (count == 1 && after == 0) {
                val deleted = s[start]
                if (OpenFile.closerFor(deleted) != null && autoClosedPositions.contains(start + 1)) {
                    deletedOpenerInfo = start to deleted
                } else if (isCloserChar(deleted)) {
                    // Se borró un cierre "suelto" (no el caso anterior): puede dejar una
                    // apertura sin su pareja en el texto. Se reconcilia en afterTextChanged,
                    // comparando conteos reales, para no activarse por borrar basura que no
                    // rompe la simetría (p.ej. un cierre ya sobrante).
                    deletedCloserChar = deleted
                    autoClosedPositions.remove(start)
                }
            }
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (selfEditing || s == null) return
            // Detecta inserción de un solo carácter "abridor" para autocompletar su cierre.
            if (count == 1 && before == 0) {
                val inserted = s[start]
                // Las comillas son simétricas (abren Y cierran con el mismo carácter), así
                // que hay que comprobar PRIMERO si en esta posición ya hay un cierre auto-
                // insertado esperando: si no se chequea esto antes, '"'/'\'' siempre caía en
                // la rama de "nueva apertura" (closerFor las resuelve a sí mismas) y nunca
                // se detectaba como el cierre manual de una ya pendiente, duplicándose.
                if (isCloserChar(inserted) && autoClosedPositions.contains(start)) {
                    // El usuario tipeó manualmente un cierre que ya estaba autocompletado
                    // justo en esa posición: en vez de duplicarlo, se sobrescribe (se mueve el cursor).
                    typedManualCloser = start to inserted
                } else {
                    val closer = OpenFile.closerFor(inserted)
                    if (closer != null) {
                        insertedOpener = inserted
                        insertStart = start
                    }
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
                textChangeInFlight = false
                lastCursorPos = selectionEnd
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

            val deletedCloser = deletedCloserChar
            if (deletedCloser != null) {
                deletedCloserChar = null
                reconcileSymmetryAfterDelete(editable, deletedCloser)
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
            textChangeInFlight = false
            lastCursorPos = selectionEnd
        }
    }

    private fun isCloserChar(c: Char): Boolean = c == '}' || c == ')' || c == ']' || c == '"' || c == '\''

    private val OPENER_BY_CLOSER = mapOf('}' to '{', ')' to '(', ']' to '[', '"' to '"', '\'' to '\'')

    /** Cuenta, para el tipo de [closer] borrado, si en el texto actual quedó una
     *  apertura sin cierre (más aperturas que cierres de ese tipo). Si es así,
     *  se asume que el usuario borró el cierre "por error" y se vuelve a
     *  acumular en la pila de OpenFile (reaparece en la celda dedicada), para
     *  que no se pierda la señal de "cierre pendiente". No compara posiciones,
     *  solo totales, así que un cierre ya sobrante (de más) no activa nada. */
    private fun reconcileSymmetryAfterDelete(editable: Editable, closer: Char) {
        val opener = OPENER_BY_CLOSER[closer] ?: return
        val full = editable.toString()
        val openCount: Int
        val closeCount: Int
        if (opener == closer) {
            // Comillas: cada carácter alterna abrir/cerrar; una cantidad impar
            // significa una comilla sin su pareja.
            val total = full.count { it == closer }
            openCount = total
            closeCount = if (total % 2 == 0) total else total - 1
        } else {
            openCount = full.count { it == opener }
            closeCount = full.count { it == closer }
        }
        if (openCount > closeCount) {
            openFile?.pushCloser(closer)
            onPendingCloserChanged?.invoke(openFile?.peekCloser(), openFile?.pendingCount() ?: 0)
        }
    }

    init {
        addTextChangedListener(watcher)
    }

    /** Posición del cursor antes del cambio de selección en curso, para saber
     *  si avanzó (cruzó hacia la derecha) sobre algún auto-cierre pendiente.
     *  Solo se actualiza/evalúa fuera de una edición de texto en curso (ver
     *  textChangeInFlight): mientras se escribe letra a letra DENTRO de un
     *  par, la posición trackeada se desplaza junto con el cursor en el mismo
     *  ciclo de watcher y nunca debe leerse como "cruce". */
    private var lastCursorPos: Int = -1
    private var textChangeInFlight = false

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (selfEditing || textChangeInFlight) {
            lastCursorPos = selEnd
            return
        }
        checkCursorCrossedPending(selEnd)
    }

    /** Si el cursor avanzó hacia la derecha cruzando una o más posiciones
     *  auto-cerradas pendientes sin consumirlas, se asume que el usuario las
     *  abandonó: se descartan de la pila y de la celda, tantas como se hayan
     *  saltado de una vez (apiladas en el mismo orden). */
    private fun checkCursorCrossedPending(selEnd: Int) {
        val prev = lastCursorPos
        lastCursorPos = selEnd
        if (prev < 0 || autoClosedPositions.isEmpty() || selEnd <= prev) return
        val skipped = autoClosedPositions.filter { it in prev until selEnd }
        if (skipped.isEmpty()) return
        autoClosedPositions.removeAll(skipped.toSet())
        val of = openFile
        if (of != null) {
            repeat(skipped.size) { of.popCloser() }
            onPendingCloserChanged?.invoke(of.peekCloser(), of.pendingCount())
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (selectModeActive && event.action == MotionEvent.ACTION_UP) {
            val offset = getOffsetForPosition(event.x, event.y)
            if (selectionAnchor < 0) selectionAnchor = offset
            setSelection(minOf(selectionAnchor, offset), maxOf(selectionAnchor, offset))
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
