package com.codemobile.editor

import com.codemobile.files.FileRepository.Lang

/**
 * Autoindentación básica por heurística de línea anterior (no por parseo real):
 *
 * - Si la línea anterior termina en un carácter "abridor de bloque" ({, (, [, o ':'
 *   en Python), la nueva línea hereda la indentación anterior + 1 nivel.
 * - En cualquier otro caso, la nueva línea hereda exactamente la misma indentación
 *   ("mantener sangría"), que es el comportamiento mínimo esperable.
 *
 * Usa el carácter tab real (\t) como unidad de indentación, no espacios: así el
 * ancho visual queda a criterio de cada editor (incluido uno de escritorio que
 * abra el mismo archivo después), en vez de imponer un número fijo de espacios
 * que puede chocar con la convención del proyecto.
 *
 * No hace dedent automático al escribir '}' o 'return' — eso queda fuera del MVP.
 */
object AutoIndent {

    private const val TAB = "\t"

    fun indentUnitFor(lang: Lang): String = TAB

    /**
     * Calcula la indentación a insertar después de un salto de línea, dada la
     * línea anterior completa (sin el \n) y el lenguaje activo.
     */
    fun indentForNewLine(previousLine: String, lang: Lang): String {
        val currentIndent = leadingWhitespace(previousLine)
        val trimmed = previousLine.trimEnd()
        val opensBlock = when (lang) {
            Lang.PYTHON -> trimmed.endsWith(":")
            Lang.JAVASCRIPT -> trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")
            Lang.PLAIN -> trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")
        }
        return if (opensBlock) currentIndent + indentUnitFor(lang) else currentIndent
    }

    private fun leadingWhitespace(line: String): String {
        val sb = StringBuilder()
        for (c in line) {
            if (c == ' ' || c == '\t') sb.append(c) else break
        }
        return sb.toString()
    }
}
