package com.codemobile.files

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * Maneja el permiso de almacenamiento general (MANAGE_EXTERNAL_STORAGE en Android 11+).
 * En versiones anteriores, el permiso clásico READ/WRITE_EXTERNAL_STORAGE declarado
 * en el manifest es suficiente una vez concedido en el diálogo estándar.
 */
object PermissionHelper {

    fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // se gestiona con el diálogo estándar de permisos en versiones viejas
        }
    }

    fun requestStorageAccess(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:" + activity.packageName)
                activity.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            }
        }
    }
}
