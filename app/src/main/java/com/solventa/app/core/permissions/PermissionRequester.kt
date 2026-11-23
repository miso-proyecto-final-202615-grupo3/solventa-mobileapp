package com.solventa.app.core.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class PermissionStatus { GRANTED, DENIED, PERMANENTLY_DENIED }

/**
 * Pide permisos solo cuando se van a usar (mínimo privilegio). Distingue "denegado" de "no volver a
 * preguntar": en ese caso el sistema ya no muestra el diálogo y la UI ofrece abrir los ajustes.
 * Con varios permisos, basta con que se otorgue uno (p. ej. ubicación aproximada).
 */
@Composable
fun rememberPermissionRequester(permissions: Array<String>, onResult: (PermissionStatus) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val status = when {
            result.values.any { it } -> PermissionStatus.GRANTED
            permissions.none { context.findActivity()?.let { a -> ActivityCompat.shouldShowRequestPermissionRationale(a, it) } == true } ->
                PermissionStatus.PERMANENTLY_DENIED
            else -> PermissionStatus.DENIED
        }
        onResult(status)
    }
    return {
        if (context.isAnyGranted(*permissions)) onResult(PermissionStatus.GRANTED) else launcher.launch(permissions)
    }
}

fun Context.isAnyGranted(vararg permissions: String): Boolean =
    permissions.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
