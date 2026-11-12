package com.codemobile.files

import java.io.File
import java.util.ArrayDeque

/**
 * Representa un archivo abierto en una pestaña.
 *
 * El color de la pestaña lo elige el usuario al crear o guardar el archivo
 * (paleta fija, ver colors.xml tab_01..tab_16), no depende de la extensión.
 *
 * La pila [pendingClosers] implementa el comportamiento de autocompletado de pares:
 * cada vez que se abre un carácter como '{', '(', '[' o '"', se empuja su cierre
 * correspondiente. Tocar la celda dedicada de la barra de controles inserta el
 * carácter que está en el tope y lo saca de la pila. Es por archivo (no global),
 * y persiste sin importar dónde esté el cursor, para que también sirva como señal
 * de "cierre olvidado" al revisar el código.
 */
data class OpenFile(
    var file: File?,
    var displayName: String,
    var extension: String,
    var colorIndex: Int,
    var content: StringBuilder = StringBuilder(),
    var cursorPosition: Int = 0,
    var isDirty: Boolean = false
) {
    val pendingClosers: ArrayDeque<Char> = ArrayDeque()

    fun pushCloser(closer: Char) {
        pendingClosers.push(closer)
    }

    /** Devuelve el cierre a insertar y lo remueve de la pila, o null si no hay pendientes. */
    fun popCloser(): Char? = if (pendingClosers.isEmpty()) null else pendingClosers.pop()

    fun peekCloser(): Char? = if (pendingClosers.isEmpty()) null else pendingClosers.peek()

    fun pendingCount(): Int = pendingClosers.size

    companion object {
        private val OPEN_TO_CLOSE = mapOf(
            '{' to '}',
            '(' to ')',
            '[' to ']',
            '"' to '"',
            '\'' to '\''
        )

        fun closerFor(opener: Char): Char? = OPEN_TO_CLOSE[opener]

        fun untitled(colorIndex: Int, extension: String = "txt"): OpenFile {
            return OpenFile(
                file = null,
                displayName = "sin título",
                extension = extension,
                colorIndex = colorIndex
            )
        }

        fun fromFile(file: File, colorIndex: Int): OpenFile {
            val name = file.name
            val ext = name.substringAfterLast('.', "")
            val base = name.substringBeforeLast('.', name)
            val of = OpenFile(
                file = file,
                displayName = base,
                extension = ext,
                colorIndex = colorIndex
            )
            if (file.exists()) {
                of.content = StringBuilder(file.readText())
            }
            return of
        }
    }
}
