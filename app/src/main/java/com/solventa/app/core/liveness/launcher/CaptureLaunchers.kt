package com.solventa.app.core.liveness.launcher

import android.app.AlertDialog
import androidx.activity.ComponentActivity
import com.solventa.app.R
import com.solventa.app.data.activation.CaptureType
import com.solventa.app.data.activation.SdkStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Estrategia de captura por tipo (misma idea que `LivenessProvider` en el adaptador del backend):
 * el tipo llega en la sesión y cada estrategia lo resuelve a su manera. Agregar un SDK = un lanzador.
 */
interface LivenessCaptureLauncher {
    fun supports(type: CaptureType): Boolean
}

/** Captura propia: la pantalla de captura (CameraX + ML Kit) entrega las tres fotos. */
object OwnCaptureLauncher : LivenessCaptureLauncher {
    override fun supports(type: CaptureType): Boolean = type is CaptureType.Frames
}

/** Captura delegada al SDK de Didit (solo demostración). */
interface DiditCaptureLauncher : LivenessCaptureLauncher {
    override fun supports(type: CaptureType): Boolean = type is CaptureType.Sdk && type.sdkName == SDK_NAME

    /** Abre la verificación del proveedor; su estado es informativo, la decisión la lee el backend. */
    suspend fun launch(activity: ComponentActivity, sessionToken: String): SdkStatus

    companion object {
        const val SDK_NAME = "DIDIT"
    }
}

/** Factory: entrega la estrategia que soporta el tipo de captura, o `null` si esta versión no lo conoce. */
class CaptureLauncherFactory(private val launchers: List<LivenessCaptureLauncher>) {
    fun forType(type: CaptureType): LivenessCaptureLauncher? = launchers.firstOrNull { it.supports(type) }
}

/** Lanzador de Didit simulado (por defecto): no usa red ni genera costo. */
class SimulatedDiditCaptureLauncher : DiditCaptureLauncher {
    override suspend fun launch(activity: ComponentActivity, sessionToken: String): SdkStatus =
        suspendCancellableCoroutine { continuation ->
            val dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.didit_simulated_title)
                .setMessage(R.string.didit_simulated_body)
                .setCancelable(false)
                .setPositiveButton(R.string.didit_simulated_complete) { _, _ ->
                    if (continuation.isActive) continuation.resume(SdkStatus.COMPLETED)
                }
                .setNegativeButton(R.string.didit_simulated_cancel) { _, _ ->
                    if (continuation.isActive) continuation.resume(SdkStatus.CANCELLED)
                }
                .show()
            continuation.invokeOnCancellation { dialog.dismiss() }
        }
}
