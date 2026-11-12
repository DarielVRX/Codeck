package com.codemobile.exec

import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject

/**
 * Ejecuta scripts JavaScript con Rhino (motor JS puro JVM, sin NDK). Más lento
 * que un motor nativo tipo QuickJS, pero no requiere compilación nativa y por
 * eso compila de forma confiable en CI sin configuración adicional.
 *
 * Expone un `console.log` mínimo que acumula su salida en un buffer, ya que
 * Rhino no trae uno por defecto (no es un entorno Node/browser).
 */
object JsRunner {

    fun run(code: String): ExecutionResult {
        val startTime = System.currentTimeMillis()
        val output = StringBuilder()

        val rhino = RhinoContext.enter()
        return try {
            rhino.optimizationLevel = -1 // evita generación de bytecode, más compatible en Android
            val scope = rhino.initStandardObjects()

            val console = NativeObject()
            val logFn = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: RhinoContext?,
                    scope: org.mozilla.javascript.Scriptable?,
                    thisObj: org.mozilla.javascript.Scriptable?,
                    args: Array<out Any?>?
                ): Any {
                    output.append(args?.joinToString(" ") { RhinoContext.toString(it) } ?: "")
                    output.append("\n")
                    return org.mozilla.javascript.Undefined.instance
                }
            }
            ScriptableObject.putProperty(console, "log", logFn)
            ScriptableObject.putProperty(scope, "console", console)

            rhino.evaluateString(scope, code, "script.js", 1, null)
            val duration = System.currentTimeMillis() - startTime
            ExecutionResult(output.toString(), null, duration)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ExecutionResult(output.toString(), e.message ?: e.toString(), duration)
        } finally {
            RhinoContext.exit()
        }
    }
}
