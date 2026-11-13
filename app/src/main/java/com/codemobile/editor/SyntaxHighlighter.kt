package com.codemobile.editor

import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import com.codemobile.files.FileRepository.Lang
import java.util.regex.Pattern

/**
 * Resaltado de sintaxis por reglas regex simples, aplicado línea por línea.
 * No es un parser real: no entiende contexto profundo (ej. strings multilínea),
 * pero cubre el caso cotidiano de palabras clave, strings, comentarios y números
 * con un costo de implementación y de CPU muy bajo, razonable para un MVP.
 */
class SyntaxHighlighter(private val lang: Lang) {

    private data class Rule(val pattern: Pattern, val colorAttr: Int)

    private val rules: List<Rule> = buildRules()

    private fun buildRules(): List<Rule> {
        val keywords = when (lang) {
            Lang.PYTHON -> listOf(
                "def", "class", "return", "if", "elif", "else", "for", "while", "in", "not",
                "and", "or", "import", "from", "as", "try", "except", "finally", "with",
                "lambda", "pass", "break", "continue", "yield", "None", "True", "False", "self"
            )
            Lang.JAVASCRIPT -> listOf(
                "function", "return", "if", "else", "for", "while", "in", "of", "const", "let",
                "var", "class", "extends", "new", "try", "catch", "finally", "throw", "typeof",
                "null", "undefined", "true", "false", "this", "import", "export", "default", "async", "await"
            )
            Lang.PLAIN -> emptyList()
        }

        val list = mutableListOf<Rule>()
        if (keywords.isNotEmpty()) {
            val kwPattern = Pattern.compile("\\b(${keywords.joinToString("|")})\\b")
            list.add(Rule(kwPattern, R_KEYWORD))
        }
        // Números
        list.add(Rule(Pattern.compile("\\b\\d+(\\.\\d+)?\\b"), R_NUMBER))
        // Strings simples (sin soporte multilínea)
        list.add(Rule(Pattern.compile("\"([^\"\\\\]|\\\\.)*\""), R_STRING))
        list.add(Rule(Pattern.compile("'([^'\\\\]|\\\\.)*'"), R_STRING))
        // Nombre de función tras 'def'/'function' o antes de '('
        list.add(Rule(Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()"), R_FUNCTION))
        // Comentarios: deben ir al final para pisar coincidencias previas en esa porción de línea
        val commentPattern = if (lang == Lang.PYTHON) Pattern.compile("#.*$") else Pattern.compile("//.*$")
        list.add(Rule(commentPattern, R_COMMENT))

        return list
    }

    /** Aplica los spans de color sobre [editable] para el rango [lineStart, lineEnd). */
    fun highlightLine(editable: Editable, lineStart: Int, lineEnd: Int) {
        if (lineStart >= lineEnd) return
        // Limpia spans previos de resaltado en el rango
        val spans = editable.getSpans(lineStart, lineEnd, ForegroundColorSpan::class.java)
        for (s in spans) {
            if (editable.getSpanStart(s) >= lineStart && editable.getSpanEnd(s) <= lineEnd) {
                editable.removeSpan(s)
            }
        }
        val tabSpans = editable.getSpans(lineStart, lineEnd, TabMarkerSpan::class.java)
        for (s in tabSpans) {
            if (editable.getSpanStart(s) >= lineStart && editable.getSpanEnd(s) <= lineEnd) {
                editable.removeSpan(s)
            }
        }
        val lineText = editable.subSequence(lineStart, lineEnd).toString()
        for (rule in rules) {
            val matcher = rule.pattern.matcher(lineText)
            while (matcher.find()) {
                val start = lineStart + matcher.start()
                val end = lineStart + matcher.end()
                if (start == end) continue
                editable.setSpan(
                    ForegroundColorSpan(colorFor(rule.colorAttr)),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        // Marca visual del tab real (\t), para distinguirlo de espacios cuando
        // el mismo archivo se edita también en un editor de escritorio.
        for (i in lineText.indices) {
            if (lineText[i] == '\t') {
                val pos = lineStart + i
                editable.setSpan(
                    TabMarkerSpan(colorFor(R_COMMENT)),
                    pos, pos + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun colorFor(tag: Int): Int = when (tag) {
        R_KEYWORD -> 0xFFC586C0.toInt()
        R_STRING -> 0xFFCE9178.toInt()
        R_FUNCTION -> 0xFFDCDCAA.toInt()
        R_COMMENT -> 0xFF6A9955.toInt()
        R_NUMBER -> 0xFFB5CEA8.toInt()
        else -> 0xFFE4E4EC.toInt()
    }

    companion object {
        private const val R_KEYWORD = 1
        private const val R_STRING = 2
        private const val R_FUNCTION = 3
        private const val R_COMMENT = 4
        private const val R_NUMBER = 5
    }
}
