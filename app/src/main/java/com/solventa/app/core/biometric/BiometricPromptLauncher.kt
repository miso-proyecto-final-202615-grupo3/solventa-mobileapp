package com.solventa.app.core.biometric

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

sealed interface BiometricPromptEvent {
    data class Succeeded(val cipher: Cipher) : BiometricPromptEvent

    /** Huella o rostro no reconocidos en un intento. */
    data object AttemptFailed : BiometricPromptEvent

    /** El usuario cerró el aviso o eligió la contraseña: no cuenta como intento fallido. */
    data object Canceled : BiometricPromptEvent

    /** El sistema operativo bloqueó la biometría por demasiados intentos. */
    data object SystemLockout : BiometricPromptEvent

    data class Error(val message: String) : BiometricPromptEvent
}

data class BiometricPromptTexts(
    val title: String,
    val subtitle: String,
    val negativeButton: String,
)

/** Muestra el aviso biométrico nativo de Android asociado a un [Cipher] del Keystore. */
class BiometricPromptLauncher(private val activity: FragmentActivity) {

    /**
     * @param closeOnFailedAttempt si es true, el aviso se cierra tras cada intento fallido
     * para que la app muestre su propio contador de intentos (pantalla "No reconocimos tu huella").
     */
    fun launch(
        cipher: Cipher,
        texts: BiometricPromptTexts,
        closeOnFailedAttempt: Boolean,
        onEvent: (BiometricPromptEvent) -> Unit,
    ) {
        var closedByApp = false
        lateinit var prompt: BiometricPrompt

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher == null) {
                    onEvent(BiometricPromptEvent.Error("Autenticación sin objeto criptográfico"))
                } else {
                    onEvent(BiometricPromptEvent.Succeeded(authenticatedCipher))
                }
            }

            override fun onAuthenticationFailed() {
                onEvent(BiometricPromptEvent.AttemptFailed)
                if (closeOnFailedAttempt) {
                    closedByApp = true
                    prompt.cancelAuthentication()
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_CANCELED ->
                        if (!closedByApp) onEvent(BiometricPromptEvent.Canceled)
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    -> onEvent(BiometricPromptEvent.Canceled)
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                    -> onEvent(BiometricPromptEvent.SystemLockout)
                    else -> onEvent(BiometricPromptEvent.Error(errString.toString()))
                }
            }
        }

        prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(texts.title)
            .setSubtitle(texts.subtitle)
            .setNegativeButtonText(texts.negativeButton)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}

@Composable
fun rememberBiometricPromptLauncher(): BiometricPromptLauncher {
    val activity = checkNotNull(LocalActivity.current as? FragmentActivity) {
        "BiometricPrompt requiere una FragmentActivity"
    }
    return remember(activity) { BiometricPromptLauncher(activity) }
}
