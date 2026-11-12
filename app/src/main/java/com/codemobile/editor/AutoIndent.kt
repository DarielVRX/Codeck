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
 * No hace dedent automático al escribir '}' o 'return' — eso queda fuera del MVP.
 */
object AutoIndent {

    private const val SPACES_PYTHON = "    "      // 4 espacios
    private const val SPACES_JS = "  "             // 2 espacios
    private const val SPACES_DEFAULT = "    "

    fun indentUnitFor(lang: Lang): String = when (lang) {
        Lang.PYTHON -> SPACES_PYTHON
        Lang.JAVASCRIPT -> SPACES_JS
        Lang.PLAIN -> SPACES_DEFAULT
    }

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
