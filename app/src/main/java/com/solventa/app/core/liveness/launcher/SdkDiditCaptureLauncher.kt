package com.solventa.app.core.liveness.launcher

import android.content.Context
import androidx.activity.ComponentActivity
import com.solventa.app.data.activation.SdkStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.didit.sdk.DiditSdk
import me.didit.sdk.DiditSdkState
import me.didit.sdk.VerificationResult

/**
 * SDK oficial de Didit (solo demostración, `solventa.diditSdkEnabled=true`).
 * El backend crea la sesión con costo cero (sandbox o passive liveness gratuito) y lee la decisión;
 * el estado que entrega el SDK es solo informativo.
 */
class SdkDiditCaptureLauncher(context: Context) : DiditCaptureLauncher {
    init {
        if (!DiditSdk.isInitialized()) DiditSdk.initialize(context.applicationContext)
    }

    override suspend fun launch(activity: ComponentActivity, sessionToken: String): SdkStatus = coroutineScope {
        val result = CompletableDeferred<SdkStatus>()
        DiditSdk.startVerification(token = sessionToken) { verification ->
            result.complete(
                when (verification) {
                    is VerificationResult.Completed -> SdkStatus.COMPLETED
                    is VerificationResult.Cancelled -> SdkStatus.CANCELLED
                    else -> SdkStatus.FAILED
                },
            )
        }
        val uiLauncher = launch {
            val state = DiditSdk.state.first { it is DiditSdkState.Ready || it is DiditSdkState.Error }
            if (state is DiditSdkState.Ready) {
                DiditSdk.launchVerificationUI(activity)
            } else {
                result.complete(SdkStatus.FAILED)
            }
        }
        try {
            result.await()
        } finally {
            uiLauncher.cancel()
        }
    }
}
