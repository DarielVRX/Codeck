package com.codemobile.exec

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.codemobile.files.FileRepository.Lang
import java.util.concurrent.Executors

/**
 * Corre el script en un hilo de fondo (nunca en el hilo de UI) y entrega el
 * resultado en el hilo principal vía callback, para que el panel de consola
 * se actualice de forma segura.
 */
object ExecutionDispatcher {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun run(context: Context, code: String, lang: Lang, onResult: (ExecutionResult) -> Unit) {
        executor.execute {
            val result = when (lang) {
                Lang.PYTHON -> PythonRunner.run(context, code)
                Lang.JAVASCRIPT -> JsRunner.run(code)
                Lang.PLAIN -> ExecutionResult(
                    output = "",
                    error = "No se puede ejecutar este tipo de archivo todavía.",
                    durationMs = 0
                )
            }
            mainHandler.post { onResult(result) }
        }
    }
}
