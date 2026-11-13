package com.codemobile.exec

import android.content.Context
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Ejecuta scripts Python embebidos vía Chaquopy. No se valida nada antes de
 * ejecutar (ej. cierres de paréntesis pendientes): si el código tiene un error
 * de sintaxis, el traceback real de Python es la señal — se deja "crashear"
 * a propósito, por decisión explícita, en vez de duplicar advertencias.
 */
object PythonRunner {

    private var started = false

    fun ensureStarted(context: Context) {
        if (!started) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            started = true
        }
    }

    fun run(context: Context, code: String): ExecutionResult {
        ensureStarted(context)
        val startTime = System.currentTimeMillis()

        val outStream = ByteArrayOutputStream()
        val errStream = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err

        return try {
            System.setOut(PrintStream(outStream))
            System.setErr(PrintStream(errStream))

            val py = Python.getInstance()
            val builtins = py.getModule("builtins")
            // exec(code) sin un diccionario de globals explícito intenta inspeccionar
            // el frame del llamador (vía sys._getframe) para resolverlos — y llamado
            // desde Java, sin un frame Python real detrás, eso falla con un error de
            // frame no disponible. Pasar un dict nuevo evita esa inspección por completo.
            val globals = py.getModule("builtins").callAttr("dict")
            builtins.callAttr("exec", code, globals)

            val duration = System.currentTimeMillis() - startTime
            val errText = errStream.toString().ifBlank { null }
            ExecutionResult(outStream.toString(), errText, duration)
        } catch (e: PyException) {
            val duration = System.currentTimeMillis() - startTime
            ExecutionResult(outStream.toString(), e.message ?: e.toString(), duration)
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
    }
}
