package com.codemobile.files

import android.os.Environment
import java.io.File

/**
 * Acceso simple al almacenamiento general del dispositivo (no SAF/sandbox).
 * Requiere MANAGE_EXTERNAL_STORAGE, concedido manualmente por el usuario en Ajustes
 * (ver PermissionHelper). Arranca en el almacenamiento raíz compartido.
 */
object FileRepository {

    fun rootDir(): File = Environment.getExternalStorageDirectory()

    /** Lista carpetas y archivos de [dir], carpetas primero, orden alfabético. */
    fun list(dir: File): List<File> {
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { !it.isHidden }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun createFile(dir: File, name: String): File {
        val f = File(dir, name)
        if (!f.exists()) {
            f.createNewFile()
        }
        return f
    }

    fun save(openFile: OpenFile) {
        val f = openFile.file ?: return
        f.writeText(openFile.content.toString())
        openFile.isDirty = false
    }

    /** Guarda un archivo nuevo (sin File asociado aún) en [dir] con el nombre dado. */
    fun saveAs(openFile: OpenFile, dir: File, fileName: String): File {
        val f = createFile(dir, fileName)
        f.writeText(openFile.content.toString())
        openFile.file = f
        openFile.displayName = fileName.substringBeforeLast('.', fileName)
        openFile.extension = fileName.substringAfterLast('.', "")
        openFile.isDirty = false
        return f
    }

    /** Extensiones reconocidas para elegir comportamiento de autoindentación/resaltado. */
    enum class Lang { PYTHON, JAVASCRIPT, PLAIN }

    fun langFor(extension: String): Lang = when (extension.lowercase()) {
        "py" -> Lang.PYTHON
        "js", "mjs", "cjs" -> Lang.JAVASCRIPT
        else -> Lang.PLAIN
    }
}
